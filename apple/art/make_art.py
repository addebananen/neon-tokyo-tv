#!/usr/bin/env python3
"""Draws the Apple app icons and Apple TV top shelf images in the Neon Tokyo banner style:
night sky, striped sunset, the Tokyo tower in neon and the name in Kanit.

    python3 apple/art/make_art.py   (needs Pillow; writes into apple/NeonTokyo/*/Assets.xcassets)
"""
import json
import pathlib

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONT = ROOT / "app/src/main/res/font/kanit_semibold_italic.ttf"
SCENE = pathlib.Path(__file__).resolve().parent / "skyline.png"
NIGHT = (13, 7, 33)
VIOLET = (42, 15, 79)
MAGENTA = (255, 61, 154)
SUN_TOP = (255, 196, 107)
SUN_BOTTOM = (255, 61, 154)
INFO = {"author": "xcode", "version": 1}


def gradient(w, h, top, bottom, mid=None):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(h - 1, 1)
        if mid:
            a, b, tt = (top, mid, t * 2) if t < 0.5 else (mid, bottom, (t - 0.5) * 2)
        else:
            a, b, tt = top, bottom, t
        d.line([(0, y), (w, y)], fill=tuple(int(a[i] + (b[i] - a[i]) * tt) for i in range(3)))
    return img


def sun(size):
    """A striped sunset disc on transparency."""
    s = size
    disc = gradient(s, s, SUN_TOP, SUN_BOTTOM).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([0, 0, s - 1, s - 1], fill=255)
    # the stripes cut through the lower half
    for i, frac in enumerate([0.58, 0.68, 0.77, 0.85]):
        hgt = s * (0.025 + 0.012 * i)
        md.rectangle([0, s * frac, s, s * frac + hgt], fill=0)
    md.rectangle([0, s * 0.92, s, s], fill=0)
    disc.putalpha(mask)
    return disc


def tower(w, h, width):
    """The tower's lines in neon: a soft glow under a bright core."""
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def draw(d, colour, lw):
        cx = w / 2
        top = (cx, h * 0.08)
        d.line([top, (w * 0.18, h * 0.95)], fill=colour, width=lw)
        d.line([top, (w * 0.82, h * 0.95)], fill=colour, width=lw)
        d.line([(cx, h * 0.0), top], fill=colour, width=lw)
        d.line([(w * 0.36, h * 0.40), (w * 0.64, h * 0.40)], fill=colour, width=lw)
        d.line([(w * 0.26, h * 0.66), (w * 0.74, h * 0.66)], fill=colour, width=lw)
        d.arc([w * 0.30, h * 0.80, w * 0.70, h * 1.05], start=200, end=340, fill=colour, width=lw)

    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw(ImageDraw.Draw(glow), MAGENTA + (255,), int(width * 2.4))
    glow = glow.filter(ImageFilter.GaussianBlur(width * 1.6))
    layer = Image.alpha_composite(layer, glow)
    core = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw(ImageDraw.Draw(core), (255, 120, 190, 255), width)
    draw(ImageDraw.Draw(core), (255, 230, 245, 255), max(1, width // 3))
    return Image.alpha_composite(layer, core)


def neon_text(canvas_size, text, xy, size, anchor="lm"):
    w, h = canvas_size
    font = ImageFont.truetype(str(FONT), size)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).text(xy, text, font=font, fill=MAGENTA + (255,), anchor=anchor)
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.12))
    out = Image.alpha_composite(Image.new("RGBA", (w, h), (0, 0, 0, 0)), glow)
    out = Image.alpha_composite(out, glow)
    ImageDraw.Draw(out).text(xy, text, font=font, fill=(255, 244, 250, 255), anchor=anchor)
    return out


def tv_layers(w, h):
    """Back: sky and sun. Front: tower and name. Apple TV moves them apart for the parallax."""
    back = gradient(w, h, NIGHT, (250, 90, 140), VIOLET).convert("RGBA")
    s = int(h * 0.52)
    back.alpha_composite(sun(s), (int(w * 0.30 - s / 2), int(h * 0.20)))
    front = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    tw, th = int(h * 0.42), int(h * 0.72)
    front.alpha_composite(tower(tw, th, max(2, h // 70)), (int(w * 0.30 - tw / 2), int(h * 0.14)))
    front = Image.alpha_composite(front, neon_text((w, h), "NEON", (int(w * 0.53), int(h * 0.38)), int(h * 0.2)))
    front = Image.alpha_composite(front, neon_text((w, h), "TOKYO", (int(w * 0.53), int(h * 0.62)), int(h * 0.2)))
    return back, front


def write_json(path, data):
    path.mkdir(parents=True, exist_ok=True)
    (path / "Contents.json").write_text(json.dumps(data, indent=2) + "\n")


def imageset(path, images):
    """images: list of (filename, PIL image, scale)."""
    write_json(path, {"images": [{"idiom": "tv", "filename": f, "scale": sc} for f, _, sc in images], "info": INFO})
    for f, img, _ in images:
        img.convert("RGB" if img.mode == "RGB" else "RGBA").save(path / f)


def imagestack(path, sizes):
    """sizes: list of (scale, w, h)."""
    write_json(path, {"info": INFO, "layers": [{"filename": "Front.imagestacklayer"}, {"filename": "Back.imagestacklayer"}]})
    fronts, backs = [], []
    for scale, w, h in sizes:
        back, front = tv_layers(w, h)
        backs.append((f"back-{scale}.png", back.convert("RGB"), scale))
        fronts.append((f"front-{scale}.png", front, scale))
    for name, imgs in [("Front", fronts), ("Back", backs)]:
        layer = path / f"{name}.imagestacklayer"
        write_json(layer, {"info": INFO})
        imageset(layer / "Content.imageset", imgs)


def top_shelf(w, h):
    scene = Image.open(SCENE).convert("RGBA")
    sw, sh = scene.size
    # fill the width, keep the skyline and the sun
    scale = w / sw
    scene = scene.resize((w, int(sh * scale)), Image.LANCZOS)
    y = int(scene.size[1] * 0.42 - h / 2)
    y = max(0, min(y, scene.size[1] - h))
    img = scene.crop((0, y, w, y + h))
    shade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(shade)
    for x in range(int(w * 0.5)):
        d.line([(x, 0), (x, h)], fill=NIGHT + (int(200 * (1 - x / (w * 0.5))),))
    img = Image.alpha_composite(img, shade)
    img = Image.alpha_composite(img, neon_text((w, h), "NEON TOKYO", (int(h * 0.12), int(h * 0.5)), int(h * 0.2)))
    return img.convert("RGB")


def main():
    tv = ROOT / "apple/NeonTokyo/tvOS/Assets.xcassets"
    brand = tv / "App Icon & Top Shelf Image.brandassets"
    write_json(tv, {"info": INFO})
    write_json(brand, {
        "assets": [
            {"filename": "App Icon - App Store.imagestack", "idiom": "tv", "role": "primary-app-icon", "size": "1280x768"},
            {"filename": "App Icon.imagestack", "idiom": "tv", "role": "primary-app-icon", "size": "400x240"},
            {"filename": "Top Shelf Image Wide.imageset", "idiom": "tv", "role": "top-shelf-image-wide", "size": "2320x720"},
            {"filename": "Top Shelf Image.imageset", "idiom": "tv", "role": "top-shelf-image", "size": "1920x720"},
        ],
        "info": INFO,
    })
    imagestack(brand / "App Icon - App Store.imagestack", [("1x", 1280, 768)])
    imagestack(brand / "App Icon.imagestack", [("1x", 400, 240), ("2x", 800, 480)])
    imageset(brand / "Top Shelf Image.imageset", [("shelf-1x.png", top_shelf(1920, 720), "1x"), ("shelf-2x.png", top_shelf(3840, 1440), "2x")])
    imageset(brand / "Top Shelf Image Wide.imageset", [("wide-1x.png", top_shelf(2320, 720), "1x"), ("wide-2x.png", top_shelf(4640, 1440), "2x")])
    launch_color(tv, "tv")

    ios = ROOT / "apple/NeonTokyo/iOS/Assets.xcassets"
    write_json(ios, {"info": INFO})
    icon = gradient(1024, 1024, NIGHT, (250, 90, 140), VIOLET).convert("RGBA")
    icon.alpha_composite(sun(560), (232, 190))
    icon.alpha_composite(tower(430, 740, 15), (297, 150))
    write_json(ios / "AppIcon.appiconset", {
        "images": [{"filename": "icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
        "info": INFO,
    })
    icon.convert("RGB").save(ios / "AppIcon.appiconset/icon-1024.png")
    launch_color(ios, "universal")
    print("art written")


def launch_color(catalog, idiom):
    write_json(catalog / "LaunchBackground.colorset", {
        "colors": [{"idiom": idiom, "color": {"color-space": "srgb", "components": {
            "red": f"{NIGHT[0] / 255:.3f}", "green": f"{NIGHT[1] / 255:.3f}", "blue": f"{NIGHT[2] / 255:.3f}", "alpha": "1.000"}}}],
        "info": INFO,
    })


if __name__ == "__main__":
    main()
