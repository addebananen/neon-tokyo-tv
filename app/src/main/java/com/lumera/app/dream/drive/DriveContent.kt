package com.lumera.app.dream.drive

// The words on the signs of the endless city, the districts it runs through and the camera's
// shots. Every Japanese character here must be in res/font/neon_jp.ttf, a subset of M PLUS
// Rounded 1c made from this file.

object DriveContent {
    // Vertical signs that hang out over the pavement, read top to bottom
    val vertical = listOf(
        "ラーメン", "居酒屋", "カラオケ", "喫茶", "寿司", "焼肉", "薬局", "ホテル", "麻雀", "ゲーム",
        "酒場", "書店", "銭湯", "占い", "古着", "電器", "中華", "珈琲", "餃子", "映画", "劇場",
        "うどん", "焼鳥", "漫画", "質屋", "夜光", "電脳", "ネオン", "営業中", "空室", "定食", "甘味",
        "洋食", "花屋", "理容", "湯", "酒", "宿", "茶", "麺"
    )

    // Horizontal signs above the shops and on the rooftops
    val horizontal = listOf(
        "BAR", "HOTEL", "NOODLES", "ARCADE", "KARAOKE", "OPEN 24H", "CYBER CAFE", "SUSHI", "CLUB",
        "LIVE", "RAMEN", "GAME CENTER", "DRINKS", "TATTOO", "DINER", "PACHINKO", "NEON", "VIDEO"
    )

    // The gantries over the road carry the message
    val gantry = listOf(
        "すぐ戻ります" to "BACK SOON",
        "ネオン東京" to "NEON TOKYO",
        "おやすみ" to "GOOD NIGHT",
        "この先" to "CITY CENTRE"
    )

    // Adverts on the big screens: the words, and a line under them
    val screens = listOf(
        "電脳" to "CYBER WORLD", "カラオケ" to "KARAOKE 24H", "ネオン" to "NEON TOKYO",
        "ラーメン" to "RAMEN", "夜光" to "NIGHT LIGHT", "ゲーム" to "GAME CENTER"
    )
}

/** A district: its sky and light, its streets, and how busy it is. */
class District(
    val key: String,
    val jp: String,
    val en: String,
    val skyTop: String,
    val skyMid: String,
    val horizon: String,
    val fog: String,
    val fogDensity: Double,
    val road: String,
    val reflect: String,
    val edge: String,
    val lamp: String,
    walls: List<String>,
    val warm: String,
    val cool: String,
    neon: List<String>,
    val rain: Double,
    val sun: Double,
    val moon: Double,
    val stars: Double,
    /** How likely a building is low, middling or tall */
    val heights: DoubleArray,
    val signs: Double,
    val alley: Double,
    val lanterns: Double,
    val styles: List<Int>,
    val flyers: Double,
    val traffic: Double,
    /** The bay boulevard: palms and water on the right instead of buildings */
    val boulevard: Boolean = false
) {
    val walls: List<FloatArray> = walls.map(::rgb)
    val neon: List<FloatArray> = neon.map(::rgb)
    val lampRgb: FloatArray = rgb(lamp)
}

val DISTRICTS = listOf(
    District(
        key = "shinjuku", jp = "新宿", en = "SHINJUKU",
        skyTop = "#06031a", skyMid = "#26104c", horizon = "#ff3d8f", fog = "#2c1040", fogDensity = 0.0052,
        road = "#14111c", reflect = "#ff5aa8", edge = "#ff3d9a", lamp = "#a8ecff",
        walls = listOf("#151029", "#1a1230", "#100c22", "#201736"), warm = "#ffc690", cool = "#c4e8ff",
        neon = listOf("#ff3d9a", "#3ef0ff", "#b44dff", "#ff9a3d", "#5dff9e", "#ff3d9a"),
        rain = 0.45, sun = 0.0, moon = 0.0, stars = 0.18,
        heights = doubleArrayOf(0.32, 0.5, 0.18), signs = 1.0, alley = 0.22, lanterns = 0.1, styles = listOf(0, 1, 3, 4),
        flyers = 0.55, traffic = 0.7
    ),
    District(
        key = "akihabara", jp = "秋葉原", en = "AKIHABARA",
        skyTop = "#040622", skyMid = "#14205a", horizon = "#3f7bff", fog = "#141e48", fogDensity = 0.0046,
        road = "#12131e", reflect = "#5ac8ff", edge = "#3ef0ff", lamp = "#e8f6ff",
        walls = listOf("#10132a", "#151a36", "#0e1024", "#1a1d3a"), warm = "#ffe3a8", cool = "#b8f0ff",
        neon = listOf("#3ef0ff", "#ffe03e", "#ff3d9a", "#5dff9e", "#ffffff", "#ff7a3d"),
        rain = 0.0, sun = 0.0, moon = 0.6, stars = 0.3,
        heights = doubleArrayOf(0.25, 0.55, 0.2), signs = 1.25, alley = 0.18, lanterns = 0.0, styles = listOf(0, 3, 4, 1),
        flyers = 0.45, traffic = 0.8
    ),
    District(
        key = "kabukicho", jp = "歌舞伎町", en = "KABUKICHO",
        skyTop = "#0a0306", skyMid = "#3a0c14", horizon = "#ff5a2c", fog = "#36120f", fogDensity = 0.0068,
        road = "#161012", reflect = "#ff6a3a", edge = "#ff3b30", lamp = "#ffb36b",
        walls = listOf("#1c0f14", "#22121a", "#160b10", "#2a1618"), warm = "#ffb070", cool = "#ffd8b0",
        neon = listOf("#ff3b30", "#ff8a2a", "#ffcc33", "#ff3d9a", "#ff5a2c", "#ffd27a"),
        rain = 1.0, sun = 0.0, moon = 0.0, stars = 0.0,
        heights = doubleArrayOf(0.4, 0.45, 0.15), signs = 1.3, alley = 0.3, lanterns = 0.55, styles = listOf(3, 1, 4),
        flyers = 1.0, traffic = 0.6
    ),
    District(
        key = "ginza", jp = "銀座", en = "GINZA",
        skyTop = "#020814", skyMid = "#0a2238", horizon = "#38d2df", fog = "#0c2434", fogDensity = 0.0044,
        road = "#10141a", reflect = "#6fe6f0", edge = "#7ff7ff", lamp = "#ffffff",
        walls = listOf("#0f1722", "#121c2a", "#0c121c", "#16202e"), warm = "#fff0d0", cool = "#d6f6ff",
        neon = listOf("#c8f7ff", "#3ef0ff", "#ffffff", "#b9a6ff", "#7ff7ff", "#ffd9a0"),
        rain = 0.15, sun = 0.0, moon = 1.0, stars = 0.35,
        heights = doubleArrayOf(0.08, 0.5, 0.42), signs = 0.45, alley = 0.12, lanterns = 0.0, styles = listOf(0, 2, 2, 0),
        flyers = 0.4, traffic = 0.9
    ),
    District(
        key = "shibuya", jp = "渋谷", en = "SHIBUYA",
        skyTop = "#08031a", skyMid = "#2c0f52", horizon = "#c24dff", fog = "#2a1048", fogDensity = 0.005,
        road = "#15111e", reflect = "#d36bff", edge = "#c44dff", lamp = "#ffd6f4",
        walls = listOf("#18102a", "#1e1434", "#120c24", "#24183a"), warm = "#ffcf9e", cool = "#dcd0ff",
        neon = listOf("#ff3d9a", "#c44dff", "#3ef0ff", "#ffd23e", "#ff7ae0", "#7a5cff"),
        rain = 0.3, sun = 0.0, moon = 0.0, stars = 0.15,
        heights = doubleArrayOf(0.3, 0.5, 0.2), signs = 1.1, alley = 0.2, lanterns = 0.08, styles = listOf(0, 1, 4, 3),
        flyers = 0.6, traffic = 0.8
    ),
    District(
        key = "odaiba", jp = "お台場", en = "ODAIBA",
        skyTop = "#120830", skyMid = "#4c1a6e", horizon = "#ff8a5c", fog = "#4a1e54", fogDensity = 0.0034,
        road = "#17121e", reflect = "#ff8f7a", edge = "#ff3d9a", lamp = "#ffcfa8",
        walls = listOf("#1d1230", "#22163a", "#160e28"), warm = "#ffd2a0", cool = "#e0d6ff",
        neon = listOf("#ff3d9a", "#3ef0ff", "#ff8a5c"),
        rain = 0.0, sun = 1.0, moon = 0.0, stars = 0.4,
        heights = doubleArrayOf(0.85, 0.15, 0.0), signs = 0.35, alley = 0.45, lanterns = 0.0, styles = listOf(1, 3),
        flyers = 0.35, traffic = 0.5, boulevard = true
    )
)

/**
 * A camera shot: how high it rides, how far behind the car, how far across from it (right is +),
 * how much it turns to keep the car in frame, where the horizon sits on the screen, how long it
 * holds (seconds) and how often it comes up.
 */
class Shot(
    val name: String,
    val h: Double,
    val d: Double,
    val u: Double,
    val aim: Double,
    val horizon: Double,
    val holdMin: Double,
    val holdMax: Double,
    val weight: Double
)

val SHOTS = listOf(
    Shot("chase", h = 2.05, d = 6.3, u = 0.0, aim = 0.0, horizon = 0.40, holdMin = 30.0, holdMax = 46.0, weight = 3.0),
    Shot("right", h = 1.35, d = 5.0, u = 1.6, aim = 0.65, horizon = 0.44, holdMin = 18.0, holdMax = 28.0, weight = 1.2),
    Shot("left", h = 1.7, d = 6.6, u = -2.7, aim = 0.55, horizon = 0.41, holdMin = 18.0, holdMax = 28.0, weight = 1.2),
    Shot("high", h = 3.5, d = 8.6, u = 0.5, aim = 0.3, horizon = 0.29, holdMin = 16.0, holdMax = 24.0, weight = 1.0),
    Shot("low", h = 0.95, d = 4.7, u = 1.0, aim = 0.5, horizon = 0.48, holdMin = 14.0, holdMax = 22.0, weight = 0.8)
)
