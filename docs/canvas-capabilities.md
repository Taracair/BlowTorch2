# What BlowTorch can draw

A short note for server authors, written because the question keeps coming up:
*if I send you something richer than text, what can you actually do with it?*

This is about what is possible, not what is finished. Almost nothing here is
built yet. The point is to say which ideas are cheap, which are expensive, and
which are genuinely out of reach — so nobody designs against the wrong picture.

---

## The one fact that matters

**BlowTorch is not a terminal emulator.** It keeps the text in its own buffer and
draws every character itself onto an Android Canvas.

This cuts both ways.

We do **not** get terminal features for free. There is no cursor, no "move to row
5, column 12", no redrawing part of the screen because the server asked. If your
plan needs a VT100, we are the wrong client.

But because we draw every glyph ourselves, we can decide what a glyph *looks
like*. A client that emulates a terminal is limited to what the terminal can do.
We are limited only by what we are willing to build. Drawing a small picture
instead of a letter is not a fight with the architecture — it is just work.

---

## Cheap: things standing on code that already works

**Coloured cell backgrounds.** We already draw a background behind characters.
Painting a block of cells to make an ASCII map look like a real map costs very
little and needs no images at all. If you want a prettier minimap next to a room
description, this is the shortest path to it.

**Clickable regions in the text.** We already find URLs in the text and let the
player tap them, so locating a span of text and reacting to a tap on it is a
solved problem here. Extending that to regions the *server* declares — tap a room
name, tap an item, tap an exit — is work on an existing pattern, not a new
mechanism. For a map made of text, this gives you a clickable map with no
graphics whatsoever.

**A small icon anchored next to text.** While drawing, we know the exact position
of every character, so putting a marker beside a room name or a player name is
straightforward.

**Bars and gauges in the margin.** Vitals drawn as actual rectangles instead of
`[####----]`, in the space beside the text.

## Moderate: real work, no obstacle in principle

**A picture instead of a character — tile graphics.** The client holds a tileset
in advance and the server sends a marker in the text stream saying which tile to
draw. It fits us: we choose what each cell looks like at draw time.

Two caveats worth knowing early. Tiles line up in columns only with a monospaced
font — with a proportional font, a grid does not exist. And the drawing routine
is the client's hottest code path; a per-cell image lookup has to be measured
before anyone calls it free. We have spent real time optimising scrolling and
would not want to give it back.

**A picture in its own window.** Floating windows already exist in BlowTorch —
the map and the extra text panes work this way. A frame carrying an image is
better served by one of these than by trying to squeeze a picture between lines
of text: no interaction with text layout, no scrolling problems, the player can
move and resize it. This is where any image a server sends should end up.

**A picture between lines of text.** Possible, but the most expensive option
here, because a line with its own height touches layout and scrolling. Worth
doing only if a floating window genuinely will not serve.

## Out of reach

**Cursor addressing and partial screen redraw.** Our model is a buffer of lines,
not a grid of cells with an addressable cursor. "Draw at row 5, column 12" has no
meaning here without inventing a whole layer that does not exist. If a design
depends on the server steering a cursor, it will not map onto this client.

**Anything assuming a fixed character grid.** Related to the above: the text is
stored as lines of styled runs, not as a rectangle of cells.

---

## What this means for a server author

Ask for **content**, not for **cursor movements**. Anything shaped like "here is
a thing, show it somewhere sensible" fits us well: a tile id, an image, a
clickable span, a colour for a region. Anything shaped like "put this character
at this coordinate" does not.

If you are choosing where to start, the two cheapest things that would look
immediately different are **coloured cell backgrounds** and **server-declared
clickable spans**. Both stand on code that already runs today, and neither needs
you to ship a single image.

And if you want to try something, say so. Most of the cost above is deciding what
the protocol should look like, not the drawing.
