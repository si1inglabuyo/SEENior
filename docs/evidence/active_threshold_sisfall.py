"""Check FallContext.ACTIVE_MIN against SisFall's elderly subjects (SE01-SE15).

Usage:  python -I active_threshold_sisfall.py <folder with SisFall .txt files> <out folder>

Files are named D01_SE01_R01.txt etc. Source: Sucerquia et al. 2017, Sensors 17(1):198.
pre_dev is computed exactly as the app does: mean of |norm(acceleration) - 1 g| over 2 s.
ADXL345 scale: +-16 g, 13 bit -> 32/2^13 g per count.  Sensor is worn at the WAIST, 200 Hz.
"""
import sys, glob, os, re
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

RAW, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
G = 9.80665
SCALE = 32.0 / 2**13 * G
FS = 200
WIN = 2 * FS
CURRENT, PROPOSED = 1.5, 1.0

def load(p):
    rows = []
    with open(p, encoding="latin-1") as f:
        for line in f:
            q = line.strip().rstrip(";").split(",")
            if len(q) >= 3:
                try: rows.append([float(q[0]), float(q[1]), float(q[2])])
                except ValueError: pass
    return np.array(rows) * SCALE

def windows(a):
    d = np.abs(np.linalg.norm(a, axis=1) - G)
    n = len(d) // WIN
    return d[: n * WIN].reshape(n, WIN).mean(axis=1)

groups = {}
for p in sorted(glob.glob(os.path.join(RAW, "D*_SE*_R*.txt"))):
    m = re.match(r"(D\d+)_(SE\d+)_R\d+", os.path.basename(p))
    groups.setdefault(m.group(1), []).append((m.group(2), windows(load(p))))

cat = lambda c: np.concatenate([w for _, w in groups[c]])
slow, fast = cat("D01"), cat("D02")
rest = np.array([w.min() for c in [f"D{i:02d}" for i in range(7, 17)] for _, w in groups.get(c, []) if len(w)])

lines = []
lines.append("| Threshold (m/s2) | Slow walk read as active | Quietest rest read as rest |")
lines.append("|---|---|---|")
for t in [1.5, 1.2, 1.0, 0.8]:
    lines.append(f"| {t} | {100*(slow>=t).mean():.1f}% | {100*(rest<t).mean():.1f}% |")
subj = sorted({s for s, _ in groups["D01"]})
per = {s: np.median(np.concatenate([w for t, w in groups["D01"] if t == s])) for s in subj}
lines.append("")
lines.append(f"Seniors whose slow-walk median is below {CURRENT}: {sum(v < CURRENT for v in per.values())}/{len(subj)}; "
             f"below {PROPOSED}: {sum(v < PROPOSED for v in per.values())}/{len(subj)}")
lines.append(f"Windows: slow walk {len(slow)}, fast walk {len(fast)}, rest trials {len(rest)}")
open(os.path.join(OUT, "active_threshold_table.md"), "w").write("\n".join(lines) + "\n")
print("\n".join(lines))

fig, ax = plt.subplots(1, 2, figsize=(12, 4.6), gridspec_kw={"width_ratios": [3, 2]})
bins = np.linspace(0, 8, 65)
for data, name, col in [(rest, "At rest (quietest 2 s of sit/lie trials)", "#2a9d8f"),
                        (slow, "Walking slowly (D01)", "#e9a23b"),
                        (fast, "Walking quickly (D02)", "#6a4c93")]:
    ax[0].hist(np.clip(data, 0, 8), bins=bins, alpha=.6, label=name, color=col, density=True)
ax[0].axvline(CURRENT, color="#c0392b", ls="--", label=f"Current cutoff {CURRENT}")
ax[0].axvline(PROPOSED, color="#1b4f72", ls="-", label=f"Proposed cutoff {PROPOSED}")
ax[0].set_xlabel("pre_dev: mean |acceleration - 1 g| over 2 s (m/s2)")
ax[0].set_ylabel("share of windows")
ax[0].set_title("Elderly subjects SE01-SE15 (SisFall)")
ax[0].legend(fontsize=8)
vals = [per[s] for s in subj]
ax[1].barh(subj, vals, color=["#c0392b" if v < CURRENT else "#e9a23b" for v in vals])
ax[1].axvline(CURRENT, color="#c0392b", ls="--")
ax[1].axvline(PROPOSED, color="#1b4f72")
ax[1].set_xlabel("median pre_dev, slow walking (m/s2)")
ax[1].set_title("Each senior's slow walk")
ax[1].invert_yaxis()
fig.tight_layout()
fig.savefig(os.path.join(OUT, "active_threshold_sisfall.png"), dpi=160)
