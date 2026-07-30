#!/usr/bin/env python3
"""Aggregate StrictMode violations out of a logcat dump.

The test flavour arms StrictMode, which then prints one multi-line stack per
violation. Reading those by hand is hopeless — a single eden session produced
three thousand of them — and the interesting number is not any one stack but
which of our own methods accounts for the most blocked main-thread time.

So this groups every violation by its topmost com.resurrection frame, splits
main thread from not (pid == tid), and totals the durations StrictMode itself
reports. That total is the number worth quoting: "522 hits, 8 s, worst 301 ms"
is a measurement, "the session log blocks" is a guess.

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

    counts = collections.Counter()
    durations = collections.defaultdict(list)
    for violation in violations:
        key = (app_frame(violation), violation['main'])
        counts[key] += 1
        durations[key].append(violation['dur'])
    for (frame, is_main), count in counts.most_common(40):
        spans = durations[(frame, is_main)]
        print('%5d main=%-5s worst=%4dms total=%6dms  %s'
              % (count, is_main, max(spans), sum(spans), frame))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
