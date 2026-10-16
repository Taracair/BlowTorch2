#!/usr/bin/env python3
"""Draw the Signals button-face set.

Original art for BlowTorch, MIT License (same as the project). Six examples
so a second set can sit beside Marks in the picker. 192 px, transparent
outside the disc. Strokes stay thick enough for a ~48 dp tile.
"""

import math
import os

from PIL import Image, ImageDraw

SIZE = 192
OUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "..",
    "BT_Free",
    "assets",
    "buttonfaces",
    "signals",
)

PLATE = (22, 58, 54, 255)
RIM = (126, 196, 176, 255)
INK = (236, 246, 240, 255)


def new():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.ellipse((8, 8, SIZE - 8, SIZE - 8), fill=PLATE, outline=RIM, width=10)
    return im, draw


def star(draw):
    cx, cy, r, inner = 96, 96, 68, 30
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = r if i % 2 == 0 else inner
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    draw.polygon(pts, fill=INK)


def sun(draw):
    for i in range(8):
        ang = i * math.pi / 4
        x1 = 96 + 34 * math.cos(ang)
        y1 = 96 + 34 * math.sin(ang)
        x2 = 96 + 74 * math.cos(ang)
        y2 = 96 + 74 * math.sin(ang)
        draw.line((x1, y1, x2, y2), fill=RIM if i % 2 else INK, width=18)
    draw.ellipse((58, 58, 134, 134), fill=INK)


def moon(draw):
    draw.ellipse((44, 40, 136, 152), fill=INK)
    draw.ellipse((80, 32, 172, 144), fill=PLATE)


def bolt(draw):
    draw.polygon([(112, 28), (60, 100), (92, 100), (72, 164), (140, 84), (104, 84)], fill=INK)


def drop(draw):
    draw.polygon([(96, 32), (140, 96), (96, 112), (52, 96)], fill=INK)
    draw.ellipse((52, 80, 140, 164), fill=INK)


def gear(draw):
    cx, cy = 96, 96
    teeth = 8
    r_root, r_tip = 46, 72
    pts = []
    for i in range(teeth):
        a = -math.pi / 2 + i * (2 * math.pi / teeth)
        span = 2 * math.pi / teeth
        for da, rad in (
            (-0.42, r_root),
            (-0.16, r_tip),
            (0.16, r_tip),
            (0.42, r_root),
        ):
            ang = a + da * span
            pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    draw.polygon(pts, fill=INK)
    draw.ellipse((cx - 24, cy - 24, cx + 24, cy + 24), fill=PLATE)


SYMBOLS = {
    "star": star,
    "sun": sun,
    "moon": moon,
    "bolt": bolt,
    "drop": drop,
    "gear": gear,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SYMBOLS.items():
        im, draw = new()
        fn(draw)
        path = os.path.join(OUT, name + ".png")
        im.save(path)
        print(path)


if __name__ == "__main__":
    main()
