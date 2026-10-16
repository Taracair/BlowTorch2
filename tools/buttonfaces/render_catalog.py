#!/usr/bin/env python3
"""Extra button-face icons. Original art, MIT, same as BlowTorch.

Marks, Signals, Relics, and Wards. 192 px, transparent outside the plate.
Glyphs are one shape, optically centered, with strokes thick enough for a
~48 dp tile.
"""

import math
import os
import sys

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


def plate_marks(draw):
    draw.rounded_rectangle((10, 10, SIZE - 10, SIZE - 10), 36, fill=PLATE, outline=RIM, width=10)


def plate_signals(draw):
    draw.ellipse((8, 8, SIZE - 8, SIZE - 8), fill=PLATE, outline=RIM, width=10)


def plate_relics(draw):
    draw.rounded_rectangle((12, 12, SIZE - 12, SIZE - 12), 18, fill=PLATE, outline=RIM, width=10)


def plate_wards(draw):
    def hexagon(radius):
        pts = []
        for i in range(6):
            ang = math.radians(60 * i - 90)
            pts.append((96 + radius * math.cos(ang), 96 + radius * math.sin(ang)))
        return pts

    draw.polygon(hexagon(90), fill=RIM)
    draw.polygon(hexagon(76), fill=PLATE)


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


# --- Marks ---

def helm(draw):
    disc(draw, (48, 36, 144, 120), INK)
    bar(draw, (40, 96, 152, 122), RIM)
    bar(draw, (68, 118, 124, 160), INK)
    bar(draw, (68, 72, 124, 98), PLATE)


def axe(draw):
    draw.pieslice((-8, 36, 152, 156), 270, 90, fill=RIM)
    bar(draw, (36, 24, 96, 168), INK)
    bar(draw, (32, 148, 90, 170), RIM)


def bow(draw):
    draw.arc((32, 28, 148, 168), 270, 90, fill=INK, width=22)
    line(draw, (44, 36), (44, 160), RIM, 16)


def hammer(draw):
    rr(draw, (36, 44, 148, 92), 10, INK)
    bar(draw, (36, 44, 64, 92), RIM)
    bar(draw, (84, 88, 108, 164), INK)


def coin(draw):
    disc(draw, (40, 40, 152, 152), INK)
    disc(draw, (60, 60, 132, 132), PLATE)
    poly(draw, [(96, 72), (120, 96), (96, 120), (72, 96)], RIM)


def crown(draw):
    poly(draw, [(36, 136), (48, 60), (74, 100), (96, 40), (118, 100), (144, 60), (156, 136)], INK)
    bar(draw, (36, 120, 156, 150), RIM)


def chest(draw):
    rr(draw, (40, 78, 152, 158), 8, INK)
    rr(draw, (40, 44, 152, 96), 16, RIM)
    disc(draw, (80, 100, 112, 132), PLATE)


def flag(draw):
    bar(draw, (48, 32, 70, 164), INK)
    poly(draw, [(70, 40), (152, 72), (70, 112)], RIM)


def torch(draw):
    bar(draw, (84, 96, 108, 164), INK)
    poly(draw, [(96, 28), (140, 108), (52, 108)], RIM)
    disc(draw, (76, 72, 116, 112), INK)


def mug(draw):
    rr(draw, (40, 56, 124, 156), 18, INK)
    draw.arc((100, 72, 164, 136), 280, 80, fill=RIM, width=18)
    bar(draw, (56, 72, 112, 92), PLATE)


def book(draw):
    rr(draw, (40, 36, 152, 156), 8, INK)
    bar(draw, (86, 36, 108, 156), RIM)
    bar(draw, (116, 36, 136, 96), RIM)


def gem(draw):
    poly(draw, [(96, 32), (148, 78), (96, 160), (44, 78)], INK)
    poly(draw, [(96, 32), (148, 78), (96, 92), (44, 78)], RIM)


def horn(draw):
    disc(draw, (16, 108, 92, 172), RIM)
    draw.arc((28, 20, 172, 164), 130, 340, fill=INK, width=36)


def rope(draw):
    draw.ellipse((44, 28, 148, 92), outline=INK, width=18)
    draw.ellipse((44, 64, 148, 128), outline=RIM, width=18)
    draw.ellipse((44, 100, 148, 164), outline=INK, width=18)


def arrow(draw):
    bar(draw, (84, 78, 108, 164), INK)
    poly(draw, [(96, 28), (148, 92), (44, 92)], RIM)


def band(draw):
    draw.ellipse((48, 56, 144, 148), outline=INK, width=22)
    disc(draw, (108, 86, 140, 118), RIM)


def mapfold(draw):
    poly(
        draw,
        [(36, 52), (72, 40), (96, 60), (120, 40), (156, 52), (156, 144), (120, 128), (96, 152), (72, 128), (36, 144)],
        INK,
    )
    bar(draw, (88, 52, 104, 152), PLATE)


def anvil(draw):
    poly(draw, [(24, 78), (52, 44), (152, 44), (160, 84), (132, 100), (44, 100)], INK)
    poly(draw, [(60, 100), (128, 100), (144, 128), (48, 128)], RIM)
    bar(draw, (76, 128, 116, 164), INK)


MARKS = [
    ("helm", "Helm", helm),
    ("axe", "Axe", axe),
    ("bow", "Bow", bow),
    ("hammer", "Hammer", hammer),
    ("coin", "Coin", coin),
    ("crown", "Crown", crown),
    ("chest", "Chest", chest),
    ("flag", "Flag", flag),
    ("torch", "Torch", torch),
    ("mug", "Mug", mug),
    ("book", "Book", book),
    ("gem", "Gem", gem),
    ("horn", "Horn", horn),
    ("rope", "Rope", rope),
    ("arrow", "Arrow", arrow),
    ("band", "Band", band),
    ("mapfold", "Map", mapfold),
    ("anvil", "Anvil", anvil),
]


# --- Signals ---

def eye(draw):
    poly(draw, [(28, 96), (96, 48), (164, 96), (96, 144)], INK)
    disc(draw, (70, 70, 122, 122), PLATE)
    disc(draw, (84, 84, 108, 108), RIM)


def cross(draw):
    bar(draw, (76, 32, 116, 160), INK)
    bar(draw, (40, 72, 152, 112), RIM)


def heart(draw):
    disc(draw, (40, 48, 104, 112), INK)
    disc(draw, (88, 48, 152, 112), INK)
    poly(draw, [(44, 88), (96, 156), (148, 88)], INK)


def flame(draw):
    poly(draw, [(96, 24), (140, 84), (124, 84), (144, 128), (96, 168), (48, 128), (68, 84), (52, 84)], RIM)
    poly(draw, [(96, 72), (120, 116), (96, 152), (72, 116)], INK)


def target(draw):
    disc(draw, (32, 32, 160, 160), INK)
    disc(draw, (56, 56, 136, 136), PLATE)
    disc(draw, (76, 76, 116, 116), RIM)


def bell(draw):
    poly(draw, [(60, 64), (132, 64), (156, 132), (36, 132)], INK)
    rr(draw, (72, 36, 120, 76), 14, RIM)
    bar(draw, (44, 124, 148, 144), RIM)
    disc(draw, (80, 128, 112, 164), RIM)


def mountain(draw):
    poly(draw, [(16, 152), (78, 48), (112, 152)], INK)
    poly(draw, [(72, 152), (132, 64), (176, 152)], RIM)


def tree(draw):
    poly(draw, [(96, 28), (156, 108), (36, 108)], RIM)
    poly(draw, [(96, 68), (148, 140), (44, 140)], INK)
    bar(draw, (82, 132, 110, 166), RIM)


def snow(draw):
    for i in range(6):
        ang = i * math.pi / 3 - math.pi / 2
        x = 96 + 64 * math.cos(ang)
        y = 96 + 64 * math.sin(ang)
        line(draw, (96, 96), (x, y), INK, 16)
    disc(draw, (80, 80, 112, 112), RIM)


def hand(draw):
    rr(draw, (56, 88, 140, 160), 16, INK)
    rr(draw, (60, 44, 80, 104), 8, INK)
    rr(draw, (86, 32, 106, 104), 8, RIM)
    rr(draw, (112, 44, 132, 104), 8, INK)
    rr(draw, (32, 96, 70, 124), 12, RIM)


def lock(draw):
    draw.arc((56, 28, 136, 112), 180, 360, fill=INK, width=18)
    rr(draw, (44, 84, 148, 164), 12, RIM)
    disc(draw, (80, 104, 112, 136), PLATE)
    poly(draw, [(86, 120), (106, 120), (114, 152), (78, 152)], PLATE)


def wave(draw):
    poly(
        draw,
        [(24, 84), (58, 56), (96, 84), (134, 56), (168, 84), (168, 108), (134, 80), (96, 108), (58, 80), (24, 108)],
        INK,
    )
    poly(
        draw,
        [(24, 116), (58, 88), (96, 116), (134, 88), (168, 116), (168, 144), (134, 116), (96, 144), (58, 116), (24, 144)],
        RIM,
    )


def diamond(draw):
    poly(draw, [(96, 28), (160, 96), (96, 164), (32, 96)], INK)
    poly(draw, [(96, 56), (132, 96), (96, 136), (60, 96)], RIM)


def spiral(draw):
    draw.arc((36, 40, 156, 156), 20, 310, fill=INK, width=18)
    draw.arc((64, 68, 128, 128), 200, 100, fill=RIM, width=16)


def comet(draw):
    poly(draw, [(28, 148), (72, 96), (108, 112), (64, 160)], RIM)
    disc(draw, (84, 40, 152, 108), INK)


def leaf(draw):
    poly(draw, [(96, 28), (156, 96), (96, 164), (36, 96)], INK)
    bar(draw, (86, 48, 106, 148), RIM)


def anchor(draw):
    draw.ellipse((70, 28, 122, 80), outline=INK, width=16)
    bar(draw, (86, 72, 106, 140), INK)
    bar(draw, (44, 96, 148, 116), RIM)
    draw.arc((44, 88, 148, 168), 0, 180, fill=INK, width=18)


def fish(draw):
    poly(draw, [(36, 96), (100, 56), (100, 136)], RIM)
    disc(draw, (64, 60, 160, 136), INK)
    disc(draw, (124, 82, 148, 106), PLATE)


def bird(draw):
    poly(draw, [(16, 108), (70, 64), (96, 100), (122, 64), (176, 108), (122, 92), (96, 116), (70, 92)], INK)
    poly(draw, [(78, 108), (96, 156), (114, 108)], RIM)


def house(draw):
    poly(draw, [(96, 32), (164, 96), (28, 96)], RIM)
    rr(draw, (44, 92, 148, 164), 6, INK)
    rr(draw, (80, 112, 116, 164), 6, PLATE)


def path(draw):
    poly(draw, [(68, 164), (96, 32), (124, 164)], INK)
    bar(draw, (84, 68, 108, 92), PLATE)
    bar(draw, (84, 108, 108, 132), RIM)


def plus(draw):
    bar(draw, (76, 32, 116, 160), INK)
    bar(draw, (32, 76, 160, 116), RIM)


SIGNALS = [
    ("eye", "Eye", eye),
    ("cross", "Cross", cross),
    ("heart", "Heart", heart),
    ("flame", "Flame", flame),
    ("target", "Target", target),
    ("bell", "Bell", bell),
    ("mountain", "Mountain", mountain),
    ("tree", "Tree", tree),
    ("snow", "Snow", snow),
    ("hand", "Hand", hand),
    ("lock", "Lock", lock),
    ("wave", "Wave", wave),
    ("diamond", "Diamond", diamond),
    ("spiral", "Spiral", spiral),
    ("comet", "Comet", comet),
    ("leaf", "Leaf", leaf),
    ("anchor", "Anchor", anchor),
    ("fish", "Fish", fish),
    ("bird", "Bird", bird),
    ("house", "House", house),
    ("path", "Path", path),
    ("plus", "Plus", plus),
]


# --- Relics ---

def chalice(draw):
    poly(draw, [(44, 40), (148, 40), (124, 100), (68, 100)], INK)
    bar(draw, (86, 96, 106, 140), RIM)
    bar(draw, (52, 136, 140, 158), INK)


def candle(draw):
    bar(draw, (80, 72, 112, 156), INK)
    poly(draw, [(96, 28), (122, 80), (70, 80)], RIM)
    bar(draw, (64, 144, 128, 164), RIM)


def mask(draw):
    disc(draw, (40, 40, 152, 152), INK)
    disc(draw, (58, 70, 90, 106), PLATE)
    disc(draw, (102, 70, 134, 106), PLATE)
    rr(draw, (74, 114, 118, 136), 8, PLATE)


def quill(draw):
    poly(draw, [(40, 148), (124, 44), (156, 32), (140, 68), (56, 164)], INK)
    poly(draw, [(124, 40), (160, 28), (148, 64)], RIM)


def hourglass(draw):
    poly(draw, [(44, 36), (148, 36), (112, 96), (80, 96)], INK)
    poly(draw, [(44, 156), (148, 156), (112, 96), (80, 96)], RIM)
    bar(draw, (40, 28, 152, 48), INK)
    bar(draw, (40, 144, 152, 164), INK)


def scales(draw):
    bar(draw, (86, 40, 106, 136), INK)
    bar(draw, (36, 48, 156, 68), RIM)
    bar(draw, (48, 68, 68, 90), INK)
    bar(draw, (124, 68, 144, 90), INK)
    disc(draw, (32, 82, 84, 126), INK)
    disc(draw, (108, 82, 160, 126), INK)
    bar(draw, (60, 136, 132, 156), RIM)


def harp(draw):
    bar(draw, (40, 36, 64, 164), INK)
    bar(draw, (40, 140, 152, 164), RIM)
    draw.arc((40, 24, 160, 132), 190, 10, fill=INK, width=18)
    line(draw, (78, 52), (78, 140), RIM, 14)
    line(draw, (104, 44), (104, 140), INK, 14)
    line(draw, (128, 60), (128, 140), RIM, 14)


def drum(draw):
    disc(draw, (40, 56, 152, 148), INK)
    bar(draw, (40, 64, 152, 88), RIM)
    bar(draw, (40, 116, 152, 140), RIM)
    line(draw, (56, 40), (72, 64), INK, 14)
    line(draw, (136, 40), (120, 64), INK, 14)


def spear(draw):
    bar(draw, (86, 80, 106, 168), INK)
    poly(draw, [(96, 24), (140, 96), (52, 96)], RIM)


def staff(draw):
    bar(draw, (86, 64, 106, 168), INK)
    draw.arc((40, 24, 112, 96), 180, 20, fill=RIM, width=20)


def tablet(draw):
    rr(draw, (44, 32, 148, 160), 12, INK)
    bar(draw, (64, 60, 128, 76), PLATE)
    bar(draw, (64, 92, 128, 108), RIM)
    bar(draw, (64, 124, 112, 140), PLATE)


def crystal(draw):
    poly(draw, [(96, 24), (148, 76), (128, 164), (64, 164), (44, 76)], INK)
    poly(draw, [(96, 24), (148, 76), (96, 96), (44, 76)], RIM)


def chain(draw):
    draw.ellipse((60, 24, 132, 84), outline=INK, width=16)
    draw.ellipse((60, 66, 132, 126), outline=RIM, width=16)
    draw.ellipse((60, 108, 132, 168), outline=INK, width=16)


def banner(draw):
    bar(draw, (40, 32, 62, 164), INK)
    rr(draw, (62, 40, 156, 132), 8, RIM)
    bar(draw, (78, 68, 140, 88), INK)


def tent(draw):
    poly(draw, [(96, 32), (164, 160), (28, 160)], INK)
    poly(draw, [(96, 96), (124, 160), (68, 160)], PLATE)
    bar(draw, (78, 28, 114, 48), RIM)


def claw(draw):
    poly(draw, [(32, 44), (64, 32), (82, 136), (48, 144)], INK)
    poly(draw, [(78, 24), (116, 28), (110, 136), (74, 132)], RIM)
    poly(draw, [(128, 36), (162, 48), (140, 140), (106, 136)], INK)
    rr(draw, (36, 128, 156, 164), 10, INK)


def wing(draw):
    poly(draw, [(36, 92), (120, 36), (164, 64), (80, 108)], RIM)
    poly(draw, [(28, 108), (140, 68), (168, 120), (60, 140)], INK)
    poly(draw, [(40, 124), (132, 108), (160, 156), (68, 164)], RIM)


def pipe(draw):
    rr(draw, (28, 108, 120, 140), 14, INK)
    disc(draw, (96, 56, 164, 128), RIM)
    disc(draw, (114, 72, 148, 110), PLATE)


def well(draw):
    disc(draw, (44, 76, 148, 164), INK)
    disc(draw, (68, 96, 124, 148), PLATE)
    poly(draw, [(32, 88), (96, 32), (160, 88), (144, 100), (96, 52), (48, 100)], RIM)


def cart(draw):
    rr(draw, (28, 52, 148, 116), 8, INK)
    disc(draw, (32, 104, 84, 156), RIM)
    disc(draw, (100, 104, 152, 156), RIM)
    disc(draw, (46, 118, 70, 142), PLATE)
    disc(draw, (114, 118, 138, 142), PLATE)


def relicbell(draw):
    poly(draw, [(64, 72), (128, 72), (152, 136), (40, 136)], INK)
    bar(draw, (84, 36, 108, 76), RIM)
    disc(draw, (76, 20, 116, 52), RIM)
    bar(draw, (48, 128, 144, 148), RIM)
    disc(draw, (84, 124, 108, 160), INK)


def nest(draw):
    draw.pieslice((40, 40, 152, 152), 0, 180, fill=INK)
    draw.pieslice((64, 68, 128, 136), 0, 180, fill=PLATE)
    draw.arc((40, 40, 152, 152), 20, 160, fill=RIM, width=18)


def egg(draw):
    disc(draw, (56, 36, 136, 156), INK)
    poly(draw, [(68, 88), (124, 76), (118, 100), (64, 112)], RIM)


def mushroom(draw):
    bar(draw, (78, 96, 114, 160), INK)
    disc(draw, (32, 48, 160, 116), RIM)


def goblet(draw):
    disc(draw, (48, 36, 144, 112), INK)
    bar(draw, (86, 100, 106, 140), RIM)
    bar(draw, (56, 136, 136, 158), INK)


def rune(draw):
    bar(draw, (84, 28, 108, 164), INK)
    poly(draw, [(40, 64), (84, 96), (84, 124), (40, 108)], RIM)
    poly(draw, [(152, 64), (108, 96), (108, 124), (152, 108)], RIM)


def horseshoe(draw):
    draw.arc((40, 32, 152, 160), 200, 340, fill=INK, width=26)
    bar(draw, (40, 108, 74, 164), RIM)
    bar(draw, (118, 108, 152, 164), RIM)


def censer(draw):
    disc(draw, (40, 96, 152, 168), INK)
    bar(draw, (48, 88, 144, 116), RIM)
    disc(draw, (68, 68, 124, 112), RIM)
    disc(draw, (76, 32, 116, 76), INK)


RELICS = [
    ("chalice", "Chalice", chalice),
    ("candle", "Candle", candle),
    ("mask", "Mask", mask),
    ("quill", "Quill", quill),
    ("hourglass", "Hourglass", hourglass),
    ("scales", "Scales", scales),
    ("harp", "Harp", harp),
    ("drum", "Drum", drum),
    ("spear", "Spear", spear),
    ("staff", "Staff", staff),
    ("tablet", "Tablet", tablet),
    ("crystal", "Crystal", crystal),
    ("chain", "Chain", chain),
    ("banner", "Banner", banner),
    ("tent", "Tent", tent),
    ("claw", "Claw", claw),
    ("wing", "Wing", wing),
    ("pipe", "Pipe", pipe),
    ("well", "Well", well),
    ("cart", "Cart", cart),
    ("bell", "Bell", relicbell),
    ("nest", "Nest", nest),
    ("egg", "Egg", egg),
    ("mushroom", "Mushroom", mushroom),
    ("goblet", "Goblet", goblet),
    ("rune", "Rune", rune),
    ("horseshoe", "Horseshoe", horseshoe),
    ("censer", "Censer", censer),
]


# --- Wards ---

def whex(draw):
    def hexagon(radius):
        pts = []
        for i in range(6):
            ang = math.radians(60 * i - 90)
            pts.append((96 + radius * math.cos(ang), 96 + radius * math.sin(ang)))
        return pts

    draw.polygon(hexagon(62), outline=INK, width=16)
    draw.polygon(hexagon(34), fill=RIM)


def knot(draw):
    draw.arc((36, 48, 124, 136), 20, 220, fill=INK, width=20)
    draw.arc((68, 56, 156, 144), 200, 40, fill=RIM, width=20)


def gate(draw):
    bar(draw, (36, 72, 62, 164), INK)
    bar(draw, (130, 72, 156, 164), INK)
    draw.arc((36, 32, 156, 124), 180, 360, fill=RIM, width=22)


def arch(draw):
    draw.arc((36, 36, 156, 156), 180, 360, fill=INK, width=24)
    bar(draw, (36, 92, 60, 164), INK)
    bar(draw, (132, 92, 156, 164), INK)


def tower(draw):
    bar(draw, (64, 68, 128, 164), INK)
    poly(draw, [(48, 72), (96, 24), (144, 72)], RIM)
    rr(draw, (80, 96, 112, 132), 4, PLATE)


def pillar(draw):
    bar(draw, (68, 56, 124, 136), INK)
    bar(draw, (44, 32, 148, 60), RIM)
    bar(draw, (44, 132, 148, 160), RIM)


def bridge(draw):
    bar(draw, (28, 80, 164, 104), RIM)
    draw.arc((28, 88, 100, 156), 180, 360, fill=INK, width=18)
    draw.arc((92, 88, 164, 156), 180, 360, fill=INK, width=18)
    bar(draw, (28, 116, 52, 164), INK)
    bar(draw, (140, 116, 164, 164), INK)


def wall(draw):
    bar(draw, (48, 40, 92, 72), INK)
    bar(draw, (106, 40, 144, 72), RIM)
    bar(draw, (48, 84, 72, 116), RIM)
    bar(draw, (86, 84, 144, 116), INK)
    bar(draw, (48, 128, 108, 148), INK)
    bar(draw, (122, 128, 144, 148), RIM)


def stair(draw):
    bar(draw, (32, 136, 160, 160), INK)
    bar(draw, (52, 112, 160, 136), RIM)
    bar(draw, (72, 88, 160, 112), INK)
    bar(draw, (92, 64, 160, 88), RIM)
    bar(draw, (112, 40, 160, 64), INK)


def seal(draw):
    disc(draw, (40, 40, 152, 152), INK)
    disc(draw, (60, 60, 132, 132), PLATE)
    poly(draw, [(96, 72), (124, 108), (96, 132), (68, 108)], RIM)


def bars(draw):
    bar(draw, (40, 40, 152, 60), INK)
    bar(draw, (40, 140, 152, 160), INK)
    bar(draw, (52, 60, 74, 140), RIM)
    bar(draw, (86, 60, 108, 140), INK)
    bar(draw, (120, 60, 142, 140), RIM)


def chevron(draw):
    poly(draw, [(36, 84), (96, 36), (156, 84), (156, 112), (96, 64), (36, 112)], INK)
    poly(draw, [(36, 116), (96, 68), (156, 116), (156, 148), (96, 100), (36, 148)], RIM)


def wspiral(draw):
    draw.arc((40, 40, 152, 152), 30, 320, fill=INK, width=18)
    draw.arc((66, 66, 126, 126), 210, 80, fill=RIM, width=16)


def window(draw):
    rr(draw, (52, 32, 140, 160), 28, INK)
    rr(draw, (70, 50, 122, 142), 16, PLATE)
    bar(draw, (88, 50, 104, 142), RIM)
    bar(draw, (70, 88, 122, 104), RIM)


def links(draw):
    draw.ellipse((52, 28, 116, 92), outline=INK, width=16)
    draw.ellipse((76, 64, 140, 128), outline=RIM, width=16)
    draw.ellipse((52, 100, 116, 164), outline=INK, width=16)


def crest(draw):
    poly(draw, [(96, 32), (148, 64), (136, 120), (96, 160), (56, 120), (44, 64)], INK)
    bar(draw, (86, 64, 106, 132), PLATE)
    bar(draw, (68, 88, 124, 104), RIM)


def wrune(draw):
    poly(draw, [(96, 28), (120, 88), (96, 164), (72, 88)], INK)
    bar(draw, (40, 80, 152, 100), RIM)


def wring(draw):
    draw.ellipse((40, 40, 152, 152), outline=INK, width=16)
    draw.ellipse((68, 68, 124, 124), outline=RIM, width=16)


def wdiamond(draw):
    poly(draw, [(96, 32), (160, 96), (96, 160), (32, 96)], RIM)
    poly(draw, [(96, 58), (132, 96), (96, 134), (60, 96)], INK)


def nova(draw):
    for i in range(8):
        ang = i * math.pi / 4
        x = 96 + 66 * math.cos(ang)
        y = 96 + 66 * math.sin(ang)
        line(draw, (96, 96), (x, y), INK if i % 2 == 0 else RIM, 16)
    disc(draw, (72, 72, 120, 120), INK)


def spark(draw):
    poly(draw, [(96, 28), (116, 76), (164, 96), (116, 116), (96, 164), (76, 116), (28, 96), (76, 76)], INK)


def keyhole(draw):
    disc(draw, (60, 32, 132, 104), INK)
    poly(draw, [(74, 88), (118, 88), (136, 164), (56, 164)], RIM)
    disc(draw, (78, 50, 114, 86), PLATE)


def north(draw):
    poly(draw, [(96, 28), (140, 96), (96, 164), (52, 96)], INK)
    poly(draw, [(96, 28), (140, 96), (96, 96), (52, 96)], RIM)
    disc(draw, (80, 80, 112, 112), RIM)


def dome(draw):
    draw.pieslice((44, 52, 148, 128), 180, 360, fill=INK)
    bar(draw, (44, 88, 148, 132), RIM)
    rr(draw, (80, 100, 112, 132), 6, PLATE)


def fence(draw):
    bar(draw, (48, 56, 68, 136), INK)
    bar(draw, (86, 40, 106, 136), RIM)
    bar(draw, (124, 56, 144, 136), INK)
    bar(draw, (48, 64, 144, 82), INK)
    bar(draw, (48, 100, 144, 118), RIM)


def post(draw):
    bar(draw, (80, 52, 112, 156), INK)
    disc(draw, (64, 24, 128, 72), RIM)
    bar(draw, (52, 140, 140, 164), INK)


def portal(draw):
    disc(draw, (36, 36, 156, 156), RIM)
    disc(draw, (64, 64, 128, 128), PLATE)
    disc(draw, (80, 80, 112, 112), INK)


def maze(draw):
    bar(draw, (40, 40, 152, 60), INK)
    bar(draw, (40, 40, 64, 124), INK)
    bar(draw, (40, 104, 116, 124), RIM)
    bar(draw, (96, 68, 152, 88), INK)
    bar(draw, (132, 68, 152, 152), RIM)
    bar(draw, (60, 136, 152, 156), INK)


WARDS = [
    ("hex", "Hex", whex),
    ("knot", "Knot", knot),
    ("gate", "Gate", gate),
    ("arch", "Arch", arch),
    ("tower", "Tower", tower),
    ("pillar", "Pillar", pillar),
    ("bridge", "Bridge", bridge),
    ("wall", "Wall", wall),
    ("stair", "Stair", stair),
    ("seal", "Seal", seal),
    ("bars", "Bars", bars),
    ("chevron", "Chevron", chevron),
    ("spiral", "Spiral", wspiral),
    ("window", "Window", window),
    ("links", "Links", links),
    ("crest", "Crest", crest),
    ("rune", "Rune", wrune),
    ("ring", "Ring", wring),
    ("diamond", "Diamond", wdiamond),
    ("nova", "Nova", nova),
    ("spark", "Spark", spark),
    ("keyhole", "Keyhole", keyhole),
    ("north", "North", north),
    ("dome", "Dome", dome),
    ("fence", "Fence", fence),
    ("post", "Post", post),
    ("portal", "Portal", portal),
    ("maze", "Maze", maze),
]


MARKS_KEPT = [
    ("sword", "Sword"),
    ("shield", "Shield"),
    ("compass", "Compass"),
    ("door", "Door"),
    ("scroll", "Scroll"),
    ("flask", "Flask"),
    ("key", "Key"),
    ("pack", "Pack"),
    ("lantern", "Lantern"),
    ("boot", "Boot"),
]
SIGNALS_KEPT = [
    ("star", "Star"),
    ("sun", "Sun"),
    ("moon", "Moon"),
    ("bolt", "Bolt"),
    ("drop", "Drop"),
    ("gear", "Gear"),
]


def save_set(folder, plate_fn, symbols, colors):
    use(*colors)
    out = os.path.join(ROOT, folder)
    os.makedirs(out, exist_ok=True)
    for name, _label, fn in symbols:
        im, draw = blank()
        plate_fn(draw)
        fn(draw)
        im.save(os.path.join(out, name + ".png"))


def lua_rows(pairs):
    lines = []
    for icon_id, label in pairs:
        lines.append('\t\t\t{ id = "%s", label = "%s" },' % (icon_id, label))
    return "\n".join(lines)


def print_lua():
    marks = MARKS_KEPT + [(a, b) for a, b, _c in MARKS]
    signals = SIGNALS_KEPT + [(a, b) for a, b, _c in SIGNALS]
    relics = [(a, b) for a, b, _c in RELICS]
    wards = [(a, b) for a, b, _c in WARDS]
    print("marks", len(marks))
    print("signals", len(signals))
    print("relics", len(relics))
    print("wards", len(wards))
    print(lua_rows(marks))
    print("---")
    print(lua_rows(signals))
    print("---")
    print(lua_rows(relics))
    print("---")
    print(lua_rows(wards))


def main():
    save_set("marks", plate_marks, MARKS, ((36, 48, 68, 255), (196, 165, 116, 255), (244, 230, 200, 255)))
    save_set(
        "signals",
        plate_signals,
        SIGNALS,
        ((22, 58, 54, 255), (126, 196, 176, 255), (236, 246, 240, 255)),
    )
    save_set(
        "relics",
        plate_relics,
        RELICS,
        ((92, 52, 28, 255), (232, 168, 96, 255), (255, 236, 214, 255)),
    )
    save_set(
        "wards",
        plate_wards,
        WARDS,
        ((48, 32, 78, 255), (186, 160, 232, 255), (244, 236, 255, 255)),
    )
    if "--lua" in sys.argv:
        print_lua()


if __name__ == "__main__":
    main()
