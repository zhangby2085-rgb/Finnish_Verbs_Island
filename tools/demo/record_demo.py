"""Record the demo scenes of 芬兰语动词闯关岛 on the emulator (emulator-5556).

Each scene is one `adb shell screenrecord` file in ../演示视频/_raw/<scene>.mp4 (outside the repo: videos are kept
out of git) plus tools/demo/data/<scene>.json with
  start  : device epoch when screenrecord was launched
  events : [(device epoch, what)] for every tap the script made
The app logs every spoken sentence ("verbisland speak: ...") — capture them while recording with
  adb -s emulator-5556 logcat -v epoch -s verbisland:I > tools/demo/data/speak.log
The editor reads those
because the emulator cannot record sound; the editor puts the built-in Piper clips at those moments.

usage: record_demo.py <scene> [<scene> ...]      scenes: home stage1 stage2 stage3 stage4 stage5 book voice
"""
import html
import json
import os
import random
import re
import subprocess
import sys
import time

ADB = os.path.expanduser(r'~\AppData\Local\Android\Sdk\platform-tools\adb.exe')
DEV = ['-s', 'emulator-5556']
PKG = 'com.finnishkids.verbisland'
HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(os.path.dirname(HERE))
RAW = os.path.join(os.path.dirname(PROJECT), '演示视频', '_raw')   # screen recordings (video, not in git)
META = os.path.join(HERE, 'data')                                  # tap timelines (in git)
DATA = os.path.join(PROJECT, 'app', 'src', 'main', 'java', 'com', 'finnishkids', 'verbisland', 'VerbData.kt')

P = ['Minä', 'Sinä', 'Hän', 'Me', 'Te', 'He']
NEG = ['en', 'et', 'ei', 'emme', 'ette', 'eivät']
CN = ['我', '你', '他/她', '我们', '你们', '他们']
KINDS = ('选出正确的词尾', '选出正确的变位', '谁在做这件事？', '变成否定句')
VERBS = {inf: forms.split() for _s, inf, _c, forms in
         re.findall(r'\bv\(\s*(\d)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"', open(DATA, encoding='utf-8').read())}

events = []


def adb(*a):
    return subprocess.run([ADB, *DEV, *a], capture_output=True)


def sh(cmd):
    return adb('shell', cmd).stdout.decode('utf-8', 'replace')


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/u.xml')
    xml = adb('exec-out', 'cat', '/sdcard/u.xml').stdout.decode('utf-8', 'replace')
    out = []
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        t = re.search(r' text="([^"]*)"', n).group(1)
        d = re.search(r'content-desc="([^"]*)"', n).group(1)
        x0, y0, x1, y1 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        if t or d:
            out.append((html.unescape(t or d).replace('\n', ' '), (x0 + x1) // 2, (y0 + y1) // 2))
    return out


def tap_xy(x, y, what):
    t = float(sh(f'date +%s.%N; input tap {x} {y}').split()[0])
    events.append((t, what))
    print(f'  tap {what}')


def find(text, k=0, ns=None):
    ns = ns if ns is not None else nodes()
    hits = [n for n in ns if n[0] == text] or [n for n in ns if text in n[0]]
    return hits[k] if len(hits) > k else None


def tap(text, k=0):
    n = find(text, k)
    if not n:
        raise SystemExit(f'not found: {text}')
    tap_xy(n[1], n[2], text)


def swipe(y0, y1, ms=600):
    sh(f'input swipe 540 {y0} 540 {y1} {ms}')


def wait(s):
    time.sleep(s)


def question(ns=None):
    """(kind, answer, choices, info) for the question on screen"""
    ns = ns or nodes()
    texts = [n[0] for n in ns]
    kind = next(t for t in texts if t in KINDS)
    top = next(n[2] for n in ns if n[0] == kind)
    bottom_ui = ('下一题 ▶', '打开宝箱 🎁')
    choices = [n for n in ns if n[2] > 1500 and n[0] not in bottom_ui and '小锤子' not in n[0] and n[0] not in CN]
    prompt = [n for n in ns if top < n[2] < 1500]
    info = {}
    if kind == '谁在做这件事？':
        form = next(n[0] for n in prompt if n[0] not in P and any(n[0] in f for f in VERBS.values()))
        inf = next(i for i, f in VERBS.items() if form in f)
        ans = P[VERBS[inf].index(form)]
        info = {'inf': inf}
    else:
        p = next(n[0] for n in prompt if n[0] in P)
        k = P.index(p)
        info = {'person': p}
        if kind == '选出正确的词尾':
            ans = '-' + VERBS['puhua'][k][4:]
        else:
            inf = next(n[0].replace(' 🔊', '') for n in prompt if n[0].replace(' 🔊', '') in VERBS)
            info['inf'] = inf
            f = VERBS[inf]
            ans = f[k] if kind == '选出正确的变位' else f'{NEG[k]} {f[0][:-1]}'
            if kind == '变成否定句':
                info['keep'] = f'{NEG[k]} {f[k]}'
    return kind, ans, choices, info


def answer(wrong=False):
    kind, ans, choices, info = question()
    if not wrong:
        c = next(c for c in choices if c[0] == ans)
    else:
        others = [c for c in choices if c[0] != ans]
        if kind == '选出正确的变位':   # prefer the trap (a form that is not in the table at all)
            forms = VERBS[info['inf']]
            traps = [c for c in others if c[0] not in forms and c[0] != info['inf']]
            c = (traps or others)[0]
        elif kind == '变成否定句':
            c = next((c for c in others if c[0] == info.get('keep')), others[0])
        else:
            c = random.choice(others)
    tap_xy(c[1], c[2], ('✗ ' if wrong else '✓ ') + c[0])
    return kind, info


def next_q():
    n = find('下一题 ▶') or find('打开宝箱')
    tap_xy(n[1], n[2], n[0])


# ─────────────────────────── recording ───────────────────────────

def record(name, actions):
    os.makedirs(RAW, exist_ok=True)
    events.clear()
    adb('shell', 'rm', '-f', f'/sdcard/{name}.mp4')
    proc = subprocess.Popen([ADB, *DEV, 'shell', f'date +%s.%N; exec screenrecord --bit-rate 16M /sdcard/{name}.mp4'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    start = float(proc.stdout.readline().decode().strip())
    print(f'[{name}] recording')
    time.sleep(1.2)
    try:
        actions()
    finally:
        time.sleep(0.6)
        sh('pkill -2 screenrecord')
        proc.wait(timeout=20)
        time.sleep(1.0)
        dest = os.path.join(RAW, f'{name}.mp4')
        for _ in range(5):
            r = adb('pull', f'/sdcard/{name}.mp4', dest)
            if r.returncode == 0 and os.path.exists(dest):
                break
            print('  pull failed:', r.stderr.decode(errors='replace').strip())
            time.sleep(2)
        os.makedirs(META, exist_ok=True)
        json.dump({'start': start, 'events': events}, open(os.path.join(META, f'{name}.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
        print(f'[{name}] saved, {len(events)} taps')


def go_home():
    """Back to the island map, scrolled to the top (never presses BACK on the map itself)."""
    sh('cmd statusbar collapse')
    adb('shell', 'am', 'start', '-n', f'{PKG}/.MainActivity')
    wait(1.0)
    for _ in range(5):
        texts = [n[0] for n in nodes()]
        home = any(t in ('芬兰语动词闯关岛', '⚙ 家长设置', '闯关岛地图', '📖 词卡') or t.startswith('Hei! 我是小驯鹿') for t in texts)
        dialog = any(t in ('家长设置', '芬兰语语音', '肯定句') for t in texts)
        if home and not dialog:
            break
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        wait(1.2)
    swipe(700, 2000, 300)
    swipe(700, 2000, 300)
    wait(0.8)


# ─────────────────────────── scenes ───────────────────────────

def scene_home():
    adb('shell', 'pm', 'clear', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/.MainActivity')
    wait(3.5)

    def act():
        wait(1.5)
        tap('男孩')
        wait(2.6)
        tap('小驯鹿 Poro')
        wait(3.6)
        swipe(1900, 1100, 900)
        wait(2.6)
        swipe(1900, 1000, 900)
        wait(2.4)
        swipe(800, 2000, 700)
        swipe(800, 2000, 400)
        wait(1.5)
    record('home', act)


def play_round(think=1.7, after=2.0, wrong_at=(), stop_after=None, wrong_kind=None):
    for i in range(5):
        wait(think)
        kind = question()[0] if wrong_kind else None
        if i in wrong_at or (wrong_kind and kind == wrong_kind):
            answer(wrong=True)
            wrong_kind = None
            wait(3.6)
        answer()
        wait(after)
        if stop_after is not None and i + 1 >= stop_after:
            return False
        next_q()
    return True


def scene_stage1():
    go_home()

    def act():
        wait(0.8)
        tap('开始 ▶')
        wait(0.6)
        play_round(think=1.9, after=2.2, wrong_at=(0,))
        wait(6.5)
    record('stage1', act)
    tap('回到岛屿地图')
    wait(1.5)


def unlock_all():
    go_home()
    swipe(1900, 300, 300)
    swipe(1900, 300, 300)
    wait(1)
    tap('⚙ 家长设置')
    wait(1)
    n = find('全部关卡解锁')
    tap_xy(850, n[2] + 40, 'switch')
    wait(0.6)
    tap('完成')
    wait(0.8)
    go_home()


def scene_stage2():
    go_home()

    def act():
        wait(0.8)
        tap('开始 ▶', 1)
        wait(0.6)
        play_round(think=1.6, after=2.0)
        wait(3)
    record('stage2', act)
    go_home()


def scene_stage3():
    # retry until the first question has a "soft" person and a normal KPT verb, so the hammer visibly softens
    for attempt in range(12):
        go_home()

        def act():
            wait(0.8)
            tap('3')                       # the map pin of island 3
            wait(2.0)
            kind, ans, choices, info = question()
            if info['person'] in ('Hän', 'He') or info['inf'] == 'tykätä':
                raise RuntimeError('retry')
            wait(1.0)
            tap('小锤子帮帮我：敲一敲词干')
            wait(3.0)
            answer(wrong=True)
            wait(4.0)
            answer()
            wait(3.2)
            next_q()
            for _ in range(3):
                wait(2.2)
                answer()
                wait(3.0)
                next_q()
            wait(1.5)
        try:
            record('stage3', act)
            go_home()
            return
        except RuntimeError:
            print('  first question not suitable, again')
    raise SystemExit('stage3: gave up')


def scene_stage4():
    go_home()

    def act():
        wait(0.8)
        n = find('4')
        tap_xy(n[1], n[2], 'pin 4')
        wait(0.6)
        play_round(think=1.6, after=2.0)
        wait(6.5)
    record('stage4', act)
    go_home()


def scene_stage5():
    go_home()

    def act():
        wait(0.8)
        n = find('5')
        tap_xy(n[1], n[2], 'pin 5')
        wait(0.6)
        play_round(think=1.9, after=2.4, wrong_kind='变成否定句', stop_after=4)
        wait(1)
    record('stage5', act)
    go_home()


def scene_book():
    go_home()
    swipe(1900, 300, 300)
    swipe(1900, 300, 300)
    wait(1)

    def act():
        wait(0.8)
        tap('📖 动词词卡本（65 个）')
        wait(2.2)
        tap('第3关 · 14词')
        wait(2.2)
        tap('kirjoittaa')
        wait(2.6)
        for p in ('Minä', 'Hän', 'Me'):
            n = [x for x in nodes() if x[0] == p and x[1] < 300]
            tap_xy(700, n[0][2] + 20, 'row ' + p)
            wait(1.9)
        tap('否定句')
        wait(1.6)
        n = [x for x in nodes() if x[0] == 'Minä' and x[1] < 300]
        tap_xy(700, n[0][2] + 20, 'row neg Minä')
        wait(2.2)
        t = float(sh('date +%s.%N; input keyevent KEYCODE_BACK').split()[0])
        events.append((t, 'close card'))
        wait(1.2)
        tap('第1关 词尾')
        wait(2.0)
        n = [x for x in nodes() if x[0] == 'Me']
        tap_xy(800, n[0][2] + 20, 'ending row Me')
        wait(2.4)
    record('book', act)
    go_home()


def scene_voice():
    go_home()

    def act():
        wait(0.8)
        tap('开启芬兰语语音')
        wait(3.2)
        tap('🔊 试听：Hei! Tervetuloa verbisaarelle!')
        wait(4.0)
        tap('知道了')
        wait(1.0)
    record('voice', act)


SCENES = {'home': scene_home, 'stage1': scene_stage1, 'unlock': unlock_all, 'stage2': scene_stage2,
          'stage3': scene_stage3, 'stage4': scene_stage4, 'stage5': scene_stage5, 'book': scene_book,
          'voice': scene_voice}

if __name__ == '__main__':
    for s in sys.argv[1:]:
        SCENES[s]()
