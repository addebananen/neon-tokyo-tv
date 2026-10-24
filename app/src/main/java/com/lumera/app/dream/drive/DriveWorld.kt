package com.lumera.app.dream.drive

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// The endless city: a road that bends and climbs a little, districts with their own colours, the
// buildings, signs and lamps along it, the traffic, the flying cars, and our car with its driver.
// Positions along the road are s (metres), across it u (metres, left negative), height y.

object Cfg {
    const val HFOV = 64 * PI / 180
    const val HORIZON = 0.40
    const val CAR_DIST = 6.6
    const val NEAR = 0.3
    const val FAR = 900.0
    const val VIEW = 640.0
    const val DS = 4.0
    const val LANE = 3.4
    const val ROAD = 6.8
    const val WALK = 10.8
    // Along-road texture coordinates restart every period: a whole number of every pattern's repeat
    const val PERIOD = 960.0
}

/** A district's colours as numbers, mixed between districts while driving from one to the next. */
class Palette {
    val skyTop = FloatArray(3)
    val skyMid = FloatArray(3)
    val horizon = FloatArray(3)
    val fog = FloatArray(3)
    val road = FloatArray(3)
    val reflect = FloatArray(3)
    val edge = FloatArray(3)
    val lamp = FloatArray(3)
    val warm = FloatArray(3)
    val cool = FloatArray(3)
    var fogDensity = 0.0
    var rain = 0.0
    var sun = 0.0
    var moon = 0.0
    var stars = 0.0
    private val colours = arrayOf(skyTop, skyMid, horizon, fog, road, reflect, edge, lamp, warm, cool)

    fun set(d: District) {
        val hex = arrayOf(d.skyTop, d.skyMid, d.horizon, d.fog, d.road, d.reflect, d.edge, d.lamp, d.warm, d.cool)
        for (i in colours.indices) rgb(hex[i]).copyInto(colours[i])
        fogDensity = d.fogDensity
        rain = d.rain
        sun = d.sun
        moon = d.moon
        stars = d.stars
    }

    fun mix(a: Palette, b: Palette, t: Double) {
        val k = t.toFloat()
        for (i in colours.indices) for (c in 0 until 3) colours[i][c] = a.colours[i][c] + (b.colours[i][c] - a.colours[i][c]) * k
        fogDensity = lerp(a.fogDensity, b.fogDensity, t)
        rain = lerp(a.rain, b.rain, t)
        sun = lerp(a.sun, b.sun, t)
        moon = lerp(a.moon, b.moon, t)
        stars = lerp(a.stars, b.stars, t)
    }
}

// --- The road ----------------------------------------------------------------------------------

/** Curvature in stretches: straights and long bends, eased from one into the next. */
class Road(private val rng: Rng) {
    private class Section(val s0: Double, val len: Double, val k0: Double, val k1: Double)

    private val sections = ArrayList<Section>().apply { add(Section(-400.0, 900.0, 0.0, 0.0)) }
    private val phase = doubleArrayOf(rng.range(0.0, 6.28), rng.range(0.0, 6.28), rng.range(0.0, 6.28))

    fun extend(until: Double) {
        var last = sections[sections.size - 1]
        while (last.s0 + last.len < until) {
            val straight = rng.chance(0.32)
            val k = if (straight) 0.0 else (if (rng.chance(0.5)) -1 else 1) * rng.range(1 / 1700.0, 1 / 560.0)
            val next = Section(last.s0 + last.len, rng.range(160.0, 460.0), last.k1, k)
            sections += next
            last = next
        }
    }

    fun prune(before: Double) {
        while (sections.size > 2 && sections[1].s0 + sections[1].len < before) sections.removeAt(0)
    }

    fun curvature(s: Double): Double {
        var i = 0
        while (i < sections.size - 1 && s >= sections[i].s0 + sections[i].len) i++
        val sec = sections[i]
        val ease = min(140.0, sec.len * 0.6)
        return lerp(sec.k0, sec.k1, smooth((s - sec.s0) / ease))
    }

    /** Gentle rises and dips, never more than a few percent. */
    fun elevation(s: Double): Double =
        2.6 * sin(s / 290 + phase[0]) + 1.2 * sin(s / 113 + phase[1]) + 0.5 * sin(s / 47 + phase[2])
}

/**
 * The road worked out for one frame, in camera space: a table every few metres of where the
 * middle of the road is, which way it points and how high it is, from just behind the camera to
 * the far end. Everything along the road is placed by reading it.
 */
class Frame {
    val n = ceil(Cfg.VIEW / Cfg.DS).toInt() + 6
    private val x = DoubleArray(n)
    private val y = DoubleArray(n)
    private val z = DoubleArray(n)
    private val h = DoubleArray(n)
    private val wx = DoubleArray(n)
    private val wz = DoubleArray(n)
    private val wt = DoubleArray(n)
    var s0 = 0.0
        private set
    var yaw = 0.0
        private set
    var thetaCam = 0.0
        private set

    fun build(road: Road, camS: Double, camU: Double, lookAhead: Double, camH: Double, yawBias: Double) {
        val ds = Cfg.DS
        s0 = floor(camS / ds) * ds - 2 * ds
        var px = 0.0
        var pz = 0.0
        var th = 0.0
        for (k in 0 until n) {
            wx[k] = px; wz[k] = pz; wt[k] = th
            val th2 = th + road.curvature(s0 + (k + 0.5) * ds) * ds
            val mid = (th + th2) / 2
            px += ds * sin(mid)
            pz += ds * cos(mid)
            th = th2
        }
        fun sample(arr: DoubleArray, s: Double): Double {
            val f = (s - s0) / ds
            val i = clamp(floor(f), 0.0, n - 2.0).toInt()
            return lerp(arr[i], arr[i + 1], f - i)
        }
        val thc = sample(wt, camS)
        val look = lerp(thc, sample(wt, camS + lookAhead), 0.55) + yawBias
        val cx = sample(wx, camS) + camU * cos(thc)
        val cz = sample(wz, camS) - camU * sin(thc)
        val cy = road.elevation(camS) + camH
        val c = cos(look)
        val sn = sin(look)
        for (k in 0 until n) {
            val dx = wx[k] - cx
            val dz = wz[k] - cz
            x[k] = dx * c - dz * sn
            z[k] = dx * sn + dz * c
            h[k] = wt[k] - look
            y[k] = road.elevation(s0 + k * ds) - cy
        }
        yaw = look
        thetaCam = thc
    }

    /** Camera space position of (s, u, y) into out. */
    fun at(s: Double, u: Double, yUp: Double, out: FloatArray): FloatArray {
        val f = (s - s0) / Cfg.DS
        val i = clamp(floor(f), 0.0, n - 2.0).toInt()
        val t = f - i
        val hh = lerp(h[i], h[i + 1], t)
        out[0] = (lerp(x[i], x[i + 1], t) + u * cos(hh)).toFloat()
        out[1] = (lerp(y[i], y[i + 1], t) + yUp).toFloat()
        out[2] = (lerp(z[i], z[i + 1], t) - u * sin(hh)).toFloat()
        return out
    }

    fun headingAt(s: Double): Double {
        val f = (s - s0) / Cfg.DS
        val i = clamp(floor(f), 0.0, n - 2.0).toInt()
        return lerp(h[i], h[i + 1], f - i)
    }
}

// --- The city ----------------------------------------------------------------------------------

private val HEIGHTS = arrayOf(doubleArrayOf(6.5, 13.0), doubleArrayOf(16.0, 42.0), doubleArrayOf(55.0, 125.0))

// How far across the camera may go: clear of the oncoming lanes, and of the lamp posts on the left
private const val CAM_U_MIN = -6.0
private const val CAM_U_MAX = -0.6

class Building(
    val s0: Double, val s1: Double, val side: Int, val uIn: Double, val depth: Double, val h: Double,
    val style: Int, val seed: Double, val wall: FloatArray, val lit: Double, val store: Double, val strip: Int,
    val neon: FloatArray?, val tower: Boolean, val crown: FloatArray?, val accent: FloatArray?
) {
    /** The width of a big screen on its front, or 0 */
    var screen = 0.0
}

class Sign(
    val kind: Char, val s: Double, val u: Double, val y: Double, val w: Double, val h: Double,
    val sprite: Sprite, val tint: FloatArray, val flicker: Double, val off: Boolean, val seed: Double
)

class Lamp(val s: Double, val side: Int, val color: FloatArray)
class Lantern(val s: Double, val y: Double, val sag: Double, val count: Int, val tint: FloatArray, val seed: Double)
class Gantry(val s: Double, val board: Int, val tint: FloatArray)
class Palm(val s: Double, val u: Double, val h: Double, val tint: FloatArray, val lean: Double)
class Crossing(val s: Double, val w: Double)
class TrafficCar(var s: Double, val u: Double, val dir: Int, var speed: Double, val seed: Double)

class Flyer(
    var s: Double, var u: Double, var y: Double, val vs: Double, val vu: Double, val vy: Double,
    var age: Double, val life: Double, val seed: Double
)

class DistrictSpan(val s0: Double, val s1: Double, val def: District)

/** The district we drove into last, and when, for its name on the screen. */
class Entered(val def: District, val at: Double)

/** Our car and its driver. */
class Hero {
    var s = 0.0
    var u = -Cfg.LANE / 2
    var lane = 1
    var from = -Cfg.LANE / 2
    var to = -Cfg.LANE / 2
    var laneT = 1.0
    var speed = 21.0
    var brake = 0.0
    var signal = 0.0
    var signalT = 0.0
    var nextChange = 18.0
    var roll = 0.0
    var rollV = 0.0
    var look = 0.0
    var lookTarget = 0.0
    var nextLook = 6.0
    var nod = 0.0
    var nodFrom = 0.0
    var nodUntil = 0.0
    var nextNod = 20.0
    var signalSide = 0
    var lateral = 0.0
}

/** Where the camera is for this frame: height, distance behind, offset, aim and horizon. */
class CameraSet(var h: Double, var d: Double, var u: Double, var aim: Double, var horizon: Double) {
    fun copyFrom(o: CameraSet) {
        h = o.h; d = o.d; u = o.u; aim = o.aim; horizon = o.horizon
    }
}

private val GANTRY_AMBER = rgb("#ffb347")
private val GANTRY_ICE = rgb("#7ff7ff")
private val LANTERN_RED = rgb("#ff4a2a")

class World(seed: Int, private val art: DriveArt, firstDistrict: String? = null) {
    private val rng = Rng(seed)
    val road = Road(rng)
    val frame = Frame()
    @Volatile var time = 0.0
        private set
    // Districts follow each other in a shuffled order, never the same twice running
    private val districts = ArrayList<DistrictSpan>()
    val buildings = ArrayList<Building>()
    val signs = ArrayList<Sign>()
    val lamps = ArrayList<Lamp>()
    val lanterns = ArrayList<Lantern>()
    val gantries = ArrayList<Gantry>()
    val palms = ArrayList<Palm>()
    val crossings = ArrayList<Crossing>()
    val cars = ArrayList<TrafficCar>()
    val flyers = ArrayList<Flyer>()
    private val cursor = doubleArrayOf(0.0, 0.0)
    private var lampCursor = 0.0
    private var decorCursor = 0.0
    private var towerCursor = 60.0
    private var crossCursor = 220.0
    private var gantryCursor = 380.0
    private var nextFlyer = 6.0
    val hero = Hero()
    private var camU: Double
    private var camUv = 0.0

    // The camera director: which shot it holds, since when, and the move into it
    private val camRng = Rng(seed * 7 + 13)
    private var shot = SHOTS[0]
    val cam = CameraSet(SHOTS[0].h, SHOTS[0].d, SHOTS[0].u, SHOTS[0].aim, SHOTS[0].horizon)
    private val camFrom = CameraSet(cam.h, cam.d, cam.u, cam.aim, cam.horizon)
    private var shotAt = 0.0
    private var shotHold = camRng.range(24.0, 34.0)
    private var shotMove = 1.0
    private var shotDrift = 0.0
    private var shotSway = camRng.range(0.0, 100.0)
    var camS: Double
        private set

    private val palFrom = Palette()
    private val palTo = Palette()
    val pal = Palette()
    var palT = 1.0
        private set
    private var current: DistrictSpan
    @Volatile var entered: Entered
        private set
    private var heading = 0.0
    var yawAbs = 0.0
        private set
    var sunAz = 0.0
        private set
    private var lastCamS: Double
    var lastDt = 1 / 60.0
        private set
    var flash = 0.0
        private set
    private var nextFlash = 25.0
    private var flashAt = Double.NaN

    init {
        val first = DISTRICTS.firstOrNull { it.key == firstDistrict } ?: DISTRICTS[0]
        addDistrict(0.0, first)
        hero.s = Cfg.CAR_DIST
        camU = hero.u
        camS = hero.s - cam.d
        palFrom.set(first)
        palTo.set(first)
        pal.set(first)
        current = districts[0]
        entered = Entered(first, 0.5)
        lastCamS = camS
        generate(hero.s + Cfg.VIEW + 40)
    }

    private fun addDistrict(s0: Double, def: District) {
        val len = rng.range(1500.0, 2500.0)
        districts += DistrictSpan(s0, s0 + len, def)
    }

    fun districtAt(s: Double): District {
        while (districts[districts.size - 1].s1 < s + 10) {
            val last = districts[districts.size - 1]
            var def: District
            do {
                def = rng.pick(DISTRICTS)
            } while (def === last.def)
            addDistrict(last.s1, def)
        }
        for (d in districts) if (s < d.s1) return d.def
        return districts[districts.size - 1].def
    }

    private fun inCrossing(s0: Double, s1: Double): Crossing? {
        for (c in crossings) if (s1 > c.s - 1 && s0 < c.s + c.w + 1) return c
        return null
    }

    private fun generate(until: Double) {
        road.extend(until + 200)
        val r = rng
        // Crossings first: the buildings and lamps leave room for them
        while (crossCursor < until + 60) {
            val d = districtAt(crossCursor)
            if (!d.boulevard) crossings += Crossing(crossCursor, 15.0)
            crossCursor += r.range(300.0, 680.0)
        }
        generateSide(-1, until)
        generateSide(1, until)
        // Lamps on both sides, staggered
        while (lampCursor < until) {
            val s = lampCursor
            val d = districtAt(s)
            if (inCrossing(s - 2, s + 2) == null) lamps += Lamp(s, -1, d.lampRgb)
            if (inCrossing(s + 15, s + 19) == null) lamps += Lamp(s + 17, 1, d.lampRgb)
            lampCursor += 34
        }
        // Lanterns strung across the street, and the gantries
        while (decorCursor < until) {
            val s = decorCursor
            val d = districtAt(s)
            if (d.lanterns > 0 && r.chance(d.lanterns) && inCrossing(s - 3, s + 3) == null) {
                val tint = if (r.chance(0.7)) LANTERN_RED else GANTRY_AMBER
                val y = r.range(6.4, 7.6)
                val sag = r.range(0.8, 1.6)
                val count = 13 + r.int(5)
                lanterns += Lantern(s, y, sag, count, tint, r.next())
            }
            decorCursor += r.range(48.0, 92.0)
        }
        while (gantryCursor < until) {
            val s = gantryCursor
            if (inCrossing(s - 8, s + 8) == null) {
                val first = gantries.isEmpty()
                val board = if (first || r.chance(0.5)) 0 else 1 + r.int(art.gantry.size - 1)
                gantries += Gantry(s, board, if (r.chance(0.5)) GANTRY_AMBER else GANTRY_ICE)
                gantryCursor += r.range(780.0, 1300.0)
            } else {
                gantryCursor += 40
            }
        }
        // Towers far off the street, seen over the roofs and through the gaps
        while (towerCursor < until) {
            val s = towerCursor
            val d = districtAt(s)
            val side = if (r.chance(0.5)) -1 else 1
            val w = r.range(22.0, 42.0)
            val tall = if (d.boulevard) r.range(70.0, 160.0) else r.range(120.0, 300.0)
            val uIn = r.range(38.0, 170.0)
            val style = if (r.chance(0.6)) 2 else 0
            val seed = r.next()
            val wall = mixRgb(r.pick(d.walls), floatArrayOf(0f, 0f, 0f), 0.2)
            val lit = r.range(0.18, 0.4)
            val crown = if (r.chance(0.65)) r.pick(d.neon) else null
            buildings += Building(s, s + w, side, uIn, w, tall, style, seed, wall, lit, 0.0, 0, null, true, crown, null)
            towerCursor += r.range(140.0, 380.0)
        }
    }

    private fun generateSide(side: Int, until: Double) {
        val r = rng
        val idx = if (side < 0) 0 else 1
        var s = cursor[idx]
        while (s < until) {
            val d = districtAt(s)
            if (d.boulevard && side > 0) {
                // The bay side of the boulevard: palms along the promenade, then the water
                val ps = s + r.range(0.0, 3.0)
                val pu = side * (Cfg.WALK - 1.6 + r.range(-0.4, 0.4))
                val ph = r.range(9.0, 12.5)
                val tint = r.pick(d.neon)
                palms += Palm(ps, pu, ph, tint, r.range(-0.08, 0.08))
                s += r.range(15.0, 24.0)
                continue
            }
            s += if (r.chance(d.alley)) r.range(2.5, 6.0) else r.range(0.0, 0.5)
            val len = r.range(9.0, 24.0)
            val cross = inCrossing(s, s + len)
            if (cross != null) {
                s = cross.s + cross.w + r.range(0.5, 2.0)
                continue
            }
            val hc = pickHeight(d.heights)
            val h = r.range(HEIGHTS[hc][0], HEIGHTS[hc][1])
            val setback = if (r.chance(0.12)) r.range(1.2, 3.6) else r.range(0.0, 0.5)
            val style = r.pick(d.styles)
            val depth = r.range(10.0, 24.0)
            val seed = r.next()
            val wall = r.pick(d.walls)
            val lit = r.range(0.22, 0.6)
            val store = if (hc < 2 || r.chance(0.5)) 4.2 else 0.0
            val strip = if (style == 4) 2 + r.int(3) else 0
            val neon = if (r.chance(0.22)) r.pick(d.neon) else null
            val crown = if (hc == 2 && r.chance(0.5)) r.pick(d.neon) else null
            val accent = r.pick(d.neon)
            val b = Building(s, s + len, side, Cfg.WALK + setback, depth, h, style, seed, wall, lit, store, strip, neon, false, crown, accent)
            // Now and then a big screen on the front, playing its adverts to the empty street
            if (h > 18 && len > 11 && r.chance(0.16 * d.signs)) b.screen = min(len - 3, r.range(7.0, 12.0))
            buildings += b
            if (b.store > 0) addSigns(b, d)
            s += len
        }
        cursor[idx] = s
    }

    private fun pickHeight(weights: DoubleArray): Int {
        val x = rng.next()
        if (x < weights[0]) return 0
        if (x < weights[0] + weights[1]) return 1
        return 2
    }

    private fun addSigns(b: Building, d: District) {
        val r = rng
        val len = b.s1 - b.s0
        // Vertical signs hanging out over the pavement
        val count = floor((len / 7) * d.signs * r.range(0.3, 1.3)).toInt()
        for (k in 0 until count) {
            val spec = r.pick(art.vertical)
            val scale = r.range(0.85, 1.25)
            val hm = spec.hm * scale
            val y0 = r.range(4.4, max(4.6, min(b.h - hm - 0.6, 14.0)))
            if (y0 + hm > b.h - 0.3) continue
            val s = b.s0 + r.range(0.8, len - 0.8)
            val tint = r.pick(d.neon)
            val flicker = if (r.chance(0.08)) r.next() else -1.0
            val off = r.chance(0.05)
            signs += Sign('v', s, b.side * (b.uIn - 0.78), y0, spec.wm * scale, hm, spec.sprite, tint, flicker, off, r.next())
        }
        // A sign flat on the front above the shops
        if (r.chance(0.4 * d.signs)) {
            val spec = r.pick(art.horizontal)
            val scale = r.range(0.75, 1.0)
            val w = spec.wm * scale
            if (w < len - 1) {
                val sc = b.s0 + r.range(w / 2 + 0.3, len - w / 2 - 0.3)
                val tint = r.pick(d.neon)
                val flicker = if (r.chance(0.05)) r.next() else -1.0
                signs += Sign('f', sc, b.side * (b.uIn - 0.06), 4.45, w, spec.hm * scale, spec.sprite, tint, flicker, false, r.next())
            }
        }
        // A billboard on the roof of a low building, facing down the street
        if (b.h < 30 && r.chance(0.28 * d.signs)) {
            val spec = r.pick(art.horizontal)
            val scale = r.range(1.6, 2.6)
            val u = b.side * (b.uIn + min(b.depth * 0.45, spec.wm * scale * 0.5 + 0.5))
            val tint = r.pick(d.neon)
            val flicker = if (r.chance(0.06)) r.next() else -1.0
            signs += Sign('r', b.s0 + 0.6, u, b.h + 0.5, spec.wm * scale, spec.hm * scale, spec.sprite, tint, flicker, false, r.next())
        }
    }

    private fun prune(before: Double) {
        buildings.removeAll { it.s1 <= before }
        signs.removeAll { it.s + 30 <= before }
        lamps.removeAll { it.s + 30 <= before }
        lanterns.removeAll { it.s + 30 <= before }
        gantries.removeAll { it.s + 30 <= before }
        palms.removeAll { it.s + 30 <= before }
        crossings.removeAll { it.s + it.w + 40 <= before }
        while (districts.size > 1 && districts[0].s1 < before - 100) districts.removeAt(0)
        road.prune(before - 50)
    }

    // --- Traffic, flying cars, our car -----------------------------------------------------------

    private fun spawnTraffic() {
        val r = rng
        val h = hero
        val d = districtAt(h.s + 400)
        val ours = cars.count { it.dir > 0 }
        val theirs = cars.size - ours
        if (ours < 3 + (3 * d.traffic).roundToInt() && r.chance(0.02)) {
            val s = h.s + r.range(260.0, 520.0)
            val lane = if (r.chance(0.5)) -Cfg.LANE * 1.5 else -Cfg.LANE * 0.5
            if (cars.none { it.dir > 0 && abs(it.u - lane) < 1 && abs(it.s - s) < 40 }) {
                val speed = h.speed * r.range(0.55, 0.85)
                cars += TrafficCar(s, lane, 1, speed, r.next())
            }
        }
        if (theirs < 3 + (5 * d.traffic).roundToInt() && r.chance(0.035)) {
            val s = h.s + r.range(520.0, 620.0)
            val lane = if (r.chance(0.5)) Cfg.LANE * 0.5 else Cfg.LANE * 1.5
            if (cars.none { it.dir < 0 && abs(it.u - lane) < 1 && abs(it.s - s) < 60 }) {
                val speed = r.range(14.0, 23.0)
                cars += TrafficCar(s, lane, -1, speed, r.next())
            }
        }
    }

    private fun spawnFlyer() {
        val r = rng
        val h = hero
        val crossing = r.chance(0.6)
        val side = if (r.chance(0.5)) -1 else 1
        if (crossing) {
            // Across the street ahead, high between the towers
            val dist = r.range(110.0, 260.0)
            val u = -side * r.range(90.0, 140.0)
            val y = r.range(26.0, 58.0)
            val vs = h.speed * r.range(0.2, 0.6)
            val vu = side * r.range(22.0, 34.0)
            val vy = r.range(-0.6, 0.6)
            flyers += Flyer(h.s + dist, u, y, vs, vu, vy, 0.0, 12.0, r.next())
        } else {
            // Along the street, overtaking us high up and pulling away into the haze
            val u = r.range(-10.0, 10.0)
            val y = r.range(22.0, 40.0)
            val vs = h.speed + r.range(16.0, 26.0)
            val vu = r.range(-1.5, 1.5)
            val vy = r.range(0.2, 1.2)
            flyers += Flyer(h.s - 30, u, y, vs, vu, vy, 0.0, 22.0, r.next())
        }
    }

    fun update(dt: Double) {
        time += dt
        val h = hero
        val r = rng
        val t = time

        // Speed: an easy cruise that drifts, slowing behind a slower car if we can't pass
        var target = 21 + 2.4 * sin(t * 0.045 + 1.3) + 1.2 * sin(t * 0.11 + 0.4)
        val ahead = carAhead(h.u, 75.0)
        if (ahead != null && ahead.speed < h.speed) {
            val other = if (h.lane == 0) 1 else 0
            val otherU = laneU(other)
            val free = cars.none { it.dir > 0 && abs(it.u - otherU) < 1.6 && it.s > h.s - 14 && it.s < h.s + 55 }
            if (free && h.laneT >= 1 && h.signal <= 0) beginLaneChange(other)
            else if (h.laneT >= 1) target = min(target, ahead.speed + (ahead.s - h.s - 28) * 0.15)
        }
        if (h.laneT >= 1 && h.signal <= 0 && t > h.nextChange) {
            val other = if (h.lane == 0) 1 else 0
            val otherU = laneU(other)
            val free = cars.none { it.dir > 0 && abs(it.u - otherU) < 1.6 && it.s > h.s - 20 && it.s < h.s + 70 }
            if (free) beginLaneChange(other)
            h.nextChange = t + r.range(24.0, 52.0)
        }
        val accel = clamp((target - h.speed) * 0.6, -3.2, 1.1)
        h.speed += accel * dt
        h.brake = lerp(h.brake, if (accel < -0.9) 1.0 else 0.0, 1 - exp(-dt * 8))
        h.s += h.speed * dt

        // Lane change: the indicator blinks a moment first, then an easy glide across
        if (h.signal > 0) {
            h.signal -= dt
            h.signalT += dt
            if (h.signal <= 0 && h.laneT < 0) h.laneT = 0.0
        }
        if (h.laneT >= 0 && h.laneT < 1) {
            h.laneT = min(1.0, h.laneT + dt / 3.2)
            if (h.laneT >= 1) h.signalT = 0.0
        }
        val prevU = h.u
        h.u = lerp(h.from, h.to, smoother(max(0.0, h.laneT)))
        h.lateral = (h.u - prevU) / max(dt, 1e-4)

        // The body leans out of the lane change on its springs
        val rollTarget = -h.lateral * 0.012
        h.rollV += (38 * (rollTarget - h.roll) - 9 * h.rollV) * dt
        h.roll += h.rollV * dt

        // The driver looks about now and then, and sometimes nods along to the radio
        if (t > h.nextLook) {
            h.lookTarget = if (r.chance(0.4)) 0.0 else r.range(-1.0, 1.0)
            h.nextLook = t + r.range(3.0, 9.0)
        }
        h.look = lerp(h.look, h.lookTarget, 1 - exp(-dt * 2.2))
        if (t > h.nextNod) {
            h.nodFrom = t
            h.nodUntil = t + r.range(6.0, 14.0)
            h.nextNod = t + r.range(25.0, 60.0)
        }
        // A nod on every beat at about 105 bpm, eased in and out
        val groove = if (t < h.nodUntil) smooth((t - h.nodFrom) / 1.5) * smooth((h.nodUntil - t) / 1.5) else 0.0
        h.nod = groove * max(0.0, sin(t * PI * 1.75)).pow(3)

        // The camera follows the car across on a soft spring, so the car swings a little in the frame
        val w0 = 1.9
        camUv += (w0 * w0 * (h.u - camU) - 2 * w0 * camUv) * dt
        camU += camUv * dt

        // Other cars
        for (car in cars) car.s += car.dir * car.speed * dt
        cars.removeAll { it.s <= h.s - 40 || it.s >= h.s + 700 }
        // Keep their distance from each other in a lane
        for (car in cars) {
            if (car.dir < 0) continue
            for (o in cars) {
                if (o === car || o.dir < 0 || abs(o.u - car.u) > 1 || o.s <= car.s) continue
                if (o.s - car.s < 30) car.speed = min(car.speed, o.speed)
            }
        }
        spawnTraffic()

        // Flying cars
        if (t > nextFlyer) {
            spawnFlyer()
            val d = districtAt(h.s)
            nextFlyer = t + r.range(14.0, 34.0) / max(0.3, d.flyers)
        }
        for (f in flyers) {
            f.age += dt
            f.s += f.vs * dt
            f.u += f.vu * dt
            f.y += f.vy * dt
        }
        flyers.removeAll { it.age >= it.life }

        directCamera()
        val cs = h.s - cam.d
        camS = cs

        // Districts: the sky and the light change as we cross into the next one
        val now = districts.firstOrNull { cs < it.s1 } ?: districts[0]
        if (now !== current) {
            current = now
            palFrom.mix(pal, pal, 0.0)
            palTo.set(now.def)
            palT = 0.0
            entered = Entered(now.def, t)
        }
        if (palT < 1) {
            palT = min(1.0, palT + dt / 7)
            pal.mix(palFrom, palTo, smooth(palT))
        }

        generate(h.s + Cfg.VIEW + 40)
        prune(cs - 30)
        // Across, the camera keeps clear of the oncoming lanes and the posts, rounding off at the limits
        val raw = camU + cam.u
        val cu = -smin(-smin(raw, CAM_U_MAX, 0.6), -CAM_U_MIN, 0.6)
        val aimAt = atan2(h.u - cu, cam.d + 2.1)
        frame.build(road, cs, cu, 22.0, cam.h, cam.aim * aimAt)

        // Which way we face in the world, for the sky: the sun stays roughly ahead, drifting behind
        // the bends
        heading += road.curvature((cs + lastCamS) / 2) * (cs - lastCamS)
        lastCamS = cs
        yawAbs = heading + (frame.yaw - frame.thetaCam)
        sunAz = lerp(sunAz, yawAbs, 1 - exp(-dt / 30))
        lastDt = dt

        // Lightning far off in a downpour: two quick pulses, then the dark again
        if (pal.rain > 0.75 && t > nextFlash) {
            flashAt = t
            nextFlash = t + r.range(14.0, 40.0)
        }
        if (!flashAt.isNaN()) {
            val e = t - flashAt
            flash = if (e < 0 || e > 0.9) 0.0
            else 0.5 * exp(-e * 18) + (if (e > 0.18) 0.35 * exp(-max(0.0, e - 0.18) * 9) else 0.0)
        }
    }

    /**
     * The camera director: holds a shot for half a minute or so, then moves to another, never the
     * same one twice running, easing all the way in and out. Within a shot the camera floats a
     * little and pushes in or pulls back slowly, so no two minutes look the same.
     */
    private fun directCamera() {
        val t = time
        val r = camRng
        if (t - shotAt > shotHold) {
            val pool = SHOTS.filter { it !== shot }
            var pick = r.next() * pool.sumOf { it.weight }
            var next = pool[pool.size - 1]
            for (x in pool) {
                pick -= x.weight
                if (pick < 0) {
                    next = x
                    break
                }
            }
            camFrom.copyFrom(cam)
            shot = next
            shotAt = t
            shotHold = r.range(next.holdMin, next.holdMax)
            shotMove = r.range(5.0, 7.0)
            shotDrift = r.range(-0.7, 0.5)
            shotSway = r.range(0.0, 100.0)
        }
        val x = shot
        val along = min(1.0, (t - shotAt) / shotHold)
        val k = smoother((t - shotAt) / shotMove)
        cam.h = lerp(camFrom.h, x.h + 0.06 * sin(t * 0.37 + shotSway), k)
        cam.d = lerp(camFrom.d, x.d + shotDrift * along, k)
        cam.u = lerp(camFrom.u, x.u + 0.1 * sin(t * 0.23 + shotSway * 1.7), k)
        cam.aim = lerp(camFrom.aim, x.aim, k)
        cam.horizon = lerp(camFrom.horizon, x.horizon, k)
    }

    private fun laneU(lane: Int): Double = if (lane == 0) -Cfg.LANE * 1.5 else -Cfg.LANE * 0.5

    private fun carAhead(u: Double, within: Double): TrafficCar? {
        var best: TrafficCar? = null
        for (c in cars) {
            if (c.dir < 0 || abs(c.u - u) > 1.6) continue
            val gap = c.s - hero.s
            if (gap > 2 && gap < within && (best == null || c.s < best.s)) best = c
        }
        return best
    }

    private fun beginLaneChange(lane: Int) {
        val h = hero
        h.from = h.u
        h.to = laneU(lane)
        h.lane = lane
        h.laneT = -1.0
        h.signal = 1.3
        h.signalT = 0.0
        h.signalSide = if (h.to < h.from) -1 else 1
    }
}
