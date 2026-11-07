package com.lumera.app.handoff

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.google.gson.JsonObject
import com.lumera.app.data.auth.StremioAuthManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The TV's side of "Fortsätt på TV:n": while the screen is on, it tells the Stremio account it
 * is here (so a phone can name it) and looks in the account's mailbox every few seconds. A
 * message for this TV, still fresh and not taken by another TV, is claimed and played through
 * [HandoffOpen]. See docs/handoff.md.
 */
@Singleton
class HandoffReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: StremioAuthManager,
    private val datastore: StremioDatastore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val handled = ArrayDeque<String>()
    @Volatile private var started = false
    private var presenceWrittenAt = 0L
    private var presenceOn = false

    /** This TV's id in the account: made once, kept. */
    val tvId: String by lazy {
        prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().take(12).also {
            prefs.edit().putString(KEY_ID, it).apply()
        }
    }

    /** The name a phone shows: the one set in the TV's settings, else its model. */
    val tvName: String
        get() = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty().ifBlank { "TV" }

    fun start() {
        if (started) return
        started = true
        scope.launch { loop() }
    }

    private fun screenOn(): Boolean =
        runCatching { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive }.getOrDefault(true)

    private suspend fun loop() {
        var failures = 0
        while (true) {
            val key = auth.getStoredAuthKey()
            val on = screenOn()
            if (key == null) {
                delay(IDLE_MS)
                continue
            }
            if (!on) {
                if (presenceOn) runCatching { writePresence(key, on = false) }
                delay(IDLE_MS)
                continue
            }
            try {
                tick(key)
                failures = 0
                delay(POLL_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StremioDatastore.AuthException) {
                Log.w(TAG, "Stremio sign-in no longer works: ${e.message}")
                delay(AUTH_BACKOFF_MS)
            } catch (e: Exception) {
                failures++
                if (failures == 1 || failures % 20 == 0) Log.w(TAG, "Mailbox check failed ($failures): ${e.message}")
                delay((POLL_MS * (1L shl failures.coerceAtMost(4))).coerceAtMost(60_000L))
            }
        }
    }

    private suspend fun tick(key: String) {
        val now = System.currentTimeMillis()
        val presenceDue = !presenceOn || now - presenceWrittenAt > PRESENCE_EVERY_MS
        val ids = if (presenceDue) listOf(HandoffProtocol.MAILBOX_ID, HandoffProtocol.PRESENCE_ID) else listOf(HandoffProtocol.MAILBOX_ID)
        val items = datastore.get(key, ids)
        if (presenceDue) {
            val current = items.firstOrNull { it.id() == HandoffProtocol.PRESENCE_ID }
            writePresence(key, on = true, currentName = current?.nameText())
        }
        val box = items.firstOrNull { it.id() == HandoffProtocol.MAILBOX_ID } ?: return
        val raw = box.nameText() ?: return
        val message = HandoffProtocol.parseMessage(raw) ?: return
        if (message.nonce in handled) return
        if (!message.isFor(tvId) || !message.isFresh(now) || message.takenBy != null) {
            remember(message.nonce)
            return
        }
        remember(message.nonce)
        claimAndPlay(key, box, raw, message)
    }

    private suspend fun claimAndPlay(key: String, box: JsonObject, raw: String, message: HandoffProtocol.Message) {
        val now = System.currentTimeMillis()
        val taken = HandoffProtocol.markTaken(raw, tvId, tvName, now) ?: return
        val claimed = box.deepCopy().apply {
            addProperty("name", taken)
            addProperty("_mtime", HandoffProtocol.isoTime(now))
        }
        datastore.put(key, listOf(claimed))
        // Two TVs on at once may both reach for a message sent to any TV: the last write wins,
        // and only the TV whose id stayed in it plays
        if (message.to == HandoffProtocol.ANY_TV) {
            delay(CLAIM_SETTLE_MS)
            val after = datastore.get(key, listOf(HandoffProtocol.MAILBOX_ID)).firstOrNull()
            val winner = HandoffProtocol.parseMessage(after?.nameText())
            if (winner != null && winner.nonce == message.nonce && winner.takenBy != tvId) {
                Log.i(TAG, "Another TV (${winner.takenName}) took ${message.id}")
                return
            }
        }
        Log.i(TAG, "Playing ${message.id} (${message.video}) from ${message.from} at ${message.positionMs} ms")
        HandoffOpen.start(
            context,
            HandoffOpen.Request(
                id = message.id,
                type = message.type,
                videoId = message.video,
                name = message.name,
                title = message.title,
                poster = message.poster,
                background = message.background,
                logo = message.logo,
                positionMs = message.positionMs,
                durationMs = message.durationMs,
                profileName = message.profile,
                fromDevice = message.from
            )
        )
    }

    private suspend fun writePresence(key: String, on: Boolean, currentName: String? = null) {
        val now = System.currentTimeMillis()
        val name = currentName ?: datastore.get(key, listOf(HandoffProtocol.PRESENCE_ID)).firstOrNull()?.nameText()
        val updated = HandoffProtocol.withTv(name, HandoffProtocol.Tv(tvId, tvName, now, on), now)
        datastore.put(key, listOf(HandoffProtocol.hiddenItem(HandoffProtocol.PRESENCE_ID, updated, now)))
        presenceWrittenAt = now
        presenceOn = on
    }

    private fun remember(nonce: String) {
        if (nonce in handled) return
        handled.addLast(nonce)
        while (handled.size > 32) handled.removeFirst()
    }

    private fun JsonObject.id(): String? = get("_id")?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.nameText(): String? = get("name")?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private const val TAG = "HandoffReceiver"
        private const val PREFS = "handoff"
        private const val KEY_ID = "tv_id"
        /** How often an on TV looks in the mailbox. */
        private const val POLL_MS = 4_000L
        private const val IDLE_MS = 15_000L
        private const val AUTH_BACKOFF_MS = 5 * 60_000L
        /** How often an on TV says so; phones list TVs seen within [HandoffProtocol.PRESENCE_FRESH_MS]. */
        private const val PRESENCE_EVERY_MS = 4 * 60_000L
        private const val CLAIM_SETTLE_MS = 900L
    }
}
