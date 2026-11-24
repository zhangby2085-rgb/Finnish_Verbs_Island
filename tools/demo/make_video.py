"""Edit the recorded scenes into a 1920x1080 YouTube demo of 芬兰语动词闯关岛.

Input : ../演示视频/_raw/<scene>.mp4 (screen recordings, kept outside git)
        tools/demo/data/<scene>.json + speak.log (record_demo.py; logcat of "verbisland speak: ...")
Output: ../演示视频/芬兰语动词闯关岛_演示.mp4; docs/youtube/thumbnail.png; the chapter list in docs/youtube/YouTube说明.txt

Layout: the phone recording sits in a phone frame on the left; the right side shows the chapter title and
Chinese + English captions; a chapter bar runs along the bottom. Audio = the app's own Finnish clips (Piper voice,
placed where the app spoke them) over soft synthesised background music.

Run: Game/.devenv/Scripts/python.exe tools/demo/make_video.py
"""
import hashlib
import json
import math
import os
import re
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(os.path.dirname(HERE))
OUTDIR = os.path.join(os.path.dirname(PROJECT), '演示视频')
RAW = os.path.join(OUTDIR, '_raw')            # recordings + render intermediates (video_only.mp4, audio.wav)
META = os.path.join(HERE, 'data')             # tap timelines, speak.log, placed_audio.json, chapters.txt
NARR_DIR = os.path.join(HERE, 'narration')    # Edge TTS cache
YT = os.path.join(PROJECT, 'docs', 'youtube')
RES = os.path.join(PROJECT, 'app', 'src', 'main', 'res')
CLIPS = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'fi')
OUT = os.path.join(OUTDIR, '芬兰语动词闯关岛_演示.mp4')

FPS = 30
W, H = 1920, 1080
PW, PH = 450, 1000            # phone screen in the video (1080x2400 scaled by 0.4167)
PX, PY = 236, 40              # its top-left corner
RX = 800                      # right panel starts here
SR = 48000

FONT_R = r'C:\Windows\Fonts\msyh.ttc'
FONT_B = r'C:\Windows\Fonts\msyhbd.ttc'

SEA = (30, 120, 180)
SEA_DEEP = (21, 80, 124)
INK = (34, 53, 72)
INK_SOFT = (94, 110, 128)
SUN = (255, 182, 39)
STAGE = {1: (47, 155, 214), 2: (124, 92, 214), 3: (240, 138, 36), 4: (63, 163, 77), 5: (214, 85, 143)}

CHAPTERS = ['地图', '第1关', '第2关', '第3关', '第4关', '第5关', '词卡本', '语音']

# (scene, [(from, to) in seconds of the recording], chapter index, chip, title zh, title en, [(t, zh, en)])
SEGMENTS = [
    ('home', [(0.4, 20.0)], 0, '开始', '岛屿地图', 'The island map', [
        (0.4, '第一次打开：选一个小探险家', 'First launch: pick your explorer'),
        (5.3, '岛屿地图：5 座小岛就是 5 关，一关一关解锁', 'Five islands = five levels, unlocked one by one'),
        (9.3, '点小驯鹿 Poro，听芬兰语问好', 'Tap Poro the reindeer to hear Finnish'),
        (12.8, '手机没有芬兰语语音？自动改用 App 内置的芬兰语录音', 'No Finnish voice on the phone? Built-in recordings take over'),
        (16.3, '每一关的卡片：最好成绩，还有这一关的词卡', 'Each level card shows the best stars and its word cards'),
    ]),
    ('stage1', [(3.0, 25.0), (45.6, 58.8)], 1, '第 1 关', '词尾配对小达人', 'Level 1 · Person endings', [
        (3.0, '人称代词和它的词尾颜色一样：Sinä 配 -t', 'Each pronoun shares a colour with its ending'),
        (8.4, '选错了：卡片轻轻晃动，小驯鹿用一句中文讲原因', 'Wrong: a gentle shake and a one-line hint in Chinese'),
        (14.0, '答对：手机轻震、卡片弹跳，芬兰语读出整句', 'Right: a soft vibration, a bounce, the phrase read aloud'),
        (22.1, '第一次就答对，星星飞进口袋', 'Right on the first try earns a star'),
        (45.6, '每轮随机 5 题，大约 90 秒', 'Five random questions a round, about 90 seconds'),
        (52.7, '打开宝箱：星级、用时，还解锁下一关', 'Treasure chest: stars, time, and the next level unlocks'),
    ]),
    ('stage2', [(4.0, 12.7), (35.4, 44.4)], 2, '第 2 关', '规则动词大挑战', 'Level 2 · Regular verbs', [
        (4.0, '17 个常用动词：去掉最后的 a / ä，再加人称词尾', '17 everyday verbs: drop the final a / ä, add the ending'),
        (35.4, '“谁在做？”看词尾认人称：unohdatte → Te', '"Who is it?" Read the ending: unohdatte → Te'),
    ]),
    ('stage3', [(3.5, 36.0)], 3, '第 3 关', 'KPT 辅音“变软”魔法', 'Level 3 · Consonant gradation', [
        (3.5, '四年级最难的 KPT 辅音变化，黄金口诀一直置顶', 'The hardest grade-4 topic; the golden rule stays on top'),
        (10.8, '小锤子敲一敲：leikki- 变软成 leiki-', 'The little hammer softens the stem: kk → k'),
        (15.8, '陷阱选项都是孩子真会犯的错：leikkimme 忘了变软', 'Every distractor is a real mistake: leikkimme forgot to soften'),
        (21.8, 'Me leikimme = 我们玩耍', 'Me leikimme = we play'),
        (27.1, 'Hän 和 He 是“大力士”，词干保持原样：Hän lukee', 'Hän and He keep the strong stem: Hän lukee'),
    ]),
    ('stage4', [(3.5, 12.4), (27.7, 35.2), (44.8, 51.6)], 4, '第 4 关', '吃喝与校园日常', 'Level 4 · Everyday verbs', [
        (3.5, '20 个吃喝、上学的高频动词', '20 high-frequency verbs for food and school'),
        (8.0, '特殊动词单独提醒：tehdä → Me teemme，Hän tekee', 'Irregular verbs get their own note: tehdä → teemme / tekee'),
        (27.7, '反向魔法：riidellä 的 d 变强成 t → Minä riitelen', 'Reverse gradation: riidellä → Minä riitelen'),
        (44.8, '5 题全对：三颗星！Mahtavaa!', 'Five out of five: three stars!'),
    ]),
    ('stage5', [(14.6, 38.6)], 5, '第 5 关', '状态、需求与否定句', 'Level 5 · Negative sentences', [
        (14.6, '否定词跟着人称变：en / et / ei / emme / ette / eivät', 'The negative verb follows the person: en, et, ei, emme, ette, eivät'),
        (20.7, '最常见的错：ei putoaa —— 词尾已经被否定词抢走了', 'Classic mistake: ei putoaa — the negative verb already took the ending'),
        (26.3, '光杆词干 = Minä 形式去掉 -n：putoan → putoa', 'Bare stem = the Minä form minus -n: putoan → putoa'),
        (31.0, 'He eivät halua = 他们不想要', 'He eivät halua = they don\'t want'),
    ]),
    ('book', [(3.5, 35.2), (37.5, 42.75)], 6, '词卡', '动词词卡本', 'Verb book', [
        (3.5, '动词词卡本：65 个动词按关卡分好', 'All 65 verbs, sorted by level'),
        (12.3, '完整变位表：强词干和软词干并排对比', 'Full table, strong and soft stems side by side'),
        (16.9, '每一行都能点开听，带中文意思', 'Tap any row to hear it, with the Chinese meaning'),
        (28.7, '一键切换否定句：Minä en kirjoita', 'One tap for the negative: Minä en kirjoita'),
        (37.5, '第 1 关的词尾表：puhu + mme = puhumme', 'Level 1 ending chart: puhu + mme = puhumme'),
    ]),
    ('voice', [(3.6, 15.3)], 7, '语音', '芬兰语朗读', 'Finnish voice', [
        (3.6, '右上角的语音提示：一步步告诉家长怎样开启芬兰语语音', 'The voice chip shows parents how to switch on Finnish speech'),
        (9.4, '不开也能学：855 条内置芬兰语录音，完全离线', 'Or just start: 855 built-in Finnish clips, fully offline'),
    ]),
]
TITLE_SEC = 5.0
OUTRO_SEC = 6.5
DIP = 0.18   # seconds of fade at every cut inside the phone


# ─────────────────────────── drawing helpers ───────────────────────────

def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT_R, size)


def tokens(text):
    out, word = [], ''
    for ch in text:
        if ord(ch) < 0x2E80 and not ch.isspace():
            word += ch
        else:
            if word:
                out.append(word)
                word = ''
            out.append(ch)
    if word:
        out.append(word)
    return out


def wrap(draw, text, f, width):
    lines, cur = [], ''
    for t in tokens(text):
        trial = cur + t
        if draw.textlength(trial, font=f) <= width or not cur:
            cur = trial
        else:
            lines.append(cur.rstrip())
            cur = t.lstrip()
    if cur:
        lines.append(cur.rstrip())
    return lines


def gradient(top, bottom):
    a = np.linspace(0, 1, H)[:, None, None]
    g = np.array(top)[None, None, :] * (1 - a) + np.array(bottom)[None, None, :] * a
    return Image.fromarray(np.repeat(g, W, axis=1).astype(np.uint8))


def load(name, size):
    im = Image.open(os.path.join(RES, 'drawable-nodpi', name + '.webp')).convert('RGBA')
    im.thumbnail(size, Image.LANCZOS)
    return im


def app_icon(size):
    bg = Image.new('RGBA', (size, size), (10, 182, 192, 255))
    fg = Image.open(os.path.join(RES, 'mipmap-xxxhdpi', 'ic_launcher_foreground.png')).convert('RGBA')
    fg = fg.resize((int(size * 1.25), int(size * 1.25)), Image.LANCZOS)
    o = (size - fg.size[0]) // 2
    bg.alpha_composite(fg, (o, o)) if o >= 0 else bg.paste(fg.crop((-o, -o, -o + size, -o + size)), (0, 0))
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=size // 4, fill=255)
    bg.putalpha(mask)
    return bg


def pill(draw, xy, text, f, fill, color=(255, 255, 255), pad=(22, 8)):
    x, y = xy
    w = draw.textlength(text, font=f)
    box = (x, y, x + w + pad[0] * 2, y + f.size + pad[1] * 2 + 6)
    draw.rounded_rectangle(box, radius=(box[3] - box[1]) // 2, fill=fill)
    draw.text((x + pad[0], y + pad[1]), text, font=f, fill=color)
    return box


def shadow(im, box, radius, blur=18, offset=(0, 10), alpha=70):
    sh = Image.new('RGBA', im.size, (0, 0, 0, 0))
    b = (box[0] + offset[0], box[1] + offset[1], box[2] + offset[0], box[3] + offset[1])
    ImageDraw.Draw(sh).rounded_rectangle(b, radius=radius, fill=(20, 40, 70, alpha))
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))


def scene_background(chapter, chip, title_zh, title_en, color):
    im = gradient((228, 243, 252), (255, 248, 236)).convert('RGBA')
    # faint island map in the lower right corner
    m = load('bg_island_map', (1100, 700))
    m.putalpha(Image.eval(m.getchannel('A'), lambda v: v * 0.10))
    im.alpha_composite(m, (W - m.size[0] + 120, H - m.size[1] + 160))
    d = ImageDraw.Draw(im)
    # phone body
    body = (PX - 16, PY - 16, PX + PW + 16, PY + PH + 16)
    shadow(im, body, 64, blur=26, offset=(0, 16), alpha=90)
    d.rounded_rectangle(body, radius=64, fill=(30, 40, 56))
    d.rounded_rectangle((body[0] + 3, body[1] + 3, body[2] - 3, body[3] - 3), radius=61, outline=(70, 84, 104), width=2)
    # brand row
    ic = app_icon(64)
    im.alpha_composite(ic, (RX, 52))
    d.text((RX + 82, 54), '芬兰语动词闯关岛', font=font(32, True), fill=SEA_DEEP)
    d.text((RX + 82, 96), 'Finnish Verb Island · Android', font=font(20), fill=INK_SOFT)
    # chapter block
    f_chip = font(30, True)
    pill(d, (RX, 180), chip, f_chip, color)
    d.text((RX, 250), title_zh, font=font(66, True), fill=INK)
    d.text((RX + 2, 346), title_en, font=font(32), fill=INK_SOFT)
    # chapter bar
    x = RX
    fb = font(22, True)
    for i, name in enumerate(CHAPTERS):
        on = i == chapter
        w = d.textlength(name, font=fb) + 32
        d.rounded_rectangle((x, 990, x + w, 1034), radius=22,
                            fill=color if on else (255, 255, 255), outline=None if on else (214, 226, 236), width=2)
        d.text((x + 16, 997), name, font=fb, fill=(255, 255, 255) if on else INK_SOFT)
        x += w + 10
    return im


def caption_image(zh, en, color):
    """RGBA card shown on the right; returns (image, position)."""
    width = W - RX - 70
    tmp = ImageDraw.Draw(Image.new('RGBA', (10, 10)))
    fz, fe = font(46, True), font(28)
    zl = wrap(tmp, zh, fz, width - 90)
    el = wrap(tmp, en, fe, width - 90)
    h = 44 + len(zl) * 64 + 12 + len(el) * 40 + 40
    im = Image.new('RGBA', (width + 40, h + 40), (0, 0, 0, 0))
    shadow(im, (20, 14, 20 + width, 14 + h), 28, blur=14, offset=(0, 8), alpha=45)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((20, 14, 20 + width, 14 + h), radius=28, fill=(255, 255, 255, 250))
    d.rounded_rectangle((20, 14, 32, 14 + h), radius=6, fill=color)
    y = 14 + 40
    for line in zl:
        d.text((76, y), line, font=fz, fill=INK)
        y += 64
    y += 12
    for line in el:
        d.text((78, y), line, font=fe, fill=INK_SOFT)
        y += 40
    return im, (RX - 20, 430)


def card_bg(size=(W, H), fade=0.55):
    bg = Image.open(os.path.join(RES, 'drawable-nodpi', 'bg_island_map.webp')).convert('RGB')
    bg = bg.resize(size, Image.LANCZOS).filter(ImageFilter.GaussianBlur(6 * size[0] / W))
    return Image.blend(bg, Image.new('RGB', size, (255, 252, 244)), fade).convert('RGBA')


def title_card():
    im = card_bg()
    d = ImageDraw.Draw(im)
    ic = app_icon(220)
    shadow(im, (200, 230, 420, 450), 55, blur=20, alpha=80)
    im.alpha_composite(ic, (200, 230))
    d.text((480, 222), '芬兰语动词闯关岛', font=font(112, True), fill=SEA_DEEP)
    d.text((486, 372), 'Finnish Verb Island', font=font(48, True), fill=SEA)
    d.text((204, 520), '给在芬兰上小学的中文孩子：玩着学会芬兰语动词变位', font=font(44, True), fill=INK)
    d.text((206, 590), 'An Android app that teaches Finnish present-tense verbs to Chinese-speaking kids', font=font(28), fill=INK_SOFT)
    x = 204
    for i, t in enumerate(['5 关闯关', '65 个动词', '中文讲解', '芬兰语朗读', '完全离线']):
        b = pill(d, (x, 680), t, font(32, True), list(STAGE.values())[i])
        x = b[2] + 16
    mascot = load('mascot_reindeer', (420, 460))
    im.alpha_composite(mascot, (W - mascot.size[0] - 140, H - mascot.size[1] - 70))
    return im.convert('RGB')


def outro_card():
    im = card_bg(fade=0.78)
    d = ImageDraw.Draw(im)
    ic = app_icon(150)
    im.alpha_composite(ic, (200, 150))
    d.text((380, 150), '芬兰语动词闯关岛', font=font(84, True), fill=SEA_DEEP)
    d.text((384, 262), 'Finnish Verb Island', font=font(40, True), fill=SEA)
    rows = [
        ('词尾 → 规则动词 → KPT 变软 → 日常动词 → 否定句', 'Endings → regular verbs → gradation → everyday verbs → negatives'),
        ('每一步都有中文讲解，每个变位都能听芬兰语', 'A Chinese hint for every step, Finnish audio for every form'),
        ('完全离线，不用登录，没有广告', 'Fully offline, no login, no ads'),
    ]
    y = 380
    for i, (zh, en) in enumerate(rows):
        d.ellipse((206, y + 20, 226, y + 40), fill=list(STAGE.values())[i])
        d.text((250, y), zh, font=font(42, True), fill=INK)
        d.text((252, y + 60), en, font=font(26), fill=INK_SOFT)
        y += 130
    d.text((250, 790), 'Kotlin · Jetpack Compose · Material 3', font=font(26), fill=INK_SOFT)
    d.text((200, 880), 'Kiitos!  谢谢观看', font=font(72, True), fill=(214, 85, 143))
    mascot = load('mascot_reindeer', (380, 420))
    im.alpha_composite(mascot, (W - mascot.size[0] - 160, H - mascot.size[1] - 90))
    return im.convert('RGB')


def screen_mask():
    m = Image.new('L', (PW * 4, PH * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, PW * 4 - 1, PH * 4 - 1), radius=48 * 4, fill=255)
    m = m.resize((PW, PH), Image.LANCZOS)
    return (np.asarray(m, np.float32) / 255.0)[:, :, None]


# ─────────────────────────── narration ───────────────────────────
# Chinese voice-over: (time, line). Times are seconds of the recording (or of the title / outro card).
# The scheduler never lets two voices overlap: when a line would run into the app's next Finnish clip (or past the
# end of a cut), the picture holds on that frame until the line is finished.
NARRATION = {
    'title': [(0.6, '这是一个安卓应用，帮在芬兰上小学的中文孩子，玩着学会芬兰语的动词变位。')],
    'home': [
        (0.6, '第一次打开，先选一个小探险家。'),
        (5.3, '首页是岛屿地图，五座小岛就是五关。'),
        (12.9, '没有芬兰语语音的手机，会自动改用应用里内置的芬兰语录音。'),
    ],
    'stage1': [
        (3.1, '第一关，配词尾。'),
        (5.3, '代词和它的词尾是同一个颜色，帮孩子记住搭配。'),
        (9.9, '选错了也不怕：卡片轻轻抖一抖，小驯鹿用一句中文讲清楚原因。'),
        (15.7, '答对了，手机会轻轻一震，还会用芬兰语读出整句。'),
        (24.0, '第一次就答对，还能得到一颗星星。'),
        (45.7, '每轮随机五道题，大约九十秒。'),
        (54.9, '最后打开宝箱，看看拿到几颗星，还会解锁下一关。'),
    ],
    'stage2': [
        (6.3, '第二关是十七个常用的规则动词，词干不变，只要换词尾。'),
        (35.5, '还有“谁在做”的题：看词尾，就知道是哪个人称。'),
    ],
    'stage3': [
        (6.0, '第三关是四年级最难的 KPT 辅音变化，黄金口诀一直放在最上面。'),
        (12.2, '不会的时候，点一下小锤子，它会敲一敲，让词干变软。'),
        (16.4, '错误选项都是孩子最容易犯的错，比如忘了变软。'),
        (23.8, '他和他们是大力士，词干保持原样。'),
    ],
    'stage4': [
        (5.8, '第四关是吃喝、上学最常用的二十个动词。'),
        (10.4, '特殊的动词，会有单独的提醒。'),
        (33.4, '有的动词反过来变强，小锤子也会演示。'),
        (48.6, '五道题全部答对，就是三颗星！'),
    ],
    'stage5': [
        (16.8, '第五关学否定句：否定词跟着人称变，后面的动词要去掉词尾。'),
        (22.8, '最常见的错误，就是否定词后面还留着词尾。'),
        (28.1, '记住：否定词已经把词尾抢走了。'),
    ],
    'book': [
        (3.6, '还有一本动词词卡本，六十五个动词都按每一关分好了。'),
        (8.8, '点开一个动词，就是完整的变位表，还带中文意思。'),
        (15.1, '每一行都能点开听。'),
        (27.2, '还可以一键切换成否定句。'),
        (37.6, '第一关的词尾表也在这里，随时可以复习。'),
    ],
    'voice': [
        (3.7, '右上角的语音提示，会告诉家长怎样开启手机的芬兰语语音。'),
        (12.9, '不开也没关系，应用里已经内置了八百五十五条芬兰语录音，完全离线。'),
    ],
    'outro': [(0.6, '五关循序渐进，每一步都有中文讲解，每个变位都能听芬兰语。谢谢观看！')],
}
VOICE = 'zh-CN-XiaoxiaoNeural'
# Edge TTS misreads some polyphones; it gets a single-reading homophone instead (the captions are not affected)
TTS_SAY = {'词干': '词赣', '人称': '人撑', '每一行': '每一航'}
GAP = 0.25          # seconds of silence between two voices
NARR_RMS = 0.075    # target loudness of the voices (linear RMS of the speech part)
FI_RMS = 0.085


# ─────────────────────────── audio pieces ───────────────────────────

def speak_events():
    out = []
    for line in open(os.path.join(META, 'speak.log'), encoding='utf-8'):
        m = re.match(r'\s*([\d.]+)\s.*speak: (.*)$', line)
        if m:
            out.append((float(m.group(1)), m.group(2).strip()))
    return out


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         capture_output=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def level(a, target):
    """Scale speech to a common loudness (RMS over the samples that are not near-silent)."""
    act = a[np.abs(a) > 0.01]
    rms = float(np.sqrt(np.mean(act ** 2))) if len(act) else 0.0
    return a * (target / rms) if rms > 1e-4 else a


def trim(a, thr=0.004, keep=0.04):
    idx = np.nonzero(np.abs(a) > thr)[0]
    if not len(idx):
        return a
    s = max(0, idx[0] - int(keep * SR))
    e = min(len(a), idx[-1] + int(keep * SR * 3))
    return a[s:e]


def clip(text):
    name = hashlib.sha1(text.encode('utf-8')).hexdigest()[:12] + '.ogg'
    path = os.path.join(CLIPS, name)
    if not os.path.exists(path):
        print('  no clip for', text)
        return None
    return level(decode(path), FI_RMS)


def narr(text):
    """Chinese narration line via Edge TTS (cached in _raw/narration)."""
    say = text
    for k, v in TTS_SAY.items():
        say = say.replace(k, v)
    folder = NARR_DIR
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, hashlib.sha1((VOICE + say).encode('utf-8')).hexdigest()[:12] + '.mp3')
    if not os.path.exists(path):
        import asyncio
        import edge_tts
        for attempt in range(4):
            try:
                asyncio.run(edge_tts.Communicate(say, VOICE, rate='+0%').save(path))
                break
            except Exception as e:  # network hiccups
                print('  edge-tts retry', attempt, e)
                if os.path.exists(path):
                    os.remove(path)
    return level(trim(decode(path)), NARR_RMS)


# ─────────────────────────── schedule ───────────────────────────

def fit(items, length):
    """items: [(t, kind, audio)] in piece time. Returns (holds, placed, new_length).
    holds: [(t, seconds)] freeze the picture just before t; placed: [(out_t, kind, audio)]."""
    shift, busy, holds, placed = 0.0, -1e9, [], []
    for t, kind, a in sorted(items, key=lambda x: x[0]):
        want = t + shift
        start = max(want, busy + GAP)
        if start > want + 1e-3:
            holds.append((t, start - want))
            shift += start - want
        busy = start + len(a) / SR
        placed.append((start, kind, a))
    end = length + shift
    if busy + 0.35 > end:
        holds.append((length, busy + 0.35 - end))
        end = busy + 0.35
    return holds, placed, end


def build_plan():
    events = speak_events()
    plan = []
    t_items = [(t, 'zh', narr(x)) for t, x in NARRATION['title']]
    _, placed, length = fit(t_items, TITLE_SEC)
    plan.append({'kind': 'title', 'len': max(TITLE_SEC, length + 0.3), 'audio': placed})
    for scene, ranges, chapter, chip, tz, te, caps in SEGMENTS:
        st = json.load(open(os.path.join(META, scene + '.json'), encoding='utf-8'))['start']
        for ri, (a, b) in enumerate(ranges):
            items = []
            for t_dev, text in events:
                src = t_dev - st
                if a - 0.05 <= src < b - 0.25:
                    c = clip(text)
                    if c is not None:
                        items.append((max(0.0, src - a), 'fi', c))
            for t, text in NARRATION.get(scene, []):
                if a <= t < b:
                    items.append((t - a, 'zh', narr(text)))
            holds, placed, length = fit(items, b - a)
            plan.append({'kind': 'range', 'scene': scene, 'a': a, 'b': b, 'first': ri == 0, 'seg': (scene, ranges, chapter, chip, tz, te, caps),
                         'holds': [(a + t, s) for t, s in holds], 'audio': placed, 'len': length})
    o_items = [(t, 'zh', narr(x)) for t, x in NARRATION['outro']]
    _, placed, length = fit(o_items, OUTRO_SEC)
    plan.append({'kind': 'outro', 'len': max(OUTRO_SEC, length + 0.8), 'audio': placed})
    return plan


def source_times(a, b, holds, n_out):
    """Recording time shown on each output frame of a cut (holds repeat the frame just before their time)."""
    out = []
    cur_src, cur_out = a, 0.0
    for t, sec in sorted(holds) + [(b, 0.0)]:
        play = max(0.0, t - cur_src)
        steps = int(round(play * FPS))
        out += [cur_src + i / FPS for i in range(steps)]
        cur_src = t
        hold_frames = int(round(sec * FPS))
        out += [max(a, t - 1.0 / FPS)] * hold_frames
    while len(out) < n_out:
        out.append(out[-1] if out else a)
    return out[:n_out]


# ─────────────────────────── video ───────────────────────────

class Source:
    """Sequential decoder of one cut of a recording at FPS; frame(i) for non-decreasing i."""

    def __init__(self, scene, a, b):
        cmd = ['ffmpeg', '-v', 'error', '-ss', f'{a:.3f}', '-i', os.path.join(RAW, scene + '.mp4'), '-t', f'{b - a + 0.5:.3f}',
               '-vf', f'fps={FPS},scale={PW}:{PH}:flags=lanczos', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
        self.p = subprocess.Popen(cmd, stdout=subprocess.PIPE)
        self.idx = -1
        self.cur = None

    def frame(self, i):
        size = PW * PH * 3
        while self.idx < i:
            buf = self.p.stdout.read(size)
            self.idx += 1
            if len(buf) == size:
                self.cur = np.frombuffer(buf, np.uint8).reshape(PH, PW, 3)
            else:
                break
        return self.cur

    def close(self):
        self.p.stdout.close()
        self.p.kill()


def render(plan, timeline_out):
    mask = screen_mask()
    enc = subprocess.Popen(['ffmpeg', '-y', '-v', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                            '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18',
                            '-pix_fmt', 'yuv420p', timeline_out], stdin=subprocess.PIPE)
    frames = 0

    def emit(arr):
        nonlocal frames
        enc.stdin.write(np.ascontiguousarray(arr, dtype=np.uint8).tobytes())
        frames += 1

    white = np.full((H, W, 3), 255, np.float32)

    def card(img, sec):
        arr = np.asarray(img, np.float32)
        n = int(round(sec * FPS))
        for i in range(n):
            t = i / FPS
            a = min(1.0, t / 0.6, (sec - t) / 0.5)
            emit(arr * a + white * (1 - a))

    cache = {}
    prev_cap, cap_since = None, 0
    for piece in plan:
        piece['out'] = frames / FPS
        if piece['kind'] == 'title':
            card(title_card(), piece['len'])
            continue
        if piece['kind'] == 'outro':
            card(outro_card(), piece['len'])
            continue
        scene, ranges, chapter, chip, tz, te, caps = piece['seg']
        if piece['first']:
            color = STAGE[chapter] if 1 <= chapter <= 5 else ((232, 135, 30) if chapter == 7 else SEA)
            cache = {'bg': np.asarray(scene_background(chapter, chip, tz, te, color).convert('RGB'), np.float32),
                     'caps': [(t, caption_image(zh, en, color)) for t, zh, en in caps]}
            prev_cap = None
        bg, cap_imgs = cache['bg'], cache['caps']
        a, b = piece['a'], piece['b']
        n = int(round(piece['len'] * FPS))
        srcs = source_times(a, b, piece['holds'], n)
        src = Source(scene, a, b)
        for i, t_src in enumerate(srcs):
            ph = src.frame(int((t_src - a) * FPS + 1e-6))
            f = bg.copy()
            region = f[PY:PY + PH, PX:PX + PW]
            if ph is not None:
                p = ph.astype(np.float32)
                edge = min(i / FPS, (n - 1 - i) / FPS)
                if edge < DIP:                      # soft dip to white at every cut
                    k = edge / DIP
                    p = p * k + 255 * (1 - k)
                region[:] = p * mask + region * (1 - mask)
            cur = None
            for t, ci in cap_imgs:
                if t <= t_src + 1e-6:
                    cur = ci
            if cur is None and piece['first']:
                cur = cap_imgs[0][1]
            if cur is not None:
                if prev_cap is not cur:
                    prev_cap, cap_since = cur, frames
                img, (cx, cy) = cur
                a_in = min(1.0, (frames - cap_since + 1) / (0.3 * FPS))
                ca = np.asarray(img, np.float32)
                al = ca[:, :, 3:4] / 255.0 * a_in
                h, w = ca.shape[:2]
                roi = f[cy:cy + h, cx:cx + w]
                roi[:] = ca[:, :, :3] * al + roi * (1 - al)
            emit(f)
        src.close()
        print(f'  {scene} {a:.1f}-{b:.1f}: {piece["len"]:.1f}s (holds {sum(s for _, s in piece["holds"]):.1f}s), at {frames / FPS:.1f}s')
    enc.stdin.close()
    enc.wait()
    return frames / FPS


def build_audio(plan, total, wav_out):
    n = int(total * SR) + SR
    voice = np.zeros(n, np.float32)
    log = []
    for piece in plan:
        for t, kind, a in piece['audio']:
            s = int((piece['out'] + t) * SR)
            e = min(n, s + len(a))
            if e > s:
                voice[s:e] += a[:e - s]
            log.append((round(piece['out'] + t, 2), kind, round(len(a) / SR, 2)))
    bed = music(total)
    bed = np.pad(bed, (0, n - len(bed)))
    blk = SR // 100                      # duck the music under any voice
    nb = n // blk
    act = (np.abs(voice[:nb * blk]).reshape(nb, blk).max(axis=1) > 0.01).astype(np.float32)
    act = np.convolve(act, np.ones(25) / 25, 'same')
    env = np.pad(np.repeat(act, blk), (0, n - nb * blk))
    duck = 1 - 0.65 * np.clip(env * 3, 0, 1)
    mix = bed * 0.55 * duck + voice
    mix /= max(1e-6, np.abs(mix).max() / 0.89)
    stereo = np.repeat((mix * 32767).astype(np.int16)[:, None], 2, axis=1)
    with wave.open(wav_out, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(stereo.tobytes())
    return log


def chapters(plan):
    names = {'home': '岛屿地图', 'stage1': '第 1 关：词尾配对', 'stage2': '第 2 关：规则动词', 'stage3': '第 3 关：KPT 辅音变软',
             'stage4': '第 4 关：吃喝与校园日常', 'stage5': '第 5 关：否定句', 'book': '动词词卡本',
             'voice': '芬兰语朗读（没有芬兰语语音的手机也能听）'}
    out = ['0:00 片头']
    for p in plan:
        if p['kind'] == 'range' and p['first']:
            s = int(p['out'] + 0.5)
            out.append(f'{s // 60}:{s % 60:02d} {names[p["scene"]]}')
        if p['kind'] == 'outro':
            s = int(p['out'] + 0.5)
            out.append(f'{s // 60}:{s % 60:02d} 结尾')
    return out


def music(seconds):
    """Soft original background loop: kalimba-like arpeggio + pad + bass, I–V–vi–IV in C, 96 bpm."""
    n = int(seconds * SR)
    out = np.zeros(n, np.float32)
    beat = 60 / 96
    chords = [(48, [0, 4, 7]), (43, [0, 4, 7]), (45, [0, 3, 7]), (41, [0, 4, 7])]   # C G Am F (root midi)
    hz = lambda m: 440.0 * 2 ** ((m - 69) / 12)
    t_note = np.arange(int(SR * 1.6)) / SR

    def pluck(m, amp):
        f = hz(m)
        env = np.exp(-t_note * 3.2) * np.minimum(1, t_note / 0.004)
        return amp * env * (np.sin(2 * np.pi * f * t_note) + 0.25 * np.sin(4 * np.pi * f * t_note) * np.exp(-t_note * 6))

    pattern = [0, 2, 1, 2, 3, 2, 1, 2]       # chord-tone index per eighth note (3 = octave)
    bar = 4 * beat
    t = 0.0
    k = 0
    while t < seconds:
        root, iv = chords[(k // 2) % 4]
        tones = [root + 24 + i for i in iv] + [root + 36]
        for e in range(8):
            s = int((t + e * beat / 2) * SR)
            if s >= n:
                break
            note = pluck(tones[pattern[e]] + (12 if (k % 2 and e in (4, 6)) else 0), 0.10)
            out[s:s + len(note)] += note[:max(0, n - s)]
        for e in (0, 2):                    # bass on beats 1 and 3
            s = int((t + e * beat) * SR)
            if s < n:
                f = hz(root - 12 + 24)
                tb = np.arange(int(SR * 1.2)) / SR
                bass = 0.16 * np.exp(-tb * 2.0) * np.minimum(1, tb / 0.01) * np.sin(2 * np.pi * f * tb)
                out[s:s + len(bass)] += bass[:max(0, n - s)]
        s = int(t * SR)                     # soft pad
        tp = np.arange(int(SR * bar)) / SR
        env = np.minimum(1, tp / 0.6) * np.minimum(1, (bar - tp) / 0.6)
        pad = sum(np.sin(2 * np.pi * hz(root + 12 + i) * tp) for i in iv) * 0.025 * env
        out[s:s + len(pad)] += pad[:max(0, n - s)]
        t += bar
        k += 1
    # light echo for space
    d = int(0.27 * SR)
    wet = np.zeros_like(out)
    wet[d:] = out[:-d] * 0.28
    wet[2 * d:] += out[:-2 * d] * 0.12
    out = out + wet
    fade = int(SR * 1.5)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade * 2:] *= np.linspace(1, 0, fade * 2)
    return out


def thumbnail(path):
    """1280x720 YouTube thumbnail: big title on the left, the hammer moment on a tilted phone on the right."""
    TW, TH = 1280, 720
    im = card_bg((TW, TH), fade=0.5)
    d = ImageDraw.Draw(im)
    d.text((60, 70), '芬兰语', font=font(118, True), fill=SEA_DEEP)
    d.text((60, 205), '动词闯关岛', font=font(118, True), fill=SEA_DEEP)
    pill(d, (64, 372), '玩着学会动词变位', font(50, True), (240, 138, 36), pad=(26, 10))
    d.text((66, 480), 'Finnish Verb Island', font=font(40, True), fill=SEA)
    d.text((66, 534), '5 关 · 65 个动词 · 中文讲解 · 离线', font=font(32, True), fill=INK)
    from io import BytesIO
    shot = subprocess.run(['ffmpeg', '-v', 'error', '-ss', '24.0', '-i', os.path.join(RAW, 'stage3.mp4'),
                           '-frames:v', '1', '-f', 'image2pipe', '-vcodec', 'png', '-'], capture_output=True).stdout
    ph = Image.open(BytesIO(shot)).convert('RGBA').resize((306, 680), Image.LANCZOS)
    m = Image.new('L', ph.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, ph.size[0] - 1, ph.size[1] - 1), radius=34, fill=255)
    ph.putalpha(m)
    body = Image.new('RGBA', (ph.size[0] + 24, ph.size[1] + 24), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, body.size[0] - 1, body.size[1] - 1), radius=44, fill=(30, 40, 56, 255))
    body.alpha_composite(ph, (12, 12))
    body = body.rotate(-7, resample=Image.BICUBIC, expand=True)
    im.alpha_composite(body, (TW - body.size[0] - 90, 10))
    mascot = load('mascot_reindeer', (250, 280))
    im.alpha_composite(mascot, (TW - body.size[0] - 260, TH - mascot.size[1] - 10))
    im.convert('RGB').save(path)


def update_description(lines):
    """Replace the chapter block of docs/youtube/YouTube说明.txt (it follows the line '章节：')."""
    p = os.path.join(YT, 'YouTube说明.txt')
    s = open(p, encoding='utf-8').read()
    nl = chr(10)
    a = s.index('章节：' + nl) + len('章节：' + nl)
    b = s.index(nl + nl, a)
    open(p, 'w', encoding='utf-8', newline=nl).write(s[:a] + nl.join(lines) + s[b:])


def mux(tmp_video, tmp_audio):
    """Two-pass loudness normalisation to -15 LUFS (YouTube turns loud videos down but never quiet ones up)."""
    target = 'I=-15:TP=-1.5:LRA=11'
    probe = subprocess.run(['ffmpeg', '-hide_banner', '-i', tmp_audio, '-af', f'loudnorm={target}:print_format=json',
                            '-f', 'null', '-'], capture_output=True, text=True).stderr
    m = json.loads(probe[probe.rindex('{'):probe.rindex('}') + 1])
    af = (f"loudnorm={target}:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
          f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true,aresample=48000")
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', tmp_video, '-i', tmp_audio, '-af', af, '-c:v', 'copy',
                    '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', OUT], check=True)


def main():
    tmp_video = os.path.join(RAW, 'video_only.mp4')
    tmp_audio = os.path.join(RAW, 'audio.wav')
    if '--mux-only' in sys.argv:
        mux(tmp_video, tmp_audio)
        print('remuxed', OUT)
        return
    print('synthesising narration and planning ...')
    plan = build_plan()
    if '--plan-only' in sys.argv:
        for p in plan:
            if p['kind'] == 'range':
                print(f"  {p['scene']} {p['a']:.1f}-{p['b']:.1f} len {p['len']:.1f} holds {[(round(t, 1), round(x, 1)) for t, x in p['holds']]}")
            else:
                print(f"  {p['kind']} len {p['len']:.1f}")
        print('total', round(sum(p['len'] for p in plan), 1))
        return
    print('rendering video ...')
    total = render(plan, tmp_video)
    print(f'video {total:.1f}s; mixing audio ...')
    log = build_audio(plan, total, tmp_audio)
    json.dump(log, open(os.path.join(META, 'placed_audio.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
    open(os.path.join(META, 'chapters.txt'), 'w', encoding='utf-8').write(chr(10).join(chapters(plan)) + chr(10))
    update_description(chapters(plan))
    mux(tmp_video, tmp_audio)
    thumbnail(os.path.join(YT, 'thumbnail.png'))
    zh = sum(1 for x in log if x[1] == 'zh')
    print(f'done: {OUT} ({os.path.getsize(OUT) / 1e6:.1f} MB, {total:.1f}s, {len(log) - zh} Finnish clips, {zh} narration lines)')


if __name__ == '__main__':
    sys.exit(main())
