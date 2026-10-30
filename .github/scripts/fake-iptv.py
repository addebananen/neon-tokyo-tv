"""
A pretend IPTV provider for the TV smoke test: answers the Xtream API (login neon / tokyo), the
XMLTV guide with programmes around the time it starts, an M3U playlist, and serves one short
test-card stream for every channel. The emulator reaches it at 10.0.2.2.

The login big / tokyo is a provider the size of a real one: 40 000 channels in 600 groups and a
gzipped guide for 15 000 of them, a day ahead with long descriptions, as the one that froze the
TV on "Tuning in the channels".

    python3 fake-iptv.py <port> <stream.ts or "-">
"""
import gzip
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


BIG_USER = "big"
BIG_CHANNELS, BIG_GUIDED, BIG_GROUPS = 40_000, 15_000, 600
COUNTRIES = ["SE", "UK", "DE", "AR", "US", "FR", "PL", "TR"]
_big = {}


def big_categories():
    return [{"category_id": str(i), "category_name": f"{COUNTRIES[i % 8]} | Group {i}", "parent_id": 0} for i in range(BIG_GROUPS)]


def big_streams():
    if "streams" not in _big:
        out = []
        for i in range(1, BIG_CHANNELS + 1):
            c = COUNTRIES[i % 8]
            out.append({"num": i, "name": f"{c}: Big Channel {i} {['HD', 'FHD', 'SD', '4K'][i % 4]}", "stream_type": "live",
                        "stream_id": 100000 + i, "stream_icon": f"http://10.0.2.2:{PORT}/logo/{i}.png",
                        "epg_channel_id": f"big{i}.{c.lower()}" if i <= BIG_GUIDED else "", "added": "1700000000",
                        "category_id": str(i % BIG_GROUPS), "custom_sid": "", "tv_archive": 0, "direct_source": "",
                        "tv_archive_duration": 0})
        _big["streams"] = json.dumps(out).encode()
    return _big["streams"]


# The library: Dune and Dune: Prophecy are what the smoke test opens from Search. The big login
# has 30 000 more films and 5 000 series around them, the size of a real provider's library.
VOD = [
    {"num": 1, "name": "SE - Dune (2021) 4K", "stream_id": 701, "stream_type": "movie", "container_extension": "ts",
     "tmdb": "438631", "category_id": "10", "added": "1700000000", "rating": "8"},
    {"num": 2, "name": "SE - Dune: Part Two (2024)", "stream_id": 702, "stream_type": "movie", "container_extension": "ts",
     "tmdb": "", "category_id": "10", "added": "1700000000", "rating": "8"},
]
SERIES = [{"num": 1, "name": "SE - Dune: Prophecy", "series_id": 801, "releaseDate": "2024-11-17", "tmdb": "", "category_id": "20"}]
EPISODES = {"801": {"seasons": [], "info": {"name": "Dune: Prophecy"}, "episodes": {"1": [
    {"id": "9001", "episode_num": 1, "title": "The Hidden Hand", "container_extension": "ts", "season": 1},
    {"id": "9002", "episode_num": 2, "title": "Two Wolves", "container_extension": "ts", "season": 1}]}}}
BIG_FILMS, BIG_SERIES = 30_000, 5_000


def vod(is_big):
    if not is_big:
        return json.dumps(VOD).encode()
    if "vod" not in _big:
        out = list(VOD)
        for i in range(1, BIG_FILMS + 1):
            out.append({"num": i + 10, "name": f"{COUNTRIES[i % 8]} - Big Film {i} ({1950 + i % 75})", "stream_id": 200000 + i,
                        "stream_type": "movie", "container_extension": ["mkv", "mp4"][i % 2], "tmdb": str(900000 + i),
                        "category_id": str(i % 40), "added": "1700000000", "rating": "6.5", "stream_icon": ""})
        _big["vod"] = json.dumps(out).encode()
    return _big["vod"]


def series(is_big):
    if not is_big:
        return json.dumps(SERIES).encode()
    if "series" not in _big:
        out = list(SERIES)
        for i in range(1, BIG_SERIES + 1):
            out.append({"num": i + 10, "name": f"{COUNTRIES[i % 8]} - Big Series {i}", "series_id": 300000 + i,
                        "releaseDate": f"{1990 + i % 35}-01-01", "tmdb": "", "category_id": str(i % 20), "cover": ""})
        _big["series"] = json.dumps(out).encode()
    return _big["series"]


def library(action, q, is_big):
    if action == "get_vod_streams":
        return vod(is_big)
    if action == "get_series":
        return series(is_big)
    if action == "get_series_info":
        return json.dumps(EPISODES.get(q.get("series_id"), {"episodes": {}})).encode()
    return None


def big_guide():
    if "guide" not in _big:
        start = int(time.time()) // 3600 * 3600 - 2 * 3600
        fmt = lambda t: time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(t))
        desc = "A long description of tonight's programme, as a real guide has for every show. " * 4
        parts = ['<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="fake">']
        for i in range(1, BIG_GUIDED + 1):
            cid = f"big{i}.{COUNTRIES[i % 8].lower()}"
            parts.append(f'<channel id="{cid}"><display-name>Big Channel {i}</display-name></channel>')
        for i in range(1, BIG_GUIDED + 1):
            cid = f"big{i}.{COUNTRIES[i % 8].lower()}"
            for h in range(30):
                t = start + h * 3600
                title = "SHL: Big Match" if i == 7 and h == 2 else f"Show {(i + h) % 4000}"
                parts.append(f'<programme start="{fmt(t)}" stop="{fmt(t + 3600)}" channel="{cid}"><title lang="en">{title}</title>'
                             f'<desc lang="en">{desc}</desc></programme>')
        parts.append("</tv>")
        _big["guide"] = gzip.compress("\n".join(parts).encode(), 1)
    return _big["guide"]


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
        big = q.get("username") == BIG_USER and q.get("password") == PASS
        if big and url.path == "/player_api.php":
            action = q.get("action")
            if action == "get_live_categories":
                return self.send(200, json.dumps(big_categories()))
            if action == "get_live_streams":
                return self.send(200, big_streams())
            if library(action, q, True) is not None:
                return self.send(200, library(action, q, True))
            return self.send(200, json.dumps({"user_info": {"auth": 1, "status": "Active", "allowed_output_formats": ["ts", "m3u8"]},
                                              "server_info": {"url": "10.0.2.2"}}))
        if big and url.path == "/xmltv.php":
            return self.send(200, big_guide(), "application/xml")
        if url.path == "/player_api.php":
            if not login:
                return self.send(200, json.dumps({"user_info": {"auth": 0}}))
            action = q.get("action")
            if action == "get_live_categories":
                return self.send(200, json.dumps(CATEGORIES))
            if action == "get_live_streams":
                return self.send(200, json.dumps(STREAMS))
            if library(action, q, False) is not None:
                return self.send(200, library(action, q, False))
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
        # Films and episodes play the same test card, for either login
        for kind in ("movie", "series"):
            for user in (USER, BIG_USER):
                if url.path.startswith(f"/{kind}/{user}/{PASS}/") and STREAM is not None:
                    return self.send(200, STREAM, "video/mp2t")
        self.send(404, "")

    def log_message(self, *args):
        pass


if __name__ == "__main__" and len(sys.argv) > 3 and sys.argv[3] == "--sizes":
    print(f"big streams {len(big_streams()) // 1_000_000} MB, big guide {len(big_guide()) // 1_000_000} MB gzipped")
    sys.exit(0)

ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
