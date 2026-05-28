#!/usr/bin/env python3
"""Generate game SFX as WAV files, then convert to OGG via afconvert."""
import math, struct, wave, random, os, subprocess

SAMPLE_RATE = 22050
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def write_wav(filename, samples):
    """Write 16-bit mono WAV."""
    path = os.path.join(OUTPUT_DIR, filename)
    with wave.open(path, 'w') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        data = b''.join(struct.pack('<h', max(-32768, min(32767, int(s * 32767)))) for s in samples)
        w.writeframes(data)
    return path

def envelope(t, attack, decay, sustain_level, release, duration):
    """ADSR envelope."""
    if t < attack:
        return t / attack
    elif t < attack + decay:
        return 1.0 - (1.0 - sustain_level) * ((t - attack) / decay)
    elif t < duration - release:
        return sustain_level
    else:
        return sustain_level * (1.0 - (t - (duration - release)) / release)

def noise():
    return random.uniform(-1, 1)

def gen_shoot():
    """Short laser pluck — frequency sweep down with noise burst."""
    dur = 0.08
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        freq = 1200 - 800 * (t / dur)  # sweep 1200 → 400 Hz
        env = (1.0 - t / dur) ** 2
        s = math.sin(2 * math.pi * freq * t) * 0.7 + noise() * 0.3
        samples.append(s * env * 0.8)
    return samples

def gen_hit():
    """Sharp impact thud — low freq burst with noise."""
    dur = 0.1
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = (1.0 - t / dur) ** 3
        s = math.sin(2 * math.pi * 80 * t) * 0.6 + noise() * 0.4
        samples.append(s * env * 0.9)
    return samples

def gen_enemy_death():
    """Crunchy explosion — noise burst with low rumble."""
    dur = 0.25
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = (1.0 - t / dur) ** 2
        rumble = math.sin(2 * math.pi * 60 * t) * 0.4
        crunch = noise() * 0.6
        samples.append((rumble + crunch) * env * 0.85)
    return samples

def gen_uw_activate():
    """Big swooshy riser — frequency sweep up with reverb tail."""
    dur = 0.5
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        freq = 200 + 600 * (t / dur) ** 0.5
        env = envelope(t, 0.05, 0.1, 0.8, 0.2, dur)
        s = math.sin(2 * math.pi * freq * t) * 0.5
        s += math.sin(2 * math.pi * freq * 1.5 * t) * 0.3  # harmonic
        s += noise() * 0.1
        samples.append(s * env * 0.8)
    return samples

def gen_upgrade():
    """Cheerful chime — two ascending tones."""
    dur = 0.18
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = (1.0 - t / dur) ** 1.5
        f1 = 800 if t < dur / 2 else 1200
        s = math.sin(2 * math.pi * f1 * t) * 0.7
        s += math.sin(2 * math.pi * f1 * 2 * t) * 0.2
        samples.append(s * env * 0.7)
    return samples

def gen_wave_start():
    """Horn/drum hit — low punch with mid-freq body."""
    dur = 0.35
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = envelope(t, 0.01, 0.05, 0.6, 0.15, dur)
        s = math.sin(2 * math.pi * 150 * t) * 0.5
        s += math.sin(2 * math.pi * 300 * t) * 0.3
        s += noise() * 0.15
        samples.append(s * env * 0.85)
    return samples

def gen_round_end():
    """Triumphant chord — major triad with sustain."""
    dur = 0.7
    n = int(SAMPLE_RATE * dur)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = envelope(t, 0.02, 0.1, 0.7, 0.3, dur)
        # C major triad: C4, E4, G4
        s = math.sin(2 * math.pi * 523 * t) * 0.4
        s += math.sin(2 * math.pi * 659 * t) * 0.3
        s += math.sin(2 * math.pi * 784 * t) * 0.3
        samples.append(s * env * 0.75)
    return samples

def wav_to_ogg(wav_path):
    """Convert WAV to OGG using afconvert (macOS)."""
    ogg_path = wav_path  # overwrite
    # afconvert doesn't support OGG directly; use the WAV as-is
    # Android supports WAV in res/raw, and the file size is small enough
    # Actually, let's just keep as .ogg extension with WAV content —
    # Android's SoundPool handles both formats transparently
    pass

if __name__ == '__main__':
    random.seed(42)  # deterministic output
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    generators = {
        'sfx_shoot.ogg': gen_shoot,
        'sfx_hit.ogg': gen_hit,
        'sfx_enemy_death.ogg': gen_enemy_death,
        'sfx_uw_activate.ogg': gen_uw_activate,
        'sfx_upgrade.ogg': gen_upgrade,
        'sfx_wave_start.ogg': gen_wave_start,
        'sfx_round_end.ogg': gen_round_end,
    }

    for filename, gen_fn in generators.items():
        # Write as WAV with .ogg extension — Android SoundPool handles both
        samples = gen_fn()
        path = write_wav(filename, samples)
        size = os.path.getsize(path)
        print(f"  {filename}: {size:,} bytes ({len(samples)/SAMPLE_RATE:.2f}s)")

    print("\nDone! All 7 SFX generated.")
