# BlowTorch User Manual

Source of truth for in-app **Help**. Keep this file in sync with
`BTLib/res/raw/user_manual.txt` (packaged into the Help dialog).

## Before you start

Because BlowTorch has a lot of features and tries to cover what many different
players need, this guide (and the Starter Tutorial) may occasionally be
slightly out of date in a few places. When that happens, the app itself — what
it shows on screen — is the source of truth. Please report mistakes on
[GitHub Issues](https://github.com/Taracair/BlowTorch2/issues).

## Encrypted connections (TLS)

**Use TLS (encrypted)** is a checkbox on each world, in the same editor as its
host and port (add or edit a world from the launcher).

Turn it on only when the world offers a TLS port. That is usually a *different
port number* from the plain one — a world might take plain connections on 4000
and TLS on 4443 — so turning the checkbox on without changing the port normally
just fails.

When it connects, the game window says what you got:

    TLS: TLSv1.3, cipher TLS_AES_128_GCM_SHA256

If that line is not there, the connection is not encrypted. That is the way to
check, rather than trusting the checkbox.

**Self-signed certificates are refused.** Some MUDs use them. The connection
fails with a message saying so rather than connecting anyway, because a
certificate nobody can vouch for gives you the padlock without the protection.
If a world only offers a self-signed certificate, leave TLS off for it — you are
no worse off than before, and the client is not pretending otherwise.

The certificate's host name is checked too, so a valid certificate for some
other site will not be accepted for this one.

Compression (MCCP) works normally with TLS on: encryption sits underneath it.

## Suggestions (`.suggest on`)

    .suggest on | off | lines N | 1..8
    .suggest where floating | bar | off
    .suggest opacity N | persist on | off
    .suggest loose on | off | ghost on | off
    .suggest rank on | off | pairs on | off

(`.complete` is the same command under its old name and still works.)

Type two letters of something the game has just said and it appears on a strip
above the input bar. Tap it and it goes in, correctly spaced.

    The game says:  A grizzled cave troll lumbers in.
    You type:       k gri
    The strip:      grizzled
    Tap it:         k grizzled

**Taking one without tapping it.** Each chip is numbered, and `.suggest 3`
takes the third. That is there so a super button over the keyboard can hold
`.suggest 1`, `.suggest 2` and so on — you pick a suggestion without your
thumb ever leaving the keys. Aliases and triggers can use it the same way.

Note that **typing `.suggest 3` into the input bar cannot work**, and this is
not a fault: the bar holds the half-typed command the strip is completing, so
typing anything else into it replaces what you were completing and the strip
empties. Put it on a button. Out of range does nothing, because the strip
changes as you type and inserting the wrong word is worse than inserting none.

**Why not just the keyboard.** Gboard completes from an English dictionary and
from what you have typed before, and it cannot see the screen. The words that
are slow to type in a MUD are exactly the ones it will never learn — a mob
called *grizzled*, a player called *Tonkatsu*, an item called *gnarled oaken
staff* — and it will happily correct them into something else. Type `grizz` and
it offers *grid*, *grim*, *grip*.

This completes only from what the world actually sent, newest first, because the
thing that just walked in is nearly always the thing you are about to hit.

Words shorter than four letters are ignored, and so are pure numbers; you need
to have typed at least two letters before anything is offered.

**How far back counts as recent** is measured in lines, not words — the last 300
by default, so "recent" means here what it means on screen. `.suggest lines 80`
narrows it to roughly a screenful; `.suggest lines 0` keeps everything the
session said. Counting words instead would mean a quiet hour of a few lines kept
names from hours ago alive, while one wide room description threw out everything
you were just looking at.

**Whole names, not just the first word.** `.suggest phrases on` — **off by
default** — offers the words that followed as well. After

    A grizzled cave troll lumbers in.

typing `gri` offers `grizzled cave troll` first and plain `grizzled` right under
it, so a three-word mob is one tap instead of three. Take whichever you meant.

Three words at most, and never past the end of a line. There is nothing here
that knows where a name stops and the sentence carries on — that would need a
dictionary of English grammar, which is tens of megabytes for a language MUDs
barely speak — so the cap is what keeps `lumbers` out of the name. Short words
break a phrase rather than being skipped: `a sword of power` offers `sword`, not
`sword power`, because the world never said that.

If a name shows up somewhere else, the phrase follows it: after
`a gnarled iron gate`, `gnar` stops offering `gnarled oaken staff` and starts
offering `gnarled iron gate`.

**Ordered by where you are in the line.** `.suggest rank on` — **off by
default** — uses one thing the app already knows for free: the first word of
every command you send *is* a verb this world takes, and what follows it is a
thing you point commands at. With it on, `ki` at the start of a line offers
`kill` above `kindle` if `kill` is what you type commands with, and the same
`ki` after `kill ` puts the things you have aimed at first.

It only ever **reorders** — nothing is thrown out of the candidates. There is
one place that still costs you something, and it is worth knowing: the bar
holds eight chips. When more than eight words match what you have typed,
changing the order changes *which* eight you see, so a word that was on the bar
can be pushed off the end of it. One more letter narrows the matches and it is
back. It also knows nothing on a world you have just started, and fills up as
you play; and what
you type after `say`, `tell`, `chat` and the like is left out of it, or a
sentence of chat would teach the app that `should` and `think` are things in
the room.

Nothing you type is ever offered back as a suggestion — only what the *world*
said is. Typing a name the world never used does not make it completable. And a
line the world has masked, such as a password, is left out of this entirely.

**What you usually do with that command.** `.suggest pairs on` — **off by
default**, and it needs `rank` above to be on as well. Ranking knows that after
a command word you are naming a *thing*; this knows *which* thing. If you have
killed the troll a dozen times and worn the trophy, then `kill tro` offers
`troll` first and `wear tro` offers `trophy` first — same letters, different
answer, because they are different questions.

It is a count of what you have aimed each command at, kept per world, a few
kilobytes. No grammar and no dictionary: it is a record of how *you* play, not a
claim about English. So it knows nothing on a world you have just started, it
fills up as you play, and it will be wrong the first time you do something new —
which is why it has its own switch. Like `rank`, it only reorders; a word it has
never seen with this command still follows, it does not vanish.

**When you mistype it.** `.suggest loose on` adds a second pass: if the exact
spelling finds nothing, a word whose letters you typed *in order, with gaps* is
offered instead. `grzld` finds `grizzled`. It only ever runs after an exact
match found nothing, so typing accurately never gets you a different answer than
before. The first letter still has to be right, and you need at least four
letters — below that almost every word in the room matches.

**The rest of the word, as you type.** `.suggest ghost on` draws the top
suggestion's remaining letters after the cursor in dimmed type, with a small `1`
marking it as the first suggestion — the same 1 that `.suggest 1` takes.

    You type:   k gri
    You see:    k gri[zzled]¹    ← the bracketed part is dimmed, and not there

**Tap the ghost to take it.** It is a target, not only a hint — the same result
as tapping the first chip or sending `.suggest 1`, without moving your thumb
off the line you are typing.

That "not there" is literal: the ghost is **drawn, never put in the input bar**.
What you send is always exactly what you typed, so there is nothing to strip off
and nothing that can go out by accident.

A forgiven typo gets a ghost too, in the other shape. Its letters have to
change rather than grow, so the whole word is shown behind an arrow, and tapping
it replaces what you typed:

    You type:   k grzld
    You see:    k grzld[ → grizzled]¹

Because the ghost is drawn, it takes part in no measurement — it never makes the
input bar taller or wider. When it does not fit the rest of the line it carries
on at the start of the next line, if the bar already has one; when there is no
next line it is cut short with `…`. The bar still grows with what you actually
type, as it always has.

**Where the chips sit.** One setting, `.suggest where`, with three answers. It
is one setting and not two switches because "no bar, but floating" is not a
thing — picking one place puts the other away.

- `.suggest where floating` (the default) floats them *over* the game text,
  resting on the top edge of the input bar. They cost the layout nothing, so
  nothing moves when they appear or go.
- `.suggest where bar` puts them in a strip below the game window instead. That
  strip takes height while it is showing, so the window shrinks a little every
  time a suggestion appears and grows back when it goes, and the text hops with
  it — which is why floating is the default, and why `persist` below matters
  most here. The strip is still there for anyone who would rather have the chips
  out of the way of the game text.
- `.suggest where off` shows no bar at all. **The suggestions still work**: the
  ghost still draws after the cursor and `.suggest 1`..`8` still picks, so
  `ghost on` with `where off` is completion with nothing on screen but the
  dimmed word you are typing.

(`.suggest overlay on|off` is the old name for the first two and still works.)

**A bar that stays put.** `.suggest persist on` leaves the floating bar up even
when there is nothing to suggest. The chips stop appearing and disappearing
under your thumb, because the bar itself stops moving — the suggestions simply
change inside a thing that is always in the same place.

Empty, it shows only its grip: the six dots at its left end.

- **Tap the grip** to collapse the bar to just that grip, and tap again to open
  it. Useful when a line of the game is underneath it.
- **Drag the grip** to put the bar somewhere else entirely. No holding first —
  take hold of the dots and the bar comes with your finger. It
  stays where you drop it, remembered **per world and per screen rotation** —
  a place that suits a portrait phone is off the side of a landscape one. Drop
  it back near the input bar and it forgets the placement and goes back to
  following the bar, which is how you undo this without an option.
- To get rid of an empty bar altogether, `.suggest persist off` — then it hides
  itself whenever it has nothing to say, as it does by default.

The grip is the handle for both gestures on purpose. The chips themselves scroll
sideways, and a long press inside something that scrolls fights the scrolling
for every touch.

Without `persist`, the chips no longer blink either: an empty panel waits a
moment before it goes, because typing walks through prefixes that match nothing
on the way to one that does.

`.suggest opacity 40` makes them see-through enough to read the line behind
them. Only the backing fades — the words stay fully readable at every setting,
because a suggestion you have to squint at is worse than none. Anything from 10
to 100.

Off by default, and while off the text is not sent to the completer at all, so it
costs nothing. Both settings are also under **Options → Input**, and both are
saved with the profile. The vocabulary is forgotten when you connect, so one
world's mob names are never offered in another.

## Prompt on its own bar (`.prompt on`)

    .prompt on | off | (no argument = say which, and how many were seen)

Puts the world's prompt on a thin bar just above the input line, and stops it
repeating down the screen. On a phone, a prompt printed on every line is eating
a large share of what you can see.

    [HP 450/500 EN 300/300] >     ← pinned here, always current
    ─────────────────────────
    (your input bar)

**How it knows which line is the prompt** — no pattern, no guessing at its
shape. A prompt is a line the world never finishes: no newline follows it, which
is why your cursor sits on it. The client already holds an unfinished line back
so that a trigger cannot cut one in half, so it knows exactly which line that is.
Where a world marks its prompts with `IAC GA`, the bar updates the instant the
prompt arrives.

**If the bar stays empty**, `.prompt` on its own prints "prompts seen: N". Zero
after a while of play is the answer, not a fault: that world sends no prompt.
Many MOOs do not, and nothing can be pinned that was never sent. The count is
kept whether the bar is on or off, and starts again each time you connect.

Off by default, because it changes where text appears. Also under **Options →
Input**, and saved with the profile.

## Dot commands

Lines that start with a single `.` are handled by BlowTorch when “process period”
is enabled (default). Type `..` alone to toggle that processing on or off.
Prefix a server command with `..` to send a leading `.` to the game without
running a client command (e.g. `..look` sends `.look`).

Aliases that share a simple name with a command win when you type `.name newtext`
(that changes the alias’s replacement text). Plugins may register additional
commands via `RegisterSpecialCommand`; those appear only while the plugin is
loaded.

Registrations live in `Connection` (built-ins) and Lua
`RegisterSpecialCommand(...)` (plugins).

## Repeating a command (`#5 north`)

A line that starts with `#` and a number sends the rest of the line that many
times:

    #5 north            walks five rooms north
    #3 kick troll       kicks three times
    #4 get all from bag

It works wherever a line is sent — typed, on a button, and in each segment of a
`;` list, so `stand;#3 kick troll;sit` is stand, three kicks, sit.

The multiplier counts **what you typed**, not what it expanded into. With an
alias `kk` → `kill $1`, typing `#3 kk troll` sends `kill troll` three times.

**It does not work inside an alias's replacement text.** An alias whose text is
`#3 kick troll` sends that line to the game as it stands. The multiplier is read
once, on the line you send, before aliases are expanded — so put the `#` in
front of the alias (`#3 kk troll`), not inside it.

**No pause between them.** All the copies go out at once, exactly as if you had
typed `north;north;north;north;north`. This is not a way to pace commands — for
that, use a timer.

**Limit: 1 to 100.** Anything outside that is refused and the line is left
exactly as you typed it, with a red note saying so. `#500 north` is nearly
always a slip, and a world may read the flood as an attack.

**Worlds that use `#` themselves.** Two hashes send one literal hash and skip
the repeat, the same way `..` sends a literal dot: `##5 north` reaches the game
as `#5 north`. A `#` that is not a number followed by a space is never touched,
so `#help` and `say cost is #3 gold` go out unchanged.

## Aliases and triggers (patterns / `$1`)

BlowTorch does not use TinTin-style `%1` wildcards. Patterns are **regular
expressions** (Java regex). Captured pieces go into `$1`, `$2`, … in the
replacement or trigger action.

### Aliases

Open the alias editor from the session Options / editors list.

- **Replace** = pattern to match what you type  
- **With** = text to send (can include `$1`, `$2`, …)  
- Optional **start of line (^)** / **end of line ($)** checkboxes

Examples:

    `c`
        With: `cast`
        You type: `c fireball`
        Sent: (word alias; see below)

    `^cast (.+)$`
        With: `c $1`
        You type: `cast fireball`
        Sent: `c fireball`

    `^kill (.+)$`
        With: `k $1`
        You type: `kill goblin`
        Sent: `k goblin`

Notes:

- Without `^`/`$`, BlowTorch wraps the pattern in word boundaries and uses
  normal regex `$n` groups in **With**.
- With **both** `^` and `$`, `$1` is the first `(…)` group in the pattern
  (example above).
- With **only** `^` (no trailing `$`), the line is split on spaces: `$0` is
  the first word, `$1` the next, and so on.

**Changing an alias from the input bar:** for a simple word name (letters,
digits, `_`), type:

```
.name new replacement text
```

That updates the **With** field only. Example: alias key `c`, then
`.c cast 'fireball'` sets the replacement. Patterns with spaces or regex
(like `^cast (.+)$`) must be edited in the alias dialog — the `.name …`
shortcut only works for simple `\w+` keys.

**Local echo (per alias):** the alias editor has a **Local echo** spinner:

- **Use client setting** — follow Options → Service → Local Echo? (default)
- **Always show** — echo this alias’s expanded text even when Local Echo is off;
  also shows `[name=>…]` when a trigger/command updates the alias via `.name …`
- **Always hide** — suppress expansion echo and `.name` update echo even when
  Local Echo / Echo Alias Updates? are on

For **Use client setting**, `.name` update echoes also need **Options → Service →
Echo Alias Updates?** (default on).

Telnet password masking still wins: while the server holds ECHO, nothing is
echoed, including Always show. The typed shortcut is never what appears —
you see (or hide) the **expanded** command, same as today.

### Triggers

In the trigger editor:

- **Literal?** on → match the pattern as plain text (no regex)  
- **Literal?** off → pattern is a regular expression  
- **Group** → optional label (e.g. `combat`); blank = ungrouped. Use the
  **Group** dropdown (existing names) or type a new name below it. The Triggers
  list shows `[group]`, sorts by group, and has **Plugin** / **Group** spinners
  under search (All / Main / plugins, and All groups / (default) / named).
  Options (=) is Enable/Disable all for the current filter.
- **Conditions** → extra gate after the pattern matches — not a substitute
  for the pattern. Optional AND/OR list checked before responders. Empty =
  always fire. Types: Trigger enabled/disabled (pick another trigger;
  `plugin:name` ok), Alias enabled/disabled, Alias replacement equals,
  Variable equals/exists. Set vars with the **Set Variable** responder or
  Lua `SetVariable` / `GetVariable` / `UnsetVariable` (session only, not
  persisted).

In regex mode you can capture with `(…)` and use `$1`, `$2`, … in Ack,
Replace, Toast, Notification, Speak, Set Variable text, and similar actions.

### Speak Out Loud

The **Speak Out Loud** action says the message with the phone's own voice — for
the line you must not miss while you are looking at something else. A tell, a
warning, a health threshold.

    `(\w+) tells you`
        Action: Speak Out Loud, say: `$1 is talking to you`

It uses the speech engine your phone already has, so it adds nothing to the size
of the app and uses whatever voice and language the system is set to. Phones
with no engine installed simply stay quiet.

**Say this at once, cutting off the previous line** decides what happens when
two things want to be said in the same second. Off, they queue and are read in
order — right for a tell, which is still worth hearing a moment later. On, this
one stops whatever is mid-sentence and is read straight away — right for
anything that is only true *now*. "You are bleeding" read out fifteen seconds
late, after four misses have been announced, is worse than not read at all.

**▶ Say it now** reads the message aloud there and then, so you can hear it
before you rely on it. Nothing is spoken when you press Done — the phone talking
by itself because someone closed a dialog is not a thing anyone wants in public.

**?** explains what to check when it stays silent, and opens Android's own
text-to-speech settings, where voices are installed.

Either way the speech never runs far behind the screen. At most a few lines wait
their turn; past that the backlog is dropped and the newest line is read
instead, because speech describing a fight that has already ended helps nobody.
The same line repeated within a second and a half is only said once.

It speaks whether or not the game window is in front, which is the point of an
alert.

**Quiet while you type** (Options → Input) is **off** by default. Turn it on and
a speaking trigger drops anything it would have said between the first letter of
a command and sending it — dropped, not held back, because a backlog let loose
the moment you press Send would read you a fight that has already moved on. It
does **not** cut short a line that is already being spoken, so a sentence that
started before you touched the keyboard finishes. Worth it if you write long
tells while a chatty trigger reads the screen at you. Leave it off if your
speaking triggers are alerts: you type most in a fight, and that is the moment
this makes the phone go quiet.

Timers have the same action, so a timer can say "potion ready" instead of only
printing it.

Examples (Literal off):

    `You hit (.+) for (\d+)`
        Action text: `emote crushed $1 ($2 dmg)`
        Meaning: Name → `$1`, damage → `$2`

    `A (.+) appears`
        Action text: `kill $1`
        Meaning: Auto-target the thing that appeared

**Patterns across several lines.** A trigger can match a block, not just one
line. Write `\n` where the line break is:

    Pattern        You see (.+) here\.\nIt looks (\w+)
    Meaning        thing → $1, condition → $2, from two different lines

Two rules worth knowing:

- **`.` never crosses a line break.** `.+` stops at the end of its line, so a
  block pattern has to say `\n` for every break it spans. This is what keeps a
  greedy pattern from swallowing your whole screen.
- **`^` and `$` bind to each line**, not to the block, which is the readable way
  to write one:

      ^\+-+\+$\n^\| (.+) \|$\n^\+-+\+$
      matches a three-line box and captures what is inside it

A **Gag** on a multi-line pattern removes the whole block, not just its first
line — one trigger to hide a five-line advert. With **Send to window** set, the
whole block is forwarded there, in the order it arrived.

Colour still marks the first line of a match; colouring a whole block is not
done yet.

**Sample — fire only if another trigger is enabled:** create trigger
`combat_mode` (any pattern; leave it disabled until you want the mode on). On
a second trigger, under **Conditions**, Add → Trigger enabled → pick
`combat_mode`. Responders on the second trigger run only while `combat_mode`
is enabled (`.trigger on combat_mode`).

**Using an alias in the pattern.** Type an alias's **name on its own** in the
pattern box and the trigger watches for that alias's *text* instead of the name.
Edit the alias later and every trigger using it follows at once.

    Alias   _tappable1 → circuit
    Pattern `_tappable1`
    Matches the word `circuit` in the game text

To use one **inside a longer pattern**, write `$alias{name}`:

    Pattern `You see a $alias{_tappable1} here\.`
    Matches `You see a circuit here.`

Both work in Literal and regex mode — in regex mode the alias's text is pasted
in as regex, in Literal mode as plain text. The braces are required in the
second form, so `$1` is still a capture and never an alias. The preview under
the pattern box always names the alias it found and the text it will watch for.

**Changing the alias updates the triggers straight away** — whether you edit it
in the Aliases dialog or set its text from the input bar with `.name newtext`.
Nothing has to be reopened and no new line has to arrive: the lines already on
screen are re-marked, so a tappable word loses its frame on the old text and
gains it on the new one where it stands.

**Four aliases cannot be used.** The pattern is then left exactly as you wrote
it, so the trigger visibly does not fire rather than quietly watching for
something else. The preview says which of the four it was:

1. **No alias of that name.**
2. **The alias is several commands** — `sip health;stand`. That is not one
   piece of text the game can print.
3. **The alias uses `$1`-style captures from what you type** — `get $1 from
   bag`. A trigger has nothing to fill those from.
4. **The alias names another alias.** One level only, so a pair of aliases
   naming each other cannot loop.

A **disabled** alias still gives its text: disabling stops it expanding what you
*type*, and the trigger is only borrowing the words.

**If you want the name itself as text:** only a pattern that is *exactly* the
name is replaced. Turn **Literal?** off and write `^name$` and it is a pattern of
its own again.

The **?** button says all of this on the phone. There is one on each of the
Aliases, Triggers and Timers lists — what that kind of thing is for, with
examples — and one beside Done in each of the three editors, for the fields in
front of you. (**More** on a list is its actions menu, not help.)

**Trying a trigger without the game:** `.note some text` prints a line into the
game window and sends nothing to the server, so a colour, a gag or a tappable
word can be checked on a line you wrote yourself.

**GMCP note:** a **literal** trigger whose pattern starts with `%` (default
GMCP character) is a GMCP hook (`%module.path`), not a line wildcard. See
GMCP below.

Options → Service → **Regular Expression Warning?** controls the reminder
dialog when you turn Literal off.

### Aliases and Timers list filters

Triggers, Aliases, and Timers lists show **Plugin** (All / Main / plugins) next
to search. Triggers and Timers also show **Group** (All groups / (default) /
named). Changing a spinner rebuilds the list and shows a short toast.
Enable/Disable all (Triggers/Aliases) stays under Options (`=`).

Timers support an optional **Group** in the timer editor (same idea as
triggers): list subtitle `[group]`, sort by group, XML `group` attribute.

Timers also support **Conditions** in the timer editor — an extra gate when
the timer fires (same AND/OR types as triggers). Empty = always fire
responders. Types: Trigger enabled/disabled, Alias enabled/disabled, Alias
replacement equals, Variable equals/exists. Set vars with the **Set
Variable** responder or Lua `SetVariable` / `GetVariable` / `UnsetVariable`
(session only). Use `${name}` in alias or action text — variables are not
typed into the trigger pattern.

### Making a trigger make a noise

Three ways, and they answer different questions.

**Play a sound.** Give the trigger a **Play a Sound** action and it plays a
short sound file. In a fight this is the one you want: a ping is over in a fifth
of a second where a spoken sentence takes two, and a MUD can print six lines a
second. Each trigger carries its **own** sound, so a tell and a critical hit do
not have to sound alike.

    Pattern:  ^\w+ tells you
    Action:   Play a Sound → Soft chime

Where the sound comes from, in the order the picker offers them:

- **The five sounds that ship with BlowTorch.** These can never go missing and
  need no permissions. Start here.
- **Your own files, from `/BlowTorch/sounds` on the phone's shared storage.**
  Drop `.wav`, `.ogg`, `.mp3` or `.m4a` files in that folder and they appear in
  the list. The folder is created the first time you open the picker.
- **Anything else on the phone,** through *Pick from storage*.

**Keep your own sounds in that folder and leave them there.** A sound of yours is
remembered by *where it is* — the app does not copy it inside itself, so it stays
your file and costs the app nothing to carry. The price is that moving or
deleting it makes the trigger go quiet. That case is not passed over in silence:
the action's editor shows **MISSING** next to the name and says where the file
should be, and the error log records it once. The fix is on the same screen —
put the file back, or open the action and pick another sound.

**Which volume it uses.** By default the **media** volume — the game-and-video
one your phone's side buttons reach for. That is a decision with a story: the
first build played on the *notification* volume, which follows the ringer, so a
silenced phone meant silent triggers and no clue why. Nobody turns their ringer
on for a game.

    .sound stream media | notification | alarm
    .sound warn on | off
    .sound                       (what it is set to now)

`alarm` is the loudest and usually gets through Do Not Disturb — for the one
trigger you must not miss. `notification` is still there if you want trigger
sounds to follow the ringer switch along with everything else.

And because a volume turned to zero has no symptom at all — the trigger fires,
the sound plays, nothing comes out — the app says so: a short message, at most
one every thirty seconds, naming the volume to turn up. `.sound warn off` if you
would rather it did not.

Two numbers on that editor:

- **Volume %** — how loud, 0 to 100. It plays on the notification stream, so a
  phone on silent stays silent.
- **Gap (ms)** — the shortest time between two of *this* trigger's sounds,
  250 ms by default. It stops a trigger that matches every line from turning
  into a buzz. `0` turns it off. Each trigger counts its own gap, so one noisy
  trigger never silences another.

Timers can play a sound too — same action, same editor.

**Speak it.** Give the trigger a **Speak** action and it says the line out loud.
Good for something you need the words of — a tell, a name, a number. It runs in
the connection service, so it is heard with the game in the background and the
screen off. Speech is queued three deep and the newest wins, so a fight does not
put you a minute behind; and it stays quiet from the first letter of a command
until you send it, unless you turn that off in Options → Input.

**Ring the bell.** Give the trigger a **Script** action of `.dobell` and it fires
the bell reaction — whichever of vibrate, notification and the on-screen bell are
turned on in **Options → Bell**. Better than speech in combat for one reason:
a buzz is over in a moment and a sentence is not, and a MUD can print six lines
a second.

    Pattern:  ^\w+ tells you
    Action:   Script → .dobell

If nothing happens, type `.dobell` by hand: with every bell reaction turned off
it now says so and names the three switches, instead of leaving you guessing
whether the trigger fired. Only **Vibrate** is on by default, and a phone in
silent mode will not buzz.

What the bell cannot do: it is **one reaction for the whole profile**, so every
trigger that rings it sounds the same, and it plays the system notification
sound rather than a file of your choosing. That is what **Play a Sound** above
is for; the bell is still the quickest way to get a buzz out of a trigger
without choosing anything.

## Recipes

Worked examples. Each one is a complete thing you can build; the field names
match what you see in the editors.

### 1. A shortcut that takes an argument

**Want:** type `kk goblin`, send `kill goblin`.

Options → Aliases → new:

    Replace   `kk (.+)`
    With      `kill $1`

The `(.+)` captures what you typed after `kk`, and `$1` puts it back. **The
pattern must contain a group** — a bare `kk` has nothing to capture, and `$1`
would be sent literally.

### 2. Remember something the game said, use it later

**Want:** the game names a target; you attack it without retyping the name.

This is the one thing aliases cannot do alone: an alias only sees what *you*
type. A trigger sees what the *game* prints. A session variable joins them.

**Trigger** — Options → Triggers → new:

    Pattern    `A plush suede (\w+) sits against the wall\.`
    Literal?   **off** (this is a regex)
    Action     **Set Variable**, name `target`, value `$1`

**Alias** — Options → Aliases → new:

    Replace   `att`
    With      `kill ${target}`

Walk into the room, then type `att`. The client sends `kill recliner`.

Braces are required: `${target}` is a variable, `$1` is a capture. An unset
variable is left written as `${target}` rather than vanishing, so you can see
what went wrong.

**Tip while building one of these:** add a second action to the trigger,
**Ack** with `.note got target=$1`. That prints a line only you can see, so you
know whether the trigger fired before you start blaming the alias.

### 3. Combat mode: a set of triggers that arm and disarm together

**Want:** healing triggers that only run while you are fighting.

1. Give each combat trigger the same **Group**, e.g. `combat`.
2. Make one trigger that spots the fight starting. Its action is **Script**:
   ```lua
   EnableTriggerGroup("combat", true)
   ```
3. Another spots it ending: `EnableTriggerGroup("combat", false)`.

Turn the whole group on or off by hand any time with
`.trigger group on combat` / `.trigger group off combat`.

### 4. A trigger that only fires under a condition

**Want:** auto-eat, but only when a `hungry` flag is set.

On the trigger, under **Conditions**: Add → *Variable equals* → name `hungry`,
value `1`. Empty conditions mean "always fire".

Set the flag from another trigger's **Set Variable** action, or from Lua with
`SetVariable("hungry", "1")`.

Timers take the same conditions, which is how you get "heal every 10s, but only
in combat".

### 5. Two alias sets, one for travel and one for fighting

Aliases have no conditions, but they can be switched:

```lua
EnableAlias("travel_home", false)
EnableAlias("kk", true)
```

Put that in a trigger's Script action. From the input bar the same thing is
`.alias off travel_home` and `.alias on kk`; `.alias list` shows every alias and
whether it is on.

### 6. One button, ten commands

Edit buttons (⋮ → Edit buttons), then edit a tile:

- **Command** — plain tap
- **Swipe** — eight directions: up, down, left, right, and the four corners
  (↖ ↗ ↙ ↘); each can run a different command
- **Hold** — press-and-hold (~0.45 s)

Set **Switch to named button set** on a tile and tapping it swaps the whole pad
— a movement pad that becomes a combat pad. Same as `.loadset combat`.

### 7. Walk a route

```
.run 3n2ew
```

Three north, two east, one west. Add your own direction words under ⋮ →
Speedwalk Directions. Commas insert literal commands:
`.run 2n,open door,n`.

### 8. Put chat in its own window

Options → Window → Extra text windows → **Manage windows…** → add a slot named
`chat`. Then either:

- **GMCP**: tick the modules to route into it, e.g. `Comm.*`.
- **Triggers**: on a matching trigger, add a **Gag** or **Replace** action and
  set **retarget** to `chat`. The line leaves the main window and appears in
  the slot; **Replace** lets you rewrite it first (`$1` works). Lua
  `NoteToWindow("chat", "…")` adds client-only text without touching MUD output.

Each slot keeps its own scrollback while closed and shows what it missed when
you open it.

### 9. Highlight or hide a line

On a trigger:

- **Color** action — recolour the matching line.
- **Gag** action — hide it entirely (optional **retarget** sends the line to
  an extra text window instead of discarding it).
- **Replace** action — swap text in it, `$1` works here too (also has
  **retarget**).

Several actions on one trigger run in order, so you can gag a line *and* print
your own version of it.

### 9b. Make a word in the game text tappable

On a trigger, add the **Tappable Word** action. The trigger's pattern decides
what lights up, and tapping it sends a command.

- **Tappable part** — `0` marks the whole match. `1`–`9` marks only that
  bracketed part of the pattern, which is what you usually want: the pattern
  needs the rest of the line to recognise it. Pattern
  `You see (.+) lying here` with `1` lights up just the thing on the floor.
- **In the command**: `$word` is the text that was tapped, `$0` the whole
  match, `$1`–`$9` the bracketed parts. Pattern `(\w+) drops (\w+)` with
  `get $2` picks up what was dropped.
- **More than one command** — press *Add another command* and a tap opens a
  small menu at the word instead of sending straight away. The first command
  stays at the top of it. Long commands are shortened in the menu with `(...)`;
  the whole command is still what gets sent.
- **Put the word in the input bar instead of sending it** — type the command
  `.kb insert $word` into a command box. Tapping the word then types it into the
  input bar at the cursor, correctly spaced, and sends nothing. Type `k`, tap
  the mob's name, and the bar reads `k grizzled ` waiting for Send. Two taps
  build one command: `k` + *grizzled* + *troll* gives `k grizzled troll `. This
  is the fastest way to name something the game just mentioned without spelling
  it out on a phone keyboard. Put it beside a real command and a tap offers
  both — e.g. `kill $word` and `.kb insert $word`.
- **Tap sends the first command, hold to choose** — off by default, and set per
  action, not once for the whole world. Off, a tap on a word with several
  commands opens the list, which is what the app has always done. On, a tap
  sends the first command straight to the game and *holding* the word opens the
  list instead. Worth turning on for `kill $word` on a mob you fight all day —
  one touch instead of two. Leave it off wherever sending the wrong thing would
  cost you something, because a tap then goes to the game with nothing in
  between. Holding still works on a word with several commands whether this is
  on or not, so the list is never out of reach; sliding your finger off the word
  cancels the hold and scrolls the text as usual.
- **Underline / Bold / Frame** — any combination, or none. Colour is not here:
  put a **Color** action on the same trigger.
- Two Tappable Word actions on one trigger behave as one word that offers both
  sets of commands, and the look comes from the first of them — including
  whether a tap sends.

The word stays tappable for as long as the line is in the buffer, not just at
the moment the trigger fired, and scrolling back does not change that.

**Worked examples.** Literal? is **off** in all of them (these are regexes).

    Pattern        You see (.+) lying here
    Tappable part  1
    Command        get $1
        "You see a rusty sword lying here" — only "a rusty sword" lights up,
        pressing it sends `get a rusty sword`.

    Pattern        (\w+) drops (\w+)
    Tappable part  2
    Commands       get $2
                   kill $1
        "Goblin drops sword" — "sword" lights up; a press offers both
        `get sword` and `kill Goblin`, because the command may use any part of
        the match, not only the part that was pressed.

    Pattern        (\w+) the (\w+) is standing here
    Tappable part  0
    Commands       kill $1
                   look $word
                   consider $1
        Whole match lights up; the menu offers three things to do with it.

    Pattern        \b(\d+) (?:gold|credits)\b
    Tappable part  1
    Command        get $1 gold
        Digits only are pressable; the currency word is context.

    Pattern        \[(\w+)\] (\w+):
    Tappable part  2
    Commands       tell $2
                   ignore $2
        A chat line "[ooc] Fred:" — press the speaker's name to reply.

Things worth knowing when the pattern gets ambitious:

- **A match may cross a colour change.** Matching runs on the whole line, so a
  phrase the MUD colours halfway through still matches; each coloured piece is
  marked in its own colour and pressing either piece does the same thing.
- **Several matches on one line** are each pressable — "You see a sword, a
  shield and a lamp lying here" with pattern `a (\w+)` gives three separate
  words. At most 16 matches per coloured run are marked, so a pattern that
  matches almost everything cannot cover the screen in boxes.
- **A group the pattern does not have** substitutes as empty, not as a literal
  `$7`.
- **Literal? on** matches the pattern as plain text, exactly as it does for
  firing the trigger — `[ 9 | -4 | 1 ]` is those characters, not a regex
  character class.
- **Conditions and groups work as usual**: a tappable trigger that is disabled,
  or whose condition is false, marks nothing.
- **Overlapping triggers**: two different triggers matching the same word each
  mark it; the press uses the last box drawn there. Two Tappable Word actions
  on *one* trigger are merged instead (see above).

### 9c. Tap a name to retarget every button

A command sent by a tappable word takes **the same road as a line you type**:
aliases expand, `.` commands run, `;` splits into several commands. So a tap can
change what your buttons do.

**Set up once.** Options → Aliases → new:

    Replace   `tgt`
    With      `nothing`

**The tappable trigger.** Options → Triggers → new (Literal? off):

    Pattern        (\w+) is standing here
    Action         Tappable Word
    Tappable part  1
    Command        .tgt $1

`.name text` is the built-in "change this alias" command, so tapping a monster's
name rewrites the alias `tgt` to that name — nothing is sent to the game.

**Your buttons.** Give them `kill tgt`, `look tgt`, `throw dagger at tgt`. A
word alias expands anywhere in the line, so every one of them follows whatever
you last tapped.

Variables do the same job with `${target}` instead: use a **Set Variable**
action on the trigger and write `kill ${target}` in the alias. The alias route
is the one a tap can change on its own, without a second trigger.

**Careful:** because a tapped command goes through alias expansion, an alias
whose name is an ordinary word ("sword", "north") will also rewrite what a tap
sends. Name aliases you use this way so they cannot collide — `tgt`, `_it`.

### 9d. One trigger that reads several lines

Write `\n` in the pattern where the line break is. Literal? is **off** for all
of these.

**Hide a whole block.** An advert, a banner, an ASCII box — one trigger instead
of one per line, and no leftover fragments.

    Pattern   ^\+-+\+$\n^\| (.+) \|$\n^\+-+\+$
    Action    Gag
        Removes all three lines. $1 is the text that was inside the box, so a
        Toast or Set Variable on the same trigger can still use it.

**Take two facts from two lines at once.**

    Pattern   You see (.+) here\.\nIt looks (\w+)
    Action    Ack   get $1
        "You see a rusty sword here." / "It looks battered" — $1 is the sword,
        $2 is "battered", and both arrive in one firing. No variable, no second
        trigger waiting for the line after.

**A two-line event.**

    Pattern   You hit (\w+) for \d+\.\n\1 collapses
    Action    Ack   loot corpse
        `\1` refers back to the first capture, so this only fires when the thing
        that collapsed is the thing you hit — not when someone else's kill
        happens to print underneath yours.

**A row under the right heading.**

    Pattern   ^Name +Price$\n^(\w+) +(\d+)$
    Action    Set Variable
        Only matches a row that comes directly under that heading, so you pick
        up the table you meant and not a similar-looking line elsewhere.

**Send a block to another window.**

    Pattern   ^\[quest\] (.+)$\n^  (.+)$
    Action    Gag, Send to window: quests
        Both lines leave the main window together and arrive in the quest
        window in the order they were sent.

**The two rules.** `.` never crosses a line break — `.+` stops at the end of its
line, which is what keeps a greedy pattern from swallowing the screen, and why
every break has to be written out. `^` and `$` bind to each line rather than to
the block, which is what makes the box example above readable.

**Colour** still marks only the first line of a match.

### 10. Start mapping

1. Open the map: ⋮ → **Map**, or `.map open`
2. Switch **Browse** → **Edit**
3. **Nav → Record** on, then walk. Rooms appear as you go.

If your MUD sends GMCP `Room.Info`, turn on **View & sync → GMCP sync** instead
and just walk — nothing to record. `.map find <text>` locates a room,
`.map goto <room>` walks you there.

Maps are per world, saved under `/BlowTorch/maps/`.

### 11. A timer that does something every N seconds

**Want:** sip a health potion every 15 seconds while fighting.

Options → Timers → new:

    Timer Name       `heal`
    Every            `0` h `0` m `15` s
    Repeat           on
    Action           **Ack With** → `drink health`

**Every** is three boxes — hours, minutes, seconds — with quick presets
(`30s`, `1m`, `5m`, `15m`, `1h`) and a running `= 15s` summary under them. The
boxes are added up rather than range-checked, so `90` in the seconds box is the
same as `1m 30s`. Timers are still stored as a total number of seconds.

Control it from the input bar, by name:

```
.timer play heal      start it
.timer pause heal     hold it where it is
.timer reset heal     back to full duration
.timer stop heal      stop and reset
.timer info heal      how long is left
.timer duration heal 30   change how long it runs (whole seconds)
```

Changing the duration — from the input bar or in the editor — does **not** stop
the timer. One that was running keeps running on the new length, starting from
now; one that was stopped stays stopped. Use `.timer stop` to stop it.

Add `silent` as a last word to suppress the toast: `.timer play heal silent`.
Useful when a trigger drives the timer and you do not want a popup each time.

### 12. A timer that only fires in combat

Timers take the same **Conditions** as triggers. On the `heal` timer:

Conditions → Add → *Variable equals* → name `fighting`, value `1`.

Now leave the timer running permanently. It ticks all the time but only acts
while `fighting` is `1`. Set that flag from your combat triggers:

- fight starts → **Set Variable** `fighting` = `1`
- fight ends → **Set Variable** `fighting` = `0`

This is usually nicer than starting and stopping the timer, because you never
end up with a timer that was left paused.

### 13. A trigger that starts and stops a timer

**Want:** the timer runs only while a specific enemy is up.

On the trigger that spots the enemy appearing, add a **Script** action:

```lua
SetVariable("fighting", "1")
```

and on the one that spots it dying:

```lua
SetVariable("fighting", "0")
```

Dot commands ride the same outbound path as typing, so **Ack With**
`.timer play heal` (or Script `SendToServer(".timer play heal")`) *can* start
a timer from a trigger. Prefer a variable + timer **Conditions** (recipe 12)
when you want the timer always armed and gated; use Ack / `SendToServer` when
you truly want play/stop. Keep process-period on (default), or a leading `.`
goes to the MUD.

### 14. One-shot reminder

Leave **Repeat** off and the timer fires once, then stops. Combined with a
trigger that starts it, that is a "remind me in 30 seconds" — a Notification
action makes it show up even if you have switched away from the app.

### 15. Debugging any of this

The single most useful habit: add an extra **Ack With** action printing a
`.note`, which is client-side only and never reaches the game.

```
.note FIRED: target=$1
```

Put one on a trigger while you are building it and you can see whether it
matched at all, and what it captured, before you start suspecting the alias or
the timer downstream. Delete it when the thing works.

`.trigger status <name>` and `.alias status <name>` tell you whether something
is enabled; `.alias list` shows every alias at once.

## Built-in commands

    `.colordebug <0|1|2|3>`             ANSI color debug: `0` normal; `1` color on + codes; `2` color off + codes; `3` color off, no codes
    `.closewindow`                      Dirty-exit the game window
    `.note <text>`                      Client-only echo to the game window; never sent to the MUD. Useful for button tips and debugging
    `.probe lines on|off|report|reset`  Measure how the game's text is cut up on the way in; see below. Off by default, costs nothing when off
    `.trigger …`                        Enable/disable triggers (`on`/`off`/`toggle`/`status`/`group`/`all`/`plugin`; main + plugins); see below
    `.alias …`                          Enable/disable aliases (`list`/`status`/`on`/`off`/`toggle`/`all`); see below
    `.timer <action> <name> [silent]`   Timer control: `play`, `pause`, `reset`, `stop`, `info`. Optional third token suppresses toasts (not `info`)
    `.timer duration <name> <seconds> [silent]`   Change stored duration and save. A running timer keeps running on the new length, from now
    `.settings …`                       Settings file housekeeping. No argument (or `status`) names this world's settings file and the date/size of the `.bak` copy kept beside it; `backup` saves now and refreshes that copy; `restore` puts it back and reloads. For a copy you can move off the phone use Export / **Backup All Settings** instead
    `.echo [on|off]`                    Show or hide what you type when the server has taken telnet ECHO (a password prompt). No argument prints the current state. The next change from the server wins
    `.dobell`                           Fire the bell reaction now — vibrate, notification, on-screen bell, whichever are on in Options → Bell. This is how a trigger makes a noise; see "Making a trigger make a noise"
    `.togglefullscreen`                 Toggle fullscreen preference
    `.wrap [on|off]`                    Input bar growth (default on); also Options → Input → Grow Input Bar?
    `.editbutton [on|off]`              Show or hide the Edit button; also Options → Window → Show Edit button?
    `.editpanel [on|off]`               Toggle/show/hide the Edit tools strip (Sel/Cut/…)
    `.sendbutton [on|off]`              Show or hide the Send button; also Options → Window → Show Send button?
    `.font [size|+n|-n|default]`        Game font size without leaving the game. No argument prints it. `.font +2` steps up from where you are; clamped to 6–48. Also Options → Window → Font size
    `.width [percent|+n|-n|toggle|off]` Text canvas width as a percent of the screen (100–200). Over 100 the text is drawn wider than the screen and you drag it sideways with one finger. `toggle` flips to 100% and back to the last wide setting — put it on a button for ASCII maps. Also Options → Window → Text width (% of screen)
    `.gmcp …`                           GMCP helpers (status / sniff / version / supports / dump / send); see below
    `.frame …`                          Frames a server opened (`list`, `close <id>`, `close all`); see below. Not the same as `.window`
    `.mcp …`                            MCP helpers (Mud Client Protocol `#$#`); see below
    `.mssp`                             Dump the cached MSSP server listing (server announces it; nothing to ask for)
    `.msdp …`                           Dump the MSDP cache, or ask the server: `list`, `send <var>`, `report <var>`, `unreport <var>`, `reset <group>`
    `.keyboard` / `.kb`                 Input-bar control — see `.kb` section below
    `.disconnect`                       Disconnect the current session (same as overflow **Disconnect**)
    `.reconnect`                        Reconnect the current session (same as overflow **Reconnect**)
    `.run <directions>`                 Speedwalk; mapping from **Speedwalk Directions**; commas insert free-text commands
    `.loadset <setname>`                Built-in stub; `button_window` overrides to load a button set
    `.clearbuttons`                     Hide every on-screen button; one **BACK** button stays to bring them all back
    `.switch <connection>`              Switch foreground UI to another open connection by exact display name; bare `.switch` lists open sessions (unknown names are refused — they used to black-screen the UI)
    `.search …`                         Scrollback search; see forms below
    `.map …`                            Built-in Mapper (record/draw/links/find/path/maps); see Mapper
    `.window …`                         Extra text windows (list/show/hide/clear/create/destroy); see below

### `.trigger` forms

```
.trigger
.trigger on <name|plugin:name>
.trigger off <name|plugin:name>
.trigger toggle <name|plugin:name>
.trigger status [name]
.trigger group on <group>
.trigger group off <group>
.trigger group toggle <group>
.trigger all on
.trigger all off
.trigger plugin <plugin> all on|off
```

Unqualified names resolve **main settings** first, then a unique plugin
match; use `plugin:name` when names collide. Names and groups may contain
spaces (rest of line after the action). `status` with no name prints
main + plugin counts. Empty group name matches the default group (exact
string match, same as Lua `EnableTriggerGroup`). Group commands apply to
**main + all plugins**. `.trigger all` affects main only; use
`.trigger plugin <plugin> all on|off` for one plugin.

### `.probe lines`

```
.probe lines on
.probe lines off
.probe report        (or plain .probe)
.probe reset
```

Answers one question about the world you are on: **do several lines of game text
arrive together, or cut up?**

Text does not arrive one line at a time. It arrives in whatever pieces the
network hands over, and a trigger sees a whole piece at once. That is why a
pattern can only ever match across several lines if those lines came in the same
piece. This tells you whether they do.

Turn it on, play normally for a few minutes — walk around, fight something, read
a long room description — then `.probe report`. It costs nothing while it is off,
and next to nothing while it is on: it counts, it does not store your text.

The reading looks like this:

    Chunks seen:        412
    Complete lines:     1180
    Lines per chunk:    1: 190  2: 96  3-5: 88  6-10: 30  11+: 8
    Longest run:        14 lines in one chunk
    Ended mid-line:     171 of 412 (41%)

**Reading it.** A high *ended mid-line* percentage, or a *longest run* of only
one or two, means lines usually arrive separately on this world — so a pattern
spanning several lines would miss often. Plenty of chunks in the 3-5 and 6-10
buckets means blocks of text do arrive whole.

Nothing in the client uses this yet. It exists so that a decision about
multi-line triggers rests on a measurement from a real session rather than on a
guess about how the network behaves.

### `.search` forms

```
.search phrase
.search 'phrase with spaces'
.search "phrase"
.search next | n
.search prev | previous | p
.search close | hide | clear
```

Empty argument opens the search UI. Buttons may also use `/search 'phrase'`.

## Mapper

Built-in room map (not the legacy ForgeMap plugin). Open from overflow **Map**,
or `.map` / `.map open` / `.map close` / `.map toggle`. Prefer floating or
fullscreen with `.map mode float|fullscreen` (also **Options → Mapper**).
Floating windows can be dragged and resized; opacity is in Options. The overlay
stays under the ⋮ chrome so overflow remains reachable.

### Concepts

    **Tile**         One room cell on a grid (x, y) plus a **level**
    **Exit**         Edge from tile A to B via a walk command (`n`, `go west`, `out`, …)
    **Current**      Where the mapper thinks you are (green highlight)
    **Selected**     Tile you tapped (yellow outline); used by Edit / Here / Links
    **Follow**       Camera keeps current centered; pan/pinch turns Follow off until you Center
    **Level nest**   A floor anchored on a door/stairs tile (not one global stack)

Maps are JSON files under `/BlowTorch/maps/` (autosave ~2s after changes; **Save**
in **Map** or `.map export` / `.map save` forces a write). With a path,
`.map export|save <path>` writes JSON there (absolute or BlowTorch-relative).
`.map import <path|name>` loads JSON from an absolute/BlowTorch-relative path
or a maps-dir name, then copies it into `/BlowTorch/maps/`.

Title bar shows a breadcrumb when you are on a nested floor, e.g.
`map · L-1 ← Hallway` (map name · level · door you entered from). **[REC]** means
recording is on. The title has **Browse | Edit** and **Float | Full** segments
(**Browse** / **Float** default). **Browse** is view/navigate only — no
recording, Draw, Links, or tile edits. **Edit** is required to record, create
nests, use **Draw** / **Links**, and delete levels. Long-press the title opens
the **Floors** radial.

### Category radials (bottom chips; hide with ▾ tools)

Bottom chrome: **Nav** · **Floors** · **Edit** · **Map** · **View**, plus
**Browse|Edit** and **Float|Full**. The chip row scrolls if it does not fit. Tap
**▾ tools** in the title to hide that chrome when you want more map. Each
category opens an in-map pie menu (no system status-bar flash). Toggle wedges
show their current state under the label (e.g. Follow **on**).

    **Nav**
        What it is for: Getting around
        Actions: Path to, Go there, Find, Center, Follow, Record

    **Floors**
        What it is for: Levels of this map
        Actions: Floor list, Floor ↑/↓, Root floor, To entrance, Rename floor, Delete floor

    **Edit**
        What it is for: Changing the map by hand
        Actions: Draw, Link mode, Set Here, Edit tile, Spacing (spread/packed), Tidy now, 1-way specials, Moves, Link map, Undo

    **Map**
        What it is for: The map as a file
        Actions: Save, Maps, New map, Export, Capture

    **View**
        What it is for: Standing preferences
        Actions: Opacity…, Arrow labels, Window echo, GMCP sync, GMCP grow

**Close** is only the title **✕** (not in the pie menus).

Two names worth separating: **Spacing** (Edit) is a standing preference for how
far apart tiles are drawn, while **Tidy now** re-runs the layout once. **Undo**
is in Edit because it undoes edits.

**New map** works in Browse as well as Edit — starting a map is a file
operation, like Maps and Save.

**Floors → Floor list**: tap a floor to view; long-press = Go Here. In **Edit**,
**Rename…** / **Delete…** on the selected row. You cannot delete the last level.

**Draw** (Edit): grid on; tap empty cell to place; long-press empty = place + Here.
**Link mode** (Edit): tap FROM then TO, then pick a Moves verb (or unlink).
**Layout**: **spread** spaces tiles for arrows + labels; **packed** is compact
(arrows only, thinner heads). **Opacity…** (View): pick a percent — 100% is fully opaque.

While **Record**ing, outbound exits store your typed command; the reverse edge is
guessed. Walking back the same path overwrites that guess with your return
command (e.g. guessed `s` → your `go south`). Specials (`out`/`enter`) with
**1-way off** (default) close to the unique room that already leads into Here
(e.g. freezer `out` → hallway). **1-way ON** always places a new nearby tile.
**Follow** (without Record) advances Here along known exits so the map camera
tracks you.

Long-press a tile: **Path to here** (toast only) or **Go there** (toast + send
commands so the character walks). Find dialog **Go** does the same.
Long-press a tile and drag to move it on the grid (release without moving opens the
tile menu). **Double-tap** a tile = **Set as Here**. Double-tap empty map = center on
current.

Exits with a known destination draw as **arrows** between tiles. In **spread**
layout, walk-word labels sit on the shaft; in **packed**, only the shaft + heads
(including diagonals). If more than two commands share an edge in spread mode,
the label shows `cmd1 · cmd2 +N` — tap it for the full list.

Badge glyphs on a tile:

    **▲** / **▼**
        Meaning: Exit to another **floor** of this map
        Tap: Jump camera / Here to that floor

    **◆**
        Meaning: Special same-map nest (`enter` / `out`, …)
        Tap: Jump to that floor

    **○**
        Meaning: Portal to **another map file**
        Tap: Save current map, then open the linked map in the overlay

To create a portal: Edit → long-press tile → **Link to map…** (or **Edit → Link map**),
pick the destination map and Moves command. Walking that command with **Follow**
also loads the other map.

### Levels (tile-anchored)

Floors nest **per Here tile**: each stairs/door can have its own basement or attic.
**↑** / **↓** (Floors radial) follow an existing nest from Here or return to the
anchor door when leaving (**Browse**). Creating a nest needs **Edit**.

**Browse floors:** **Floors → Floor list** (name, tile count, “via …” for nests;
tap = view; long-press = Go Here), or tap ▲/▼/◆/○ badges. **Root floor** /
**To entrance** jump to the root floor or the nest’s anchor door.

Hierarchy: **map file** → **floors (levels)** → **tiles**. Same-map height or
independent floors stay in one file; a wholly separate zone is another map + **○**.

Recording still maps `up`/`down` while you walk. Use **↑**/**↓** when you need a
pocket floor that is not a simple vertical stack.

### Movement lexicon

Planar grid: **+x = east**, **+y = south** (north decreases y).

    `n`/`north`, `s`/`south`, `e`/`east`, `w`/`west` (and `go`/`walk`/`move` prefixes)   Neighbor on the same level
    `ne`/`nw`/`se`/`sw`                                                                  Diagonal
    `u`/`up`/`climb`/`ascend`                                                            Level +1 (while Recording)
    `d`/`down`/`descend`                                                                 Level −1 (while Recording)
    `in`/`enter`, `out`/`leave`/`exit`, other text                                       Special exit (nearby cell, not a compass step)

Recording prefers this built-in compass lexicon before Speedwalk key bindings
(default `.run`: `h`=nw, `j`=ne, `k`=sw, `l`=se), so typing `go se` still places a
southeast neighbor. Custom Speedwalk commands still apply for non-compass verbs.
Print the summary with `.map dirs`.

### Commands

    `.map`                                                                  Help / status
    `.map open|close|toggle`                                                Show or hide the map UI
    `.map mode fullscreen|float`                                            Presentation mode
    `.map mode browse|edit|toggle`                                          Map interaction mode (Browse default; Edit for nests/Draw/Links/delete)
    `.map record on|off|toggle`                                             Record movement into tiles/exits (`rec` alias)
    `.map follow on|off|toggle`                                             Keep the view centered on you
    `.map level list|prev|next|set <name>`                                  List / nest down (prev) / nest up (next) / jump by name
    `.map level delete <id|name>`                                           Delete a floor and all its tiles (Edit; cannot delete last)
    `.map level move <tileId> <level>`                                      Move a tile onto another level
    `.map find <query>`                                                     Search rooms (`search` alias)
    `.map path <query>`                                                     Show path commands (no send)
    `.map goto <query>`                                                     Path; send only if **Path auto-send** is on
    `.map go <query|id>`                                                    Path and **always** send walk commands (tile long-press **Go there**)
    `.map center`                                                           Center on current room
    `.map undo`                                                             Undo last graph change
    `.map dirs`                                                             Movement lexicon / grid offsets
    `.map maps` / `.map load <name>` / `.map new <name>`                    List / open / create (new name must be unique)
    `.map deletemap <name>`                                                 Delete a saved map file (UI: **Map → Maps** → long-press)
    `.map portal|linkmap <cmd> map <name> [from <id>]`                      Portal exit to another map file
    `.map levelink <cmd> new|to <levelId>|independent <name> [from <id>]`   Floor link (↑/↓ / existing / independent)
    `.map level rename [<id|name>] <newName>`                               Rename a floor
    `.map opacity [40-100]`                                                 Overlay opacity
    `.map export` / `.map save`                                             Save now (`/BlowTorch/maps/`)
    `.map export|save <path>`                                               Write JSON to that path
    `.map import <path|name>`                                               Import JSON (path or maps-dir name); copy into maps
    `.map zoom in|out|reset`                                                Zoom the open map UI
    `.map zoom <factor>`                                                    Zoom by scale factor (map UI must be open)
    `.map add [x y] [title] [here]`                                         Place a tile (optional title; `here` sets current)
    `.map here [id]`                                                        Mark current position
    `.map delete [id]`                                                      Remove a tile (and links to it)
    `.map neighbor <cmd> [from <id>]`                                       Create/link a neighbor by walk verb
    `.map move [id] <x> <y>`                                                Reposition a tile on the grid
    `.map title` / `.map note`                                              Edit current tile text
    `.map title for <id> <text>` / `.map note for <id> <text>`              Edit a specific tile
    `.map link <cmd> [from <id>] to <tileId>`                               Manual link
    `.map unlink <cmd> [from <id>]`                                         Remove an exit
    `.map conflict` / `list` / `list all`                                   List open conflicts (or all, including resolved)
    `.map conflict resolve|ignore <id|n>`                                   Mark one conflict resolved
    `.map conflict resolve|ignore all`                                      Mark all open conflicts resolved
    `.map conflict purge`                                                   Remove resolved conflicts (open ones kept)
    `.map capture preview`                                                  Match Options title/exits regex on recent buffer
    `.map capture apply`                                                    Apply last preview to the current tile

**Options → Mapper:** enable module, float/fullscreen default, opacity,
recording defaults, follow, path auto-send, Use GMCP Room,
**Configure Room Sync…** (room number / absolute coords / create exits),
auto reverse links, legacy toolbar CSV (UI uses **Nav/Floors/Edit/Map/View** chips),
**Capture Title Regex** / **Capture Exits Regex** (keys
`mapper_capture_title_regex` / `mapper_capture_exits_regex`; used by
`.map capture`).

**GMCP Room:** with GMCP and **Use GMCP Room** on (and **Room** in Manage
modules…), `Room.Info` builds the map as you walk:
- **num/id/vnum** → stable tile identity (IRE games and others)
- **coords** / **coord** `{x,y,z}` → place on the grid (z → floor)
- **exits** `{n:123,…}` → create/link neighbors (destination stubs by vnum)

Does not delete exits absent from GMCP. Does **not** parse ASCII maps from
game text — that is Capture regex / Record / Draw. Without GMCP (typical on
many MOOs), use **Rec** while walking, **Edit** for **Draw** / **Links**,
and/or `.map capture`.

### Typical workflows (mini-tutorial)

1. **Record while exploring:** `.map new mymap` → open map → **Edit** mode → **Nav → Record** → walk → Record off → **Map → Save**.
2. **Draw by hand:** **Edit** mode → **Edit → Draw** → tap empty cells → **Link mode** → **Set Here** on your room.
3. **Floors:** long-press a tile → **Add level…** → Floor ↑/↓ (new or existing) / Independent floor / Another map….
4. **Jump maps:** after linking, tap **○** on the tile (or walk the portal command with Follow on).
5. **Fix layout:** long-press-drag a tile. Use **Edit → Spacing** (spread) to see arrow labels.

### `.run` defaults

Direction letters (editable in Speedwalk Directions): `n e s w u d`,
`h`=nw, `j`=ne, `k`=sw, `l`=se. Prefix with a count. Examples:
`.run 3desw2n`, `.run 3ds,open door,3w`.
Mapper recording still treats `se`/`sw`/… as compass diagonals even when those
letters are Speedwalk *keys*.

### `.keyboard` / `.kb`

    *(no args)*                Print help
    `insert <text>`            Drop text into the input bar at the cursor, spaced
    `add` / `popup` + text     Set or append input; `popup` also shows the IME
    `flush`                    Send current input
    `close` / `clear`          Hide IME / clear text
    `sel` / `selectall`        Select all
    `cut` / `copy` / `paste`   Clipboard
    `start` / `cursorstart`    Caret to start
    `end` / `cursorend`        Caret to end
    `stepf` / `stepr`          Caret one character right
    `stepb` / `stepl`          Caret one character left
    `stepu` / `stepd`          Command history (↑ older / ↓ newer), like keyboard arrows; within multiline text, move one line first

Examples: `.kb popup reply`, `.kb sel`, `.kb cut`, `.kb start`, `.kb end`, `.kb stepf`, `.kb stepb`.

**`insert` vs `add`.** `add` glues text onto the end exactly as given; `insert`
puts it where the cursor is and works out the spaces, so the bar never ends up
reading `ktroll`. `insert` also does not expand aliases — the text goes in
literally, which is what you want when the text is a name you pointed at. Its
main use is a tappable word bound to `.kb insert $word`; see below.

**Edit** on the input bar expands Sel/Cut/Copy/Paste plus a compact **← ↑ ↓ →** pad (hidden again with **Hide**). ↑/↓ recall previous commands (same as keyboard up/down); ←/→ move the caret.

**Options → Window → Show Edit button?** / **Show Send button?** (both on by default). Dot commands: `.editbutton on|off`, `.sendbutton on|off`, `.editpanel on|off` (tools strip). With Send hidden, use keyboard Send/Enter or `.kb flush`.

### `.editbutton`

```
.editbutton
.editbutton on | off
```

Shows or hides the **Edit** button (same as Options → Window → Show Edit button?). No argument prints status.

### `.editpanel`

```
.editpanel
.editpanel on | off
```

Toggles (or forces) the Edit tools strip above the input row. Same strip as the **Edit** button. No argument toggles.

### `.sendbutton`

```
.sendbutton
.sendbutton on | off
```

Shows or hides the **Send** button (same as Options → Window → Show Send button?). No argument prints status. When off, send with the keyboard Send/Enter key or `.kb flush`.

## Copy text from the game window

- **First finger** — touch where selection should start (marks the start).
- **Second finger** — tap to open the selection / copy widget.
- One-finger long-press alone does not open copy.
- Drag the cursors, then use the widget’s copy control. On-screen buttons may hide while selecting so the widget stays usable.
- The same two-finger gesture works in **extra text** windows (float/drawer).

## Font size

New profiles start around font size **20** (readable on phones). Change under
Options → Window → Font Size.

## Newest text at top

By default, fresh game output sits at the **bottom** of the window (classic
terminal). Enable **Options → Window → Newest text at top?** to put live lines
at the **top** and older scrollback below — handy when on-screen buttons cover
the bottom edge. Drag **up** to dig into history; the home chevron stays in the
**bottom-right** and points **up** toward live output.

**Caveat:** this reverses the on-screen order of consecutive lines. MUD maps,
room diagrams, and other ASCII graphics drawn line-by-line will appear
**upside down**. Leave the option off for those games (or use **Top padding** /
**Keep text still with keyboard?** instead to keep buttons usable without
flipping text).

**Options → Window → Top padding (px)** leaves empty space above game text for
notch/camera cutouts (on-screen buttons are unaffected). Try values like
`40`–`80` on punched-hole phones.

**Options → Window → Bottom padding (px)** does the same at the bottom edge, all
the time — use it to keep the newest line clear of the input bar or a gesture bar.

**Options → Window → Bottom padding with keyboard (px)** adds further space below
game text only while the soft keyboard is open. The two are independent: set
either on its own, or both, in which case they add up while the keyboard is out.
The gap is measured from the bottom of the text area, which rises with the
keyboard unless **Keep text still with keyboard?** is on.

**Options → Window → Keep text still with keyboard?** — when on, opening the soft
keyboard lifts only the input bar; game text stays put (may sit under the IME).
Works with either text direction. Off = classic lift (text rises with the keyboard).

## On-screen buttons: swipe + accordion

**Load a button set from the wizard:** **Options → Button → Load button set from wizard**
(or type `.layoutwizard`). Check one or more packs (Compass, Newbie, Combat,
Explorer, Social), give each a set name, and pick size / alignment / colors.
Packs install complete — there is no Simple/Advanced choice any more; it was
worth three tiles on Compass and nothing at all on Newbie, and an unwanted tile
is easier to delete than a missing one is to discover. Apply only writes the
named sets you checked — other sets stay put; same name overwrites after a
warning.

Set names are folded to lowercase and to `a–z 0–9 _ -` when you Apply (spaces
become `_`), because the name also goes into the `.loadset <name>` cross-links
the packs write between each other; the wizard tells you the name it will
actually use.

New MUD profiles may offer a soft prompt once after connect; turn
**Options → Button → Offer button layout wizard** back on to see that prompt
again. Offline Starter Tutorial keeps its own teaching pad.

The pad lands just under the action bar, high enough that the soft keyboard
cannot cover it — a pad anchored near the bottom of the screen disappears behind
the keyboard the moment you type. Its accordion tiles (MORE, NAV, TIP, CAST,
DOORS, CHAT) sit on the bottom row and open **downward** into the empty game
area beneath it, so they never cover the compass rose above them.

Named sizes are capped so the whole pad stays above the keyboard: Compass has
six rows, so **Extra large** comes back a little under 72dp on a tall phone —
still clearly bigger than Large, and wholly visible, which is the point.
**Fit to screen** is the exception. It sizes a pack so its columns span the
width, which for a tall pack means part of it sits below the keyboard line.

**Change the size later:** **Options → Button → Button size** is a dropdown
(Compact, Comfortable, Large, Extra large, Fit to screen). Picking one resizes
the set on screen straight away, keeping its arrangement — the grid spacing
moves with the tiles rather than leaving them to overlap, and nothing is
re-flowed into rows. **Layout template** next to it only chooses which pack the
wizard offers first; it installs nothing on its own.

**Edit layout:** open **⋮ → Edit buttons**, or long-press the **⋮** next to Edit/Send. In edit mode ⋮ is hidden — use the strip icons: gear (set options), **Cancel** left, **Done** right.

The default `button_window` plugin supports more than tap:

- **Swipe** — eight directions (up, down, left, right, and the four corners);
  each can run a different command (edit button → Swipe). Overrides classic Flip.
  Drag roughly a finger-width off the tile.
- **Hold** — optional command after press-and-hold.
- **Accordion** — up to five child buttons expand from a parent (direction + tap/hold/swipe trigger). Handy when you want several macros on one tile. Editor badges: **T** tap, **H** hold, **S** swipe. Options can draw gesture hints (uncheck to hide **U/D/L/R**, diagonal arrows, Hold, and accordion badges).

### Copying buttons between sets

Select the buttons you want (tap one, or tap several), tap one of them to open
the menu, and choose **Copy**. They go to the system clipboard.

To paste, either:

- **long press an empty grid cell** in any set — the buttons land with the block's
  top-left at that cell, keeping the shape they were copied in; or
- open the editor settings sheet and press **Paste copied buttons**, which drops
  them in the middle of the grid.

A short tap on empty grid still makes a new button, exactly as before — only a
long press pastes, and only when something has been copied.

The copy carries each button's *own* settings and leaves inherited ones
inherited, so buttons pasted into a set with different defaults take on that
set's look rather than dragging the old set's factory values with them.

### Copying a button set

The button sets list gives each set four icons: load, edit, **copy**, delete.
Copy duplicates the set — every button and the set's own defaults — as
`<name> copy`, saved straight away, and the new set appears in the list without
closing it. Copy it again and you get `<name> copy 2`.

Useful for trying a rearrangement without losing the pad you already trust:
copy, edit the copy, and switch between them with `.loadset`.

## Super-buttons (buttons on top of the keyboard)

Any button can also be put **on the screen itself**, over the game and over the
soft keyboard. Edit the button → **Others** → **Float over the game**.

Two modes, in the **When** picker:

- **Always visible** — the button stays on screen wherever you dragged it.
- **Show with keyboard** — the button exists only while the keyboard is open,
  and is hidden the rest of the time, including from the button grid. This is
  the keyboard-assistant mode: pair it with `.kb` commands
  (`.kb stepb`, `.kb stepf`, `.kb stepu`, `.kb paste`, `.kb close` — see
  [`.keyboard` / `.kb`](#keyboard--kb)) to get caret keys, command recall and
  paste next to your thumb while typing.

The button keeps everything it already had — tap, hold, flip, all eight swipe
directions, colours, size, `switchTo`. It can also be drawn as a circle, with
an optional outline. **Very long press (~2 s)** picks it up and moves it; a
normal hold (~0.45 s) still runs the Hold command. Where you drop it is
remembered per world.

**Permission.** Android does not let an app draw on top of the keyboard without
**"Display over other apps"**. BlowTorch asks the first time you tick the box,
never at startup, and the button is saved either way. If you refuse, the button
still exists but the keyboard covers it — which leaves the feature doing very
little, so it is worth granting.

**Android 9 and 10:** the client often cannot tell whether the keyboard is
open, so **Show with keyboard** may never appear there. Android 11 and newer
are fine. **Always visible** works everywhere.

## Extra text windows

Optional top-drawer or floating panes (chat, tells, combat, …) beside the main game
output. Each slot has a public **name** (lowercase `a-z`, `0-9`, `_`, max 8
slots). The same name is used for gag/replace **retarget**, Lua, and `.window`.

Configure under **Options → Window → Extra text windows** (**Enable**, **Manage windows…**,
or advanced JSON). Modes: **`drawer_top`** (top strip, no title bar — show/hide via
`.window show|hide` or Manage → Show window) or **`float`** (titled, draggable panes).
Overlay geometry (drawer height ≥ 50dp, float position, **opacity 40–100%**) is owned by
the UI; buffers are named `WindowToken`s.

A floating pane's chrome is per slot, in **Manage windows… → Edit**:

- **Show title bar** — the strip across the top, with the ☰ grip and the window's
  title. On by default. Turning it off **hides** it rather than removing it: the
  strip is still there and still drags the window, it is simply not drawn.
- **Show resize grip ◢** — the corner marker at the bottom right. Same idea: off
  hides the marker, the corner still resizes the window.
- **Close button ✕** — hides the window; bring it back with `.window show <slot>`
  or Manage → Show window. On by default. Off means gone, not invisible — an
  unseen ✕ inside the drag strip would close the window every time you missed.

All three off gives a bare pane with no visible chrome at all: still draggable
by its top strip and resizable from its bottom-right corner if you know they are
there, and closed only by `.window hide` or Options.

**Scroll speed** is per slot, in Manage windows. The default, *Same as main
window*, follows **Options → Window → Scroll sensitivity**, so that one control
still steers every extra window at once and a slot only breaks away when you
set it to a specific speed. Changes apply straight away, with the window open.

A slot keeps collecting text while it is closed, and shows what it missed when
you open it again — up to the most recent 128 KB.

In **Manage windows…**, pick GMCP modules with checkboxes (advanced CSV for patterns
like `Comm.*`). Routes need **Options → Service → GMCP → Use GMCP?** on.

### `.window` forms

```
.window
.window list
.window show <slot>
.window hide <slot>
.window clear <slot>
.window create <slot> [title…]
.window destroy <slot>
.window opacity <slot> [40-100]
```

### Gag / replace retarget

In the trigger gag or replace editor, pick a known slot from the spinner
(**None** = no retarget) or type a custom name. An empty retarget string means
no retarget. Gag removes the line from the main window and can forward it to the
slot; replace can rewrite text and optionally send the line to the slot.

Lua `NewTrigger` tables:

```lua
{ type = "gag", output = true, log = true, retarget = "chat" }
{ type = "replace", text = "[redacted]", retarget = "tells" }
```

### Lua (extra text)

```lua
CreateTextWindow("chat", "Chat")   -- create/update slot
DestroyTextWindow("chat")
ListTextWindows()                  -- array of names
ShowTextWindow("chat", true)
ClearTextWindow("chat")
NoteToWindow("chat", "hello")      -- client-only note into the slot
WindowExists("chat")
AppendLineToWindow("chat", line)   -- (windowName, line) — matches Java
```

### GMCP → window

**Options → Window → Extra text windows → Manage windows… → Edit** has GMCP checkboxes (and an
advanced CSV). Matching inbound GMCP packets are written into that slot as
`[GMCP] ModuleName {json…}` (passwords redacted). Patterns: exact (`Char.Vitals`),
family (`Char.` / `Char.*`), or `Comm.*`.

GMCP is out-of-band — it does **not** appear in the main mud buffer unless **Show
GMCP in game window?** is on. When a module is routed to an extra window, that
module is **not** also fed into main (intercept for the live feed only). Lua GMCP
watchers and mapper/native handlers still run. In-band MUD lines are unchanged —
use gag/replace if you also want to hide related room text.

### `.alias` forms

```
.alias list                    every alias and whether it is on
.alias status [name]           counts, or one alias and what it expands to
.alias on|off|toggle <name>    turn one on or off
.alias all on|off              every alias in main settings
```

Use `plugin:name` when the same name exists in more than one plugin. A disabled
alias stops matching immediately.

### Session variables in alias text

Write `${name}` in an alias replacement to drop in a session variable:

    `att`
        Replacement: `kill ${target}`
        Variable: `target` = `goblin`
        Sent: `kill goblin`

Set the variable from a trigger's **Set Variable** action, or from Lua with
`SetVariable("target", "$1")`. That is how text the *game* printed reaches a
command you type: the trigger captures it, the alias spends it.

Braces are required, so `${name}` never collides with the numeric `$1`
captures, and a bare `$` in text is left alone. An **unset** variable is left
written as-is rather than becoming empty — sending `kill` with no target is
worse than sending something visibly wrong.

Variables are per session and are not saved.

### Switching alias sets by mode

`EnableAlias(name)` returns whether an alias is live; `EnableAlias(name, true|false)`
turns it on or off. A disabled alias stops matching immediately.

Call it from a trigger's script action and aliases become mode-dependent — a
combat trigger can enable the combat aliases and switch off the travel ones:

```lua
EnableAlias("kk", true)
EnableAlias("travel_home", false)
```

Triggers and timers go further: both carry **conditions** (trigger/alias on or
off, alias replacement equals, variable equals/exists, combined with and/or)
that decide whether they fire at all, edited in their own editors. Aliases
themselves have no conditions — use `EnableAlias` to turn an alias on or off;
a trigger condition can *read* that on/off state or the alias **With** text.

## GMCP (short)

Enable under **Options → Service → GMCP Options**. Prefer **Manage modules…**
over editing the raw Supports String.

**If you need to know exactly what a world sends**, turn on **Log GMCP?** and
read `/BlowTorch/logs/gmcp.log`. That file is the real traffic. Text copied out
of the game window can pick up escaping on the way to the clipboard, so it is
not evidence of what was on the wire — a lesson learned the expensive way.

Helpers:

```
.gmcp                 — help
.gmcp ask|handshake   — Hello / enabled / native / seen (honest)
.gmcp modules         — enabled vs seen this session
.gmcp enable|disable  — toggle modules (+ live Add/Remove)
.gmcp renegotiate     — re-send Hello + Supports.Set
.gmcp status          — flags
.gmcp sniff [on|off]  — log handshake/packets to logs/gmcp.log
.gmcp sniff tail [N]  — last N GMCP lines in-game (0–100, default 40)
.gmcp feed [on|off]   — live IN/OUT GMCP in the mud window
.gmcp version         — client hello / syntax notes
.gmcp supports […]   — show or set supports modules
.gmcp dump [path]     — dump cached GMCP table
.gmcp send <payload>  — queue a GMCP packet
```

## Frames a server opens (`mudstd.frame`)

Some servers can ask the client to open a window of their own — a stats panel, a
map image — through the `mudstd.frame` GMCP package. It is **off by default**:
Options → Manage modules….

**Pictures are drawn.** When a server sends an image frame you get the picture
itself, and where it goes is your choice: Options → GMCP → **Pictures the server
sends**.

* **In a window of its own** (the default) — a small window over the game text.
  Drag it by the ☰ handle, resize it from the ◢ corner, close it with the ×.
  **Long-press its title** for a short menu: switch it to a drawer, change its
  opacity, or close it. Where you leave a frame is where the next one opens.
* **In the game text** — the picture is printed into the scrollback where it
  arrived, next to the room description it belongs to, and scrolls away with it.
  Set how tall it is with **Picture height in the text (lines)**.

A picture that is still being fetched says *Loading…*, and one that could not be
fetched says why. A blank box is the one thing it will not do.

Text content (`frame.terminal`) still arrives in the game window labelled
`[frame <id>]`. There is no webview, so a webview frame is reported rather than
shown.

The conversation is complete in both directions. When a server opens a frame,
BlowTorch answers `frame.opened`, and `frame.resized` once the window has
measured itself; when a server asks for something it cannot have, BlowTorch
answers `frame.closed` with `reason: system` rather than going quiet. And when
**you** close a frame — the × or `.frame close` — the server is told, with
`reason: user`.

```
.frame                — what the server has open here
.frame list           — the same
.frame close <id>     — close it and tell the server you did
.frame close all      — close every one
```

Frame ids are chosen by the server and are case-sensitive; `.frame list` shows
them exactly. This is **not** `.window`, which is BlowTorch's own extra text
windows — those are yours, frames are the server's.

## MCP (short)

Mud Client Protocol ([MCP 2.1](https://www.moo.mud.org/mcp/)) — in-band `#$#…`
(not GMCP). Off by default. **Options → Service → MCP Options**. Prefer **Manage packages…**.

Native packages (when enabled): hellmoo-status vitals, simpleedit editor,
displayurl (browser), ping auto-reply, mcp-cord, vmoo-client info.

```
.mcp                  — help
.mcp ask|status       — handshake / flags
.mcp packages         — enabled vs seen
.mcp enable|disable   — toggle packages
.mcp renegotiate      — re-send mcp-negotiate-can
.mcp sniff|feed|dump|vitals|send|ping|client
.mcp cord open|close|send|list
```

Lua: `Send_MCP_Packet(s)`, `Get_MCP_Status()`, literal triggers `@message-name`
(same idea as GMCP `%module`).

Optional protocols (Options → Service → **MUD Protocols**). **MTTS and MCCP are
on by default; MSDP and MSSP are off.** Reconnect after changing any of them.

```
.mssp   — dump MSSP cache (enable Use MSSP? first, reconnect)
.msdp   — dump MSDP cache (enable Use MSDP? first, reconnect)
```

**Use MTTS?** — TTYPE always follows the MUD Terminal Type Standard
(`BlowTorch` → `ANSI` → `MTTS <bits>`). On = bits **13** (ANSI+UTF-8+256);
off = bits **1** (ANSI only).

**Use MCCP?** — MUD Client Compression Protocol v2 (telnet option 86), on by
default; it saves bandwidth and you should not be able to tell it is there. If
decompression ever fails, the client says so, turns compression off for that
connection and reconnects once without it, rather than dumping the compressed
stream on screen. Turn the option off for a server whose compression
misbehaves.

## Passwords are hidden while the MUD asks for them

A MUD asks for a password by taking echoing over (telnet ECHO). While a server
holds it, the input bar masks what you type and the text is kept out of the
session log; it unmasks when the server hands echoing back, or on a disconnect.
Not every world uses this — some do, some do not.

If a server takes echoing and never gives it back, `.echo on` unmasks the bar by
hand (`.echo off` masks it again, `.echo` alone reports the state). The next
change from the server wins over the command.

This is separate from **Options → Service → Local Echo?**, which decides whether
your own commands are printed into the game window at all.

## Plugin commands (when loaded)

### `button_window` (default Free build)

    `.loadset <name>`   Load named button set
    `.clearbuttons`     Hide every button for a clear view of the game
    `.layoutwizard`     Open the button layout wizard (packs, set names, size)

**Getting the buttons back after `.clearbuttons`.** One button labelled **BACK**
is left behind — press it and the whole set returns. So does pressing anywhere a
button used to be: while the set is hidden, any press restores it rather than
sending a command, so a stray tap cannot fire something you did not mean. The
set also comes back by itself when you switch to another world or reopen the
app; nothing is lost either way, and the layout editor is off while the buttons
are hidden.

### `starter_tutorial` (loaded by default)

    `.tutorial …`   Starter Tutorial: `help` / `start` / `next` / `prev` / `skip` / `done` / `topics` / `<topic>`

On the default button set, tap **HELP** to run `.tutorial start`. The launcher
lists a built-in **Starter Tutorial** row first (offline — no MUD). Disable the
welcome note on normal MUDs via **Options → Starter Tutorial → Show on connect**,
or type `.tutorial done`. You can also toggle `starter_tutorial` off under
**Plugins**, which keeps it loaded but silent. It ships with the app and
**cannot be deleted** — like `button_window` and `connection_settings`, the
Plugins screen refuses to remove it.

## Session overflow menu

In order, as the menu builds them (all under ⋮):

1. **Aliases** / **Triggers** / **Timers** / **Options** — the editors
2. **Button Sets** — switch saved sets (`button_window`; the pack/size wizard
   is **Options → Button → Load button set from wizard**)
3. **Edit buttons** — enter button layout edit mode
4. **Speedwalk Directions** — the direction letters `.run` uses
5. **Map** — open the built-in mapper (same as `.map open`)
6. **Plugins** — load / enable / remove Lua plugins
7. **Reconnect** / **Disconnect** — same as `.reconnect` / `.disconnect`
8. **Quit** — leave the session window
9. **Search scrollback** — same as `.search`
10. **Reload Settings** — re-read this world's settings from disk
11. **Crash report** — Show log / Share log
12. **About**
13. **Help** — this manual

**Export Settings**, **Import Settings** and **Reset Settings** are **not** in
this menu — they live under **Options → Miscellaneous**, beside the storage
settings they depend on. Storage access is there too.

Connection duration appears on the ongoing notification and launcher row.

**Persistent Connection?** (Options → Miscellaneous): after brief network loss
(VPN/Wi-Fi flaps), keep retrying longer without the disconnect dialog and wait
for connectivity before reconnecting. Cannot keep a dead TCP socket — the MUD
session is re-established when the network returns.

## Related docs

- [`plugin-authoring.md`](plugin-authoring.md) — write Lua plugins (API, limits, packaging)
- [`options-guide.md`](options-guide.md) — Options / storage layout  
- [`FDROID_README.md`](FDROID_README.md) — permissions for F-Droid  
