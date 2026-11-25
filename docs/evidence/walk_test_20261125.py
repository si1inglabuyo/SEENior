"""Walk test, 10 subjects (2026-11-25): how well does `ACTIVE_MIN` separate walking from rest on a phone?

Reads the subject folders exactly as they came from the recorder (Subject1 ... Subject10, with the
UUIDs and spelling differences left in), works out `pre_dev` for every non-overlapping 2 s window
with `recording_analysis.py` (the same sum the app uses), and writes one JSON file of every figure
the evidence page shows. Nothing is typed by hand.

    python docs/evidence/walk_test_20261125.py "<folder with Subject1..Subject10>" out.json

Group rules (same as recording_analysis.py, plus the slow-walk split):
  rest      activity starts with REST or SIT          (still on purpose)
  walking   everything else that is not CHAIR or BED  (position HAND / BAG / POCKET, or none)
  slow walk activity contains SLOW                    (no position was recorded for these)
CHAIRSTAND and BEDLIE are transitions and are kept out of the sweep.
"""
import json
import os
import re
import statistics as st
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recording_analysis as ra  # noqa: E402

THRESHOLDS = [round(x / 10, 1) for x in range(4, 21)]


def classify(fname):
    stem = os.path.splitext(fname)[0]
    stem = re.sub(r'_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', '', stem)
    act = re.sub(r'\(\d+\)', '', stem.upper())
    act = re.sub(r'^P\d+[A-Z]*_', '', act)
    act = re.sub(r'(^|_)(TEST\d+|R\d+)(?=_|$)', '', act).strip('_')
    act = re.sub(r'_+', '_', act)
    pos = next((p for p in ('HAND', 'BAG', 'POCKET') if p in act and not act.startswith('REST')), None)
    if act.startswith('REST') or act.startswith('SIT'):
        group = 'rest'
    elif act.startswith('CHAIR') or act.startswith('BED'):
        group = 'transition'
    elif 'SLOW' in act:
        group = 'slow'
    else:
        group = pos.lower() if pos else 'other'
    return act, group


def main(folder, out):
    files = []
    for sub in sorted(os.listdir(folder), key=lambda s: int(re.sub(r'\D', '', s) or 0)):
        d = os.path.join(folder, sub)
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if f.lower().endswith('.csv'):
                files.append((int(re.sub(r'\D', '', sub)), f, os.path.join(d, f)))

    rows, example = [], None
    for sid, fname, path in files:
        t, a = ra.load(path)
        w = ra.windows(t, a, 3.0)
        if not w:
            continue
        act, group = classify(fname)
        means = [x[3] for x in w]
        rows.append({'subject': sid, 'file': fname, 'activity': act, 'group': group,
                     'windows': len(means), 'median': st.median(means), 'means': means})
        if example is None and sid == 9 and act == 'WALKPOCKET':
            start, n, tot, mean = w[0]
            lo = t[0] + 3.0
            idx = [i for i in range(len(t)) if lo <= t[i] < lo + 2.0][:6]
            example = {'file': fname, 'readings_per_second': round(len(t) / (t[-1] - t[0]), 1),
                       'window_start_s': round(start, 1), 'n': n, 'sum': tot, 'mean': mean,
                       'first': [{'t': round(t[i] - t[0], 3), 'total': round(a[i], 3),
                                  'dev': round(abs(a[i] - ra.GRAVITY), 3)} for i in idx]}

    def pool(*groups):
        return [m for r in rows if r['group'] in groups for m in r['means']]

    pools = {'rest': pool('rest'), 'slow': pool('slow'), 'hand': pool('hand'), 'bag': pool('bag'),
             'pocket': pool('pocket'), 'carried': pool('hand', 'bag', 'pocket'),
             'walking': pool('slow', 'hand', 'bag', 'pocket')}
    sweep = []
    for th in THRESHOLDS:
        line = {'th': th}
        for name, vals in pools.items():
            if name == 'rest':
                line[name] = 100 * sum(m < th for m in vals) / len(vals)
            else:
                line[name] = 100 * sum(m >= th for m in vals) / len(vals)
        sweep.append(line)

    subjects = []
    for sid in sorted({r['subject'] for r in rows}):
        entry = {'subject': sid}
        for g in ('rest', 'slow', 'hand', 'bag', 'pocket'):
            vals = [m for r in rows if r['subject'] == sid and r['group'] == g for m in r['means']]
            entry[g] = round(st.median(vals), 2) if vals else None
        subjects.append(entry)

    data = {
        'files': len(rows),
        'windows': {k: len(v) for k, v in pools.items()},
        'transition_files': sum(r['group'] == 'transition' for r in rows),
        'sweep': sweep, 'subjects': subjects, 'example': example,
        'gravity': ra.GRAVITY, 'trim_s': 3.0, 'window_s': ra.WINDOW_S,
        'per_file': [{k: r[k] for k in ('subject', 'file', 'activity', 'group', 'windows')} | {'median': round(r['median'], 2)}
                     for r in rows],
    }
    with open(out, 'w', encoding='utf8') as fh:
        json.dump(data, fh, indent=1)
    print(f"{len(rows)} files, windows: {data['windows']}")


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
