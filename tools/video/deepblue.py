"""Deep Blue's film at 4K for the TV's video chip. Adrian's original (2560x1440, 60 fps) is cropped
to the 1920x1080 window the home screen has always shown, made four times larger by Real-ESRGAN's
anime video network from a 1280x720 copy (crisp edges on the girl and the fish) and blended with a
plain Lanczos enlargement (keeps the water's texture), then the shade for the clock and tiles goes
on as before and it is encoded in HEVC.

  deepblue.py original.mp4 realesr-animevideov3.pth grade.npz out.mp4 [count [start]]

At about 4 s a frame on four cores it was run in three parts of 1000 frames, joined with
ffmpeg's concat demuxer (-c copy).

grade.npz holds the shade as out = a * in + b per pixel (fitted at 240x135 against the old
1080p film, smoothed here). Needs numpy, opencv-python-headless, onnx, onnxruntime and ffmpeg."""
import sys, os, subprocess, time
import numpy as np, cv2
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compact import session

src, weights, grade, dst = sys.argv[1:5]
count = int(sys.argv[5]) if len(sys.argv) > 5 else 0
start = int(sys.argv[6]) if len(sys.argv) > 6 else 0
W, H, OW, OH = 1920, 1080, 3840, 2160
AI = 0.8

g = np.load(grade)
def smooth(m):
    m = cv2.GaussianBlur(m.astype(np.float32), (0, 0), 2.0)
    return cv2.resize(m, (OW, OH), interpolation=cv2.INTER_CUBIC)
ga, gb = smooth(g['a']), smooth(g['b'])

sess = session(weights)
dec = subprocess.Popen(['ffmpeg', '-v', 'error', '-i', src, '-vf', f'trim=start_frame={start},setpts=PTS-STARTPTS,crop={W}:{H}:320:360,format=rgb24',
                        *(['-frames:v', str(count)] if count else []), '-fps_mode', 'passthrough', '-f', 'rawvideo', '-'], stdout=subprocess.PIPE)
enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{OW}x{OH}', '-r', '60',
                        '-i', '-', '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p',
                        '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
                        '-c:v', 'libx265', '-preset', 'slow', '-crf', '20', '-tag:v', 'hvc1',
                        '-x265-params', 'log-level=error:keyint=120:min-keyint=120:aq-mode=3:deblock=-1,-1',
                        '-an', '-movflags', '+faststart', dst], stdin=subprocess.PIPE)
n, t0 = 0, time.time()
while True:
    buf = dec.stdout.read(W * H * 3)
    if len(buf) < W * H * 3:
        break
    f = np.frombuffer(buf, np.uint8).reshape(H, W, 3).astype(np.float32) / 255
    small = cv2.resize(f, (1280, 720), interpolation=cv2.INTER_AREA)
    up = sess.run(None, {'input': np.ascontiguousarray(small.transpose(2, 0, 1)[None])})[0][0].transpose(1, 2, 0)
    up = cv2.resize(up, (OW, OH), interpolation=cv2.INTER_AREA)
    plain = cv2.resize(f, (OW, OH), interpolation=cv2.INTER_LANCZOS4)
    out = (AI * up + (1 - AI) * plain) * ga + gb
    enc.stdin.write(np.clip(out * 255 + 0.5, 0, 255).astype(np.uint8).tobytes())
    n += 1
    if n % 50 == 0:
        print(n, f'{(time.time() - t0) / n:.2f} s/frame', flush=True)
enc.stdin.close()
enc.wait()
print('done', n)
