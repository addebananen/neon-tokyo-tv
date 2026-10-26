"""The home screen's little synth sounds, made from scratch (no samples), so they are ours to
ship. Writes 16-bit mono WAVs into app/src/main/res/raw. Usage: python3 make_sounds.py"""
import os
import wave
import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'src', 'main', 'res', 'raw')


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def env(x, attack, tau):
    """A quick rise, then an exponential fall with time constant tau."""
    a = np.clip(x / attack, 0, 1)
    return a * np.exp(-np.maximum(x - attack, 0) / tau)


def tri(phase):
    return 2 / np.pi * np.arcsin(np.sin(phase))


def lowpass(sig, cutoff):
    # One-pole low-pass, enough to round off the edges of a triangle
    k = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    out = np.empty_like(sig)
    y = 0.0
    for i, s in enumerate(sig):
        y += k * (s - y)
        out[i] = y
    return out


def echo(sig, delay, gain, repeats=3):
    out = np.concatenate([sig, np.zeros(int(RATE * delay * repeats))])
    d = int(RATE * delay)
    for n in range(1, repeats + 1):
        out[n * d:n * d + len(sig)] += sig * gain ** n
    return out


def save(name, sig, peak):
    sig = sig / np.max(np.abs(sig)) * peak
    fade = int(RATE * 0.004)
    sig[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((sig * 32767).astype('<i2').tobytes())


# Moving the focus: a soft glassy tick, high and short
x = t(0.07)
tick = (np.sin(2 * np.pi * 1760 * x) + 0.35 * np.sin(2 * np.pi * 2640 * x) + 0.15 * np.sin(2 * np.pi * 880 * x)) * env(x, 0.002, 0.011)
save('nt_ui_move', tick, 0.5)

# OK: a little rising synth blip, two detuned voices with a short echo
x = t(0.16)
f = 660 * (1 + 0.5 * np.clip(x / 0.05, 0, 1))
phase = 2 * np.pi * np.cumsum(f) / RATE
blip = (tri(phase) + 0.6 * tri(phase * 1.006)) * env(x, 0.003, 0.045)
blip = lowpass(blip, 4200)
save('nt_ui_select', echo(blip, 0.085, 0.28, 2), 0.6)

# A console wakes: a city-pop arpeggio, C E G C on a soft electric piano, with a gentle echo
notes = [523.25, 659.25, 783.99, 1046.5]
x = t(0.9)
chime = np.zeros_like(x)
for i, freq in enumerate(notes):
    start = i * 0.075
    y = np.maximum(x - start, 0)
    on = x >= start
    tone = np.sin(2 * np.pi * freq * y + 0.9 * np.sin(2 * np.pi * freq * 2 * y) * np.exp(-y / 0.08))
    chime += on * tone * env(y, 0.004, 0.32) * (0.9 if i < 3 else 1.0)
chime = lowpass(chime, 5200)
save('nt_ui_wake', echo(chime, 0.16, 0.22, 2), 0.6)
print('ok')
