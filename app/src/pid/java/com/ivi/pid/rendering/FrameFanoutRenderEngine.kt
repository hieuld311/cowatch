package com.ivi.pid.rendering

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Decodes once into an OES texture and draws the same frame into every registered display. */
internal class FrameFanoutRenderEngine(
    private val onOutputLost: (outputId: Int, reason: String) -> Unit
) : VideoRenderEngine {
    private val renderThread = HandlerThread("PidFrameFanout").apply { start() }
    private val renderHandler = Handler(renderThread.looper)
    private val released = AtomicBoolean(false)
    private val frameQueued = AtomicBoolean(false)
    private val frameAvailable = AtomicBoolean(false)

    private lateinit var inputSurfaceTexture: SurfaceTexture
    override lateinit var inputSurface: Surface
        private set

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var setupSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var textureId = 0
    private var program = 0
    private var positionLocation = 0
    private var textureCoordinateLocation = 0
    private var textureMatrixLocation = 0
    private val textureMatrix = FloatArray(16)
    private val outputs = linkedMapOf<Int, OutputTarget>()
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourcePixelRatio = 1f

    init {
        Matrix.setIdentityM(textureMatrix, 0)
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        renderHandler.post {
            runCatching(::initializeGl).onFailure { failure = it }
            ready.countDown()
        }
        ready.await()
        failure?.let { throw IllegalStateException("Unable to initialize PID fanout renderer", it) }
    }

    override fun addOutputAsync(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int,
        releaseSurfaceOnRemoval: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        if (released.get()) {
            onResult(false)
            return
        }
        renderHandler.post {
            onResult(addOutput(outputId, surface, width, height, releaseSurfaceOnRemoval))
        }
    }

    override fun removeOutputAsync(outputId: Int) {
        if (!released.get()) renderHandler.post { removeOutput(outputId) }
    }

    override fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float) {
        if (released.get()) return
        renderHandler.post {
            sourceWidth = width
            sourceHeight = height
            sourcePixelRatio = pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
            renderFrame()
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        val done = CountDownLatch(1)
        renderHandler.post {
            outputs.keys.toList().forEach(::removeOutput)
            inputSurface.release()
            inputSurfaceTexture.release()
            if (program != 0) GLES20.glDeleteProgram(program)
            if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            if (setupSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, setupSurface)
            }
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
            done.countDown()
        }
        done.await()
        renderThread.quitSafely()
    }

    private fun initializeGl() {
        eglDisplay = requireNotNull(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY))
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        check(
            EGL14.eglChooseConfig(
                eglDisplay,
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                ),
                0,
                configs,
                0,
                configs.size,
                configCount,
                0
            ) && configCount[0] > 0
        ) { "No EGL config" }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: ${eglError()}" }
        setupSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay,
            eglConfig,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0
        )
        check(EGL14.eglMakeCurrent(eglDisplay, setupSurface, setupSurface, eglContext)) {
            "eglMakeCurrent failed: ${eglError()}"
        }

        textureId = createOesTexture()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordinateLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureMatrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix")

        inputSurfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(
                {
                    frameAvailable.set(true)
                    scheduleFrame()
                },
                renderHandler
            )
        }
        inputSurface = Surface(inputSurfaceTexture)
        Log.i(TAG, "PID fanout renderer initialized")
    }

    private fun scheduleFrame() {
        if (released.get() || !frameQueued.compareAndSet(false, true)) return
        renderHandler.post {
            try {
                if (frameAvailable.getAndSet(false)) {
                    inputSurfaceTexture.updateTexImage()
                    inputSurfaceTexture.getTransformMatrix(textureMatrix)
                    renderFrame()
                }
            } finally {
                frameQueued.set(false)
                if (frameAvailable.get()) scheduleFrame()
            }
        }
    }

    private fun renderFrame() {
        if (outputs.isEmpty()) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(textureMatrixLocation, 1, false, textureMatrix, 0)
        outputs.keys.toList().forEach { outputId ->
            outputs[outputId]?.let { renderOutput(outputId, it) }
        }
    }

    private fun renderOutput(outputId: Int, target: OutputTarget) {
        if (!target.surface.isValid) {
            removeOutput(outputId, "Surface became invalid")
            return
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, target.eglSurface, target.eglSurface, eglContext)) {
            Log.e(TAG, "eglMakeCurrent failed for $outputId: ${eglError()}")
            removeOutput(outputId, "eglMakeCurrent failed")
            return
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val viewport = target.fitViewport()
        GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
        drawQuad()
        if (!EGL14.eglSwapBuffers(eglDisplay, target.eglSurface)) {
            Log.e(TAG, "eglSwapBuffers failed for $outputId: ${eglError()}")
            removeOutput(outputId, "eglSwapBuffers failed")
        }
    }

    private fun addOutput(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int,
        releaseSurfaceOnRemoval: Boolean
    ): Boolean {
        removeOutput(outputId)
        if (!surface.isValid) {
            if (releaseSurfaceOnRemoval) surface.release()
            return false
        }
        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "Unable to add output $outputId: ${eglError()}")
            if (releaseSurfaceOnRemoval) surface.release()
            return false
        }
        outputs[outputId] = OutputTarget(
            surface = surface,
            eglSurface = eglSurface,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            releaseSurfaceOnRemoval = releaseSurfaceOnRemoval
        )
        renderFrame()
        Log.i(TAG, "Added output $outputId ${width}x$height; count=${outputs.size}")
        return true
    }

    private fun removeOutput(outputId: Int, failureReason: String? = null) {
        val output = outputs.remove(outputId) ?: return
        EGL14.eglMakeCurrent(eglDisplay, setupSurface, setupSurface, eglContext)
        EGL14.eglDestroySurface(eglDisplay, output.eglSurface)
        if (output.releaseSurfaceOnRemoval) output.surface.release()
        Log.i(TAG, "Removed output $outputId; count=${outputs.size}")
        if (failureReason != null) onOutputLost(outputId, failureReason)
    }

    private fun OutputTarget.fitViewport(): Viewport {
        if (sourceWidth <= 0 || sourceHeight <= 0) return Viewport(0, 0, width, height)
        val videoAspect = sourceWidth * sourcePixelRatio / sourceHeight
        val outputAspect = width.toFloat() / height
        val fittedWidth: Int
        val fittedHeight: Int
        if (outputAspect > videoAspect) {
            fittedHeight = height
            fittedWidth = (height * videoAspect).toInt()
        } else {
            fittedWidth = width
            fittedHeight = (width / videoAspect).toInt()
        }
        return Viewport(
            x = (width - fittedWidth) / 2,
            y = (height - fittedHeight) / 2,
            width = fittedWidth.coerceAtLeast(1),
            height = fittedHeight.coerceAtLeast(1)
        )
    }

    private fun drawQuad() {
        VERTEX_BUFFER.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(
            positionLocation, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, VERTEX_BUFFER
        )
        VERTEX_BUFFER.position(2)
        GLES20.glEnableVertexAttribArray(textureCoordinateLocation)
        GLES20.glVertexAttribPointer(
            textureCoordinateLocation, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, VERTEX_BUFFER
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureCoordinateLocation)
    }

    private fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
        return textures[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { result ->
            GLES20.glAttachShader(result, vertex)
            GLES20.glAttachShader(result, fragment)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(result) }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        }
    }

    private fun eglError(): String = "0x${EGL14.eglGetError().toString(16)}"

    private data class OutputTarget(
        val surface: Surface,
        val eglSurface: EGLSurface,
        val width: Int,
        val height: Int,
        val releaseSurfaceOnRemoval: Boolean
    )

    private data class Viewport(val x: Int, val y: Int, val width: Int, val height: Int)

    private companion object {
        const val TAG = "PidFrameFanout"
        const val STRIDE_BYTES = 4 * 4
        val VERTEX_BUFFER = ByteBuffer.allocateDirect(4 * STRIDE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        -1f, -1f, 0f, 0f,
                        1f, -1f, 1f, 0f,
                        -1f, 1f, 0f, 1f,
                        1f, 1f, 1f, 1f
                    )
                )
                position(0)
            }
        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
