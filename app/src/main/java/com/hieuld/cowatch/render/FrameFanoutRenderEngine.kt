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
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class FrameFanoutRenderEngine : VideoRenderEngine {

    private val renderThread = HandlerThread("CoWatchFrameFanout")
    private val released = AtomicBoolean(false)

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
            ready.countDown()
        }
        ready.await()
    }

    override fun addOutput(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int
    ) {
        if (released.get()) return

        renderHandler.post {
            removeOutputOnRenderThread(outputId)

            if (!surface.isValid) return@post

            val eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                eglConfig,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )

            if (eglSurface == EGL14.EGL_NO_SURFACE) return@post

            outputs[outputId] = OutputTarget(
                surface = surface,
                eglSurface = eglSurface,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1)
            )
            renderCurrentFrame()
        }
    }

    override fun removeOutput(outputId: Int) {
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
            renderCurrentFrame()
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return

        val done = CountDownLatch(1)
        renderHandler.post {
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
                    renderHandler.post {
                        if (!released.get()) {
                            updateTexImage()
                            getTransformMatrix(textureMatrix)
                            renderCurrentFrame()
                        }
                    }
                },
                renderHandler
            )
        }
        inputSurface = Surface(inputSurfaceTexture)
    }

    private fun renderCurrentFrame() {
        if (outputs.isEmpty()) return

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniformMatrix4fv(uTexMatrixLocation, 1, false, textureMatrix, 0)

        outputs.values.forEach { target ->
            if (!target.surface.isValid) return@forEach

            EGL14.eglMakeCurrent(
                eglDisplay,
                target.eglSurface,
                target.eglSurface,
                eglContext
            )

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
            EGL14.eglSwapBuffers(eglDisplay, target.eglSurface)
        }
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
        EGL14.eglDestroySurface(eglDisplay, target.eglSurface)
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

    private data class OutputTarget(
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
        private const val STRIDE_BYTES = 4 * 4

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
