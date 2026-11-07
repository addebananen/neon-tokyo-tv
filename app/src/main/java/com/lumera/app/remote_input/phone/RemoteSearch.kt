package com.lumera.app.remote_input.phone

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Neon Tokyo's own search, typed from the phone. The app's search page has its own keyboard on
 * screen rather than a text field Android could fill, so the phone's letters come this way.
 */
object RemoteSearch {
    private val _wanted = MutableStateFlow<String?>(null)

    /** A search the phone asked for: the app goes to its Search tab and takes it from here. */
    val wanted: StateFlow<String?> = _wanted.asStateFlow()

    private val _live = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Each letter as it is typed on the phone, while the search page shows. */
    val live: SharedFlow<String> = _live.asSharedFlow()

    /** The search page is on screen, so typing on the phone goes straight into it. */
    @Volatile
    var visible = false
        private set

    /** What the search page holds now, so the phone's keyboard opens with it. */
    @Volatile
    var query = ""
        private set

    fun want(text: String) {
        _wanted.value = text
    }

    /** The search page took [text]: nothing more is waiting. */
    fun taken(text: String) {
        _wanted.compareAndSet(text, null)
    }

    fun type(text: String) {
        _live.tryEmit(text)
    }

    fun shown(on: Boolean) {
        if (visible == on) return
        visible = on
        PhoneRemote.fieldChanged()
    }

    fun queryChanged(text: String) {
        if (query == text) return
        query = text
        if (visible) PhoneRemote.fieldChanged()
    }
}
