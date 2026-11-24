"""Built-in Finnish voice clips for 芬兰语动词闯关岛.

Many phones (e.g. China-market Xiaomi) have no Finnish text-to-speech voice. FinnishTtsManager falls back to these
clips: every text the app can speak is synthesised here with Piper (voice fi_FI-harri-medium, CC0 dataset), encoded
as Ogg Opus and stored as app/src/main/assets/fi/<sha1(text)[:12]>.ogg.

The texts come from app/src/main/java/com/finnishkids/verbisland/VerbData.kt:
  the 6 pronouns, every infinitive, "Pronoun form" (Minä puhun) and "Pronoun neg stem" (Minä en puhu) for every verb,
  and the strings in object Phrases.
Rerun after changing the verb list (only new texts are synthesised; clips no longer used are deleted):

    Game/.devenv/Scripts/python.exe tools/make_voice.py --voice Game/.voices/piper
Needs piper-tts and ffmpeg on PATH.
"""
import argparse
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import wave
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
DATA = os.path.join(PROJECT, 'app', 'src', 'main', 'java', 'com', 'finnishkids', 'verbisland', 'VerbData.kt')
CLIPS = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'fi')

PRONOUNS = [('Minä', 'en'), ('Sinä', 'et'), ('Hän', 'ei'), ('Me', 'emme'), ('Te', 'ette'), ('He', 'eivät')]


def texts():
    src = open(DATA, encoding='utf-8').read()
    verbs = re.findall(r'\bv\(\s*(\d)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"', src)
    if len(verbs) != 65:
        sys.exit(f'expected 65 verbs in VerbData.kt, found {len(verbs)}')
    out = [p for p, _ in PRONOUNS]
    for _stage, inf, _cn, forms in verbs:
        forms = forms.split()
        assert len(forms) == 6, inf
        neg_stem = forms[0][:-1]
        out.append(inf)
        for (p, neg), f in zip(PRONOUNS, forms):
            out.append(f'{p} {f}')
            out.append(f'{p} {neg} {neg_stem}')
    block = src[src.index('object Phrases'):]
    block = block[:block.index('\n}')]
    out += re.findall(r'const val \w+ = "([^"]+)"', block)
    seen, uniq = set(), []
    for t in out:
        t = t.strip()
        if t not in seen:
            seen.add(t)
            uniq.append(t)
    return uniq


def clip_name(text):
    return hashlib.sha1(text.encode('utf-8')).hexdigest()[:12] + '.ogg'


def encode(wav, ogg):
    # 0.1 s lead-in so phones do not clip the first sound; Opus 32 kbit/s mono is clear for speech
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-af', 'adelay=100:all=1',
                    '-ar', '24000', '-ac', '1', '-c:a', 'libopus', '-b:a', '32k', ogg], check=True)
    os.remove(wav)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--voice', required=True, help='folder with fi_FI-harri-medium.onnx(.json)')
    ap.add_argument('--slow', type=float, default=1.12, help='Piper length_scale (1.12 ≈ speech rate 0.89)')
    a = ap.parse_args()
    all_texts = texts()
    os.makedirs(CLIPS, exist_ok=True)
    todo = [t for t in all_texts if not os.path.exists(os.path.join(CLIPS, clip_name(t)))]
    print(f'{len(all_texts)} texts, {len(todo)} to synthesise')
    if todo:
        from piper import PiperVoice, SynthesisConfig
        voice = PiperVoice.load(os.path.join(a.voice, 'fi_FI-harri-medium.onnx'))
        cfg = SynthesisConfig(length_scale=a.slow)
        tmp = tempfile.mkdtemp()
        with ThreadPoolExecutor(8) as pool:
            jobs = []
            for i, t in enumerate(todo):
                wav = os.path.join(tmp, f'{i}.wav')
                with wave.open(wav, 'wb') as w:
                    voice.synthesize_wav(t, w, syn_config=cfg)
                jobs.append(pool.submit(encode, wav, os.path.join(CLIPS, clip_name(t))))
                if (i + 1) % 100 == 0:
                    print(f'  {i + 1}/{len(todo)}')
            for j in jobs:
                j.result()
    keep = {clip_name(t) for t in all_texts}
    gone = [f for f in os.listdir(CLIPS) if f.endswith('.ogg') and f not in keep]
    for f in gone:
        os.remove(os.path.join(CLIPS, f))
    size = sum(os.path.getsize(os.path.join(CLIPS, f)) for f in keep)
    print(f'done: {len(keep)} clips, {size / 1e6:.1f} MB, removed {len(gone)} unused')


if __name__ == '__main__':
    sys.exit(main())
