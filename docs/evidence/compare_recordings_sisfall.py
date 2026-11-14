"""Put our own walk-test recordings next to SisFall's seniors, on the same measure.

Usage:
  python -I compare_recordings_sisfall.py --recordings <folder of our CSVs> --sisfall <folder of SisFall .txt> --out <folder>

  --recordings  searched recursively for .csv (Subject1/..., ALL_CSVS/...); the same file name
                found twice is counted once. Names are read loosely: any name with REST is rest;
                WALK plus HAND / POCKET / BAG says how the phone was carried (P06_WALKBAG_R1 and
                P03_Test1_WALK_BAG_R1 both work).
  --sisfall     the SE01-SE15 files, flat (D01_SE01_R01.txt ...). Only D01, D02, D07-D16 are read.
  --exclude     comma-separated file names to leave out. Default leaves out the two recordings
                known to be unusable (P01_REST_R1: not actually still; P05_WALK_BAG_R1: 4.6 Hz).

The measure is the app's pre_dev: mean of |acceleration - 1 g| over 2 s windows (same code as
recording_analysis.py for ours, same as active_threshold_sisfall.py for SisFall).

Writes comparison_sweep.png, comparison_boxes.png and comparison_table.md into --out.

Read the limits before quoting it: SisFall is worn at the waist at 200 Hz; ours is a phone carried
in the hand, a pocket or a bag at 50 Hz, by our own volunteers (ages not recorded here). SisFall has no pure-rest class, so its
"rest" is the quietest 2 s window of each sit / lie / bend trial, which is quieter than a whole
recording of rest. These are two different kinds of evidence, not one validated result.
"""
import argparse
import glob
import os
import re
import sys

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recording_analysis as ra  # our CSV loader and 2 s windows

G = 9.80665
SF_SCALE = 32.0 / 2**13 * G   # ADXL345, +-16 g, 13 bit
SF_FS = 200
SF_WIN = 2 * SF_FS
STILL_MAX, ACTIVE_MIN, OLD = 0.5, 1.0, 1.5
THS = np.round(np.arange(0.2, 2.51, 0.1), 2)
TABLE_AT = (0.5, 0.8, 1.0, 1.2, 1.5)
DEFAULT_EXCLUDE = "P01_REST_R1.csv,P05_WALK_BAG_R1.csv"


def our_groups(folder, exclude):
    seen, groups = set(), {"Rest": [], "Hand": [], "Pocket": [], "Bag": []}
    subjects = set()
    for path in sorted(glob.glob(os.path.join(folder, "**", "*.csv"), recursive=True)):
        name = os.path.basename(path)
        if name in seen or name in exclude:
            continue
        seen.add(name)
        up = name.upper()
        if "REST" in up:
            kind = "Rest"
        elif "WALK" in up and "HAND" in up:
            kind = "Hand"
        elif "WALK" in up and "POCKET" in up:
            kind = "Pocket"
        elif "WALK" in up and "BAG" in up:
            kind = "Bag"
        else:
            continue
        try:
            t, a = ra.load(path)
        except SystemExit as e:  # too few readings
            print("skipped", name, "-", e)
            continue
        w = [x[3] for x in ra.windows(t, a, 3.0)]
        if not w:
            print("skipped", name, "- too short after trimming")
            continue
        groups[kind].extend(w)
        m = re.match(r"(P\d+)", up)
        if m:
            subjects.add(m.group(1))
    return groups, sorted(subjects)


def sisfall_load(path):
    """First three columns (the ADXL345 accelerometer). pandas' C parser is ~10x faster than a line loop."""
    import pandas as pd
    df = pd.read_csv(path, header=None, usecols=[0, 1, 2], encoding="latin-1",
                     engine="c", on_bad_lines="skip")
    return df.apply(pd.to_numeric, errors="coerce").dropna().to_numpy(dtype=float) * SF_SCALE


def sisfall_windows(a):
    d = np.abs(np.linalg.norm(a, axis=1) - G)
    n = len(d) // SF_WIN
    return d[: n * SF_WIN].reshape(n, SF_WIN).mean(axis=1) if n else np.array([])


def sisfall_groups(folder):
    slow, fast, rest = [], [], []
    subjects = set()
    rest_acts = {f"D{n:02d}" for n in range(7, 17)}
    for path in sorted(glob.glob(os.path.join(folder, "**", "D*_SE*_R*.txt"), recursive=True)):
        m = re.match(r"(D\d+)_(SE\d+)_R\d+", os.path.basename(path))
        if not m:
            continue
        act, subj = m.groups()
        if act not in ("D01", "D02") and act not in rest_acts:
            continue
        w = sisfall_windows(sisfall_load(path))
        if not len(w):
            continue
        subjects.add(subj)
        if act == "D01":
            slow.extend(w)
        elif act == "D02":
            fast.extend(w)
        else:
            rest.append(w.min())  # quietest 2 s of the trial stands in for rest
    return {"slow": np.array(slow), "fast": np.array(fast), "rest": np.array(rest)}, sorted(subjects)


def pct(v, th):
    v = np.asarray(v)
    return 100.0 * (v >= th).mean() if len(v) else float("nan")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--recordings", required=True)
    p.add_argument("--sisfall", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--exclude", default=DEFAULT_EXCLUDE)
    args = p.parse_args()
    os.makedirs(args.out, exist_ok=True)

    ours, our_subj = our_groups(args.recordings, {x.strip() for x in args.exclude.split(",") if x.strip()})
    sf, sf_subj = sisfall_groups(args.sisfall)
    walk = np.array(ours["Hand"] + ours["Pocket"] + ours["Bag"])
    rest = np.array(ours["Rest"])
    if not len(walk) or not len(rest) or not len(sf["slow"]):
        sys.exit("Missing data: need our walking and rest windows and SisFall D01 files.")

    C_OURS, C_SF = "#1f6f4a", "#7b4fa3"
    # ---------------- Figure 1: the threshold sweep, walking left, rest right ----------------
    fig, (axw, axr) = plt.subplots(1, 2, figsize=(13, 5.2), sharey=True)
    series_walk = [
        ("Ours: all walking", walk, C_OURS, "-", 3),
        ("Ours: pocket", np.array(ours["Pocket"]), C_OURS, "--", 1.5),
        ("Ours: hand", np.array(ours["Hand"]), C_OURS, ":", 1.5),
        ("Ours: bag", np.array(ours["Bag"]), C_OURS, "-.", 1.5),
        ("SisFall seniors: slow walk", sf["slow"], C_SF, "-", 3),
        ("SisFall seniors: fast walk", sf["fast"], C_SF, "--", 1.5),
    ]
    for label, v, c, ls, lw in series_walk:
        axw.plot(THS, [pct(v, t) for t in THS], label=label, color=c, ls=ls, lw=lw)
    axr.plot(THS, [pct(rest, t) for t in THS], label="Ours: resting recordings", color=C_OURS, lw=3)
    axr.plot(THS, [pct(sf["rest"], t) for t in THS], label="SisFall seniors: quietest window of sit / lie / bend trials",
             color=C_SF, lw=3)
    for ax, title, yl in (
        (axw, "Walking windows counted as moving\n(higher is better)", "% of 2 s windows at or above the line"),
        (axr, "Resting windows wrongly counted as moving\n(lower is better)", ""),
    ):
        for x, lab, col in ((STILL_MAX, "still below 0.5", "#6B8E9B"), (ACTIVE_MIN, "moving from 1.0", "#D9822B"),
                            (OLD, "old line 1.5", "#444444")):
            ax.axvline(x, color=col, ls="--", lw=1.2)
            ax.text(x + 0.02, 97, lab, rotation=90, va="top", fontsize=8, color=col)
        ax.set_title(title)
        ax.set_xlabel("line on the 2 s value (m/s²)")
        if yl:
            ax.set_ylabel(yl)
        ax.grid(alpha=.3)
        ax.legend(fontsize=8, loc="center right" if ax is axw else "upper right")
        ax.set_ylim(-2, 102)
    fig.suptitle(f"Our recordings ({len(our_subj)} people) vs SisFall seniors ({len(sf_subj)} people)", fontsize=13)
    fig.tight_layout()
    fig.savefig(os.path.join(args.out, "comparison_sweep.png"), dpi=150)
    plt.close(fig)

    # ---------------- Figure 2: the spread of each group, side by side ----------------
    groups = [
        ("Ours\nrest", rest, C_OURS), ("SisFall\nrest*", sf["rest"], C_SF),
        ("Ours\nbag", np.array(ours["Bag"]), C_OURS), ("Ours\nhand", np.array(ours["Hand"]), C_OURS),
        ("Ours\npocket", np.array(ours["Pocket"]), C_OURS),
        ("SisFall\nslow walk", sf["slow"], C_SF), ("SisFall\nfast walk", sf["fast"], C_SF),
    ]
    fig, ax = plt.subplots(figsize=(11, 5.4))
    bp = ax.boxplot([g[1] for g in groups], tick_labels=[g[0] for g in groups], patch_artist=True,
                    showfliers=False, whis=(5, 95))
    for patch, g in zip(bp["boxes"], groups):
        patch.set_facecolor(g[2]); patch.set_alpha(.55)
    ax.axhline(STILL_MAX, color="#6B8E9B", ls="--"); ax.axhline(ACTIVE_MIN, color="#D9822B", ls="--")
    ax.axhline(OLD, color="#444444", ls="--")
    ax.text(len(groups) + 0.45, STILL_MAX, "0.5", va="center", fontsize=8)
    ax.text(len(groups) + 0.45, ACTIVE_MIN, "1.0", va="center", fontsize=8)
    ax.text(len(groups) + 0.45, OLD, "1.5 (old)", va="center", fontsize=8)
    ax.set_ylabel("2 s value (m/s²)")
    ax.set_title("Spread of the 2 s value. Box = middle half, whiskers = 5th to 95th percentile\n"
                 "Green = our phone recordings, purple = SisFall seniors")
    ax.grid(alpha=.3, axis="y")
    fig.text(0.01, 0.01, "*SisFall rest = quietest 2 s of each sit / lie / bend trial, so it reads quieter than a whole resting recording.",
             fontsize=8)
    fig.tight_layout(rect=(0, 0.03, 1, 1))
    fig.savefig(os.path.join(args.out, "comparison_boxes.png"), dpi=150)
    plt.close(fig)

    # ---------------- Table ----------------
    lines = [
        "# Our recordings next to SisFall seniors",
        "",
        f"Ours: {len(our_subj)} people, {len(walk)} walking windows, {len(rest)} resting windows "
        f"(phone in hand / pocket / bag, 50 Hz).",
        f"SisFall: {len(sf_subj)} seniors aged 60-75 (SE01-SE15), {len(sf['slow'])} slow-walk windows, "
        f"{len(sf['fast'])} fast-walk windows, {len(sf['rest'])} rest trials (waist sensor, 200 Hz).",
        "",
        "| Line (m/s²) | Ours: walking active | Ours: hand | Ours: pocket | Ours: bag | SisFall: slow walk active | "
        "SisFall: fast walk active | Ours: rest wrongly active | SisFall: rest* wrongly active |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for t in TABLE_AT:
        lines.append(
            f"| {t} | {pct(walk, t):.1f}% | {pct(ours['Hand'], t):.1f}% | {pct(ours['Pocket'], t):.1f}% | "
            f"{pct(ours['Bag'], t):.1f}% | {pct(sf['slow'], t):.1f}% | {pct(sf['fast'], t):.1f}% | "
            f"{pct(rest, t):.1f}% | {pct(sf['rest'], t):.1f}% |")
    lines += [
        "",
        "Median 2 s value: "
        f"ours walking {np.median(walk):.2f}, ours resting {np.median(rest):.2f}, "
        f"SisFall slow walk {np.median(sf['slow']):.2f}, SisFall fast walk {np.median(sf['fast']):.2f}, "
        f"SisFall rest* {np.median(sf['rest']):.2f}.",
        "",
        "*SisFall has no pure-rest class; its rest is the quietest 2 s window of each sit / lie / bend trial.",
        "",
        "## Limits",
        "- SisFall's sensor is worn at the waist; ours is a phone in a hand, pocket or bag, so the numbers are not the same measurement.",
        "- SisFall's seniors are healthy volunteers doing simulated activities; ours are our own volunteers (ages not recorded in the files). Neither group is frail seniors.",
        "- Different sample rates (50 Hz vs 200 Hz) and different rest definitions.",
        "- The lines are starting points to revisit with recordings from seniors. These are measurements, not a validated accuracy.",
    ]
    with open(os.path.join(args.out, "comparison_table.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
