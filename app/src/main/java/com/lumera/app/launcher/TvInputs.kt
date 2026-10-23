package com.lumera.app.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One of the TV's inputs a console can be plugged into: an HDMI port, or a device the TV
 * sees on a port over HDMI-CEC, which then has the port as its [parentId].
 */
data class TvInput(
    val id: String,
    /** What the TV calls it: the name given to it in the TV's settings, or "HDMI 2". */
    val label: String,
    /** The HDMI port number in the TV's own name for the input. */
    val port: Int?,
    val parentId: String?
)

/**
 * The TV's inputs and whether something is switched on at each of them. The TV keeps track of
 * that itself and tells any app, without a permission; this only listens while the home screen
 * shows. On a box without TV inputs, or one that hides them, the list stays empty.
 */
class TvInputs(private val context: Context) {
    private val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    private val _inputs = MutableStateFlow<List<TvInput>>(emptyList())
    val inputs: StateFlow<List<TvInput>> = _inputs.asStateFlow()

    /** The TvInputManager state of every input: connected, standby or disconnected. */
    private val _states = MutableStateFlow<Map<String, Int>>(emptyMap())
    val states: StateFlow<Map<String, Int>> = _states.asStateFlow()

    private val callback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            _states.update { it + (inputId to state) }
        }
        override fun onInputAdded(inputId: String) = reload()
        override fun onInputRemoved(inputId: String) = reload()
        override fun onInputUpdated(inputId: String) = reload()
    }
    private var listening = false

    fun start() {
        val manager = manager ?: return
        if (!listening) {
            runCatching { manager.registerCallback(callback, Handler(Looper.getMainLooper())) }
                .onSuccess { listening = true }
                .onFailure { Log.w(TAG, "No input updates", it) }
        }
        reload()
    }

    fun stop() {
        if (listening) runCatching { manager?.unregisterCallback(callback) }
        listening = false
    }

    fun reload() {
        val manager = manager ?: return
        val infos = runCatching { manager.tvInputList }
            .onFailure { Log.w(TAG, "Could not read the TV inputs", it) }
            .getOrDefault(emptyList())
            .filter { it.isPassthroughInput }
        val found = infos.map { info ->
            val system = info.loadLabel(context)?.toString().orEmpty()
            val custom = runCatching { info.loadCustomLabel(context)?.toString() }.getOrNull()
            TvInput(
                id = info.id,
                label = custom?.takeIf { it.isNotBlank() } ?: system.ifBlank { info.id },
                port = portNumber(system),
                parentId = info.parentId
            )
        }
        _inputs.value = sortInputs(found)
        _states.value = found.associate { input ->
            input.id to runCatching { manager.getInputState(input.id) }.getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED)
        }
    }

    companion object {
        private const val TAG = "TvInputs"

        /** "HDMI 2", "HDMI2 (eARC)" and "hdmi 2" are port 2. */
        fun portNumber(label: String): Int? =
            Regex("""hdmi\s*(\d+)""", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)?.toIntOrNull()

        /** Ports in their order on the TV, each followed by the devices seen on it. */
        fun sortInputs(inputs: List<TvInput>): List<TvInput> {
            val ports = inputs.filter { it.parentId == null || inputs.none { p -> p.id == it.parentId } }
                .sortedWith(compareBy<TvInput> { it.port ?: Int.MAX_VALUE }.thenBy { it.label })
            return ports.flatMap { port -> listOf(port) + inputs.filter { it.parentId == port.id }.sortedBy { it.label } }
        }

        /** What the TV's own TV app needs to show an input. */
        fun openIntent(inputId: String): Intent =
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun isOn(state: Int?): Boolean = state == TvInputManager.INPUT_STATE_CONNECTED
    }
}

/**
 * Which TV input each console is plugged into. Until it is chosen, a console uses the HDMI
 * port with its number, so with the TV's four ports PC to Wii U light up straight away and
 * Wii and Xbox wait to be told where they are.
 */
class ConsoleLinks(context: Context) {
    private val prefs = context.getSharedPreferences("neon_home", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** Goes up whenever a link changes, for the screen to read the links again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun inputFor(console: Console, inputs: List<TvInput>): TvInput? {
        val key = key(console)
        if (prefs.contains(key)) {
            val id = prefs.getString(key, null).orEmpty()
            return inputs.firstOrNull { it.id == id }
        }
        return defaultInput(console, inputs)
    }

    /** Links [console] to [input], or to nothing when [input] is null. */
    fun link(console: Console, input: TvInput?) {
        prefs.edit().putString(key(console), input?.id.orEmpty()).apply()
        _version.update { it + 1 }
    }

    private fun key(console: Console) = "console.${console.id}"

    companion object {
        /** The HDMI port with the console's number, while no other input has been picked for it. */
        fun defaultInput(console: Console, inputs: List<TvInput>): TvInput? =
            inputs.firstOrNull { it.parentId == null && it.port == console.port }
    }
}
