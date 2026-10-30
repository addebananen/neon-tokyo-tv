"""Real-ESRGAN's small video network (SRVGGNetCompact, realesr-animevideov3.pth from
github.com/xinntao/Real-ESRGAN releases v0.2.5.0, BSD-3) as an ONNX graph for onnxruntime, without
torch. Used by deepblue.py; the .pth zip is read with a restricted unpickler as in ../stills/upscale.py."""
import zipfile, pickle, collections
import numpy as np
from onnx import helper, TensorProto, numpy_helper
import onnxruntime as ort

DT = {'FloatStorage': np.float32, 'HalfStorage': np.float16}


def load(path):
    z = zipfile.ZipFile(path)
    root = z.namelist()[0].split('/')[0]

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
    sd = sd.get('params_ema', sd.get('params', sd))
    return {k: v.astype(np.float32) for k, v in sd.items()}


def session(path, threads=0):
    sd = load(path)
    convs = sorted({int(k.split('.')[1]) for k in sd if k.endswith('.weight') and sd[k].ndim == 4})
    nodes, inits = [], [numpy_helper.from_array(v, k) for k, v in sd.items()]
    x = 'input'
    for i, c in enumerate(convs):
        y = f'c{c}'
        nodes.append(helper.make_node('Conv', [x, f'body.{c}.weight', f'body.{c}.bias'], [y], pads=[1, 1, 1, 1]))
        x = y
        if i < len(convs) - 1:
            # PReLU slopes are per channel: shaped (C,) in the file, (C,1,1) for ONNX
            k = f'body.{c + 1}.weight'
            inits.append(numpy_helper.from_array(sd[k].reshape(-1, 1, 1), k + '.s'))
            y = f'p{c}'
            nodes.append(helper.make_node('PRelu', [x, k + '.s'], [y]))
            x = y
    inits.append(numpy_helper.from_array(np.array([1, 1, 4, 4], np.float32), 'sc'))
    nodes += [
        helper.make_node('DepthToSpace', [x], ['ds'], blocksize=4, mode='CRD'),
        helper.make_node('Resize', ['input', '', 'sc'], ['base'], mode='nearest'),
        helper.make_node('Add', ['ds', 'base'], ['output']),
    ]
    g = helper.make_graph(nodes, 'compact', [helper.make_tensor_value_info('input', TensorProto.FLOAT, [1, 3, None, None])],
                          [helper.make_tensor_value_info('output', TensorProto.FLOAT, [1, 3, None, None])], inits)
    m = helper.make_model(g, opset_imports=[helper.make_opsetid('', 17)], ir_version=8)
    so = ort.SessionOptions()
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    if threads:
        so.intra_op_num_threads = threads
    return ort.InferenceSession(m.SerializeToString(), so, providers=['CPUExecutionProvider'])
