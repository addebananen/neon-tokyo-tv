package com.lumera.app.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lumera.app.remote_input.AvatarServerManager
import com.lumera.app.remote_input.DisconnectBanner
import com.lumera.app.remote_input.NetworkUtils
import com.lumera.app.remote_input.ServerInfo
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.BindException

/**
 * The page a phone opens from the Today menu's QR code: the town for the weather and the Home
 * Assistant address and token, typed on the phone rather than with the remote. The TV checks each
 * before it says it worked, so a typo shows up while the phone is still in hand.
 */
class TodaySetupServer(
    port: Int,
    private val context: Context,
    private val token: String,
    private val onChanged: () -> Unit
) : NanoHTTPD(port) {

    private val main = Handler(Looper.getMainLooper())

    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/ping") return DisconnectBanner.pingResponse()
        if (session.uri.trimEnd('/') != "/$token") return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        return when (session.method) {
            Method.GET -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page())
            Method.POST -> submit(session)
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
        }
    }

    private fun submit(session: IHTTPSession): Response {
        val answer = JSONObject()
        try {
            session.parseBody(HashMap())
            val p = session.parms
            when (p["kind"]) {
                "place" -> {
                    val name = p["place"].orEmpty().trim()
                    val place = if (name.isEmpty()) null else runBlocking { HomeWeather.find(name, 1).firstOrNull() }
                    if (place == null) answer.put("ok", false).put("error", "Found no place called that")
                    else {
                        HomeWeather.setPlace(context, place)
                        answer.put("ok", true).put("said", "Weather for ${listOfNotNull(place.name, place.region).joinToString(", ")}")
                        main.post(onChanged)
                    }
                }
                "ha" -> {
                    val url = p["url"].orEmpty().trim()
                    val key = p["token"].orEmpty().trim()
                    if (url.isEmpty() || key.isEmpty()) answer.put("ok", false).put("error", "Fill in both fields")
                    else {
                        val problem = runBlocking { withContext(Dispatchers.IO) { HomeLights.linkHomeAssistant(context, url, key) } }
                        if (problem == null) {
                            answer.put("ok", true).put("said", "Linked. The lights come on the TV in a moment.")
                            main.post(onChanged)
                        } else answer.put("ok", false).put("error", problem)
                    }
                }
                else -> answer.put("ok", false).put("error", "The TV could not read that")
            }
        } catch (e: Exception) {
            answer.put("ok", false).put("error", "The TV could not read that")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", answer.toString())
    }

    private fun page(): String {
        val place = HomeWeather.place(context)?.name ?: HomeWeather.zoneCity().orEmpty()
        val ha = HomeLights.homeAssistantUrl(context).orEmpty()
        fun attr(text: String) = text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        return """
<!DOCTYPE html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Neon Tokyo · Today</title>
<style>
  :root { --night:#0d0721; --text:#f6eaff; --lilac:#b9a6d6; --magenta:#ff3d9a; --cyan:#3ef0ff; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { min-height:100vh; background:radial-gradient(120% 80% at 50% 0%, #2a0f4f 0%, var(--night) 60%); color:var(--text);
         font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display:flex; flex-direction:column; align-items:center; gap:18px; padding:28px 16px; }
  .card { width:100%; max-width:420px; background:rgba(22,10,46,.85); border:1px solid rgba(255,61,154,.35); border-radius:20px;
          padding:24px 22px; box-shadow:0 0 40px rgba(255,61,154,.15); }
  h1 { font-style:italic; font-size:26px; letter-spacing:1px; text-shadow:0 0 18px rgba(255,61,154,.8); }
  h2 { font-size:18px; margin-bottom:4px; }
  .sub { color:var(--lilac); font-size:14px; margin:6px 0 4px; line-height:1.45; }
  label { display:block; font-size:12px; letter-spacing:2px; text-transform:uppercase; color:var(--cyan); margin:14px 0 6px; }
  input { width:100%; padding:14px; border-radius:12px; border:1px solid rgba(185,166,214,.3); background:rgba(0,0,0,.35);
          color:var(--text); font-size:16px; outline:none; }
  input:focus { border-color:var(--cyan); }
  .hint { color:var(--lilac); font-size:12px; margin-top:6px; line-height:1.4; }
  button.go { width:100%; margin-top:18px; padding:14px; border:0; border-radius:26px; font-size:16px; font-weight:700; color:#1a0420;
              background:linear-gradient(90deg, var(--magenta), #ff7ac0); box-shadow:0 0 22px rgba(255,61,154,.45); }
  button.go:disabled { opacity:.6; }
  .msg { margin-top:12px; font-size:14px; min-height:20px; text-align:center; }
  .msg.bad { color:#ff8fb8; } .msg.good { color:var(--cyan); }
</style></head>
<body>
<div class="card" style="background:transparent;border:0;box-shadow:none;padding:0 6px">
  <h1>Today</h1>
  <p class="sub">The everyday cards on Neon Home. Each part saves on its own.</p>
</div>
<form class="card" data-kind="place" autocomplete="off">
  <h2>Weather</h2>
  <label for="place">Town</label><input id="place" name="place" value="${attr(place)}" placeholder="Luleå">
  <button class="go" type="submit">Use this town</button>
  <p class="msg"></p>
</form>
<form class="card" data-kind="ha" autocomplete="off">
  <h2>Home Assistant</h2>
  <p class="sub">For lights from IKEA, Shelly, Tuya, Hue and the rest. A Hue bridge alone needs nothing here: the TV finds it itself.</p>
  <label for="url">Address</label><input id="url" name="url" inputmode="url" autocapitalize="off" value="${attr(ha)}" placeholder="http://homeassistant.local:8123">
  <label for="token">Long-lived access token</label><input id="token" name="token" type="password" autocapitalize="off" autocorrect="off" spellcheck="false">
  <p class="hint">In Home Assistant: your profile › Security › Long-lived access tokens › Create token. The token stays encrypted on the TV and is only ever sent to your Home Assistant.</p>
  <button class="go" type="submit">Link</button>
  <p class="msg"></p>
</form>
<script>
  document.querySelectorAll('form').forEach(function (f) {
    f.addEventListener('submit', async function (e) {
      e.preventDefault();
      var go = f.querySelector('button'), msg = f.querySelector('.msg'), label = go.textContent;
      var body = new URLSearchParams();
      body.set('kind', f.dataset.kind);
      f.querySelectorAll('input').forEach(function (i) { body.set(i.name, i.value); });
      go.disabled = true; go.textContent = 'Checking…'; msg.textContent = ''; msg.className = 'msg';
      try {
        var res = await fetch(location.pathname, { method: 'POST', body: body });
        var out = await res.json();
        msg.className = 'msg ' + (out.ok ? 'good' : 'bad');
        msg.textContent = out.ok ? (out.said || 'Saved') : (out.error || 'That did not work');
      } catch (err) {
        msg.className = 'msg bad'; msg.textContent = 'Lost the TV. Is the phone on the same Wi-Fi?';
      }
      go.disabled = false; go.textContent = label;
    });
  });
</script>
${DisconnectBanner.htmlSnippet}
</body></html>
""".trimIndent()
    }

    /** Runs one setup server on the first free port from 8091, behind a fresh random path. */
    class Manager {
        @Volatile private var server: TodaySetupServer? = null

        suspend fun start(context: Context, onChanged: () -> Unit): ServerInfo? = withContext(Dispatchers.IO) {
            val ip = NetworkUtils.getLocalIpAddress() ?: return@withContext null
            val token = AvatarServerManager.newToken()
            for (port in 8091..8099) {
                try {
                    val s = TodaySetupServer(port, context.applicationContext, token, onChanged)
                    s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    server = s
                    return@withContext ServerInfo(ip, port, "/$token")
                } catch (e: BindException) {
                    continue
                } catch (e: Exception) {
                    continue
                }
            }
            null
        }

        fun stop() {
            server?.stop()
            server = null
        }
    }
}
