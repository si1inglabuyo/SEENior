

import argparse
import csv
import math
import os
import statistics as st
import sys

GRAVITY = 9.80665
WINDOW_S = 2.0
EXTRA_THRESHOLDS = (1.0, 0.8)


def load(path):
    """Return (times, magnitudes) from a recorder CSV or a phyphox .csv/.xlsx."""
    ext = os.path.splitext(path)[1].lower()
    if ext == ".xlsx":
        try:
            import openpyxl
        except ImportError:
            sys.exit("Reading .xlsx needs openpyxl:  pip install openpyxl   (or export phyphox as CSV)")
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        sheet = wb["Raw Data"] if "Raw Data" in wb.sheetnames else wb[wb.sheetnames[0]]
        rows = [list(r) for r in sheet.iter_rows(values_only=True)]
    else:
        with open(path, encoding="utf-8-sig", newline="") as f:
            rows = list(csv.reader(f))
    header, data = rows[0], rows[1:]
    names = [str(h).strip().lower() for h in header]

    def col(*keys):
        for i, n in enumerate(names):
            if all(k in n for k in keys):
                return i
        return None

    t_i = col("time")
    abs_i = col("absolute acceleration") if col("absolute acceleration") is not None else None
    # "Absolute linear acceleration" (without gravity) must not be mistaken for the total.
    for i, n in enumerate(names):
        if n.startswith("absolute acceleration"):
            abs_i = i
            break
    x_i, y_i, z_i = col("acceleration x"), col("acceleration y"), col("acceleration z")
    if t_i is None or (abs_i is None and None in (x_i, y_i, z_i)):
        sys.exit(f"{path}: cannot find time and acceleration columns. Found: {header}")
    t, a = [], []
    for r in data:
        try:
            ti = float(r[t_i])
            if abs_i is not None and r[abs_i] not in (None, ""):
                m = float(r[abs_i])
            else:
                m = math.sqrt(float(r[x_i]) ** 2 + float(r[y_i]) ** 2 + float(r[z_i]) ** 2)
        except (ValueError, TypeError, IndexError):
            continue
        t.append(ti)
        a.append(m)
    if len(t) < 10:
        sys.exit(f"{path}: too few readings ({len(t)})")
    return t, a


def windows(t, a, trim):
    """Non-overlapping 2 s windows between trim and (end - trim). Returns [(start, n, sum, mean)]."""
    lo, hi = t[0] + trim, t[-1] - trim
    out = []
    s = lo
    while s + WINDOW_S <= hi + 1e-9:
        devs = [abs(a[i] - GRAVITY) for i in range(len(t)) if s <= t[i] < s + WINDOW_S]
        if devs:
            out.append((s - t[0], len(devs), sum(devs), sum(devs) / len(devs)))
        s += WINDOW_S
    return out


def summarise(w, thresholds):
    means = [x[3] for x in w]
    s = {"n": len(means), "median": st.median(means), "min": min(means), "max": max(means)}
    for th in thresholds:
        s[th] = sum(m >= th for m in means)
    return s


def analyse_file(path, threshold, trim, plot, working, quiet=False):
    t, a = load(path)
    dur = t[-1] - t[0]
    rate = len(t) / dur
    w = windows(t, a, trim)
    if not w:
        print(f"{path}: recording too short after trimming {trim} s from each end")
        return None
    ths = [threshold] + [x for x in EXTRA_THRESHOLDS if x != threshold]
    s = summarise(w, ths)

    if quiet:
        return s, ths, w
    print("=" * 72)
    print(os.path.basename(path))
    print(f"  {len(t)} readings over {dur:.1f} s  (~{rate:.0f} Hz); mean total acceleration "
          f"{st.mean(a):.2f} m/s2 (gravity is {GRAVITY})")
    print(f"  windows of {WINDOW_S:.0f} s after trimming {trim:g} s from each end:")
    print("    start(s)   n    sum      pre_dev   >= %.1f" % threshold)
    for start, n, tot, mean in w:
        print(f"    {start:7.1f}  {n:4d}  {tot:7.2f}  {mean:8.2f}   {'yes' if mean >= threshold else 'no'}")
    print(f"  median pre_dev {s['median']:.2f}   lowest {s['min']:.2f}   highest {s['max']:.2f} m/s2")
    for th in ths:
        print(f"  windows at or above {th:g}: {s[th]} of {s['n']}  ({100 * s[th] / s['n']:.0f}%)")
    if working:
        start, n, tot, mean = w[0]
        print(f"  working, first window: sum of |total - {GRAVITY}| over its {n} readings = {tot:.2f}; "
              f"{tot:.2f} / {n} = {mean:.2f}")
    if plot:
        try:
            import matplotlib
            matplotlib.use("Agg")
            import matplotlib.pyplot as plt
        except ImportError:
            print("  (matplotlib missing, no plot)")
        else:
            fig, ax = plt.subplots(figsize=(8, 3.2))
            ax.step([x[0] + WINDOW_S / 2 for x in w], [x[3] for x in w], where="mid", marker="o")
            ax.axhline(threshold, color="crimson", ls="--", label=f"threshold {threshold:g}")
            ax.set_xlabel("time (s)")
            ax.set_ylabel("pre_dev (m/s2)")
            ax.set_title(os.path.basename(path))
            ax.legend()
            fig.tight_layout()
            png = os.path.splitext(path)[0] + "_analysis.png"
            fig.savefig(png, dpi=150)
            plt.close(fig)
            print(f"  plot saved: {png}")
    return s, ths, w


def parse_name(path):
    """P01_WALK_POCKET_R1.csv -> ('P01', 'WALK_POCKET', 'POCKET', 'walking'). Unknown names -> '?'."""
    stem = os.path.splitext(os.path.basename(path))[0].upper()
    parts = stem.split('_')
    if len(parts) >= 3 and parts[-1].startswith('R') and parts[-1][1:].isdigit():
        parts = parts[:-1]
    participant = parts[0] if parts else '?'
    activity = '_'.join(parts[1:]) if len(parts) > 1 else '?'
    position = next((x for x in ('HAND', 'POCKET', 'BAG') if x in activity), '-')
    kind = 'still' if activity.startswith('REST') or activity.startswith('SIT') else 'walking'
    return participant, activity, position, kind


def sweep(items):
    """items: [(path, windows)]. Print how each threshold treats walking and resting windows."""
    walk, rest, by_pos = [], [], {}
    for path, w in items:
        _, _, pos, kind = parse_name(path)
        means = [x[3] for x in w]
        if kind == 'still':
            rest += means
        else:
            walk += means
            by_pos.setdefault(pos, []).extend(means)
    if not walk or not rest:
        print('(no sweep: needs at least one walking file and one REST file named like P01_REST_R1.csv)')
        return
    positions = sorted(by_pos)
    print('=' * 72)
    print('THRESHOLD SWEEP  (every window of every file; walking should be >= threshold, rest <)')
    print(f"  walking windows: {len(walk)}, resting windows: {len(rest)}")
    head = '  thr   walking caught   rest correct' + ''.join(f'   {q.lower():>8s}' for q in positions)
    print(head)
    best = None
    for k in range(5, 21):
        th = k / 10
        wc = 100 * sum(m >= th for m in walk) / len(walk)
        rc = 100 * sum(m < th for m in rest) / len(rest)
        pc = {q: 100 * sum(m >= th for m in by_pos[q]) / len(by_pos[q]) for q in positions}
        print(f"  {th:3.1f}   {wc:12.1f}%   {rc:11.1f}%" + ''.join(f"   {pc[q]:7.1f}%" for q in positions))
        if rc >= 95 and min(pc.values()) >= 90:
            best = th
    if best is None:
        print('  No threshold keeps rest >= 95% correct and every position >= 90% caught. Say so; do not force one.')
    else:
        print(f'  Highest threshold with rest >= 95% correct and every position >= 90% caught: {best:.1f}')
        print('  (a guide for the discussion, not a validated result)')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("target")
    p.add_argument("--threshold", type=float, default=1.5)
    p.add_argument("--trim", type=float, default=3.0)
    p.add_argument("--plot", action="store_true")
    p.add_argument("--working", action="store_true")
    p.add_argument("--detail", action="store_true", help="in folder mode, also print every file's window table")
    args = p.parse_args()

    if os.path.isdir(args.target):
        files = sorted(
            os.path.join(args.target, f) for f in os.listdir(args.target)
            if f.lower().endswith((".csv", ".xlsx")) and not f.lower().endswith("_analysis.csv")
        )
    else:
        files = [args.target]
    results, items = [], []
    folder = len(files) > 1
    for f in files:
        r = analyse_file(f, args.threshold, args.trim, args.plot, args.working,
                         quiet=folder and not args.detail)
        if r:
            results.append((os.path.basename(f), r[0]))
            items.append((f, r[2]))
    if folder:
        print("=" * 72)
        print(f"SUMMARY  (share of 2 s windows at or above {args.threshold:g}, after trimming {args.trim:g} s)")
        print(f"  {'file':34s} {'type':8s} {'windows':>7s} {'median':>7s} {'lowest':>7s} {'>=thr':>6s}")
        for (name, s), (f, _) in zip(results, items):
            kind = parse_name(f)[3]
            print(f"  {name[:34]:34s} {kind:8s} {s['n']:7d} {s['median']:7.2f} {s['min']:7.2f} "
                  f"{100 * s[args.threshold] / s['n']:5.0f}%")
        sweep(items)


if __name__ == "__main__":
    main()
