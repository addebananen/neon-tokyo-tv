# Packs the layers from render.js as WebP for the app. Card layers are 1.5x the frames
# render.js draws them in, larger than a card ever gets on a 1080p screen, so they stay
# sharp when the subject grows on focus; the app decodes them at the card's size. Hero layers are
# 1280x720. The hero's subject and every moving part are cropped to where they are drawn, so
# they take little memory, and motion.json says where each one sits as left, top, right and
# bottom fractions of its frame, next to how it moves (SceneMotion.kt in the app reads it).
#
#   python3 pack.py [out dir] [assets dir]
import json
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "out")
DST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "service_art")
CARDS = {"wide": (448, 252), "square": (320, 320), "tall": (264, 396)}
SCALE = 1.5


def cropped(im, pad=6):
    """The image cut to what is visible in it, and that box as fractions of the image."""
    W, H = im.size
    box = im.getchannel("A").point(lambda a: 255 if a > 6 else 0).getbbox() or (0, 0, 2, 2)
    box = (max(0, box[0] - pad), max(0, box[1] - pad), min(W, box[2] + pad), min(H, box[3] + pad))
    return im.crop(box), [round(box[0] / W, 4), round(box[1] / H, 4), round(box[2] / W, 4), round(box[3] / H, 4)]


motion = json.load(open(os.path.join(SRC, "motion.json")))
# ONLY=code,code packs just those scenes, for quick turns while drawing; motion.json is always whole
only = set(filter(None, os.environ.get("ONLY", "").split(",")))
os.makedirs(DST, exist_ok=True)
for code, frames in motion.items():
    if only and code not in only:
        dst_motion = json.load(open(os.path.join(DST, "motion.json"))) if os.path.exists(os.path.join(DST, "motion.json")) else {}
        if code in dst_motion:
            motion[code] = dst_motion[code]
        continue
    for frame, m in frames.items():
        hero = frame == "hero"
        size = None if hero else (round(CARDS[frame][0] * SCALE), round(CARDS[frame][1] * SCALE))

        def load(name, mode):
            im = Image.open(os.path.join(SRC, f"{code}_{frame}_{name}@2x.png")).convert(mode)
            return im if hero else im.resize(size, Image.LANCZOS)

        load("bg", "RGB").save(os.path.join(DST, f"{code}_{frame}_bg.webp"), "WEBP", quality=80 if hero else 84, method=6)
        fg = load("fg", "RGBA")
        if hero:
            fg, m["fgBox"] = cropped(fg)
        fg.save(os.path.join(DST, f"{code}_{frame}_fg.webp"), "WEBP", quality=86 if hero else 84, method=6)
        for p in m["parts"]:
            im, p["box"] = cropped(load("p_" + p["name"], "RGBA"))
            im.save(os.path.join(DST, f"{code}_{frame}_p_{p['name']}.webp"), "WEBP", quality=86, method=6)

with open(os.path.join(DST, "motion.json"), "w") as out:
    json.dump(motion, out, separators=(",", ":"))
print(f"packed {len(motion)} scenes")
