package com.hieuld.cowatch.render

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
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FrameFanoutRenderEngine : VideoRenderEngine {

    private val renderThread = HandlerThread("CoWatchFrameFanout")
    private val released = AtomicBoolean(false)
    private val frameRenderQueued = AtomicBoolean(false)
    private val frameAvailable = AtomicBoolean(false)
    private val coalescedFrameSignals = AtomicInteger(0)

    private lateinit var renderHandler: Handler
    private lateinit var inputSurfaceTexture: SurfaceTexture
    override lateinit var inputSurface: Surface
        private set

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var setupSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var oesTextureId = 0
    private var program = 0
    private var aPositionLocation = 0
    private var aTexCoordLocation = 0
    private var uTexMatrixLocation = 0

    private val textureMatrix = FloatArray(16)
    private val outputs = linkedMapOf<Int, OutputTarget>()
    private val frameOutputIds = ArrayList<Int>(EXPECTED_MAX_OUTPUTS)
    private var sourceVideoWidth = 0
    private var sourceVideoHeight = 0
    private var sourcePixelWidthHeightRatio = 1f

    init {
        Matrix.setIdentityM(textureMatrix, 0)
        renderThread.start()
        renderHandler = Handler(renderThread.looper)

        val ready = CountDownLatch(1)
        renderHandler.post {
            initGl()
            Log.d(TAG, "Renderer initialized.")
            ready.countDown()
        }
        ready.await()
    }

    override fun addOutput(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean {
        if (released.get()) {
            Log.w(TAG, "Reject output $outputId because renderer is released.")
            return false
        }

        var added = false
        runOnRenderThreadAndWait {
            added = addOutputOnRenderThread(outputId, surface, width, height)
        }

        return added
    }

    override fun addOutputAsync(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int,
        onResult: (Boolean) -> Unit
    ) {
        if (released.get()) {
            Log.w(TAG, "Reject async output $outputId because renderer is released.")
            onResult(false)
            return
        }

        renderHandler.post {
            val added = addOutputOnRenderThread(outputId, surface, width, height)
            onResult(added)
        }
    }

    override fun removeOutput(outputId: Int) {
        if (released.get()) return

        runOnRenderThreadAndWait {
            removeOutputOnRenderThread(outputId)
        }
    }

    override fun removeOutputAsync(outputId: Int) {
        if (released.get()) return

        renderHandler.post {
            removeOutputOnRenderThread(outputId)
        }
    }

    override fun setVideoSize(
        width: Int,
        height: Int,
        pixelWidthHeightRatio: Float
    ) {
        renderHandler.post {
            sourceVideoWidth = width
            sourceVideoHeight = height
            sourcePixelWidthHeightRatio = pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
            Log.d(
                TAG,
                "Source video size ${sourceVideoWidth}x$sourceVideoHeight par=$sourcePixelWidthHeightRatio"
            )
            renderCurrentFrame()
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return

        val done = CountDownLatch(1)
        renderHandler.post {
            Log.i(TAG, "Releasing renderer. outputCount=${outputs.size}")
            outputs.keys.toList().forEach(::removeOutputOnRenderThread)
            inputSurface.release()
            inputSurfaceTexture.release()

            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }

            if (oesTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                oesTextureId = 0
            }

            if (setupSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, setupSurface)
                setupSurface = EGL14.EGL_NO_SURFACE
            }

            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)

            Log.d(TAG, "Renderer released.")
            done.countDown()
        }
        done.await()
        renderThread.quitSafely()
    }

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
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
            numConfigs,
            0
        )
        eglConfig = configs[0]

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )

        setupSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay,
            eglConfig,
            intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            ),
            0
        )

        EGL14.eglMakeCurrent(eglDisplay, setupSurface, setupSurface, eglContext)

        oesTextureId = createOesTexture()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix")

        inputSurfaceTexture = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener(
                {
                    frameAvailable.set(true)
                    scheduleFrameRender()
                },
                renderHandler
            )
        }
        inputSurface = Surface(inputSurfaceTexture)
    }

    private fun scheduleFrameRender() {
        if (released.get()) return

        if (!frameRenderQueued.compareAndSet(false, true)) {
            val count = coalescedFrameSignals.incrementAndGet()
            if (count % COALESCED_FRAME_LOG_INTERVAL == 0) {
                Log.d(TAG, "Coalesced $count frame signals while render was busy.")
            }
            return
        }

        renderHandler.post {
            try {
                if (!released.get() && frameAvailable.getAndSet(false)) {
                    inputSurfaceTexture.updateTexImage()
                    inputSurfaceTexture.getTransformMatrix(textureMatrix)
                    renderCurrentFrame()
                }
            } finally {
                frameRenderQueued.set(false)
                if (frameAvailable.get()) {
                    scheduleFrameRender()
                }
            }
        }
    }

    private fun renderCurrentFrame() {
        if (outputs.isEmpty()) return

        trace(TRACE_RENDER_FRAME) {
            val frameStartNanos = SystemClock.elapsedRealtimeNanos()
            prepareTextureProgram()
            snapshotOutputIds()

            try {
                // All outputs must receive the same decoded frame; per-output skipping breaks sync.
                renderHostOutput()
                renderExternalOutputs()
            } finally {
                frameOutputIds.clear()
            }

            val renderDurationMs =
                (SystemClock.elapsedRealtimeNanos() - frameStartNanos) / 1_000_000L
            if (renderDurationMs > SLOW_FRAME_THRESHOLD_MS) {
                Log.w(
                    TAG,
                    "Slow fanout frame ${renderDurationMs}ms. outputCount=${outputs.size}"
                )
            }
        }
    }

    private fun prepareTextureProgram() {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniformMatrix4fv(uTexMatrixLocation, 1, false, textureMatrix, 0)
    }

    private fun snapshotOutputIds() {
        frameOutputIds.clear()
        outputs.keys.forEach(frameOutputIds::add)
    }

    private fun renderHostOutput() {
        for (outputId in frameOutputIds) {
            if (isHostOutput(outputId)) {
                renderOutputById(outputId, TRACE_RENDER_HOST_OUTPUT)
                return
            }
        }
    }

    private fun renderExternalOutputs() {
        for (outputId in frameOutputIds) {
            if (!isHostOutput(outputId)) {
                renderOutputById(outputId, TRACE_RENDER_EXTERNAL_OUTPUT)
            }
        }
    }

    private fun renderOutputById(outputId: Int, traceSection: String) {
        val target = outputs[outputId] ?: return
        trace(traceSection) {
            renderOutputFrame(outputId, target)
        }
    }

    private fun renderOutputFrame(outputId: Int, target: OutputTarget) {
        val outputStartNanos = SystemClock.elapsedRealtimeNanos()

        if (!target.surface.isValid) {
            Log.w(TAG, "Removing output $outputId because surface became invalid.")
            removeOutputOnRenderThread(outputId)
            return
        }

        if (!makeOutputCurrent(outputId, target)) return

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val viewport = target.fitViewport()
        GLES20.glViewport(
            viewport.x,
            viewport.y,
            viewport.width,
            viewport.height
        )
        drawFullScreenQuad()

        val swapDurationMs = swapOutputBuffers(outputId, target)
        val outputDurationMs =
            (SystemClock.elapsedRealtimeNanos() - outputStartNanos) / 1_000_000L
        logSlowOutput(outputId, target, outputDurationMs, swapDurationMs)
    }

    private fun makeOutputCurrent(outputId: Int, target: OutputTarget): Boolean {
        val current = EGL14.eglMakeCurrent(
            eglDisplay,
            target.eglSurface,
            target.eglSurface,
            eglContext
        )

        if (!current) {
            Log.e(TAG, "eglMakeCurrent failed for output $outputId: ${eglError()}")
            removeOutputOnRenderThread(outputId)
        }

        return current
    }

    private fun swapOutputBuffers(outputId: Int, target: OutputTarget): Long {
        val swapStartNanos = SystemClock.elapsedRealtimeNanos()
        val swapped = trace(TRACE_SWAP_OUTPUT_BUFFERS) {
            EGL14.eglSwapBuffers(eglDisplay, target.eglSurface)
        }
        val swapDurationMs =
            (SystemClock.elapsedRealtimeNanos() - swapStartNanos) / 1_000_000L

        if (!swapped) {
            Log.e(TAG, "eglSwapBuffers failed for output $outputId: ${eglError()}")
            removeOutputOnRenderThread(outputId)
        }

        return swapDurationMs
    }

    private fun logSlowOutput(
        outputId: Int,
        target: OutputTarget,
        outputDurationMs: Long,
        swapDurationMs: Long
    ) {
        if (outputDurationMs <= SLOW_OUTPUT_THRESHOLD_MS) return

        Log.w(
            TAG,
            "Slow ${outputRole(outputId)} output $outputId frame ${outputDurationMs}ms " +
                "swap=${swapDurationMs}ms size=${target.width}x${target.height} " +
                "outputCount=${outputs.size}"
        )
    }

    private fun isHostOutput(outputId: Int): Boolean {
        return outputId == VideoRenderEngine.HOST_OUTPUT_ID
    }

    private fun outputRole(outputId: Int): String {
        return if (isHostOutput(outputId)) "host" else "external"
    }

    private fun OutputTarget.fitViewport(): Viewport {
        if (sourceVideoWidth <= 0 || sourceVideoHeight <= 0) {
            return Viewport(0, 0, width, height)
        }

        val videoAspectRatio =
            (sourceVideoWidth * sourcePixelWidthHeightRatio) / sourceVideoHeight
        val outputAspectRatio = width.toFloat() / height.toFloat()

        val fittedWidth: Int
        val fittedHeight: Int

        if (outputAspectRatio > videoAspectRatio) {
            fittedHeight = height
            fittedWidth = (height * videoAspectRatio).toInt()
        } else {
            fittedWidth = width
            fittedHeight = (width / videoAspectRatio).toInt()
        }

        return Viewport(
            x = (width - fittedWidth) / 2,
            y = (height - fittedHeight) / 2,
            width = fittedWidth.coerceAtLeast(1),
            height = fittedHeight.coerceAtLeast(1)
        )
    }

    private fun drawFullScreenQuad() {
        VERTEX_BUFFER.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLocation)
        GLES20.glVertexAttribPointer(
            aPositionLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            VERTEX_BUFFER
        )

        VERTEX_BUFFER.position(2)
        GLES20.glEnableVertexAttribArray(aTexCoordLocation)
        GLES20.glVertexAttribPointer(
            aTexCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            VERTEX_BUFFER
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aTexCoordLocation)
    }

    private fun removeOutputOnRenderThread(outputId: Int) {
        val target = outputs.remove(outputId) ?: return
        EGL14.eglMakeCurrent(eglDisplay, setupSurface, setupSurface, eglContext)
        EGL14.eglDestroySurface(eglDisplay, target.eglSurface)
        Log.i(TAG, "Removed output $outputId. outputCount=${outputs.size}")
    }

    private fun addOutputOnRenderThread(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean {
        if (released.get()) {
            Log.w(TAG, "Reject output $outputId on render thread because renderer is released.")
            return false
        }

        removeOutputOnRenderThread(outputId)

        if (!surface.isValid) {
            Log.w(TAG, "Reject output $outputId because surface is invalid.")
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
            Log.e(TAG, "Failed to create EGL surface for output $outputId: ${eglError()}")
            return false
        }

        outputs[outputId] = OutputTarget(
            surface = surface,
            eglSurface = eglSurface,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1)
        )
        renderCurrentFrame()
        val added = outputs.containsKey(outputId)
        if (added) {
            Log.i(TAG, "Added output $outputId ${width}x$height. outputCount=${outputs.size}")
        }
        return added
    }

    private fun runOnRenderThreadAndWait(block: () -> Unit) {
        if (Looper.myLooper() == renderHandler.looper) {
            block()
            return
        }

        val done = CountDownLatch(1)
        renderHandler.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        done.await()
    }

    private fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        return textures[0]
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        return GLES20.glCreateProgram().also { glProgram ->
            GLES20.glAttachShader(glProgram, vertex)
            GLES20.glAttachShader(glProgram, fragment)
            GLES20.glLinkProgram(glProgram)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }

    private fun eglError(): String {
        return "0x${EGL14.eglGetError().toString(16)}"
    }

    private inline fun <T> trace(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    private class OutputTarget(
        val surface: Surface,
        val eglSurface: EGLSurface,
        val width: Int,
        val height: Int
    )

    private data class Viewport(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    companion object {
        private const val TAG = "FrameFanoutRender"
        private const val SLOW_FRAME_THRESHOLD_MS = 33L
        private const val SLOW_OUTPUT_THRESHOLD_MS = 16L
        private const val COALESCED_FRAME_LOG_INTERVAL = 30
        private const val EXPECTED_MAX_OUTPUTS = 5
        private const val STRIDE_BYTES = 4 * 4
        private const val TRACE_RENDER_FRAME = "CoWatch.renderFrame"
        private const val TRACE_RENDER_HOST_OUTPUT = "CoWatch.renderHostOutput"
        private const val TRACE_RENDER_EXTERNAL_OUTPUT = "CoWatch.renderExternalOutput"
        private const val TRACE_SWAP_OUTPUT_BUFFERS = "CoWatch.swapOutputBuffers"

        private val VERTEX_BUFFER = java.nio.ByteBuffer
            .allocateDirect(4 * STRIDE_BYTES)
            .order(java.nio.ByteOrder.nativeOrder())
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

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
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
