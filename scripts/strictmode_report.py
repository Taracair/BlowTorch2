#!/usr/bin/env python3
"""Aggregate StrictMode violations out of a logcat dump.

The test flavour arms StrictMode, which then prints one multi-line stack per
violation. Reading those by hand is hopeless — a single eden session produced
three thousand of them — and the interesting number is not any one stack but
which of our own methods accounts for the most blocked main-thread time.

So this groups every violation by its topmost com.resurrection frame, splits
main thread from not (pid == tid), and totals the durations StrictMode itself
reports.

**Do not add those durations up naively.** StrictMode's ~duration is measured to
the end of the current span, not per operation, so the three probes inside one
ensureWritableDirectory call -- exists, isDirectory, canWrite -- each report
almost the whole blocked window. A real burst, from the 30 July log:

    09:25:06.284  ~duration=173 ms
    09:25:06.287  ~duration=171 ms
    09:25:06.289  ~duration=171 ms
    09:25:06.292  ~duration=169 ms

That is one 173 ms stall reported four times, not 684 ms of disk. Summing it
inflated ensureWritableDirectory to "19.2 s" in an earlier handoff note, which
put it above things that actually cost more. So violations whose spans started
at the same instant on the same thread are folded into one burst here, and the
burst columns are the ones to quote: "45 bursts, 5.1 s blocked, worst 517 ms".
Raw hits are still shown, because the count of *violations* is what tells you
whether a call site repeats.

    adb -s <serial> logcat -d > /tmp/logcat.txt
    scripts/strictmode_report.py /tmp/logcat.txt

Pass a method name as a second argument to see the caller chains behind one
group, which is how the session-log work found that the main-looper flush
Handler was only 45 of its 522 hits:

    scripts/strictmode_report.py /tmp/logcat.txt SessionLogger.flushLocked
"""

import collections
import re
import sys

VIOLATION = re.compile(r'~duration=(\d+) ms: (\S+)')
FRAME = 'StrictMode: \tat '
APP = 'com.resurrection'


def stamp_ms(fields):
    """Logcat's "MM-DD HH:MM:SS.mmm" as milliseconds, or None if unreadable.

    Only differences within one log matter, so the month is folded in as 31 days
    rather than being made calendar-correct.
    """
    try:
        month, day = fields[0].split('-')
        hour, minute, rest = fields[1].split(':')
        second, millis = rest.split('.')
    except (IndexError, ValueError):
        return None
    days = (int(month) * 31) + int(day)
    return ((((days * 24 + int(hour)) * 60 + int(minute)) * 60
             + int(second)) * 1000) + int(millis)


def bursts(violations):
    """Fold violations that report the same blocked span into one.

    Two violations belong to the same span when they are on the same thread and
    their spans *started* at the same moment -- start being the log timestamp
    minus the duration StrictMode reports. See the module docstring: the probes
    inside one directory check each report the whole window, a few milliseconds
    apart, so their starts coincide and their ends do not.

    Yields (count_of_violations, blocked_ms) per burst.
    """
    spans = []
    # Only the newest span on a thread can still be collecting probes: a later
    # stall on that thread cannot have begun before the previous one ended.
    newest = {}
    for violation in violations:
        if violation['at'] is None or violation['dur'] < 0:
            spans.append([1, max(violation['dur'], 0)])
            continue
        start = violation['at'] - violation['dur']
        span = newest.get(violation['tid'])
        if span is None or abs(span[2] - start) > SPAN_SLACK_MS:
            span = [0, 0, start]
            newest[violation['tid']] = span
            spans.append(span)
        span[0] += 1
        span[1] = max(span[1], violation['dur'])
        # Compare the next probe against this one rather than against the first:
        # both the log timestamp and the reported duration are whole
        # milliseconds, so a long burst drifts a millisecond at a time and a
        # fixed anchor splits it in the middle.
        span[2] = start
    return [(span[0], span[1]) for span in spans]


#: How far apart two reported span starts may be and still be the same stall.
#: The probes in one ensureWritableDirectory land within about 3 ms of each
#: other; a following, genuinely separate stall on the same thread has to begin
#: after the previous one ended, so this does not merge distinct calls unless
#: they are already back to back within the slack.
SPAN_SLACK_MS = 8


def parse(path):
    """Yield one dict per violation: duration, type, main-thread flag, frames."""
    violations = []
    current = None
    with open(path, errors='ignore') as handle:
        for line in handle:
            if 'StrictMode policy violation' in line:
                if current:
                    violations.append(current)
                fields = line.split()
                match = VIOLATION.search(line)
                current = {
                    'dur': int(match.group(1)) if match else -1,
                    'type': match.group(2) if match else '?',
                    'at': stamp_ms(fields),
                    'tid': fields[3] if len(fields) > 3 else '?',
                    # pid == tid is the process's main thread. For :stellar that
                    # is the ConnectionHandler thread, which is the one that
                    # matters: it carries text to the UI.
                    'main': len(fields) > 3 and fields[2] == fields[3],
                    'frames': [],
                }
            elif current is not None and FRAME in line:
                current['frames'].append(line.split('at ', 1)[1].strip())
            elif current is not None and 'StrictMode' not in line:
                violations.append(current)
                current = None
    if current:
        violations.append(current)
    return violations


def app_frame(violation):
    """Topmost frame that is ours; system-only stacks group together."""
    for frame in violation['frames']:
        if APP in frame:
            return frame.split('(')[0]
    return '(no app frame)'


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 1
    violations = parse(argv[1])
    print('violations parsed: %d' % len(violations))

    if len(argv) > 2:
        wanted = argv[2]
        chains = collections.Counter()
        for violation in violations:
            if wanted not in app_frame(violation):
                continue
            ours = [f.split('(')[0] for f in violation['frames'] if APP in f]
            chains[' <- '.join(ours[:5])] += 1
        for chain, count in chains.most_common(20):
            print('%5d %s' % (count, chain))
        return 0

    grouped = collections.OrderedDict()
    for violation in violations:
        key = (app_frame(violation), violation['main'])
        grouped.setdefault(key, []).append(violation)

    rows = []
    for (frame, is_main), group in grouped.items():
        folded = bursts(group)
        rows.append((sum(cost for _, cost in folded), len(group), len(folded),
                     max(cost for _, cost in folded), is_main, frame))
    # Ordered by blocked time, which is the thing being hunted. Hit count is
    # still printed: it is what says whether a call site repeats per line.
    rows.sort(reverse=True)
    print('%5s %5s %8s %8s  %s'
          % ('hits', 'stall', 'worst', 'blocked', 'thread / where'))
    for blocked, hits, stalls, worst, is_main, frame in rows[:40]:
        print('%5d %5d %6dms %6dms  main=%-5s %s'
              % (hits, stalls, worst, blocked, is_main, frame))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
