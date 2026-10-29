package com.lumera.app.siri

/**
 * The Rust engine (native/siri-remote): decodes what the Siri Remote sends and decides which keys
 * that is. Every call returns `[nextTickMs or -1, (kind, code, repeat, flags)*]`, see [SiriOut].
 */
object SiriNative {
    @JvmStatic external fun nativeCreate(generation: Int): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeConfigure(
        handle: Long,
        scrollSpeed: Float,
        momentum: Boolean,
        ringScrub: Boolean,
        edgeTaps: Boolean,
        backHoldHome: Boolean,
        siriKey: Int,
        tvDoubleKey: Int,
        tvHoldKey: Int
    )
    @JvmStatic external fun nativeConnected(handle: Long, uptimeMs: Long): IntArray
    @JvmStatic external fun nativeDisconnected(handle: Long, uptimeMs: Long): IntArray
    @JvmStatic external fun nativeReport(handle: Long, reportId: Int, data: ByteArray, uptimeMs: Long): IntArray
    @JvmStatic external fun nativeTick(handle: Long, uptimeMs: Long): IntArray
}

/** Kinds in the engine's output. */
object SiriOut {
    const val KEY_DOWN = 0
    const val KEY_UP = 1
    const val POWER = 2
    const val VOICE_START = 3
    const val VOICE_STOP = 4
    const val FLAG_CANCELED = 1
}
