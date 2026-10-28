"""Four times the pixels for a painted style, by Real-ESRGAN (RRDBNet x4) without torch: reads the
.pth zip with a restricted unpickler, builds the network as an ONNX graph and runs it tiled with
onnxruntime. Usage: upscale.py weights.pth num_block in.png out.png

Golden Dusk's 4K still came from the 1080x607 picture Adrian sent, with RealESRGAN_x4plus.pth (23
blocks, github.com/xinntao/Real-ESRGAN releases v0.1.0, BSD-3), then Lanczos to 3840x2160 and
encode.sh at crf 12; the 1080p cover is that 4K picture made smaller. Needs numpy, pillow, onnx
and onnxruntime."""
import sys, zipfile, pickle, collections
import numpy as np
import onnx
from onnx import helper, TensorProto, numpy_helper
import onnxruntime as ort
from PIL import Image

wpath, nblock, src, dst = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]

z = zipfile.ZipFile(wpath)
names = z.namelist()
root = names[0].split('/')[0]
DT = {'FloatStorage': np.float32, 'HalfStorage': np.float16}


class Storage:
    def __init__(self, kind, key):
        self.a = np.frombuffer(z.read(f'{root}/data/{key}'), DT[kind])


def rebuild(storage, offset, size, stride, *_):
    a = storage.a
    if not size:
        return a[offset:offset + 1].reshape(())
    return np.lib.stride_tricks.as_strided(a[offset:], size, [s * a.itemsize for s in stride]).copy()


class U(pickle.Unpickler):
    def find_class(self, mod, name):
        if (mod, name) == ('torch._utils', '_rebuild_tensor_v2'):
            return rebuild
        if (mod, name) == ('collections', 'OrderedDict'):
            return collections.OrderedDict
        if mod == 'torch' and name in DT:
            return name
        raise pickle.UnpicklingError(f'blocked {mod}.{name}')

    def persistent_load(self, pid):
        _, kind, key, *_ = pid
        return Storage(kind, key)


sd = U(z.open(f'{root}/data.pkl')).load()
if 'params_ema' in sd:
    sd = sd['params_ema']
elif 'params' in sd:
    sd = sd['params']
sd = {k: v.astype(np.float32) for k, v in sd.items()}

nodes, inits = [], []
n = [0]


def nm(p):
    n[0] += 1
    return f'{p}{n[0]}'


def conv(x, key):
    w, b = key + '.weight', key + '.bias'
    for k in (w, b):
        if k not in [i.name for i in inits]:
            inits.append(numpy_helper.from_array(sd[k], k))
    y = nm('c')
    nodes.append(helper.make_node('Conv', [x, w, b], [y], pads=[1, 1, 1, 1]))
    return y


def lrelu(x):
    y = nm('l')
    nodes.append(helper.make_node('LeakyRelu', [x], [y], alpha=0.2))
    return y


def cat(xs):
    y = nm('k')
    nodes.append(helper.make_node('Concat', xs, [y], axis=1))
    return y


def const(v):
    k = nm('s')
    inits.append(numpy_helper.from_array(np.array(v, np.float32), k))
    return k


S02 = const(0.2)


def scale_add(x, s, r):
    m, y = nm('m'), nm('a')
    nodes.append(helper.make_node('Mul', [x, s], [m]))
    nodes.append(helper.make_node('Add', [m, r], [y]))
    return y


def add(a, b):
    y = nm('a')
    nodes.append(helper.make_node('Add', [a, b], [y]))
    return y


def up2(x):
    sc = nm('sc')
    inits.append(numpy_helper.from_array(np.array([1, 1, 2, 2], np.float32), sc))
    y = nm('u')
    nodes.append(helper.make_node('Resize', [x, '', sc], [y], mode='nearest'))
    return y


def rdb(x, p):
    x1 = lrelu(conv(x, p + '.conv1'))
    x2 = lrelu(conv(cat([x, x1]), p + '.conv2'))
    x3 = lrelu(conv(cat([x, x1, x2]), p + '.conv3'))
    x4 = lrelu(conv(cat([x, x1, x2, x3]), p + '.conv4'))
    x5 = conv(cat([x, x1, x2, x3, x4]), p + '.conv5')
    return scale_add(x5, S02, x)


feat = conv('input', 'conv_first')
h = feat
for i in range(nblock):
    o = h
    for j in (1, 2, 3):
        o = rdb(o, f'body.{i}.rdb{j}')
    h = scale_add(o, S02, h)
feat = add(feat, conv(h, 'conv_body'))
feat = lrelu(conv(up2(feat), 'conv_up1'))
feat = lrelu(conv(up2(feat), 'conv_up2'))
out = conv(lrelu(conv(feat, 'conv_hr')), 'conv_last')
nodes.append(helper.make_node('Identity', [out], ['output']))
g = helper.make_graph(nodes, 'rrdb', [helper.make_tensor_value_info('input', TensorProto.FLOAT, [1, 3, None, None])],
                      [helper.make_tensor_value_info('output', TensorProto.FLOAT, [1, 3, None, None])], inits)
model = helper.make_model(g, opset_imports=[helper.make_opsetid('', 17)], ir_version=8)
so = ort.SessionOptions()
so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
sess = ort.InferenceSession(model.SerializeToString(), so, providers=['CPUExecutionProvider'])

img = np.asarray(Image.open(src).convert('RGB'), np.float32) / 255
H, W, _ = img.shape
T, P = 192, 16
out = np.zeros((H * 4, W * 4, 3), np.float32)
pad = np.pad(img, ((P, P), (P, P), (0, 0)), mode='reflect')
for y in range(0, H, T):
    for x in range(0, W, T):
        th, tw = min(T, H - y), min(T, W - x)
        tile = pad[y:y + th + 2 * P, x:x + tw + 2 * P].transpose(2, 0, 1)[None]
        r = sess.run(None, {'input': np.ascontiguousarray(tile)})[0][0].transpose(1, 2, 0)
        out[y * 4:(y + th) * 4, x * 4:(x + tw) * 4] = r[P * 4:(P + th) * 4, P * 4:(P + tw) * 4]
    print('row', y, flush=True)
Image.fromarray(np.clip(out * 255 + 0.5, 0, 255).astype(np.uint8)).save(dst)
print('done', out.shape)
