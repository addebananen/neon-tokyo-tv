"""
A pretend IPTV provider for the TV smoke test: answers the Xtream API (login neon / tokyo), the
XMLTV guide with programmes around the time it starts, an M3U playlist, and serves one short
test-card stream for every channel. The emulator reaches it at 10.0.2.2.

    python3 fake-iptv.py <port> <stream.ts or "-">
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(sys.argv[1])
STREAM = None if sys.argv[2] == "-" else open(sys.argv[2], "rb").read()
USER, PASS = "neon", "tokyo"

CATEGORIES = [{"category_id": "1", "category_name": "Sverige"}, {"category_id": "2", "category_name": "Sport"}]
STREAMS = [
    {"num": 1, "name": "Neon One", "stream_id": 11, "stream_icon": "", "epg_channel_id": "one.neon", "category_id": "1"},
    {"num": 2, "name": "Neon Two", "stream_id": 12, "stream_icon": "", "epg_channel_id": "two.neon", "category_id": "1"},
    {"num": 3, "name": "Neon Nyheter", "stream_id": 13, "stream_icon": "", "epg_channel_id": "", "category_id": "1"},
    {"num": 10, "name": "Neon Sport", "stream_id": 21, "stream_icon": "", "epg_channel_id": "sport.neon", "category_id": "2"},
    {"num": 101, "name": "SE: TV4 HD", "stream_id": 31, "stream_icon": "", "epg_channel_id": "", "category_id": "1"},
    {"num": 102, "name": "SE: SVT1 FHD", "stream_id": 32, "stream_icon": "", "epg_channel_id": "", "category_id": "1"},
]


def guide():
    now = int(time.time()) // 1800 * 1800
    fmt = lambda t: time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(t))
    shows = {"one.neon": ["Morning Neon", "City Pop Hour"], "two.neon": ["Tokyo Drive", "SHL: Frölunda – Färjestad"],
             "sport.neon": ["Allsvenskan Live", "Studio"]}
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv>']
    for cid, (first, second) in shows.items():
        out.append(f'<programme start="{fmt(now - 1800)}" stop="{fmt(now + 1800)}" channel="{cid}"><title>{first}</title>'
                   f'<desc>Live from the smoke test.</desc></programme>')
        out.append(f'<programme start="{fmt(now + 1800)}" stop="{fmt(now + 5400)}" channel="{cid}"><title>{second}</title></programme>')
    out.append('</tv>')
    return "\n".join(out).encode()


class Handler(BaseHTTPRequestHandler):
    def send(self, code, body, kind="application/json"):
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        login = q.get("username") == USER and q.get("password") == PASS
        if url.path == "/player_api.php":
            if not login:
                return self.send(200, json.dumps({"user_info": {"auth": 0}}))
            action = q.get("action")
            if action == "get_live_categories":
                return self.send(200, json.dumps(CATEGORIES))
            if action == "get_live_streams":
                return self.send(200, json.dumps(STREAMS))
            return self.send(200, json.dumps({"user_info": {"auth": 1, "status": "Active", "allowed_output_formats": ["ts", "m3u8"]},
                                              "server_info": {"url": "10.0.2.2"}}))
        if url.path == "/xmltv.php":
            return self.send(200, guide(), "application/xml") if login else self.send(401, "")
        if url.path == "/playlist.m3u":
            lines = ['#EXTM3U url-tvg="http://10.0.2.2:%d/xmltv.php?username=%s&password=%s"' % (PORT, USER, PASS)]
            for s in STREAMS:
                group = CATEGORIES[int(s["category_id"]) - 1]["category_name"]
                lines.append(f'#EXTINF:-1 tvg-id="{s["epg_channel_id"]}" group-title="{group}",{s["name"]}')
                lines.append(f'http://10.0.2.2:{PORT}/live/{USER}/{PASS}/{s["stream_id"]}.ts')
            return self.send(200, "\n".join(lines), "audio/x-mpegurl")
        if url.path.startswith(f"/live/{USER}/{PASS}/") and STREAM is not None:
            return self.send(200, STREAM, "video/mp2t")
        self.send(404, "")

    def log_message(self, *args):
        pass


ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
