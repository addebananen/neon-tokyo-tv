#!/usr/bin/env python3
"""A pretend Siri Remote (gen 3) for the emulator test.

  siri-sim.py make <dir>         writes the scripts the bridge plays in simulation (`play`)
  siri-sim.py check <log> <part> checks what the bridge did against what an Apple TV does

Each scenario is a run of reports between `mark` lines; the check reads the bridge's log
(`key <code> down|up r<n>`, `voice start`, `power: ...`) back by the same marks.
"""
import ctypes
import ctypes.util
import math
import os
import sys

# Android key codes
HOME, BACK, UP, DOWN, LEFT, RIGHT, OK = 3, 4, 19, 20, 21, 22, 23
VOL_UP, VOL_DOWN, PLAY_PAUSE, MUTE, POWER, WAKEUP, APP_SWITCH = 24, 25, 85, 164, 26, 224, 187
DPAD = {UP, DOWN, LEFT, RIGHT}

# gen 3 buttons (report 0xFB)
B_TV, B_VOL_UP, B_VOL_DOWN, B_SELECT, B_POWER, B_SIRI, B_BACK, B_MUTE, B_PLAY = (
    0x0001, 0x0002, 0x0004, 0x0008, 0x0010, 0x0020, 0x0040, 0x0080, 0x0100)
B_RING_UP, B_RING_RIGHT, B_RING_DOWN, B_RING_LEFT = 0x0200, 0x0400, 0x0800, 0x1000


class Script:
    def __init__(self):
        self.lines = []
        self.t = 0.0  # seconds

    def at(self, secs, text):
        self.lines.append((secs, text))

    def wait(self, secs):
        self.t += secs

    def mark(self, name):
        self.wait(0.6)
        self.at(self.t, f"mark {name}")
        self.wait(0.05)

    def ready(self):
        self.at(self.t, "ready 3")

    def lost(self):
        self.at(self.t, "lost")

    def buttons(self, mask):
        self.at(self.t, f"r fb {mask & 0xFF:02x}{mask >> 8:02x}")

    def press(self, mask, hold=0.08):
        self.buttons(mask)
        self.wait(hold)
        self.buttons(0)

    def touch(self, x, y, state=4):
        """One finger at (x, y) on the unit disc (+y up), as the remote encodes it."""
        rx = int(round(x * 640 - 320)) & 0xFFF
        ry = int(round(y * 640 - 347)) & 0xFFF
        slot = [rx & 0xFF, (rx >> 8) | ((ry & 0x0F) << 4), ry >> 4, 40, 30, 0, state]
        self.at(self.t, "r fc " + bytes([0, 0, 0, 0] + slot).hex())

    def lift(self):
        self.at(self.t, "r fc " + bytes([0] * 4 + [0, 0, 0, 0, 0, 0, 5]).hex())

    def slide(self, a, b, secs, lift=True):
        n = max(2, int(secs / 0.015))
        for i in range(n + 1):
            k = i / n
            self.touch(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, 3 if i == 0 else 4)
            self.wait(0.015)
        if lift:
            self.lift()

    def audio(self, frames):
        for seq, f in enumerate(frames):
            data = bytes([0, 0, seq & 0xFF, seq >> 8, len(f)]) + f
            data += bytes(99 - len(data))
            self.at(self.t, "r fa " + data.hex())
            self.wait(0.02)

    def write(self, path):
        with open(path, "w") as out:
            for secs, text in sorted(self.lines, key=lambda l: l[0]):
                out.write(f"{int(secs * 1000)} {text}\n")


def opus_frames(seconds, hz):
    """A tone as the remote sends speech: Opus, CELT only, wideband, 20 ms (TOC 0xB8)."""
    lib = ctypes.CDLL(ctypes.util.find_library("opus") or "libopus.so.0")
    err = ctypes.c_int()
    lib.opus_encoder_create.restype = ctypes.c_void_p
    enc = lib.opus_encoder_create(16000, 1, 2051, ctypes.byref(err))  # RESTRICTED_LOWDELAY
    assert err.value == 0, err.value
    lib.opus_encoder_ctl(ctypes.c_void_p(enc), 4002, ctypes.c_int(32000))  # SET_BITRATE
    lib.opus_encoder_ctl(ctypes.c_void_p(enc), 4008, ctypes.c_int(1103))  # SET_BANDWIDTH WB
    frames = []
    buf = (ctypes.c_ubyte * 94)()
    for n in range(int(seconds * 50)):
        pcm = (ctypes.c_short * 320)(*[
            int(9000 * math.sin(2 * math.pi * hz * (n * 320 + i) / 16000)) for i in range(320)])
        size = lib.opus_encode(ctypes.c_void_p(enc), pcm, 320, buf, 94)
        assert size > 0, size
        frames.append(bytes(buf[:size]))
    assert frames[0][0] == 0xB8, hex(frames[0][0])
    return frames


TONE_HZ = 440


def make(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    s = Script()
    s.ready()
    s.wait(0.8)  # past the burst window after connecting

    s.mark("click")
    s.press(B_SELECT)

    s.mark("slow-right")
    s.slide((-0.4, 0.0), (0.4, 0.0), 1.6)

    s.mark("slow-left")
    s.slide((0.4, 0.05), (-0.4, -0.05), 1.6)

    s.mark("fling-down")
    s.slide((0.0, 0.6), (0.0, -0.6), 0.12)
    s.wait(1.5)

    s.mark("fling-caught")
    s.slide((0.0, -0.6), (0.0, 0.6), 0.12)
    s.wait(0.08)
    s.touch(0.0, 0.0, 3)  # a touch stops the glide, like on the Apple TV
    s.wait(0.3)
    s.lift()
    s.wait(1.0)

    s.mark("slanted")
    s.slide((-0.5, -0.2), (0.5, 0.2), 1.4)  # a slant stays on its axis

    s.mark("hold-ring-right")
    s.buttons(B_RING_RIGHT)
    s.wait(1.0)
    s.buttons(0)

    s.mark("ring-up")
    s.press(B_RING_UP)

    s.mark("tv")
    s.press(B_TV)
    s.wait(0.5)

    s.mark("tv-double")
    s.press(B_TV, 0.06)
    s.wait(0.1)
    s.press(B_TV, 0.06)
    s.wait(0.5)

    s.mark("tv-hold")
    s.buttons(B_TV)
    s.wait(1.1)
    s.buttons(0)
    s.wait(0.5)

    s.mark("back")
    s.press(B_BACK)

    s.mark("back-hold")
    s.buttons(B_BACK)
    s.wait(1.3)
    s.buttons(0)

    s.mark("volume")
    s.press(B_VOL_UP)
    s.wait(0.2)
    s.press(B_VOL_DOWN)

    s.mark("play-mute")
    s.press(B_PLAY)
    s.wait(0.2)
    s.press(B_MUTE)

    s.mark("dropped-mid-hold")
    s.buttons(B_RING_DOWN)
    s.wait(0.6)
    s.lost()
    s.wait(0.5)
    # back again: the presses it buffered come in a burst, only the waking one counts
    s.ready()
    s.press(B_VOL_UP, 0.02)
    s.press(B_PLAY, 0.02)
    s.press(B_SELECT, 0.02)
    s.wait(1.0)

    s.mark("siri")
    s.buttons(B_SIRI)
    s.wait(0.05)
    s.audio(opus_frames(1.6, TONE_HZ))
    s.buttons(0)
    s.wait(0.2)
    s.mark("end")
    s.write(os.path.join(out_dir, "remote.sim"))

    for name, label in (("power-off", "power-off"), ("power-on", "power-on")):
        p = Script()
        p.mark(label)
        p.press(B_POWER)
        p.mark("end")
        p.write(os.path.join(out_dir, f"{name}.sim"))
    print(f"wrote {out_dir}")


# ---------------------------------------------------------------------- check

def parts(log):
    """Splits the bridge's log into {mark: [events]}."""
    out, cur = {}, None
    for line in log:
        line = line.strip()
        if line.startswith("mark "):
            cur = line[5:]
            out[cur] = []
        elif cur is not None and line:
            out[cur].append(line)
    return out


def keys(events):
    """[(code, down, repeat, canceled)]"""
    res = []
    for e in events:
        p = e.split()
        if p[0] == "key":
            res.append((int(p[1]), p[2] == "down", int(p[3][1:]), "canceled" in p))
    return res


def taps(events, code):
    return sum(1 for k in keys(events) if k[0] == code and k[1] and k[2] == 0)


def check(log_path, which):
    log = open(log_path, errors="replace").read().splitlines()
    got = parts(log)
    fails = []

    def expect(name, ok, why):
        if not ok:
            fails.append(f"{name}: {why} | {got.get(name)}")
        print(("ok    " if ok else "FAIL  ") + f"{name}: {why}")

    for line in log:
        if "NOT INJECTED" in line:
            fails.append(f"key not injected: {line}")

    if which == "remote":
        for name in ("click", "slow-right", "slow-left", "fling-down", "fling-caught", "slanted",
                     "hold-ring-right", "ring-up", "tv", "tv-double", "tv-hold", "back", "back-hold",
                     "volume", "play-mute", "dropped-mid-hold", "siri"):
            if name not in got:
                fails.append(f"{name}: never reached")
        k = keys(got.get("click", []))
        expect("click", k == [(OK, True, 0, False), (OK, False, 0, False)], "OK down, OK up")

        r = taps(got.get("slow-right", []), RIGHT)
        others = [x for x in keys(got.get("slow-right", [])) if x[0] != RIGHT]
        expect("slow-right", 1 <= r <= 3 and not others, f"a slow slide steps 1-3 right ({r}), nothing else")
        l = taps(got.get("slow-left", []), LEFT)
        others = [x for x in keys(got.get("slow-left", [])) if x[0] != LEFT]
        expect("slow-left", 1 <= l <= 3 and not others, f"steps 1-3 left ({l}), nothing else")

        d = taps(got.get("fling-down", []), DOWN)
        others = [x for x in keys(got.get("fling-down", [])) if x[0] != DOWN]
        expect("fling-down", d >= 6 and not others, f"a flick glides on, 6+ steps down ({d})")
        free = d
        c = taps(got.get("fling-caught", []), UP)
        expect("fling-caught", 1 <= c < free, f"touching stops the glide early ({c} < {free})")

        sl = keys(got.get("slanted", []))
        expect("slanted", sl and all(x[0] == RIGHT for x in sl), f"a slanted slide stays right ({[x[0] for x in sl]})")

        h = keys(got.get("hold-ring-right", []))
        reps = [x for x in h if x[0] == RIGHT and x[1] and x[2] > 0]
        expect("hold-ring-right", h and h[0] == (RIGHT, True, 0, False) and h[-1][:2] == (RIGHT, False)
               and 8 <= len(reps) <= 14, f"held edge repeats after 0.4 s every 50 ms ({len(reps)} repeats)")
        expect("ring-up", keys(got.get("ring-up", [])) == [(UP, True, 0, False), (UP, False, 0, False)], "up")

        expect("tv", keys(got.get("tv", [])) == [(HOME, True, 0, False), (HOME, False, 0, False)], "TV = Home")
        expect("tv-double", keys(got.get("tv-double", [])) == [(APP_SWITCH, True, 0, False), (APP_SWITCH, False, 0, False)],
               "TV twice = recent apps, no Home")
        th = got.get("tv-hold", [])
        expect("tv-hold", any(e.startswith("control center:") for e in th) and taps(th, HOME) == 0,
               "TV held = Control Center, no Home")

        expect("back", keys(got.get("back", [])) == [(BACK, True, 0, False), (BACK, False, 0, False)], "back")
        bh = keys(got.get("back-hold", []))
        expect("back-hold", (BACK, False, 0, True) in bh and taps(got.get("back-hold", []), HOME) == 1
               and bh[-1][:2] == (HOME, False), "Back held = canceled back, then Home")

        v = keys(got.get("volume", []))
        expect("volume", [x[0] for x in v if x[1] and x[2] == 0] == [VOL_UP, VOL_DOWN], "volume up, down")
        pm = keys(got.get("play-mute", []))
        expect("play-mute", [x[0] for x in pm if x[1]] == [PLAY_PAUSE, MUTE], "play/pause, mute")

        dm = keys(got.get("dropped-mid-hold", []))
        downs = [x[0] for x in dm if x[1] and x[2] == 0]
        expect("dropped-mid-hold", (DOWN, False, 0, True) in dm and downs.count(OK) == 1
               and VOL_UP not in downs and PLAY_PAUSE not in downs,
               f"a drop lets go of the held key; of the buffered presses only the last counts ({downs})")

        sv = got.get("siri", [])
        expect("siri", "voice start" in sv and "voice stop" in sv and not keys(sv), "Siri = voice search, no key")
        if fails:
            print("\n".join(fails))
            sys.exit(1)
    elif which in ("power-off", "power-on"):
        ev = got.get(which, [])
        want = (POWER, "on") if which == "power-off" else (WAKEUP, "off")
        expect(which, f"power: screen was {want[1]}" in ev and taps(ev, want[0]) == 1,
               f"Power with the screen {want[1]} sends {want[0]}")
        if fails:
            sys.exit(1)


if __name__ == "__main__":
    if sys.argv[1] == "make":
        make(sys.argv[2])
    else:
        check(sys.argv[2], sys.argv[3])
