#!/usr/bin/env python3
"""Draw the Marks button-face set.

Original art for BlowTorch, MIT License (same as the project). Pads in the
app are about 42–48 dp; these are 192 px so a 48 dp tile on a 4x screen is
1:1 and smaller tiles scale down. Corners outside the plate are transparent.
Strokes and small parts are sized to stay readable at that tile size.
"""

import os
import sys

from PIL import Image, ImageDraw

SIZE = 192
OUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "..",
    "BT_Free",
    "assets",
    "buttonfaces",
    "marks",
)

PLATE = (36, 48, 68, 255)
RIM = (196, 165, 116, 255)
INK = (244, 230, 200, 255)


def plate(draw):
    draw.rounded_rectangle((10, 10, SIZE - 10, SIZE - 10), 36, fill=PLATE, outline=RIM, width=10)


def new():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    plate(draw)
    return im, draw


def rr(draw, box, rad, fill):
    draw.rounded_rectangle(box, rad, fill=fill)


def disc(draw, box, fill):
    draw.ellipse(box, fill=fill)


def poly(draw, pts, fill):
    draw.polygon(pts, fill=fill)


def sword(draw):
    poly(draw, [(96, 32), (118, 74), (108, 112), (84, 112), (74, 74)], INK)
    rr(draw, (40, 104, 152, 130), 8, RIM)
    rr(draw, (84, 126, 108, 156), 8, INK)
    disc(draw, (78, 146, 114, 174), RIM)


def shield(draw):
    poly(draw, [(96, 32), (154, 62), (142, 116), (96, 166), (50, 116), (38, 62)], INK)
    draw.rectangle((86, 58, 106, 144), fill=PLATE)
    draw.rectangle((60, 90, 132, 110), fill=PLATE)


def compass(draw):
    draw.ellipse((40, 40, 152, 152), outline=INK, width=16)
    poly(draw, [(96, 28), (124, 96), (96, 80), (68, 96)], RIM)
    poly(draw, [(96, 164), (124, 96), (96, 112), (68, 96)], INK)
    disc(draw, (78, 78, 114, 114), RIM)


def door(draw):
    rr(draw, (58, 32, 134, 164), 34, INK)
    rr(draw, (74, 50, 118, 112), 14, PLATE)
    disc(draw, (98, 118, 128, 148), RIM)


def scroll(draw):
    rr(draw, (50, 56, 142, 140), 12, INK)
    disc(draw, (36, 42, 92, 82), RIM)
    disc(draw, (100, 114, 158, 158), RIM)
    draw.rectangle((66, 88, 126, 104), fill=PLATE)
    draw.rectangle((66, 112, 114, 128), fill=PLATE)


def flask(draw):
    rr(draw, (78, 32, 114, 74), 8, INK)
    poly(draw, [(68, 66), (124, 66), (152, 160), (40, 160)], INK)
    poly(draw, [(52, 112), (140, 112), (148, 156), (44, 156)], RIM)
    draw.rectangle((70, 32, 122, 48), fill=RIM)


def key(draw):
    disc(draw, (32, 58, 104, 130), INK)
    disc(draw, (52, 78, 84, 110), PLATE)
    rr(draw, (90, 82, 160, 106), 8, INK)
    draw.rectangle((132, 106, 156, 136), fill=RIM)
    draw.rectangle((112, 106, 130, 128), fill=RIM)


def pack(draw):
    rr(draw, (50, 62, 142, 164), 20, INK)
    draw.arc((66, 28, 126, 84), 200, 340, fill=INK, width=18)
    rr(draw, (66, 100, 126, 128), 8, RIM)


def lantern(draw):
    draw.arc((68, 26, 124, 70), 200, 340, fill=INK, width=16)
    poly(draw, [(58, 68), (134, 68), (148, 126), (44, 126)], RIM)
    poly(draw, [(72, 80), (120, 80), (128, 118), (64, 118)], INK)
    poly(draw, [(96, 84), (112, 116), (80, 116)], RIM)
    draw.rectangle((72, 126, 120, 148), fill=INK)
    draw.rectangle((84, 148, 108, 166), fill=RIM)


def boot(draw):
    rr(draw, (68, 28, 122, 120), 12, INK)
    rr(draw, (40, 100, 164, 156), 20, INK)
    draw.rectangle((68, 28, 122, 52), fill=RIM)
    draw.rectangle((40, 132, 72, 160), fill=RIM)
    disc(draw, (122, 104, 168, 152), RIM)


SYMBOLS = {
    "sword": sword,
    "shield": shield,
    "compass": compass,
    "door": door,
    "scroll": scroll,
    "flask": flask,
    "key": key,
    "pack": pack,
    "lantern": lantern,
    "boot": boot,
}


def render_all(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    images = []
    for name, fn in SYMBOLS.items():
        im, draw = new()
        fn(draw)
        path = os.path.join(out_dir, name + ".png")
        im.save(path)
        images.append((name, im))
    sheet = contact(images)
    sheet_path = os.path.join(os.path.dirname(__file__), "contact-sheet.png")
    sheet.save(sheet_path)
    return sheet_path


def contact(images):
    pad = 16
    label_h = 28
    cell = SIZE + pad
    cols = 5
    rows = 2
    small = 48
    width = cols * cell + pad
    height = rows * (cell + label_h + small + 8) + pad
    sheet = Image.new("RGBA", (width, height), (22, 26, 32, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(images):
        c = i % cols
        r = i // cols
        x = pad + c * cell
        y = pad + r * (cell + label_h + small + 8)
        sheet.paste(im, (x, y), im)
        draw.text((x + 8, y + SIZE + 4), name, fill=(220, 214, 200, 255))
        tiny = im.resize((small, small), Image.Resampling.LANCZOS)
        sheet.paste(tiny, (x + 8, y + SIZE + label_h), tiny)
    return sheet


def main():
    out = os.path.abspath(OUT)
    path = render_all(out)
    print(path)


if __name__ == "__main__":
    sys.exit(main() or 0)
