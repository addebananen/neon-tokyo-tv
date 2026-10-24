package com.lumera.app.dream.drive

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// Draws a frame of the drive with GLES 2: the browser prototype's WebGL calls, one for one.

private const val SPRITE_STRIDE = 12
private const val FACADE_STRIDE = 20
private const val ROAD_STRIDE = 6
private const val CAR_STRIDE = 12
private const val MAX_QUADS = 16000

private val SPRITE_LAYOUT = intArrayOf(3, 2, 4, 3)
private val FACADE_LAYOUT = intArrayOf(3, 2, 4, 4, 4, 3)
private val ROAD_LAYOUT = intArrayOf(3, 3)
private val CAR_LAYOUT = intArrayOf(3, 3, 2, 4)
private val SCREEN_LAYOUT = intArrayOf(2)
private val SKYLINE_LAYOUT = intArrayOf(2, 2)

/** How a building's windows are laid out: the cell, its margins, and how much of it is glass. */
private class Style(val cellX: Float, val cellY: Float, val marginX: Float, val marginY: Float, val glass: Float)

private val STYLES = arrayOf(
    Style(1.7f, 3.6f, 0.06f, 0.28f, 0.45f),
    Style(2.6f, 3.0f, 0.22f, 0.26f, 0.15f),
    Style(1.5f, 3.9f, 0.05f, 0.08f, 1.0f),
    Style(2.3f, 3.2f, 0.28f, 0.3f, 0.1f),
    Style(2.0f, 3.3f, 0.18f, 0.3f, 0.3f)
)

/** Quads written straight into a float array, drawn with one shared index buffer. */
private class Batch(val stride: Int, val max: Int) {
    val data = FloatArray(max * 4 * stride)
    val depth = FloatArray(max)
    var quads = 0
    fun reset() {
        quads = 0
    }
    fun full() = quads >= max
}

// The sun over the bay: how high its middle stands, and its radius, in radians
private const val SUN_EL = 0.15
private const val SUN_R = 0.15
// It stands off to the right of the road ahead, over the water rather than behind the towers
private const val SUN_AZ = 0.3

// The colour pairs a big screen sweeps between, one pair a slide
private val SCREEN_COLOURS = arrayOf(
    floatArrayOf(1f, 0.18f, 0.62f) to floatArrayOf(0.32f, 0.1f, 1f),
    floatArrayOf(0.1f, 0.9f, 1f) to floatArrayOf(0.12f, 0.3f, 1f),
    floatArrayOf(1f, 0.52f, 0.1f) to floatArrayOf(1f, 0.1f, 0.34f),
    floatArrayOf(0.35f, 1f, 0.62f) to floatArrayOf(0.04f, 0.5f, 0.85f),
    floatArrayOf(0.95f, 0.3f, 1f) to floatArrayOf(1f, 0.65f, 0.2f),
    floatArrayOf(1f, 0.92f, 0.3f) to floatArrayOf(1f, 0.3f, 0.2f)
)
private const val SCREEN_SLIDE = 6.5

/** What a big screen shows at a moment: the slide's words, its colours, and how far in or out it is. */
private class ScreenState {
    var words = 0
    val left = FloatArray(3)
    val right = FloatArray(3)
    var show = 0.0
    var zoom = 1.0

    fun at(b: Building, t: Double): ScreenState {
        val k = t / SCREEN_SLIDE + b.seed * 7
        val slide = floor(k)
        val into = (k - slide) * SCREEN_SLIDE
        fun pick(n: Double) = floor(hash2(n, b.seed * 50) * 997).toInt()
        val cols = SCREEN_COLOURS[pick(slide) % SCREEN_COLOURS.size]
        val prev = SCREEN_COLOURS[pick(slide - 1) % SCREEN_COLOURS.size]
        // Colours slide over in the first half second; the words fade out at the end and in at the start
        val m = smooth(into / 0.6).toFloat()
        for (c in 0 until 3) {
            left[c] = prev.first[c] + (cols.first[c] - prev.first[c]) * m
            right[c] = prev.second[c] + (cols.second[c] - prev.second[c]) * m
        }
        words = pick(slide + 101)
        show = smooth(into / 0.45) * smooth((SCREEN_SLIDE - into) / 0.35)
        zoom = 1 + 0.035 * into / SCREEN_SLIDE
        return this
    }
}

private fun colour(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

private val WHITE = colour(1f, 1f, 1f)
private val BLACK = colour(0f, 0f, 0f)
private val DARK = colour(0.035f, 0.03f, 0.05f)
private val SCREEN_FRAME = colour(0.025f, 0.022f, 0.035f)
private val BEACON_DOT = colour(1f, 0.2f, 0.15f)
private val BEACON_GLOW = colour(1f, 0.15f, 0.1f)
private val ZEBRA = colour(0.8f, 0.78f, 0.88f)
private val SIGNAL_BOX = colour(0.06f, 0.05f, 0.08f)
private val SIGNAL_GREEN = colour(0.24f, 1f, 0.72f)
private val FLYER_TRAIL = colour(0.25f, 0.95f, 1f)
private val FLYER_BELLY = colour(0.2f, 0.9f, 1f)
private val FLYER_TINT = colour(0.4f, 1f, 1f)
private val NAV_RED = colour(1f, 0.2f, 0.2f)
private val NAV_GREEN = colour(0.3f, 1f, 0.5f)
private val TRAFFIC_RED = colour(1f, 0.12f, 0.2f)
private val TRAFFIC_WHITE = colour(0.85f, 0.92f, 1f)
private val TRAFFIC_FLARE = colour(0.7f, 0.85f, 1f)
private val TAIL_RED = colour(1f, 0.1f, 0.18f)
private val INDICATOR = colour(1f, 0.56f, 0.12f)
private val HEADLIGHT_POOL = colour(0.75f, 0.85f, 1f)
private val DRIVER_TINT = colour(0.45f, 0.92f, 1f)
private val RAIN_WHITE = colour(0.9f, 0.9f, 1f)
private val NEON_CYAN = colour(0.3f, 0.9f, 1f)

private fun mixInto(out: FloatArray, a: FloatArray, b: FloatArray, t: Double): FloatArray {
    val k = t.toFloat()
    for (c in 0 until 3) out[c] = a[c] + (b[c] - a[c]) * k
    return out
}

private fun scaleInto(out: FloatArray, a: FloatArray, k: Double): FloatArray {
    val f = k.toFloat()
    for (c in 0 until 3) out[c] = a[c] * f
    return out
}

/** A linked shader program, with its uniforms looked up by name once. */
private class Program(vs: String, fs: String, attribs: Array<String>) {
    val id: Int = GLES20.glCreateProgram()
    private val uniforms = HashMap<String, Int>()

    init {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        GLES20.glAttachShader(id, v)
        GLES20.glAttachShader(id, f)
        attribs.forEachIndexed { i, a -> GLES20.glBindAttribLocation(id, i, a) }
        GLES20.glLinkProgram(id)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link failed: " + GLES20.glGetProgramInfoLog(id) }
        // Only marked for deletion: they go with the program
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
    }

    fun u(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "compile failed: " + GLES20.glGetShaderInfoLog(s) }
        return s
    }
}

private fun floatBuffer(n: Int): FloatBuffer =
    ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

/**
 * Draws the drive. It is made on the GL thread when the surface is ready and uploads the art,
 * so the bitmaps can be let go of straight after.
 */
class DriveRenderer(private val art: DriveArt) {
    private val sprite = Program(DriveShaders.SPRITE_VS, DriveShaders.SPRITE_FS, arrayOf("aPos", "aUV", "aColor", "aParams"))
    private val facade = Program(DriveShaders.FACADE_VS, DriveShaders.FACADE_FS, arrayOf("aPos", "aUV", "aWall", "aGrid", "aLook", "aAccent"))
    private val roadProgram = Program(DriveShaders.ROAD_VS, DriveShaders.ROAD_FS, arrayOf("aPos", "aRoad"))
    private val sky = Program(DriveShaders.SKY_VS, DriveShaders.SKY_FS, arrayOf("aPos"))
    private val skylineProgram = Program(DriveShaders.SKYLINE_VS, DriveShaders.SKYLINE_FS, arrayOf("aPos", "aUV"))
    private val car = Program(DriveShaders.CAR_VS, DriveShaders.CAR_FS, arrayOf("aPos", "aNormal", "aUV", "aMat"))
    private val screen = Program(DriveShaders.SCREEN_VS, DriveShaders.SCREEN_FS, arrayOf("aPos"))

    private val aniso = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty().contains("GL_EXT_texture_filter_anisotropic")
    private val atlasTex = texture(art.atlas.bitmap, mipmap = true, repeatS = false, repeatT = false, aniso = aniso)
    private val carTex = texture(art.car, mipmap = true, repeatS = false, repeatT = false, aniso = aniso)
    private val noiseTex = texture(art.noise, mipmap = true, repeatS = true, repeatT = true, aniso = aniso)
    // Wrapping top to bottom would bleed the solid foot of the skyline into a line across the sky
    private val skyFar = texture(art.skylineFar, mipmap = true, repeatS = true, repeatT = false, aniso = false)
    private val skyNear = texture(art.skylineNear, mipmap = true, repeatS = true, repeatT = false, aniso = false)

    private val sSolid = art.atlas["solid"]
    private val sGlow = art.atlas["glow"]
    private val sStreak = art.atlas["streak"]
    private val sFlare = art.atlas["flare"]
    private val sDot = art.atlas["dot"]
    private val sBeam = art.atlas["beam"]
    private val sCone = art.atlas["cone"]
    private val sScreen = art.atlas["screen"]
    private val sRain = art.atlas["rain"]
    private val sLamp = art.atlas["lamp"]
    private val sLantern = art.atlas["lantern"]
    private val sCrosswalk = art.atlas["crosswalk"]
    private val sTrafficRear = art.atlas["trafficRear"]
    private val sTrafficFront = art.atlas["trafficFront"]
    private val sFlyer = art.atlas["flyer"]
    private val sPalm = art.atlas["palm"]
    private val sTailLights = CarUV.LIGHTS.let {
        val t = CAR_TEX.toFloat()
        Sprite(it[0] / t, it[1] / t, (it[0] + it[2]) / t, (it[1] + it[3]) / t, it[2], it[3])
    }

    private val road = Batch(ROAD_STRIDE, 6000)
    private val facades = Batch(FACADE_STRIDE, 6000)
    private val decals = Batch(SPRITE_STRIDE, 4000)
    private val solids = Batch(SPRITE_STRIDE, 3000)
    private val glows = Batch(SPRITE_STRIDE, 8000)
    private val sorted = Batch(SPRITE_STRIDE, 8000)
    private val carMesh = buildCarMesh()
    private val carOpaque = Batch(CAR_STRIDE, 320)
    private val carGlass = Batch(CAR_STRIDE, 32)
    private val carCards = Batch(CAR_STRIDE, 4)
    private val carLights = Batch(SPRITE_STRIDE, 32)
    private val carReflection = Batch(SPRITE_STRIDE, 4)
    private val rain = Batch(SPRITE_STRIDE, 2000)
    private val lampPos = FloatArray(12)
    private val lampCol = FloatArray(12)
    private val indicator = FloatArray(2)
    private val sortKeys = LongArray(8000)

    private val indexBuffer: Int
    private val vertexBuffer: Int
    private val quadBuffer: Int
    private val skylineBuffer: Int
    // Room for the biggest batch: the building fronts
    private val upload = floatBuffer(6000 * 4 * FACADE_STRIDE)
    private val skylineData = FloatArray(16)
    private val skylineUpload = floatBuffer(16)

    private val proj = FloatArray(16)
    private val ident = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    private var width = 0
    private var height = 0
    private var focal = 1.0
    private var horizon = Cfg.HORIZON
    private var camH = 2.0

    // Scratch: positions callers work out, corners callers put together, and the helpers' own
    private val pA = FloatArray(3)
    private val pB = FloatArray(3)
    private val pC = FloatArray(3)
    private val pD = FloatArray(3)
    private val c0 = FloatArray(3)
    private val c1 = FloatArray(3)
    private val c2 = FloatArray(3)
    private val c3 = FloatArray(3)
    private val q0 = FloatArray(3)
    private val q1 = FloatArray(3)
    private val q2 = FloatArray(3)
    private val q3 = FloatArray(3)
    private val local = FloatArray(3)
    private val n = FloatArray(3)
    private val nk = FloatArray(3)
    private val mid = FloatArray(3)
    private val col1 = FloatArray(3)
    private val col2 = FloatArray(3)
    private val col3 = FloatArray(3)
    private val screenState = ScreenState()
    private val roadRowA = FloatArray(18)
    private val roadRowB = FloatArray(18)
    private val roadUs = doubleArrayOf(-95.0, -Cfg.WALK, -Cfg.ROAD, Cfg.ROAD, Cfg.WALK, 95.0)
    private val roadKinds = intArrayOf(2, 1, 0, 1, 2)

    // How the car sits this frame: where its tail touches the road, and its axes
    private val g = DoubleArray(3)
    private val axisR = DoubleArray(3)
    private val axisU = DoubleArray(3)
    private val axisF = DoubleArray(3)
    private val uRight = FloatArray(3)
    private val uForward = FloatArray(3)
    private val ambient = FloatArray(3)
    private val leftLight = FloatArray(3)
    private val rightLight = FloatArray(3)
    private val underglow = scaleRgb(CAR_UNDERGLOW, 0.8)
    private val nearLamps = arrayOfNulls<Lamp>(4)
    private val nearDist = DoubleArray(4)

    // Rain: its own numbers, so the city comes out the same with or without it
    private val rainRng = Rng(977)
    private var drops: FloatArray? = null

    init {
        val idx = ShortArray(MAX_QUADS * 6)
        for (q in 0 until MAX_QUADS) {
            val o = q * 6
            val v = q * 4
            idx[o] = v.toShort(); idx[o + 1] = (v + 1).toShort(); idx[o + 2] = (v + 2).toShort()
            idx[o + 3] = v.toShort(); idx[o + 4] = (v + 2).toShort(); idx[o + 5] = (v + 3).toShort()
        }
        val ids = IntArray(4)
        GLES20.glGenBuffers(4, ids, 0)
        indexBuffer = ids[0]
        vertexBuffer = ids[1]
        quadBuffer = ids[2]
        skylineBuffer = ids[3]
        val indices = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        indices.put(idx).flip()
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, indices, GLES20.GL_STATIC_DRAW)
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
        val quad = floatBuffer(8).put(corners)
        quad.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, quad, GLES20.GL_STATIC_DRAW)
    }

    // --- GL plumbing ---------------------------------------------------------------------------

    /** A texture from a bitmap, with premultiplied alpha as the bitmap keeps it. */
    private fun texture(bitmap: Bitmap, mipmap: Boolean, repeatS: Boolean, repeatT: Boolean, aniso: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (mipmap) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, if (repeatS) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, if (repeatT) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE)
        if (mipmap) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        if (aniso) GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, TEXTURE_MAX_ANISOTROPY, 4f)
        return ids[0]
    }

    private fun use(prog: Program, layout: IntArray) {
        GLES20.glUseProgram(prog.id)
        var stride = 0
        for (k in layout) stride += k
        stride *= 4
        var offset = 0
        for (i in 0 until 6) {
            if (i < layout.size) {
                GLES20.glEnableVertexAttribArray(i)
                GLES20.glVertexAttribPointer(i, layout[i], GLES20.GL_FLOAT, false, stride, offset)
                offset += layout[i] * 4
            } else {
                GLES20.glDisableVertexAttribArray(i)
            }
        }
    }

    private fun draw(b: Batch, prog: Program, layout: IntArray) {
        if (b.quads == 0) return
        val count = b.quads * 4 * b.stride
        upload.clear()
        upload.put(b.data, 0, count)
        upload.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, count * 4, upload, GLES20.GL_STREAM_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        use(prog, layout)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, b.quads * 6, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun fullscreen(prog: Program) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        use(prog, SCREEN_LAYOUT)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    // --- Writing quads -------------------------------------------------------------------------

    private fun spriteVertex(
        d: FloatArray, i: Int, p: FloatArray, u: Float, v: Float, col: FloatArray,
        alpha: Float, blend: Float, hot: Float, fog: Float
    ) {
        d[i] = p[0]; d[i + 1] = p[1]; d[i + 2] = p[2]
        d[i + 3] = u; d[i + 4] = v
        d[i + 5] = col[0]; d[i + 6] = col[1]; d[i + 7] = col[2]; d[i + 8] = alpha
        d[i + 9] = blend; d[i + 10] = hot; d[i + 11] = fog
    }

    /** A sprite quad: corners bottom left, bottom right, top right, top left. */
    private fun quad(
        b: Batch, a: FloatArray, bb: FloatArray, c: FloatArray, d: FloatArray, sp: Sprite, col: FloatArray,
        alpha: Double, blend: Double, hot: Double, fog: Double, flipV: Boolean = false
    ) {
        if (b.full()) return
        val o = b.quads * 4 * SPRITE_STRIDE
        val v0 = if (flipV) sp.v0 else sp.v1
        val v1 = if (flipV) sp.v1 else sp.v0
        val al = alpha.toFloat()
        val bl = blend.toFloat()
        val ht = hot.toFloat()
        val fg = fog.toFloat()
        spriteVertex(b.data, o, a, sp.u0, v0, col, al, bl, ht, fg)
        spriteVertex(b.data, o + SPRITE_STRIDE, bb, sp.u1, v0, col, al, bl, ht, fg)
        spriteVertex(b.data, o + 2 * SPRITE_STRIDE, c, sp.u1, v1, col, al, bl, ht, fg)
        spriteVertex(b.data, o + 3 * SPRITE_STRIDE, d, sp.u0, v1, col, al, bl, ht, fg)
        b.depth[b.quads] = (a[2] + bb[2] + c[2] + d[2]) * 0.25f
        b.quads++
    }

    private fun set(out: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        out[0] = x; out[1] = y; out[2] = z
        return out
    }

    /** A sprite facing the camera, standing on its bottom middle point (x, y, z). */
    private fun billboard(
        b: Batch, x: Float, y: Float, z: Float, w: Double, h: Double, sp: Sprite, col: FloatArray,
        alpha: Double, blend: Double, hot: Double, fog: Double
    ) {
        val hw = (w / 2).toFloat()
        val top = y + h.toFloat()
        quad(b, set(q0, x - hw, y, z), set(q1, x + hw, y, z), set(q2, x + hw, top, z), set(q3, x - hw, top, z), sp, col, alpha, blend, hot, fog)
    }

    private fun billboard(
        b: Batch, p: FloatArray, w: Double, h: Double, sp: Sprite, col: FloatArray,
        alpha: Double, blend: Double, hot: Double, fog: Double
    ) = billboard(b, p[0], p[1], p[2], w, h, sp, col, alpha, blend, hot, fog)

    /** A sprite centred on (x, y, z), facing the camera. */
    private fun glow(b: Batch, x: Float, y: Float, z: Float, w: Double, h: Double, sp: Sprite, col: FloatArray, alpha: Double, fog: Double) {
        val hw = (w / 2).toFloat()
        val hh = (h / 2).toFloat()
        quad(b, set(q0, x - hw, y - hh, z), set(q1, x + hw, y - hh, z), set(q2, x + hw, y + hh, z), set(q3, x - hw, y + hh, z), sp, col, alpha, 0.0, 1.0, fog)
    }

    private fun glow(b: Batch, p: FloatArray, w: Double, h: Double, sp: Sprite, col: FloatArray, alpha: Double, fog: Double) =
        glow(b, p[0], p[1], p[2], w, h, sp, col, alpha, fog)

    /** A sprite across the road at s, from u0 to u1 and y0 to y1. */
    private fun across(
        b: Batch, f: Frame, s: Double, u0: Double, u1: Double, y0: Double, y1: Double, sp: Sprite, col: FloatArray,
        alpha: Double, blend: Double, hot: Double, fog: Double
    ) {
        f.at(s, u0, y0, q0)
        f.at(s, u1, y0, q1)
        if (q0[2] < 0.5f && q1[2] < 0.5f) return
        f.at(s, u1, y1, q2)
        f.at(s, u0, y1, q3)
        quad(b, q0, q1, q2, q3, sp, col, alpha, blend, hot, fog)
    }

    /** A sprite lying flat on the road, centred at (s, u). */
    private fun flat(
        b: Batch, f: Frame, s: Double, u: Double, halfS: Double, halfU: Double, sp: Sprite, col: FloatArray,
        alpha: Double, blend: Double, fog: Double
    ) {
        f.at(s + halfS, u + halfU, 0.02, q2)
        if (q2[2] < 0.5f) return
        f.at(s - halfS, u - halfU, 0.02, q0)
        f.at(s - halfS, u + halfU, 0.02, q1)
        f.at(s + halfS, u - halfU, 0.02, q3)
        quad(b, q0, q1, q2, q3, sp, col, alpha, blend, 0.0, fog)
    }

    private fun facadeQuad(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray, u0: Double, v0: Double, u1: Double, v1: Double, b: Building) {
        val batch = facades
        if (batch.full()) return
        val st = STYLES[b.style]
        val o = batch.quads * 4 * FACADE_STRIDE
        val d = batch.data
        val acc = b.accent ?: b.crown ?: DEFAULT_ACCENT
        val seed = floor(b.seed * 61).toFloat()
        for (k in 0 until 4) {
            val i = o + k * FACADE_STRIDE
            val p = when (k) {
                0 -> p0
                1 -> p1
                2 -> p2
                else -> p3
            }
            d[i] = p[0]; d[i + 1] = p[1]; d[i + 2] = p[2]
            d[i + 3] = (if (k == 0 || k == 3) u0 else u1).toFloat()
            d[i + 4] = (if (k < 2) v0 else v1).toFloat()
            d[i + 5] = b.wall[0]; d[i + 6] = b.wall[1]; d[i + 7] = b.wall[2]; d[i + 8] = b.store.toFloat()
            d[i + 9] = st.cellX; d[i + 10] = st.cellY; d[i + 11] = st.marginX; d[i + 12] = st.marginY
            d[i + 13] = b.lit.toFloat(); d[i + 14] = st.glass; d[i + 15] = b.strip.toFloat(); d[i + 16] = seed
            d[i + 17] = acc[0]; d[i + 18] = acc[1]; d[i + 19] = acc[2]
        }
        batch.quads++
    }

    /** A sprite quad shading from colL on its left edge to colR on its right. */
    private fun quadGrad(
        b: Batch, a: FloatArray, bb: FloatArray, c: FloatArray, d: FloatArray, sp: Sprite, colL: FloatArray, colR: FloatArray,
        alpha: Double, blend: Double, hot: Double, fog: Double
    ) {
        if (b.full()) return
        quad(b, a, bb, c, d, sp, WHITE, alpha, blend, hot, fog)
        val o = (b.quads - 1) * 4 * SPRITE_STRIDE
        for (k in 0 until 4) {
            val col = if (k == 0 || k == 3) colL else colR
            b.data[o + k * SPRITE_STRIDE + 5] = col[0]
            b.data[o + k * SPRITE_STRIDE + 6] = col[1]
            b.data[o + k * SPRITE_STRIDE + 7] = col[2]
        }
    }

    /** A soft ribbon of light from a to c, facing the camera. */
    private fun trail(b: Batch, a: FloatArray, c: FloatArray, width: Double, col: FloatArray, alpha: Double) {
        val dx = c[0] - a[0]
        val dy = c[1] - a[1]
        val len = hypot(dx, dy).let { if (it == 0f) 1f else it }
        val half = (width / 2).toFloat()
        val nx = -dy / len * half
        val ny = dx / len * half
        set(q0, a[0] + nx, a[1] + ny, a[2])
        set(q1, a[0] - nx, a[1] - ny, a[2])
        set(q2, c[0] - nx, c[1] - ny, c[2])
        set(q3, c[0] + nx, c[1] + ny, c[2])
        // The streak sprite is bright at its top: the top goes at the flyer
        quad(b, q0, q1, q2, q3, sStreak, col, alpha, 0.0, 0.0, 0.7, flipV = true)
    }

    /** A building's big screen over the street: its frame, a sweep of colour, the advert's words. */
    private fun bigScreen(world: World, b: Building) {
        val f = world.frame
        val t = world.time
        val camS = world.camS
        val s0 = b.s0 + 1.5
        val s1 = s0 + b.screen
        if (s1 < camS + 1 || s0 > camS + Cfg.VIEW) return
        val side = b.side
        val y0 = b.store + 2.2
        val y1 = y0 + b.screen * 0.56
        val st = screenState.at(b, t)
        // Read from the street, the left side runs with the road and the right side against it
        val sa = if (side < 0) s0 else s1
        val sb = if (side < 0) s1 else s0
        val m = 0.22
        val dm = if (side < 0) -m else m
        val uF = side * (b.uIn - 0.03)
        val uS = side * (b.uIn - 0.06)
        val uT = side * (b.uIn - 0.09)
        f.at(sa - dm, uF, y0 - m, pA); f.at(sb + dm, uF, y0 - m, pB); f.at(sb + dm, uF, y1 + m, pC); f.at(sa - dm, uF, y1 + m, pD)
        quad(glows, pA, pB, pC, pD, sSolid, SCREEN_FRAME, 1.0, 1.0, 0.0, 1.0)
        f.at(sa, uS, y0, pA); f.at(sb, uS, y0, pB); f.at(sb, uS, y1, pC); f.at(sa, uS, y1, pD)
        quadGrad(glows, pA, pB, pC, pD, sScreen, st.left, st.right, 1.0, 1.0, 0.0, 1.0)
        if (st.show > 0.01) {
            val words = art.screens[st.words % art.screens.size]
            val w = b.screen * 0.84 * st.zoom
            val hgt = w * words.h / words.w
            val sc = (s0 + s1) / 2
            val yc = (y0 + y1) / 2
            val ta = if (side < 0) sc - w / 2 else sc + w / 2
            val tb = if (side < 0) sc + w / 2 else sc - w / 2
            f.at(ta, uT, yc - hgt / 2, pA); f.at(tb, uT, yc - hgt / 2, pB); f.at(tb, uT, yc + hgt / 2, pC); f.at(ta, uT, yc + hgt / 2, pD)
            quad(glows, pA, pB, pC, pD, words, SCREEN_WORDS, st.show, 1.0, 1.0, 1.0)
        }
        // Its light thrown out over the damp air and the pavement below
        val glowCol = mixInto(col3, st.left, st.right, 0.5)
        f.at(max((s0 + s1) / 2, camS + 2), side * (b.uIn - 0.4), (y0 + y1) / 2, pA)
        glow(glows, pA, b.screen * 1.8, b.screen * 1.15, sGlow, glowCol, 0.2, 1.0)
        flat(decals, f, (s0 + s1) / 2, side * (Cfg.WALK - 2.5), b.screen * 0.6, 3.2, sGlow, glowCol, 0.14, 0.0, 1.0)
    }

    // --- The frame -----------------------------------------------------------------------------

    fun resize(w: Int, h: Int) {
        width = w
        height = h
        focal = (w / 2.0) / tan(Cfg.HFOV / 2)
        val near = Cfg.NEAR
        val far = Cfg.FAR
        Arrays.fill(proj, 0f)
        proj[0] = (2 * focal / w).toFloat()
        proj[5] = (2 * focal / h).toFloat()
        proj[9] = (1 - 2 * horizon).toFloat()
        proj[10] = ((far + near) / (far - near)).toFloat()
        proj[11] = 1f
        proj[14] = (-2 * far * near / (far - near)).toFloat()
    }

    fun render(world: World, fade: Double) {
        val w = width
        val h = height
        val pal = world.pal
        val t = world.time
        // The director moves the horizon and the camera's height from shot to shot
        horizon = world.cam.horizon
        proj[9] = (1 - 2 * horizon).toFloat()
        camH = world.cam.h
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(pal.fog[0], pal.fog[1], pal.fog[2], 1f)
        GLES20.glClearDepthf(1f)
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        // Sky and the far city, behind everything
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(sky.id)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex)
        GLES20.glUniform1i(sky.u("uNoise"), 0)
        GLES20.glUniform2f(sky.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(sky.u("uFocal"), focal.toFloat())
        GLES20.glUniform1f(sky.u("uHorizonY"), (horizon * h).toFloat())
        // The sky's stars and clouds repeat every 20 radians, so the yaw is wrapped there to keep
        // its precision through a long night; the sun and moon are placed relative to it
        val yawSky = world.yawAbs - 20 * floor(world.yawAbs / 20)
        val yawShift = world.yawAbs - yawSky
        GLES20.glUniform1f(sky.u("uYaw"), yawSky.toFloat())
        GLES20.glUniform1f(sky.u("uTime"), (t % 3600).toFloat())
        GLES20.glUniform1f(sky.u("uCloud"), ((t * 0.0015) % 1).toFloat())
        GLES20.glUniform3fv(sky.u("uTop"), 1, pal.skyTop, 0)
        GLES20.glUniform3fv(sky.u("uMid"), 1, pal.skyMid, 0)
        GLES20.glUniform3fv(sky.u("uHorizon"), 1, pal.horizon, 0)
        GLES20.glUniform3fv(sky.u("uFog"), 1, pal.fog, 0)
        GLES20.glUniform4f(sky.u("uSun"), (world.sunAz + SUN_AZ - yawShift).toFloat(), SUN_EL.toFloat(), SUN_R.toFloat(), pal.sun.toFloat())
        GLES20.glUniform4f(sky.u("uMoon"), (world.sunAz - yawShift + 0.42).toFloat(), 0.3f, 0.032f, pal.moon.toFloat())
        GLES20.glUniform1f(sky.u("uStars"), pal.stars.toFloat())
        GLES20.glUniform1f(sky.u("uFlash"), world.flash.toFloat())
        fullscreen(sky)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        skyline(
            skyFar, 0.26, 0.62, mixInto(col1, pal.skyMid, pal.fog, 0.55), scaleInto(col2, pal.warm, 0.32),
            mixInto(col3, pal.horizon, pal.fog, 0.4), world.yawAbs, 0.9, t
        )
        skyline(
            skyNear, 0.36, 0.0, mixInto(col1, pal.skyTop, pal.fog, 0.35), scaleInto(col2, pal.warm, 0.45),
            mixInto(col3, pal.horizon, pal.fog, 0.55), world.yawAbs, 1.0, t
        )

        // Build this frame's geometry
        buildRoad(world)
        decals.reset(); solids.reset(); glows.reset(); sorted.reset(); facades.reset()
        buildCity(world)
        buildTraffic(world)
        buildCar(world)

        // The road, then what lies on it
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(roadProgram.id)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex)
        GLES20.glUniformMatrix4fv(roadProgram.u("uProj"), 1, false, proj, 0)
        GLES20.glUniform1i(roadProgram.u("uNoise"), 0)
        GLES20.glUniform3fv(roadProgram.u("uRoad"), 1, pal.road, 0)
        GLES20.glUniform3fv(roadProgram.u("uReflect"), 1, pal.reflect, 0)
        GLES20.glUniform3fv(roadProgram.u("uEdge"), 1, pal.edge, 0)
        GLES20.glUniform3fv(roadProgram.u("uFogColor"), 1, pal.fog, 0)
        GLES20.glUniform1f(roadProgram.u("uFogDensity"), pal.fogDensity.toFloat())
        GLES20.glUniform1f(roadProgram.u("uPixel"), (1 / focal).toFloat())
        GLES20.glUniform1f(roadProgram.u("uTime"), (t % 3600).toFloat())
        // Where the sun stands on the screen, for its broken reflection on the bay
        GLES20.glUniform3f(
            roadProgram.u("uSunGlint"),
            (w / 2.0 + (world.sunAz + SUN_AZ - world.yawAbs) * focal).toFloat(), pal.sun.toFloat(), (SUN_R * focal * 0.75).toFloat()
        )
        draw(road, roadProgram, ROAD_LAYOUT)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(sprite.id)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
        GLES20.glUniformMatrix4fv(sprite.u("uProj"), 1, false, proj, 0)
        GLES20.glUniform1i(sprite.u("uTex"), 0)
        GLES20.glUniform3fv(sprite.u("uFogColor"), 1, pal.fog, 0)
        GLES20.glUniform1f(sprite.u("uFogDensity"), pal.fogDensity.toFloat())
        draw(decals, sprite, SPRITE_LAYOUT)
        // Our car's reflection in the wet road
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, carTex)
        draw(carReflection, sprite, SPRITE_LAYOUT)

        // Buildings
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(facade.id)
        GLES20.glUniformMatrix4fv(facade.u("uProj"), 1, false, proj, 0)
        GLES20.glUniform3fv(facade.u("uFogColor"), 1, pal.fog, 0)
        GLES20.glUniform1f(facade.u("uFogDensity"), pal.fogDensity.toFloat())
        GLES20.glUniform1f(facade.u("uTime"), (t % 3600).toFloat())
        GLES20.glUniform1f(facade.u("uPixel"), (1 / focal).toFloat())
        GLES20.glUniform3fv(facade.u("uWarm"), 1, pal.warm, 0)
        GLES20.glUniform3fv(facade.u("uCool"), 1, pal.cool, 0)
        GLES20.glUniform3fv(facade.u("uSky"), 1, pal.horizon, 0)
        draw(facades, facade, FACADE_LAYOUT)

        // Poles and frames, then signs and everything that glows, far to near
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(sprite.id)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
        draw(solids, sprite, SPRITE_LAYOUT)
        GLES20.glDepthMask(false)
        sortInto(sorted, glows)
        draw(sorted, sprite, SPRITE_LAYOUT)

        // Our car in front of everything, with its own depth: the body, then the cabin and the glass
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(car.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, carTex)
        GLES20.glUniformMatrix4fv(car.u("uProj"), 1, false, proj, 0)
        GLES20.glUniform1i(car.u("uTex"), 0)
        GLES20.glUniform1i(car.u("uNoise"), 1)
        GLES20.glUniform3fv(car.u("uPaint"), 1, CAR_PAINT, 0)
        GLES20.glUniform3fv(car.u("uFlip"), 1, CAR_FLIP, 0)
        GLES20.glUniform3fv(car.u("uAmbient"), 1, ambient, 0)
        GLES20.glUniform3fv(car.u("uLeftLight"), 1, leftLight, 0)
        GLES20.glUniform3fv(car.u("uRightLight"), 1, rightLight, 0)
        GLES20.glUniform3fv(car.u("uSkyTop"), 1, pal.skyTop, 0)
        GLES20.glUniform3fv(car.u("uHorizon"), 1, pal.horizon, 0)
        GLES20.glUniform3fv(car.u("uGlow"), 1, underglow, 0)
        GLES20.glUniform3fv(car.u("uRight"), 1, uRight, 0)
        GLES20.glUniform3fv(car.u("uForward"), 1, uForward, 0)
        GLES20.glUniform3fv(car.u("uLampPos[0]"), 4, lampPos, 0)
        GLES20.glUniform3fv(car.u("uLampCol[0]"), 4, lampCol, 0)
        GLES20.glUniform1f(car.u("uTravel"), (world.hero.s % 96).toFloat())
        GLES20.glUniform2f(car.u("uTail"), (0.62 + 0.38 * world.hero.brake).toFloat(), world.hero.brake.toFloat())
        GLES20.glUniform2f(car.u("uIndicator"), indicator[0], indicator[1])
        draw(carOpaque, car, CAR_LAYOUT)
        GLES20.glDepthMask(false)
        draw(carCards, car, CAR_LAYOUT)
        draw(carGlass, car, CAR_LAYOUT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(sprite.id)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
        draw(carLights, sprite, SPRITE_LAYOUT)

        // Rain, drawn flat on the screen
        buildRain(world)
        GLES20.glUniformMatrix4fv(sprite.u("uProj"), 1, false, ident, 0)
        draw(rain, sprite, SPRITE_LAYOUT)

        // Vignette and the fade in
        GLES20.glUseProgram(screen.id)
        GLES20.glUniform2f(screen.u("uAspect"), 1f, (h.toDouble() / w * 1.15).toFloat())
        GLES20.glUniform4f(screen.u("uColor"), 0f, 0f, 0f, 0.62f)
        GLES20.glUniform1f(screen.u("uShape"), 1f)
        fullscreen(screen)
        if (fade < 1) {
            GLES20.glUniform4f(screen.u("uColor"), 0f, 0f, 0f, (1 - fade).toFloat())
            GLES20.glUniform1f(screen.u("uShape"), 0f)
            fullscreen(screen)
        }
    }

    private fun skyline(
        tex: Int, heightFrac: Double, sink: Double, sil: FloatArray, win: FloatArray, haze: FloatArray,
        yaw: Double, parallax: Double, t: Double
    ) {
        val w = width.toDouble()
        val h = height.toDouble()
        val bottomPx = horizon * h + 6
        val topPx = bottomPx - heightFrac * h
        val texW = 2048 * (h / 1080) * 1.5
        // The strip repeats, so only the fraction matters: kept small for the GPU's precision
        val ur = (yaw * focal * parallax) / texW + sink
        val u0 = (ur - floor(ur)).toFloat()
        val u1 = u0 + (w / texW).toFloat()
        val yb = (1 - 2 * bottomPx / h).toFloat()
        val yt = (1 - 2 * topPx / h).toFloat()
        val d = skylineData
        d[0] = -1f; d[1] = yb; d[2] = u0; d[3] = 1f
        d[4] = 1f; d[5] = yb; d[6] = u1; d[7] = 1f
        d[8] = 1f; d[9] = yt; d[10] = u1; d[11] = 0f
        d[12] = -1f; d[13] = yt; d[14] = u0; d[15] = 0f
        skylineUpload.clear()
        skylineUpload.put(d)
        skylineUpload.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skylineBuffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 16 * 4, skylineUpload, GLES20.GL_STREAM_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        val p = skylineProgram
        use(p, SKYLINE_LAYOUT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(p.u("uTex"), 0)
        GLES20.glUniform3fv(p.u("uSil"), 1, sil, 0)
        GLES20.glUniform3fv(p.u("uWin"), 1, win, 0)
        GLES20.glUniform3fv(p.u("uHaze"), 1, haze, 0)
        GLES20.glUniform1f(p.u("uTime"), (t % 3600).toFloat())
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    /**
     * Copies the quads of [src] into [dst] from far to near. Each key holds a depth that sorts as
     * a whole number, far first, above the quad's index.
     */
    private fun sortInto(dst: Batch, src: Batch) {
        val count = src.quads
        val keys = sortKeys
        for (i in 0 until count) {
            val bits = java.lang.Float.floatToRawIntBits(-src.depth[i])
            val order = if (bits < 0) bits.inv() else bits or Int.MIN_VALUE
            keys[i] = ((order.toLong() and 0xFFFFFFFFL) shl 16) or i.toLong()
        }
        Arrays.sort(keys, 0, count)
        val q = 4 * SPRITE_STRIDE
        for (k in 0 until count) {
            val i = (keys[k] and 0xFFFF).toInt()
            System.arraycopy(src.data, i * q, dst.data, k * q, q)
        }
        dst.quads = count
    }

    // --- Road ----------------------------------------------------------------------------------

    private fun roadVertex(d: FloatArray, i: Int, row: FloatArray, j: Int, s: Double, kind: Int) {
        d[i] = row[j * 3]; d[i + 1] = row[j * 3 + 1]; d[i + 2] = row[j * 3 + 2]
        d[i + 3] = roadUs[j].toFloat(); d[i + 4] = s.toFloat(); d[i + 5] = kind.toFloat()
    }

    private fun buildRoad(world: World) {
        val f = world.frame
        val b = road
        b.reset()
        val base = floor(world.camS / Cfg.PERIOD) * Cfg.PERIOD - Cfg.PERIOD
        var prev: FloatArray? = null
        var prevS = 0.0
        var prevWater = 0
        for (k in 0 until f.n) {
            val s = f.s0 + k * Cfg.DS
            val row = if (k and 1 == 1) roadRowB else roadRowA
            for (j in 0 until 6) {
                f.at(s, roadUs[j], 0.0, pA)
                row[j * 3] = pA[0]; row[j * 3 + 1] = pA[1]; row[j * 3 + 2] = pA[2]
            }
            val water = if (world.districtAt(s).boulevard) 3 else 2
            if (prev != null && row[2 * 3 + 2] > -2) {
                for (j in 0 until 5) {
                    if (b.full()) break
                    val kind = roadKinds[j]
                    val kA = if (j == 4 && prevWater == 3) 3 else kind
                    val kB = if (j == 4 && water == 3) 3 else kind
                    val o = b.quads * 4 * ROAD_STRIDE
                    roadVertex(b.data, o, prev, j, prevS - base, kA)
                    roadVertex(b.data, o + ROAD_STRIDE, prev, j + 1, prevS - base, kA)
                    roadVertex(b.data, o + 2 * ROAD_STRIDE, row, j + 1, s - base, kB)
                    roadVertex(b.data, o + 3 * ROAD_STRIDE, row, j, s - base, kB)
                    b.quads++
                }
            }
            prev = row
            prevS = s
            prevWater = water
        }
    }

    // --- The city ------------------------------------------------------------------------------

    private fun buildCity(world: World) {
        val f = world.frame
        val t = world.time
        val camS = world.camS
        val far = camS + Cfg.VIEW

        val buildings = world.buildings
        for (bi in buildings.indices) {
            val b = buildings[bi]
            if (b.s0 > far || b.s1 < camS) continue
            val side = b.side
            val uf = side * b.uIn
            // The face along the street, in pieces so it follows the bends
            val len = b.s1 - b.s0
            val pieces = max(1, ceil(len / 6).toInt())
            for (i in 0 until pieces) {
                val sa = b.s0 + len * i / pieces
                val sb = b.s0 + len * (i + 1) / pieces
                if (sb < camS + 0.4) continue
                f.at(sa, uf, 0.0, pA); f.at(sb, uf, 0.0, pB); f.at(sb, uf, b.h, pC); f.at(sa, uf, b.h, pD)
                facadeQuad(pA, pB, pC, pD, sa - b.s0, 0.0, sb - b.s0, b.h, b)
            }
            // The end facing us, seen over lower buildings and across gaps
            if (b.s0 > camS + 0.5) {
                val uo = side * (b.uIn + b.depth)
                f.at(b.s0, uf, 0.0, pA); f.at(b.s0, uo, 0.0, pB); f.at(b.s0, uo, b.h, pC); f.at(b.s0, uf, b.h, pD)
                facadeQuad(pA, pB, pC, pD, 0.0, 0.0, b.depth, b.h, b)
            }
            // A neon tube up the near corner
            val neon = b.neon
            if (neon != null && b.s0 > camS + 1) {
                val p = f.at(b.s0, uf, 0.3, pA)
                val hgt = min(b.h - 0.6, 30.0)
                // The tube in its own colour with a hot core, then its glow on the damp air
                billboard(glows, p, 0.12, hgt, sBeam, neon, 1.0, 0.0, 0.35, 1.0)
                billboard(glows, p[0], p[1], p[2] - 0.01f, 0.7, hgt, sBeam, neon, 0.38, 0.0, 0.0, 1.0)
                billboard(glows, p[0], p[1], p[2] - 0.02f, 2.6, hgt, sBeam, neon, 0.12, 0.0, 0.0, 1.0)
            }
            if (b.screen > 0) bigScreen(world, b)
            // A lit crown and a red light on the top of tall ones
            val crown = b.crown
            if (crown != null) {
                val cs = max(b.s0, camS + 1)
                if (cs < b.s1) {
                    across(glows, f, b.s0 + 0.01, uf, side * (b.uIn + b.depth), b.h - 1.6, b.h - 0.8, sSolid, crown, 0.85, 0.0, 0.5, 1.0)
                    val ui = uf - side * 0.05
                    f.at(cs, ui, b.h - 1.6, pA); f.at(b.s1, ui, b.h - 1.6, pB); f.at(b.s1, ui, b.h - 0.8, pC); f.at(cs, ui, b.h - 0.8, pD)
                    quad(glows, pA, pB, pC, pD, sSolid, crown, 0.85, 0.0, 0.5, 1.0)
                }
                val blink = max(0.0, sin(t * 2.4 + b.seed * 20)).pow(6)
                if (b.s0 > camS + 1) {
                    val p = f.at(b.s0, uf, b.h + 0.6, pA)
                    glow(glows, p, 0.7, 0.7, sDot, BEACON_DOT, 0.3 + 0.7 * blink, 0.6)
                    glow(glows, p, 5.0, 5.0, sGlow, BEACON_GLOW, 0.25 * blink, 0.6)
                }
            }
        }

        // Signs
        val signs = world.signs
        for (si in signs.indices) {
            val s = signs[si]
            if (s.s > far || s.s < camS + 0.5) continue
            var lit = if (s.off) 0.1 else 1.0
            if (s.flicker >= 0 && !s.off) {
                val phase = t * 0.21 + s.flicker * 17
                if (phase - floor(phase) < 0.22) lit = if (hash2(floor(t * 13), s.seed * 100) > 0.45) 1.0 else 0.18
            }
            val col = s.tint
            if (s.kind == 'f') {
                val side = if (s.u < 0) -1 else 1
                val sa = if (side < 0) s.s - s.w / 2 else s.s + s.w / 2
                val sb = if (side < 0) s.s + s.w / 2 else s.s - s.w / 2
                if (min(sa, sb) < camS + 0.5) continue
                f.at(sa, s.u, s.y, pA); f.at(sb, s.u, s.y, pB); f.at(sb, s.u, s.y + s.h, pC); f.at(sa, s.u, s.y + s.h, pD)
                quad(glows, pA, pB, pC, pD, s.sprite, col, lit, 1.0, 1.0, 1.0)
            } else {
                across(glows, f, s.s, s.u - s.w / 2, s.u + s.w / 2, s.y, s.y + s.h, s.sprite, col, lit, 1.0, 1.0, 1.0)
                if (lit > 0.5) {
                    val c = f.at(s.s - 0.05, s.u, s.y + s.h / 2, pA)
                    glow(glows, c, s.w * 3.2, s.h * 1.6, sGlow, col, 0.2 * lit, 1.0)
                    // Its smeared reflection in the wet pavement
                    across(decals, f, s.s, s.u - s.w * 0.45, s.u + s.w * 0.45, -s.y + 0.4, -s.y - s.h - 4, sStreak, col, 0.24 * lit, 0.0, 1.0, 1.0)
                }
            }
        }

        // Street lamps
        val lamps = world.lamps
        for (li in lamps.indices) {
            val l = lamps[li]
            if (l.s > far || l.s < camS - 2) continue
            val side = l.side
            val up = side * 7.5
            val uh = side * 4.7
            val near = l.s - camS < 260
            if (near && l.s > camS + 0.4) {
                across(solids, f, l.s, up - 0.09, up + 0.09, 0.0, 8.0, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
                across(solids, f, l.s, min(up, uh), max(up, uh), 7.86, 7.98, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
            }
            if (near && l.s > camS + 1.5) {
                // Its cone of light through the damp air, plainer in the rain
                val top = f.at(l.s, uh, 7.62, pA)
                val bot = f.at(l.s, uh * 0.92, 0.05, pB)
                val a = 0.07 + 0.1 * world.pal.rain
                quad(
                    glows, set(c0, bot[0] - 2.4f, bot[1], bot[2]), set(c1, bot[0] + 2.4f, bot[1], bot[2]),
                    set(c2, top[0] + 0.55f, top[1], top[2]), set(c3, top[0] - 0.55f, top[1], top[2]), sCone, l.color, a, 0.0, 0.0, 1.0
                )
            }
            if (l.s > camS + 0.4) {
                across(glows, f, l.s - 0.02, uh - 0.65, uh + 0.65, 7.68, 7.9, sLamp, l.color, 1.0, 0.0, 1.0, 1.0)
                val p = f.at(l.s - 0.05, uh, 7.75, pA)
                glow(glows, p, 4.2, 3.0, sGlow, l.color, 0.5, 1.0)
            }
            flat(decals, f, l.s, uh * 0.9, 6.0, 4.6, sGlow, l.color, 0.15, 0.0, 1.0)
            if (l.s > camS + 0.4) across(decals, f, l.s, uh - 0.5, uh + 0.5, -1.5, -16.0, sStreak, l.color, 0.32, 0.0, 1.0, 1.0)
        }

        // Lanterns across the street
        val lanterns = world.lanterns
        for (ni in lanterns.indices) {
            val lan = lanterns[ni]
            if (lan.s > far || lan.s < camS + 0.5) continue
            val count = lan.count
            for (i in 0 until count) {
                val k = i.toDouble() / (count - 1)
                val u = lerp(-9.6, 9.6, k) + 0.06 * sin(t * 1.3 + i * 0.9 + lan.seed * 10)
                val y = lan.y - lan.sag * (1 - (u / 9.6).pow(2))
                val p = f.at(lan.s, u, y - 0.62, pA)
                billboard(glows, p, 0.44, 0.62, sLantern, lan.tint, 1.0, 1.0, 0.6, 1.0)
                val c = f.at(lan.s - 0.02, u, y - 0.3, pB)
                glow(glows, c, 1.9, 1.9, sGlow, lan.tint, 0.3, 1.0)
            }
        }

        // Gantries over the road
        val gantries = world.gantries
        for (gi in gantries.indices) {
            val gy = gantries[gi]
            if (gy.s > far || gy.s < camS + 0.5) continue
            across(solids, f, gy.s, -7.6, -7.25, 0.0, 6.6, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
            across(solids, f, gy.s, 7.25, 7.6, 0.0, 6.6, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
            across(solids, f, gy.s, -7.6, 7.6, 6.25, 6.62, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
            across(glows, f, gy.s - 0.05, -6.6, -0.4, 6.66, 8.21, art.gantry[gy.board], gy.tint, 1.0, 1.0, 0.85, 1.0)
            val c = f.at(gy.s - 0.1, -3.5, 7.4, pA)
            glow(glows, c, 10.0, 4.5, sGlow, gy.tint, 0.1, 1.0)
            across(decals, f, gy.s, -6.0, -1.0, -6.2, -16.0, sStreak, gy.tint, 0.14, 0.0, 1.0, 1.0)
        }

        // Crossings: the zebra stripes and the traffic light hung over our lanes
        val crossings = world.crossings
        for (ci in crossings.indices) {
            val c = crossings[ci]
            if (c.s > far || c.s + c.w < camS) continue
            for (end in 0 until 2) {
                val s0 = if (end == 0) c.s - 4.2 else c.s + c.w + 1.2
                for (k in 0 until 15) {
                    val u = -6.3 + k * 0.9
                    flat(decals, f, s0 + 1.5, u, 1.5, 0.22, sCrosswalk, ZEBRA, 0.55, 1.0, 1.0)
                }
            }
            flat(decals, f, c.s - 5.4, -3.4, 0.2, 3.3, sSolid, ZEBRA, 0.6, 1.0, 1.0)
            val sl = c.s - 4.6
            if (sl > camS + 0.5) {
                across(solids, f, sl, -7.6, -7.4, 0.0, 5.9, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
                across(solids, f, sl, -7.5, -3.0, 5.62, 5.74, sSolid, DARK, 1.0, 1.0, 0.0, 1.0)
                across(solids, f, sl, -4.35, -2.95, 5.12, 5.6, sSolid, SIGNAL_BOX, 1.0, 1.0, 0.0, 1.0)
                val p = f.at(sl - 0.05, -3.25, 5.36, pA)
                glow(glows, p, 0.36, 0.36, sDot, SIGNAL_GREEN, 1.0, 1.0)
                glow(glows, p, 2.4, 2.4, sGlow, SIGNAL_GREEN, 0.35, 1.0)
                across(decals, f, sl, -3.55, -2.95, -4.5, -13.0, sStreak, SIGNAL_GREEN, 0.22, 0.0, 1.0, 1.0)
            }
        }

        // Palms on the boulevard
        val palms = world.palms
        for (pi in palms.indices) {
            val p = palms[pi]
            if (p.s > far || p.s < camS + 0.5) continue
            val w = (p.h * 300 / 520).toFloat()
            val base = f.at(p.s, p.u, 0.0, pA)
            val lean = (p.lean * p.h).toFloat()
            val top = base[1] + p.h.toFloat()
            quad(
                glows, set(c0, base[0] - w / 2, base[1], base[2]), set(c1, base[0] + w / 2, base[1], base[2]),
                set(c2, base[0] + w / 2 + lean, top, base[2]), set(c3, base[0] - w / 2 + lean, top, base[2]), sPalm, p.tint, 1.0, 1.0, 0.7, 1.0
            )
        }

        // Flying cars: a dark body, a lit belly, blinking lights and a trail
        val flyers = world.flyers
        for (fi in flyers.indices) {
            val fl = flyers[fi]
            val p = f.at(fl.s, fl.u, fl.y, pA)
            if (p[2] < 2) continue
            val fadeIn = smooth(fl.age / 1.5) * smooth((fl.life - fl.age) / 2)
            val back = f.at(fl.s - fl.vs * 0.9, fl.u - fl.vu * 0.9, fl.y - fl.vy * 0.9, pB)
            trail(glows, back, p, 0.55, FLYER_TRAIL, 0.4 * fadeIn)
            glow(glows, p[0], p[1] - 0.5f, p[2], 6.5, 2.2, sGlow, FLYER_BELLY, 0.45 * fadeIn, 0.7)
            billboard(glows, p[0], p[1] - 0.9f, p[2] - 0.01f, 4.4, 1.76, sFlyer, FLYER_TINT, fadeIn, 1.0, 0.0, 0.7)
            val blink = if (floor(t * 2.2 + fl.seed * 7).toLong() % 2 == 0L) 1.0 else 0.15
            glow(glows, p[0] - 2.1f, p[1] - 0.2f, p[2] - 0.02f, 0.5, 0.5, sDot, NAV_RED, blink * fadeIn, 0.7)
            glow(glows, p[0] + 2.1f, p[1] - 0.2f, p[2] - 0.02f, 0.5, 0.5, sDot, NAV_GREEN, blink * fadeIn, 0.7)
            val strobe = if ((t * 1.3 + fl.seed) % 1 < 0.06) 1.0 else 0.0
            glow(glows, p[0], p[1] + 0.1f, p[2] - 0.02f, 2.2, 2.2, sGlow, WHITE, strobe * fadeIn, 0.7)
        }
    }

    // --- Traffic -------------------------------------------------------------------------------

    private fun buildTraffic(world: World) {
        val f = world.frame
        val camS = world.camS
        val cars = world.cars
        for (ci in cars.indices) {
            val c = cars[ci]
            if (c.s < camS + 1.5 || c.s > camS + Cfg.VIEW) continue
            val p = f.at(c.s, c.u, 0.0, pA)
            if (c.dir > 0) {
                billboard(glows, p, 1.9, 1.27, sTrafficRear, WHITE, 1.0, 1.0, 0.0, 1.0)
                for (side in 0 until 2) {
                    val dx = if (side == 0) -0.62 else 0.62
                    glow(glows, p[0] + dx.toFloat(), p[1] + 0.68f, p[2] - 0.02f, 1.2, 0.8, sGlow, TRAFFIC_RED, 0.7, 1.0)
                    across(decals, f, c.s, c.u + dx - 0.18, c.u + dx + 0.18, -0.35, -3.6, sStreak, TRAFFIC_RED, 0.4, 0.0, 1.0, 1.0)
                }
            } else {
                billboard(glows, p, 1.9, 1.27, sTrafficFront, WHITE, 1.0, 1.0, 0.0, 1.0)
                for (side in 0 until 2) {
                    val dx = if (side == 0) -0.6 else 0.6
                    val x = p[0] + dx.toFloat()
                    val y = p[1] + 0.68f
                    val z = p[2] - 0.02f
                    glow(glows, x, y, z, 1.8, 1.4, sGlow, TRAFFIC_WHITE, 0.85, 1.0)
                    glow(glows, x, y, z, 6.5, 0.36, sFlare, TRAFFIC_FLARE, 0.4, 1.0)
                    across(decals, f, c.s, c.u + dx - 0.25, c.u + dx + 0.25, -0.3, -6.0, sStreak, TRAFFIC_WHITE, 0.42, 0.0, 1.0, 1.0)
                }
                flat(decals, f, c.s - 9, c.u, 8.0, 2.2, sGlow, TRAFFIC_WHITE, 0.1, 0.0, 1.0)
            }
        }
    }

    // --- Our car -------------------------------------------------------------------------------

    /** A point of the car (metres: right, up, forward from the middle of its tail) in camera space. */
    private fun xf(x: Double, y: Double, z: Double, out: FloatArray): FloatArray {
        out[0] = (g[0] + axisR[0] * x + axisU[0] * y + axisF[0] * z).toFloat()
        out[1] = (g[1] + axisR[1] * x + axisU[1] * y + axisF[1] * z).toFloat()
        out[2] = (g[2] + axisR[2] * x + axisU[2] * y + axisF[2] * z).toFloat()
        return out
    }

    private fun xf(p: FloatArray, out: FloatArray) = xf(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), out)

    private fun rot(v: FloatArray, out: FloatArray): FloatArray {
        val x = v[0].toDouble()
        val y = v[1].toDouble()
        val z = v[2].toDouble()
        out[0] = (axisR[0] * x + axisU[0] * y + axisF[0] * z).toFloat()
        out[1] = (axisR[1] * x + axisU[1] * y + axisF[1] * z).toFloat()
        out[2] = (axisR[2] * x + axisU[2] * y + axisF[2] * z).toFloat()
        return out
    }

    private fun buildCar(world: World) {
        val h = world.hero
        val f = world.frame
        val t = world.time
        val pal = world.pal
        carOpaque.reset(); carGlass.reset(); carCards.reset(); carLights.reset(); carReflection.reset()

        // How the car sits: on the road at its tail, turned into a lane change, pitched by the
        // slope, leaning on its springs, with a whisper of the road's texture in the suspension
        f.at(h.s, h.u, 0.0, pA)
        g[0] = pA[0].toDouble()
        g[1] = pA[1] + 0.006 * sin(t * 7.3) + 0.004 * sin(t * 11.1 + 1.7)
        g[2] = pA[2].toDouble()
        val yaw = f.headingAt(h.s + 2) + atan2(h.lateral, max(h.speed, 1.0))
        val road = world.road
        val pitch = atan((road.elevation(h.s + 3) - road.elevation(h.s - 1)) / 4)
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val spt = sin(pitch)
        val cr = cos(h.roll)
        val sr = sin(h.roll)
        axisF[0] = sy * cp; axisF[1] = spt; axisF[2] = cy * cp
        val u0x = -sy * spt
        val u0y = cp
        val u0z = -cy * spt
        val r0x = cy
        val r0y = 0.0
        val r0z = -sy
        axisR[0] = r0x * cr + u0x * sr; axisR[1] = r0y * cr + u0y * sr; axisR[2] = r0z * cr + u0z * sr
        axisU[0] = u0x * cr - r0x * sr; axisU[1] = u0y * cr - r0y * sr; axisU[2] = u0z * cr - r0z * sr
        for (c in 0 until 3) {
            uRight[c] = axisR[c].toFloat()
            uForward[c] = axisF[c].toFloat()
        }

        // The shell and wheels, the faces turned away from us left out
        val spin = (h.s / CarSpec.TYRE_R) % (2 * Math.PI)
        val wheel = CarUV.WHEEL
        val wcu = (wheel[0] + wheel[2] / 2.0) / CAR_TEX
        val wcv = (wheel[1] + wheel[3] / 2.0) / CAR_TEX
        val wr = (wheel[2] / 2.0 - 3) / CAR_TEX
        for (qi in carMesh.indices) {
            val q = carMesh[qi]
            xf(q.mid, mid)
            rot(q.n, n)
            if (n[0] * mid[0] + n[1] * mid[1] + n[2] * mid[2] >= 0) continue
            val b = if (q.mat == MAT_GLASS) carGlass else carOpaque
            if (b.full()) continue
            val o = b.quads * 4 * CAR_STRIDE
            val d = b.data
            val corners = q.corners
            val uv = q.uv
            val face = q.face
            for (k in 0 until 4) {
                val i = o + k * CAR_STRIDE
                xf(q.p[k], local)
                val nv = if (corners != null) rot(corners[k], nk) else n
                var u = 0.0
                var v = 0.0
                if (uv != null) {
                    u = uv[k][0].toDouble()
                    v = uv[k][1].toDouble()
                } else if (face != null) {
                    if (k == 0) {
                        u = wcu
                        v = wcv
                    } else {
                        val a = (if (k == 1) face[0] else face[1]) + spin
                        u = wcu + cos(a) * wr
                        v = wcv + sin(a) * wr
                    }
                }
                d[i] = local[0]; d[i + 1] = local[1]; d[i + 2] = local[2]
                d[i + 3] = nv[0]; d[i + 4] = nv[1]; d[i + 5] = nv[2]
                d[i + 6] = u.toFloat(); d[i + 7] = v.toFloat()
                d[i + 8] = q.mat.toFloat(); d[i + 9] = q.gloss; d[i + 10] = q.alpha; d[i + 11] = q.p[k][1]
            }
            b.quads++
        }

        // The cabin through the glass, far to near: the dash and the street ahead, the driver, the
        // seats. The driver leans with the car, looks about and now and then nods to the radio
        card(CARD_DASH, CarUV.DASH, scaleInto(col1, mixInto(col1, pal.horizon, WHITE, 0.3), 0.85), 0.0, 0.0, 0.0, 0.0, 0.0)
        card(CARD_DRIVER, CarUV.DRIVER, DRIVER_TINT, h.look * 0.025, -h.nod * 0.012, -h.roll * 1.6 + h.look * 0.05, 0.36, 0.86)
        card(CARD_SEATS, CarUV.SEATS, scaleInto(col1, mixInto(col1, pal.edge, WHITE, 0.4), 0.7), 0.0, 0.0, 0.0, 0.0, 0.0)

        // The light it sits in, and the street lamps nearest it for the highlights that slide over it
        scaleInto(ambient, mixInto(ambient, pal.fog, pal.horizon, 0.2), 0.8)
        scaleInto(leftLight, pal.edge, 0.55)
        scaleInto(rightLight, mixInto(rightLight, pal.cool, NEON_CYAN, 0.5), 0.4)
        val centre = h.s + 2.1
        val camS = world.camS
        var found = 0
        val lamps = world.lamps
        for (li in lamps.indices) {
            val l = lamps[li]
            val dist = abs(l.s - centre)
            if (l.s <= camS - 6 || dist >= 46) continue
            // Kept in order of distance, the nearest four
            var at = found
            while (at > 0 && nearDist[at - 1] > dist) at--
            if (at >= 4) continue
            val last = min(found, 3)
            for (j in last downTo at + 1) {
                nearLamps[j] = nearLamps[j - 1]
                nearDist[j] = nearDist[j - 1]
            }
            nearLamps[at] = l
            nearDist[at] = dist
            if (found < 4) found++
        }
        Arrays.fill(lampPos, 0f)
        Arrays.fill(lampCol, 0f)
        for (i in 0 until found) {
            val l = nearLamps[i] ?: continue
            f.at(l.s, l.side * 4.7, 7.75, pA)
            val w = smooth((46 - nearDist[i]) / 18).toFloat()
            for (c in 0 until 3) {
                lampPos[i * 3 + c] = pA[c]
                lampCol[i * 3 + c] = l.color[c] * w
            }
        }

        // Its lights, glowing past the body
        val lights = carLights
        for (sx in 0 until 2) {
            val x = if (sx == 0) -0.74 else 0.74
            glow(lights, xf(x, 0.77, -0.03, pA), 1.0, 0.5, sGlow, TAIL_RED, 0.22 + 0.4 * h.brake, 0.0)
        }
        glow(lights, xf(0.0, 0.766, -0.03, pA), 2.5, 0.14, sFlare, TAIL_RED, 0.14 + 0.3 * h.brake, 0.0)
        if (h.brake > 0.05) glow(lights, xf(0.0, 0.313, -0.03, pA), 0.45, 0.45, sGlow, TAIL_RED, 0.7 * h.brake, 0.0)
        // The indicator, before and through a lane change: a soft blink like a real bulb
        val signalling = h.signal > 0 || (h.laneT >= 0 && h.laneT < 1)
        val ph = (h.signalT * 1.6) % 1
        val blink = if (signalling) smooth(ph / 0.06) * (1 - smooth((ph - 0.42) / 0.1)) else 0.0
        indicator[0] = if (h.signalSide < 0) blink.toFloat() else 0f
        indicator[1] = if (h.signalSide > 0) blink.toFloat() else 0f
        if (blink > 0.01) glow(lights, xf(h.signalSide * 0.73, 0.716, -0.03, pA), 0.6, 0.4, sGlow, INDICATOR, 0.8 * blink, 0.0)

        // On the road: its shadow, the underglow on the wet tarmac, the headlights' pool ahead, and
        // the tail lights mirrored in the road
        val d = decals
        flat(d, f, h.s + 2.1, h.u, 2.6, 1.22, sGlow, BLACK, 0.92, 1.0, 0.0)
        flat(d, f, h.s + 2.1, h.u, 3.0, 1.55, sGlow, CAR_UNDERGLOW, 0.42, 0.0, 0.0)
        flat(d, f, h.s + 18, h.u, 13.0, 3.4, sGlow, HEADLIGHT_POOL, 0.12, 0.0, 1.0)
        val tail = 0.62 + 0.38 * h.brake
        for (sx in 0 until 2) {
            val top = xf(if (sx == 0) -0.74 else 0.74, 0.77, 0.0, pA)
            val y0 = (g[1] - (top[1] - g[1])).toFloat()
            quad(
                d, set(c0, top[0] - 0.2f, y0 - 2.4f, top[2]), set(c1, top[0] + 0.2f, y0 - 2.4f, top[2]),
                set(c2, top[0] + 0.2f, y0 + 0.25f, top[2]), set(c3, top[0] - 0.2f, y0 + 0.25f, top[2]),
                sStreak, TAIL_RED, 0.45 * tail, 0.0, 0.5, 0.0, flipV = true
            )
        }
        val tt = TAIL_TOP.toDouble()
        quad(
            carReflection, xf(-1.0, -tt, 0.0, pA), xf(1.0, -tt, 0.0, pB), xf(1.0, -0.17, 0.0, pC), xf(-1.0, -0.17, 0.0, pD),
            sTailLights, WHITE, 0.5 * tail, 0.0, 0.0, 0.0, flipV = true
        )
        // Fading away from the tyres, as a reflection in a wet road does
        carReflection.data[8] = 0f
        carReflection.data[SPRITE_STRIDE + 8] = 0f
    }

    /** A flat picture inside the cabin, turned by [ang] round (px, py) and moved by (dx, dy). */
    private fun card(rect: FloatArray, slot: IntArray, tint: FloatArray, dx: Double, dy: Double, ang: Double, px: Double, py: Double) {
        val b = carCards
        if (b.full()) return
        val x0 = rect[0].toDouble()
        val x1 = rect[1].toDouble()
        val y0 = rect[2].toDouble()
        val y1 = rect[3].toDouble()
        val z = rect[4].toDouble()
        val tex = CAR_TEX.toFloat()
        val u0 = slot[0] / tex
        val u1 = (slot[0] + slot[2]) / tex
        val v0 = slot[1] / tex
        val v1 = (slot[1] + slot[3]) / tex
        val ca = cos(ang)
        val sa = sin(ang)
        val o = b.quads * 4 * CAR_STRIDE
        for (k in 0 until 4) {
            val cx = if (k == 0 || k == 3) x0 else x1
            val cy = if (k < 2) y0 else y1
            val x = cx - px
            val y = cy - py
            xf(px + dx + x * ca - y * sa, py + dy + x * sa + y * ca, z, local)
            val i = o + k * CAR_STRIDE
            val d = b.data
            d[i] = local[0]; d[i + 1] = local[1]; d[i + 2] = local[2]
            d[i + 3] = tint[0]; d[i + 4] = tint[1]; d[i + 5] = tint[2]
            d[i + 6] = if (k == 0 || k == 3) u0 else u1
            d[i + 7] = if (k < 2) v1 else v0
            d[i + 8] = MAT_CARD.toFloat(); d[i + 9] = 0f; d[i + 10] = 1f; d[i + 11] = 0f
        }
        b.quads++
    }

    // --- Rain ----------------------------------------------------------------------------------

    private fun buildRain(world: World) {
        val b = rain
        b.reset()
        val amount = world.pal.rain
        if (amount < 0.02) return
        val count = (900 * amount).roundToInt()
        val r = rainRng
        val d = drops ?: FloatArray(900 * 3).also { drops = it; for (i in 0 until 900) respawn(it, i, r, true) }
        val dt = world.lastDt.toFloat()
        val v = world.hero.speed.toFloat()
        val w = width.toFloat()
        val h = height.toFloat()
        val fpx = focal.toFloat()
        val hy = (horizon * height).toFloat()
        // The drops keep their look at any size of picture
        val px = h / 1080f
        val tint = mixInto(col1, world.pal.horizon, RAIN_WHITE, 0.6)
        val lag = 0.028f
        for (i in 0 until count) {
            val o = i * 3
            d[o + 1] -= 9.5f * dt
            d[o + 2] -= v * dt
            d[o] += 1.2f * dt
            if (d[o + 2] < 0.6f || d[o + 1] < -camH) respawn(d, i, r, false)
            val x = d[o]
            val y = d[o + 1]
            val z = d[o + 2]
            // Where it is now and where it was a moment ago: the streak the eye sees
            val x2 = x - 1.2f * lag
            val y2 = y + 9.5f * lag
            val z2 = z + v * lag
            val sx = w / 2 + x / z * fpx
            val sy = hy - y / z * fpx
            val sx2 = w / 2 + x2 / z2 * fpx
            val sy2 = hy - y2 / z2 * fpx
            val wpx = (16f / z).coerceIn(0.8f, 2.6f) * px
            val dx = sx - sx2
            val dy = sy - sy2
            val len = hypot(dx, dy).let { if (it == 0f) 1f else it }
            val nx = -dy / len * wpx / 2
            val ny = dx / len * wpx / 2
            val alpha = ((1.4f - z / 30f).coerceIn(0.15f, 1f) * 0.5f).toDouble()
            quad(
                b, ndc(c0, sx2 + nx, sy2 + ny, w, h), ndc(c1, sx2 - nx, sy2 - ny, w, h),
                ndc(c2, sx - nx, sy - ny, w, h), ndc(c3, sx + nx, sy + ny, w, h), sRain, tint, alpha, 0.0, 0.0, 0.0
            )
        }
    }

    private fun ndc(out: FloatArray, x: Float, y: Float, w: Float, h: Float) = set(out, x / w * 2 - 1, 1 - y / h * 2, 0f)

    private fun respawn(d: FloatArray, i: Int, r: Rng, anywhere: Boolean) {
        val z = if (anywhere) r.range(0.8, 40.0) else r.range(8.0, 40.0)
        d[i * 3 + 2] = z.toFloat()
        d[i * 3] = (r.range(-0.8, 0.8) * z).toFloat()
        d[i * 3 + 1] = (if (anywhere) r.range(-camH, 0.5 * z) else r.range(0.0, 0.55 * z)).toFloat()
    }

    private companion object {
        const val TEXTURE_MAX_ANISOTROPY = 0x84FE
        val DEFAULT_ACCENT = floatArrayOf(1f, 0.3f, 0.6f)
        val SCREEN_WORDS = floatArrayOf(1f, 0.97f, 1f)
    }
}
