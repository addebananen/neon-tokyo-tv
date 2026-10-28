package com.lumera.app.livetv

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
 * The page a phone opens from the QR code to add the IPTV service: an Xtream login or a playlist
 * link, typed on the phone rather than with the remote. The TV checks it with the provider before
 * it says it worked, so a typo shows up on the phone while it is still in hand.
 */
class IptvSetupServer(
    port: Int,
    private val context: Context,
    private val token: String,
    private val onConnected: () -> Unit
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
            val files = HashMap<String, String>()
            session.parseBody(files)
            val p = session.parms
            val source = when (p["kind"]) {
                "xtream" -> {
                    val server = p["server"].orEmpty().trim()
                    val user = p["user"].orEmpty().trim()
                    val pass = p["pass"].orEmpty()
                    if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) null else IptvSource.Xtream(server, user, pass)
                }
                "m3u" -> {
                    val url = p["url"].orEmpty().trim()
                    val scheme = url.substringBefore("://", "").lowercase()
                    if (scheme != "http" && scheme != "https") null else IptvSource.M3u(url, p["epg"].orEmpty().trim())
                }
                else -> null
            }
            if (source == null) {
                answer.put("ok", false).put("error", "Fill in every field")
            } else {
                val problem = runBlocking { withContext(Dispatchers.IO) { LiveTv.connect(context, source) } }
                if (problem == null) {
                    answer.put("ok", true)
                    main.post(onConnected)
                } else answer.put("ok", false).put("error", problem)
            }
        } catch (e: Exception) {
            answer.put("ok", false).put("error", "The TV could not read that")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", answer.toString())
    }

    private fun page() = """
<!DOCTYPE html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Neon Tokyo · Live TV</title>
<style>
  :root { --night:#0d0721; --panel:#160a2e; --text:#f6eaff; --lilac:#b9a6d6; --magenta:#ff3d9a; --cyan:#3ef0ff; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { min-height:100vh; background:radial-gradient(120% 80% at 50% 0%, #2a0f4f 0%, var(--night) 60%); color:var(--text);
         font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display:flex; justify-content:center; padding:28px 16px; }
  .card { width:100%; max-width:420px; background:rgba(22,10,46,.85); border:1px solid rgba(255,61,154,.35); border-radius:20px;
          padding:28px 22px; box-shadow:0 0 40px rgba(255,61,154,.15); }
  h1 { font-style:italic; font-size:26px; letter-spacing:1px; text-shadow:0 0 18px rgba(255,61,154,.8); }
  .sub { color:var(--lilac); font-size:14px; margin:6px 0 22px; line-height:1.45; }
  .tabs { display:flex; gap:8px; margin-bottom:18px; }
  .tab { flex:1; padding:11px; border-radius:12px; border:1px solid rgba(185,166,214,.3); background:transparent; color:var(--lilac);
         font-size:15px; font-weight:600; }
  .tab.on { border-color:var(--magenta); color:var(--text); background:rgba(255,61,154,.16); box-shadow:0 0 14px rgba(255,61,154,.3); }
  label { display:block; font-size:12px; letter-spacing:2px; text-transform:uppercase; color:var(--cyan); margin:14px 0 6px; }
  input { width:100%; padding:14px; border-radius:12px; border:1px solid rgba(185,166,214,.3); background:rgba(0,0,0,.35);
          color:var(--text); font-size:16px; outline:none; }
  input:focus { border-color:var(--cyan); }
  .hint { color:var(--lilac); font-size:12px; margin-top:6px; }
  button.go { width:100%; margin-top:24px; padding:15px; border:0; border-radius:26px; font-size:16px; font-weight:700; color:#1a0420;
              background:linear-gradient(90deg, var(--magenta), #ff7ac0); box-shadow:0 0 22px rgba(255,61,154,.45); }
  button.go:disabled { opacity:.6; }
  .msg { margin-top:16px; font-size:14px; min-height:20px; text-align:center; }
  .msg.bad { color:#ff8fb8; } .msg.good { color:var(--cyan); }
  [hidden] { display:none !important; }
</style></head>
<body><div class="card">
  <h1>Live TV</h1>
  <p class="sub">Add your IPTV service to Neon Tokyo. It is kept encrypted on the TV and only ever sent to your provider.</p>
  <div class="tabs">
    <button class="tab on" id="tx" type="button">Xtream login</button>
    <button class="tab" id="tm" type="button">M3U link</button>
  </div>
  <form id="f" autocomplete="off">
    <div id="xt">
      <label for="server">Server</label><input id="server" name="server" inputmode="url" autocapitalize="off" placeholder="http://example.com:8080">
      <label for="user">Username</label><input id="user" name="user" autocapitalize="off" autocorrect="off" spellcheck="false">
      <label for="pass">Password</label><input id="pass" name="pass" type="password">
    </div>
    <div id="m3" hidden>
      <label for="url">Playlist link</label><input id="url" name="url" inputmode="url" autocapitalize="off" placeholder="https://…/playlist.m3u">
      <label for="epg">Guide link (optional)</label><input id="epg" name="epg" inputmode="url" autocapitalize="off" placeholder="https://…/epg.xml.gz">
      <p class="hint">Leave the guide empty if the playlist names one itself.</p>
    </div>
    <button class="go" id="go" type="submit">Connect</button>
    <p class="msg" id="msg"></p>
  </form>
</div>
<script>
  var kind = 'xtream';
  function pick(k) {
    kind = k;
    document.getElementById('xt').hidden = k !== 'xtream';
    document.getElementById('m3').hidden = k !== 'm3u';
    document.getElementById('tx').classList.toggle('on', k === 'xtream');
    document.getElementById('tm').classList.toggle('on', k === 'm3u');
    document.getElementById('msg').textContent = '';
  }
  document.getElementById('tx').onclick = function () { pick('xtream'); };
  document.getElementById('tm').onclick = function () { pick('m3u'); };
  document.getElementById('f').addEventListener('submit', async function (e) {
    e.preventDefault();
    var go = document.getElementById('go'), msg = document.getElementById('msg');
    var body = new URLSearchParams();
    body.set('kind', kind);
    (kind === 'xtream' ? ['server', 'user', 'pass'] : ['url', 'epg']).forEach(function (n) { body.set(n, document.getElementById(n).value); });
    go.disabled = true; go.textContent = 'Checking with your provider…'; msg.textContent = ''; msg.className = 'msg';
    try {
      var res = await fetch(location.pathname, { method: 'POST', body: body });
      var out = await res.json();
      if (out.ok) { msg.className = 'msg good'; msg.textContent = 'Connected. The channels are loading on the TV.'; go.textContent = 'Done'; return; }
      msg.className = 'msg bad'; msg.textContent = out.error || 'That did not work';
    } catch (err) {
      msg.className = 'msg bad'; msg.textContent = 'Lost the TV. Is the phone on the same Wi-Fi?';
    }
    go.disabled = false; go.textContent = 'Connect';
  });
</script>
${DisconnectBanner.htmlSnippet}
</body></html>
""".trimIndent()

    /** Runs one setup server on the first free port from 8080, behind a fresh random path. */
    class Manager {
        @Volatile private var server: IptvSetupServer? = null

        suspend fun start(context: Context, onConnected: () -> Unit): ServerInfo? = withContext(Dispatchers.IO) {
            val ip = NetworkUtils.getLocalIpAddress() ?: return@withContext null
            val token = AvatarServerManager.newToken()
            for (port in 8080..8090) {
                try {
                    val s = IptvSetupServer(port, context.applicationContext, token, onConnected)
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
