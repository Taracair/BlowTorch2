#!/usr/bin/env python3
"""Embers and Voyage button faces. Original art, MIT, same as BlowTorch.

192 px, transparent outside the plate. Glyphs stay thick enough for a ~48 dp
tile and fill the diamond or shield evenly.
"""

import math
import os

from PIL import Image, ImageDraw

SIZE = 192
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "BT_Free", "assets", "buttonfaces")

PLATE = (0, 0, 0, 255)
RIM = (0, 0, 0, 255)
INK = (0, 0, 0, 255)


def use(plate, rim, ink):
    global PLATE, RIM, INK
    PLATE, RIM, INK = plate, rim, ink


def blank():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def plate_embers(draw):
    draw.polygon([(96, 6), (186, 96), (96, 186), (6, 96)], fill=RIM)
    draw.polygon([(96, 22), (170, 96), (96, 170), (22, 96)], fill=PLATE)


def plate_voyage(draw):
    draw.polygon([(96, 10), (176, 52), (160, 132), (96, 182), (32, 132), (16, 52)], fill=RIM)
    draw.polygon([(96, 26), (160, 60), (148, 124), (96, 166), (44, 124), (32, 60)], fill=PLATE)


def rr(draw, box, rad, fill):
    draw.rounded_rectangle(box, rad, fill=fill)


def disc(draw, box, fill):
    draw.ellipse(box, fill=fill)


def poly(draw, pts, fill):
    draw.polygon(pts, fill=fill)


def bar(draw, box, fill):
    draw.rectangle(box, fill=fill)


def line(draw, a, b, fill, width):
    draw.line((a, b), fill=fill, width=width)


def brazier(draw):
    poly(draw, [(64, 78), (128, 78), (140, 112), (52, 112)], INK)
    bar(draw, (56, 108, 136, 124), RIM)
    bar(draw, (84, 124, 108, 148), INK)
    poly(draw, [(96, 44), (120, 82), (72, 82)], RIM)


def coal(draw):
    poly(draw, [(70, 64), (122, 52), (146, 92), (124, 136), (70, 128), (50, 92)], INK)
    poly(draw, [(84, 80), (116, 74), (124, 108), (92, 116)], RIM)


def forge(draw):
    bar(draw, (52, 84, 140, 144), INK)
    poly(draw, [(52, 88), (96, 52), (140, 88)], RIM)
    rr(draw, (72, 100, 120, 144), 6, PLATE)
    poly(draw, [(96, 108), (110, 140), (82, 140)], RIM)


def tongs(draw):
    poly(draw, [(58, 48), (80, 44), (108, 112), (88, 124)], INK)
    poly(draw, [(112, 44), (136, 48), (108, 124), (88, 112)], RIM)
    rr(draw, (76, 112, 116, 148), 8, INK)


def bellows(draw):
    poly(draw, [(48, 96), (112, 60), (148, 96), (112, 132)], INK)
    poly(draw, [(60, 96), (100, 78), (100, 114)], PLATE)
    bar(draw, (140, 84, 160, 108), RIM)


def cauldron(draw):
    disc(draw, (52, 76, 140, 144), INK)
    bar(draw, (52, 76, 140, 100), RIM)
    draw.arc((36, 52, 76, 100), 200, 40, fill=INK, width=14)
    draw.arc((116, 52, 156, 100), 140, 340, fill=INK, width=14)


def smoke(draw):
    disc(draw, (64, 40, 108, 84), INK)
    disc(draw, (96, 60, 144, 108), RIM)
    disc(draw, (56, 96, 112, 148), INK)


def hearth(draw):
    bar(draw, (48, 64, 144, 144), INK)
    bar(draw, (48, 56, 144, 76), RIM)
    poly(draw, [(72, 144), (96, 88), (120, 144)], RIM)


def poker(draw):
    bar(draw, (86, 40, 106, 128), INK)
    disc(draw, (68, 112, 124, 156), RIM)
    bar(draw, (106, 48, 140, 68), INK)


def kindling(draw):
    poly(draw, [(48, 132), (78, 52), (98, 60), (68, 144)], INK)
    poly(draw, [(72, 140), (118, 48), (138, 58), (92, 150)], RIM)
    poly(draw, [(100, 136), (140, 64), (156, 78), (116, 148)], INK)


def skillet(draw):
    disc(draw, (40, 64, 124, 144), INK)
    bar(draw, (112, 92, 164, 116), RIM)
    disc(draw, (56, 80, 108, 128), PLATE)


def chimney(draw):
    bar(draw, (76, 40, 116, 92), INK)
    poly(draw, [(44, 148), (148, 148), (120, 88), (72, 88)], RIM)
    disc(draw, (86, 28, 118, 56), INK)


def bonfire(draw):
    poly(draw, [(44, 144), (148, 144), (120, 112), (72, 112)], INK)
    poly(draw, [(96, 36), (132, 120), (60, 120)], RIM)
    poly(draw, [(96, 68), (116, 116), (76, 116)], INK)


def wick(draw):
    bar(draw, (78, 88, 114, 148), INK)
    bar(draw, (64, 132, 128, 156), RIM)
    poly(draw, [(96, 40), (118, 92), (74, 92)], RIM)


def spark(draw):
    poly(draw, [(96, 40), (112, 80), (152, 96), (112, 112), (96, 152), (80, 112), (40, 96), (80, 80)], INK)
    disc(draw, (82, 82, 110, 110), RIM)


def ash(draw):
    disc(draw, (44, 100, 92, 144), INK)
    disc(draw, (76, 80, 132, 136), RIM)
    disc(draw, (108, 104, 156, 148), INK)


def log(draw):
    rr(draw, (32, 76, 160, 124), 22, INK)
    disc(draw, (32, 76, 84, 124), RIM)
    disc(draw, (46, 88, 70, 112), PLATE)


def kettle(draw):
    disc(draw, (48, 72, 132, 144), INK)
    bar(draw, (120, 96, 164, 118), RIM)
    draw.arc((68, 44, 120, 92), 200, 340, fill=INK, width=16)
    bar(draw, (84, 36, 108, 56), RIM)


def spit(draw):
    bar(draw, (32, 92, 160, 112), INK)
    poly(draw, [(56, 64), (104, 92), (56, 124)], RIM)
    disc(draw, (128, 76, 160, 124), INK)


def grill(draw):
    bar(draw, (58, 56, 134, 72), RIM)
    bar(draw, (62, 78, 130, 94), INK)
    bar(draw, (62, 100, 130, 116), INK)
    bar(draw, (70, 116, 86, 134), RIM)
    bar(draw, (106, 116, 122, 134), RIM)


def pot(draw):
    poly(draw, [(56, 76), (136, 76), (148, 140), (44, 140)], INK)
    bar(draw, (48, 64, 144, 84), RIM)
    draw.arc((36, 48, 76, 92), 200, 40, fill=INK, width=14)
    draw.arc((116, 48, 156, 92), 140, 340, fill=INK, width=14)


def match(draw):
    bar(draw, (86, 76, 106, 156), INK)
    disc(draw, (70, 36, 122, 88), RIM)


def flare(draw):
    poly(draw, [(96, 32), (116, 88), (160, 104), (116, 124), (96, 160), (76, 124), (32, 104), (76, 88)], RIM)
    disc(draw, (76, 84, 116, 124), INK)


def cinder(draw):
    disc(draw, (64, 64, 128, 128), INK)
    poly(draw, [(96, 40), (114, 78), (96, 96), (78, 78)], RIM)
    poly(draw, [(148, 96), (112, 112), (100, 96), (112, 80)], RIM)
    poly(draw, [(56, 132), (88, 112), (100, 132), (80, 152)], RIM)


def oven(draw):
    rr(draw, (44, 52, 148, 152), 8, INK)
    rr(draw, (64, 76, 128, 136), 12, PLATE)
    bar(draw, (44, 44, 148, 68), RIM)
    disc(draw, (100, 92, 124, 116), RIM)


def brand(draw):
    bar(draw, (86, 36, 106, 112), INK)
    poly(draw, [(64, 100), (128, 100), (144, 156), (48, 156)], RIM)


def pyre(draw):
    poly(draw, [(36, 148), (156, 148), (96, 100)], INK)
    poly(draw, [(52, 124), (140, 124), (96, 84)], RIM)
    poly(draw, [(96, 40), (124, 108), (68, 108)], INK)


def glow(draw):
    for i in range(8):
        ang = i * math.pi / 4
        x1 = 96 + 28 * math.cos(ang)
        y1 = 96 + 28 * math.sin(ang)
        x2 = 96 + 62 * math.cos(ang)
        y2 = 96 + 62 * math.sin(ang)
        line(draw, (x1, y1), (x2, y2), RIM if i % 2 else INK, 16)
    disc(draw, (68, 68, 124, 124), INK)


EMBERS = [
    ("brazier", brazier),
    ("coal", coal),
    ("forge", forge),
    ("tongs", tongs),
    ("bellows", bellows),
    ("cauldron", cauldron),
    ("smoke", smoke),
    ("hearth", hearth),
    ("poker", poker),
    ("kindling", kindling),
    ("skillet", skillet),
    ("chimney", chimney),
    ("bonfire", bonfire),
    ("wick", wick),
    ("spark", spark),
    ("ash", ash),
    ("log", log),
    ("kettle", kettle),
    ("spit", spit),
    ("grill", grill),
    ("pot", pot),
    ("match", match),
    ("flare", flare),
    ("cinder", cinder),
    ("oven", oven),
    ("brand", brand),
    ("pyre", pyre),
    ("glow", glow),
]


def sail(draw):
    poly(draw, [(96, 32), (150, 140), (96, 112)], INK)
    poly(draw, [(96, 44), (48, 140), (96, 112)], RIM)
    bar(draw, (88, 28, 104, 164), INK)


def hull(draw):
    poly(draw, [(28, 100), (164, 100), (144, 148), (48, 148)], INK)
    rr(draw, (68, 64, 124, 104), 6, RIM)
    bar(draw, (88, 40, 104, 68), INK)


def wheel(draw):
    draw.ellipse((44, 44, 148, 148), outline=INK, width=16)
    line(draw, (96, 52), (96, 140), INK, 16)
    line(draw, (52, 96), (140, 96), INK, 16)
    line(draw, (64, 64), (128, 128), RIM, 14)
    line(draw, (128, 64), (64, 128), RIM, 14)
    disc(draw, (80, 80, 112, 112), INK)


def sextant(draw):
    draw.arc((40, 64, 152, 168), 200, 340, fill=INK, width=18)
    line(draw, (96, 120), (56, 64), RIM, 16)
    disc(draw, (78, 102, 114, 138), INK)


def oar(draw):
    bar(draw, (28, 92, 116, 112), INK)
    poly(draw, [(104, 64), (164, 76), (164, 128), (104, 140)], RIM)


def dock(draw):
    bar(draw, (32, 84, 160, 108), INK)
    bar(draw, (48, 108, 72, 160), RIM)
    bar(draw, (120, 108, 144, 160), RIM)
    bar(draw, (84, 52, 108, 88), INK)


def buoy(draw):
    rr(draw, (64, 32, 128, 156), 28, INK)
    bar(draw, (64, 72, 128, 96), RIM)
    bar(draw, (64, 116, 128, 136), RIM)
    bar(draw, (86, 20, 106, 44), RIM)


def net(draw):
    bar(draw, (48, 48, 144, 66), INK)
    bar(draw, (48, 126, 144, 144), INK)
    bar(draw, (48, 48, 66, 144), INK)
    bar(draw, (126, 48, 144, 144), INK)
    bar(draw, (86, 48, 106, 144), RIM)
    bar(draw, (48, 86, 144, 106), RIM)


def gaff(draw):
    bar(draw, (84, 48, 104, 164), INK)
    poly(draw, [(104, 48), (152, 36), (148, 64), (104, 80)], RIM)


def island(draw):
    disc(draw, (36, 100, 156, 156), INK)
    bar(draw, (88, 56, 106, 112), RIM)
    poly(draw, [(96, 36), (140, 80), (96, 72)], INK)
    poly(draw, [(96, 44), (56, 88), (96, 76)], RIM)


def lighthouse(draw):
    poly(draw, [(76, 148), (116, 148), (108, 64), (84, 64)], INK)
    bar(draw, (72, 48, 120, 72), RIM)
    disc(draw, (84, 28, 108, 52), INK)
    bar(draw, (60, 144, 132, 164), RIM)
    bar(draw, (88, 92, 104, 112), PLATE)


def porthole(draw):
    disc(draw, (40, 40, 152, 152), INK)
    disc(draw, (64, 64, 128, 128), PLATE)
    bar(draw, (64, 88, 128, 104), RIM)
    bar(draw, (88, 64, 104, 128), RIM)


def barrel(draw):
    disc(draw, (48, 40, 144, 152), INK)
    bar(draw, (48, 64, 144, 84), RIM)
    bar(draw, (48, 112, 144, 132), RIM)


def crate(draw):
    rr(draw, (40, 40, 152, 152), 8, INK)
    line(draw, (52, 52), (140, 140), PLATE, 16)
    line(draw, (140, 52), (52, 140), RIM, 16)


def spyglass(draw):
    poly(draw, [(32, 84), (100, 76), (100, 116), (32, 108)], INK)
    rr(draw, (92, 68, 140, 124), 8, RIM)
    rr(draw, (128, 60, 168, 132), 10, INK)


def chart(draw):
    rr(draw, (40, 40, 148, 152), 6, INK)
    poly(draw, [(108, 40), (148, 40), (148, 80)], RIM)
    line(draw, (56, 124), (96, 72), PLATE, 16)
    line(draw, (96, 72), (132, 116), PLATE, 16)
    disc(draw, (112, 96, 140, 124), RIM)


def pennant(draw):
    bar(draw, (48, 28, 68, 164), INK)
    poly(draw, [(68, 40), (156, 80), (68, 120)], RIM)


def mast(draw):
    bar(draw, (88, 28, 104, 164), INK)
    bar(draw, (48, 56, 144, 74), RIM)
    poly(draw, [(104, 74), (148, 120), (104, 120)], INK)


def rudder(draw):
    bar(draw, (84, 32, 108, 156), INK)
    poly(draw, [(108, 72), (156, 88), (156, 148), (108, 140)], RIM)


def cabin(draw):
    poly(draw, [(96, 36), (156, 96), (36, 96)], RIM)
    rr(draw, (48, 92, 144, 156), 6, INK)
    rr(draw, (80, 108, 112, 140), 4, PLATE)


def tide(draw):
    poly(
        draw,
        [(24, 80), (60, 52), (96, 80), (132, 52), (168, 80), (168, 104), (132, 76), (96, 104), (60, 76), (24, 104)],
        INK,
    )
    poly(
        draw,
        [(24, 112), (60, 84), (96, 112), (132, 84), (168, 112), (168, 140), (132, 112), (96, 140), (60, 112), (24, 140)],
        RIM,
    )


def gull(draw):
    poly(draw, [(20, 100), (72, 64), (96, 96), (120, 64), (172, 100), (120, 88), (96, 108), (72, 88)], INK)
    poly(draw, [(80, 100), (96, 140), (112, 100)], RIM)


def shell(draw):
    poly(draw, [(36, 120), (96, 40), (156, 120), (96, 156)], INK)
    line(draw, (96, 56), (96, 144), PLATE, 14)
    line(draw, (70, 72), (78, 140), RIM, 14)
    line(draw, (122, 72), (114, 140), RIM, 14)


def pearl(draw):
    draw.pieslice((24, 40, 168, 140), 180, 360, fill=INK)
    draw.pieslice((24, 72, 168, 176), 0, 180, fill=RIM)
    disc(draw, (68, 72, 124, 128), INK)


def bottle(draw):
    rr(draw, (78, 28, 114, 72), 8, INK)
    poly(draw, [(64, 68), (128, 68), (144, 156), (48, 156)], RIM)
    bar(draw, (70, 28, 122, 44), INK)


def pier(draw):
    bar(draw, (28, 72, 164, 100), INK)
    bar(draw, (44, 100, 68, 164), RIM)
    bar(draw, (88, 100, 112, 164), INK)
    bar(draw, (132, 100, 156, 164), RIM)
    bar(draw, (36, 80, 156, 92), PLATE)


def beacon(draw):
    poly(draw, [(78, 156), (114, 156), (106, 72), (86, 72)], INK)
    disc(draw, (72, 40, 120, 84), RIM)
    line(draw, (96, 48), (48, 28), INK, 14)
    line(draw, (96, 48), (144, 28), INK, 14)
    line(draw, (96, 56), (96, 20), RIM, 14)


def tiller(draw):
    bar(draw, (36, 80, 140, 104), INK)
    disc(draw, (120, 68, 164, 116), RIM)
    bar(draw, (72, 104, 96, 160), INK)


VOYAGE = [
    ("sail", sail),
    ("hull", hull),
    ("wheel", wheel),
    ("sextant", sextant),
    ("oar", oar),
    ("dock", dock),
    ("buoy", buoy),
    ("net", net),
    ("gaff", gaff),
    ("island", island),
    ("lighthouse", lighthouse),
    ("porthole", porthole),
    ("barrel", barrel),
    ("crate", crate),
    ("spyglass", spyglass),
    ("chart", chart),
    ("pennant", pennant),
    ("mast", mast),
    ("rudder", rudder),
    ("cabin", cabin),
    ("tide", tide),
    ("gull", gull),
    ("shell", shell),
    ("pearl", pearl),
    ("bottle", bottle),
    ("pier", pier),
    ("beacon", beacon),
    ("tiller", tiller),
]


def save_set(folder, plate_fn, symbols, colors):
    use(*colors)
    out = os.path.join(ROOT, folder)
    os.makedirs(out, exist_ok=True)
    for name, fn in symbols:
        im, draw = blank()
        plate_fn(draw)
        fn(draw)
        im.save(os.path.join(out, name + ".png"))
    print(folder, len(symbols))


def main():
    save_set(
        "embers",
        plate_embers,
        EMBERS,
        ((118, 42, 24, 255), (236, 156, 64, 255), (255, 232, 204, 255)),
    )
    save_set(
        "voyage",
        plate_voyage,
        VOYAGE,
        ((18, 44, 82, 255), (126, 178, 214, 255), (232, 244, 252, 255)),
    )


if __name__ == "__main__":
    main()
