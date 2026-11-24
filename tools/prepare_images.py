"""Turn the 16 picture files into app resources.

Source: design/images (the 16 pictures as delivered, original file names)
The file names 08-16 do not match what is drawn in them, so each picture is mapped by its content below.
White backgrounds that touch the border become transparent, pictures are scaled down and saved as WebP
in app/src/main/res/drawable-nodpi. The launcher picture becomes an adaptive icon (foreground + background colour).

Run: Game/.devenv/Scripts/python.exe tools/prepare_images.py
"""
import os
from collections import deque

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
SRC = os.path.join(PROJECT, 'design', 'images')
RES = os.path.join(PROJECT, 'app', 'src', 'main', 'res')
OUT = os.path.join(RES, 'drawable-nodpi')

# source file -> (resource name, max side in px, cut white background)
PICTURES = {
    '01_mascot_reindeer.png': ('mascot_reindeer', 420, True),
    '02_bg_island_map.png': ('bg_island_map', 1280, False),
    '03_icon_kpt_hammer.png': ('icon_kpt_hammer', 320, True),
    '04_badge_three_stars.png': ('badge_three_stars', 480, True),
    '05_icon_strong_helmet.png': ('icon_strong_helmet', 280, True),
    '06_icon_soft_cloud.png': ('icon_soft_cloud', 280, True),
    '07_stage1_speech_bubble.png': ('stage1_speech_bubble', 280, True),
    '08_reward_treasure_chest.png': ('stage2_storybook_pencil', 280, True),  # file name says chest, picture is a book
    '09_icon_streak_flame.png': ('stage4_school_lunch_bag', 280, True),  # picture is the backpack + apple + milk
    '10_icon_tts_megaphone.png': ('stage5_magic_shield', 280, True),  # picture is the shield
    '12_stage2_storybook_pencil.png': ('avatar_boy_beanie', 280, True),  # picture is the boy
    '13_stage5_magic_shield.png': ('avatar_girl_pigtails', 280, True),  # picture is the girl
    '14_avatar_girl_pigtails.png': ('icon_tts_megaphone', 200, True),  # picture is the megaphone
    '15_avatar_boy_beanie.png': ('reward_treasure_chest', 520, True),  # picture is the chest
    '16_stage4_school_lunch_bag.png': ('icon_streak_flame', 200, True),  # picture is the flame
}


def cut_white(im, limit=236):
    """Pixels that are near-white and connected to the border become transparent (soft edge)."""
    im = im.convert('RGBA')
    w, h = im.size
    px = im.load()
    seen = bytearray(w * h)
    q = deque()
    for x in range(w):
        q.append((x, 0)); q.append((x, h - 1))
    for y in range(h):
        q.append((0, y)); q.append((w - 1, y))
    while q:
        x, y = q.popleft()
        i = y * w + x
        if seen[i]:
            continue
        r, g, b, a = px[x, y]
        if min(r, g, b) < limit:
            continue
        seen[i] = 1
        px[x, y] = (r, g, b, 0)
        if x > 0: q.append((x - 1, y))
        if x < w - 1: q.append((x + 1, y))
        if y > 0: q.append((x, y - 1))
        if y < h - 1: q.append((x, y + 1))
    # soften the 1-pixel rim next to the removed area
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            i = y * w + x
            if seen[i]:
                continue
            n = seen[i - 1] + seen[i + 1] + seen[i - w] + seen[i + w]
            if n:
                r, g, b, a = px[x, y]
                light = min(r, g, b)
                if light > 200:
                    px[x, y] = (r, g, b, int(255 * (1 - n / 6)))
    bbox = im.getchannel('A').getbbox()
    return im.crop(bbox) if bbox else im


def save(im, name, side):
    im.thumbnail((side, side), Image.LANCZOS)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + '.webp')
    im.save(path, 'WEBP', quality=88, method=6)
    print(f'{name:28s} {im.size[0]}x{im.size[1]} {os.path.getsize(path) // 1024} KB')


def launcher():
    im = Image.open(os.path.join(SRC, '11_app_launcher_icon.png')).convert('RGB')
    # the teal rounded square: find its bounding box (non-white area)
    gray = im.convert('L').point(lambda v: 255 if v < 235 else 0)
    x0, y0, x1, y1 = gray.getbbox()
    side = min(x1 - x0, y1 - y0)
    inset = int(side * 0.085)  # drop the rounded corners
    art = im.crop((x0 + inset, y0 + inset, x0 + side - inset, y0 + side - inset))
    bg = art.getpixel((6, art.size[1] // 2))
    print('launcher background colour', '#%02X%02X%02X' % bg)
    # adaptive icon: 108dp canvas, art fills 74% so the reindeer head stays inside the mask
    for folder, px in (('mipmap-mdpi', 108), ('mipmap-hdpi', 162), ('mipmap-xhdpi', 216), ('mipmap-xxhdpi', 324),
                       ('mipmap-xxxhdpi', 432)):
        canvas = Image.new('RGB', (px, px), bg)
        inner = int(px * 0.74)
        a = art.resize((inner, inner), Image.LANCZOS)
        canvas.paste(a, ((px - inner) // 2, (px - inner) // 2))
        os.makedirs(os.path.join(RES, folder), exist_ok=True)
        canvas.save(os.path.join(RES, folder, 'ic_launcher_foreground.png'))
        # legacy icon (pre-Android 8): rounded square as drawn
        legacy = cut_white(im.copy())
        legacy.thumbnail((int(px / 1.5), int(px / 1.5)), Image.LANCZOS)
        legacy.save(os.path.join(RES, folder, 'ic_launcher.png'))
    with open(os.path.join(RES, 'values', 'ic_launcher_background.xml'), 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <color name="ic_launcher_background">#%02X%02X%02X</color>\n</resources>\n' % bg)


def main():
    os.makedirs(os.path.join(RES, 'values'), exist_ok=True)
    for src, (name, side, cut) in PICTURES.items():
        im = Image.open(os.path.join(SRC, src))
        im = cut_white(im) if cut else im.convert('RGB')
        save(im, name, side)
    launcher()


if __name__ == '__main__':
    main()
