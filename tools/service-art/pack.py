# Packs the layers from render.js as WebP for the app. Card layers are 1.5x the frames
# render.js draws them in, larger than a card ever gets on a 1080p screen, so they stay
# sharp when the subject grows on focus; the app decodes them at the card's size. Hero layers are
# 1280x720, with the subject cropped to where it is drawn so it takes little memory; the
# boxes it prints go into ServiceArt.heroSubjects in the app.
#
#   python3 pack.py [out dir] [assets dir]
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "out")
DST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "service_art")
CODES = "nfx hbm dnp amp atp sst pmp cru".split()
CARDS = {"wide": (448, 252), "square": (320, 320), "tall": (264, 396)}
SCALE = 1.5

os.makedirs(DST, exist_ok=True)
boxes = {}
for code in CODES:
    for frame, (w, h) in CARDS.items():
        size = (round(w * SCALE), round(h * SCALE))
        for layer in ("bg", "fg"):
            im = Image.open(os.path.join(SRC, f"{code}_{frame}_{layer}@2x.png"))
            im = im.convert("RGB" if layer == "bg" else "RGBA").resize(size, Image.LANCZOS)
            im.save(os.path.join(DST, f"{code}_{frame}_{layer}.webp"), "WEBP", quality=84, method=6)
    scene = Image.open(os.path.join(SRC, f"{code}_hero_bg@2x.png")).convert("RGB")
    scene.save(os.path.join(DST, f"{code}_hero_bg.webp"), "WEBP", quality=80, method=6)
    subject = Image.open(os.path.join(SRC, f"{code}_hero_fg@2x.png")).convert("RGBA")
    W, H = subject.size
    # Faint glow far out from the subject is not worth keeping
    box = subject.getchannel("A").point(lambda a: 255 if a > 6 else 0).getbbox()
    pad = 6
    box = (max(0, box[0] - pad), max(0, box[1] - pad), min(W, box[2] + pad), min(H, box[3] + pad))
    subject.crop(box).save(os.path.join(DST, f"{code}_hero_fg.webp"), "WEBP", quality=86, method=6)
    boxes[code] = (box[0] / W, box[1] / H, box[2] / W, box[3] / H)

for code, box in boxes.items():
    print(f'        "{code}" to listOf({", ".join(f"{round(v, 4)}f" for v in box)}),')
