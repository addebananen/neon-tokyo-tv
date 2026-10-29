"""4K still for a photo style: the original photo's own pixels at 4K, with the 1080p picture's grade and
painting moved onto them by a guided filter (worked out at 1080p, applied at 4K), so the result
has the photo's detail and none of the 1080p file's blocks or banding. Where something was
painted in that the photo doesn't have, it goes back on softly. Usage: build4k.py name

Needs orig/<name>.jpg, the Pexels photo downloaded at w=5000 (see third_party/photos/CREDITS.md
for the IDs), and writes out/<name>.png, which encode.sh turns into the still."""
import sys, json
import numpy as np
from PIL import Image, ImageFilter

BOXES = {
    'nature': [0.0, 70.4, 4226.0, 2377.1],
}
name = sys.argv[1]
RES = __import__('os').path.join(__import__('os').path.dirname(__file__), '../../app/src/main/res/drawable-nodpi')
src = Image.open(f'orig/{name}.jpg').convert('RGB')
edit = Image.open(f'{RES}/home_style_{name}.webp').convert('RGB')
x, y, cw, ch = BOXES[name]
Wo, Ho = src.size
W4, H4 = 3840, 2160
box = (x, y, min(Wo, x + cw), min(Ho, y + ch))
D4 = np.asarray(src.resize((W4, H4), Image.LANCZOS, box=box), np.float32) / 255
D1 = np.asarray(src.resize((1920, 1080), Image.LANCZOS, box=box), np.float32) / 255
E = np.asarray(edit, np.float32) / 255


def boxf(a, r):
    p = np.pad(a, ((r + 1, r), (r + 1, r)), mode='edge')
    c = np.cumsum(np.cumsum(p, 0), 1)
    k = 2 * r + 1
    return (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)


def up(a):
    return np.asarray(Image.fromarray(a.astype(np.float32), 'F').resize((W4, H4), Image.BICUBIC), np.float32)


r, eps = 6, 0.012 ** 2
G = np.zeros((H4, W4, 3), np.float32)
for c in range(3):
    I = D1[..., c]; p = E[..., c]
    mI = boxf(I, r); mp = boxf(p, r)
    a = np.clip((boxf(I * p, r) - mI * mp) / (boxf(I * I, r) - mI * mI + eps), -0.5, 3)
    b = mp - a * mI
    G[..., c] = up(boxf(a, r)) * D4[..., c] + up(boxf(b, r))
B = np.asarray(edit.resize((W4, H4), Image.LANCZOS), np.float32) / 255
# How far the graded photo, seen at 1080p, misses the picture: there something was painted in
G1 = np.asarray(Image.fromarray(np.clip(G * 255, 0, 255).astype(np.uint8)).resize((1920, 1080), Image.LANCZOS), np.float32) / 255
miss = np.abs(G1 - E).mean(-1)
miss = np.asarray(Image.fromarray(np.clip(miss * 255 * 4, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)).resize((W4, H4), Image.BICUBIC), np.float32) / 255
# and where the graded photo goes off colour (clipped highlights in the photo), the picture too
def chroma(a):
    return a - (a[..., 0:1] * 0.2126 + a[..., 1:2] * 0.7152 + a[..., 2:3] * 0.0722)
off = np.abs(chroma(G) - chroma(B)).max(-1)
off = np.asarray(Image.fromarray(np.clip(off * 255 * 4, 0, 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(4)), np.float32) / 255
keep = np.clip((off - 0.2) / 0.2, 0, 1)[..., None]
# Whatever the picture has that the graded photo lacks (light painted in, a grade the filter
# missed) goes back on, softened so the 1080p file's blocks don't come with it
res = E - G1
def gblur(a, sigma):
    k = np.arange(-4, 5, dtype=np.float32); k = np.exp(-k * k / (2 * sigma * sigma)); k /= k.sum()
    p = np.pad(a, 4, mode='edge')
    h = sum(k[i] * p[4:-4, i:i + a.shape[1]] for i in range(9))
    p2 = np.pad(h, ((4, 4), (0, 0)), mode='edge')
    return sum(k[i] * p2[i:i + a.shape[0], :] for i in range(9))
res = np.stack([up(gblur(res[..., c], 1.4)) for c in range(3)], -1)
G = G + res
R = G * (1 - keep) + B * keep
# A whisper of noise so smooth skies don't band once encoded
R = R + (np.random.default_rng(7).random(R.shape[:2])[..., None] - 0.5) / 255
Image.fromarray(np.clip(R * 255 + 0.5, 0, 255).astype(np.uint8)).save(f'out/{name}.png')
print(name, 'kept from the picture', round(float((keep > 0.5).mean()), 3), 'mean miss', round(float(np.abs(G1 - E).mean() * 255), 2))
