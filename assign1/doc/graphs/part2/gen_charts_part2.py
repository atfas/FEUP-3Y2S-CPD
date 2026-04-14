import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np
import os

os.makedirs('/home/claude/charts', exist_ok=True)

BLUE    = '#378ADD'
ORANGE  = '#D85A30'
GREEN   = '#2EAA6E'
GRAY    = '#888780'

sizes = [1024, 1536, 2048, 2560, 3072]

def gflops(n, t):
    return (2 * n**3) / t / 1e9

def speedup(seq_avg, par_avg):
    return [s / p for s, p in zip(seq_avg, par_avg)]

def efficiency(sp, n_threads=4):
    return [s / n_threads for s in sp]

# ── helpers ──────────────────────────────────────────────────────────────────

def make_gflops_chart(filename, title, sizes, runs_a, avg_a, runs_b, avg_b,
                      label_a='Parallel outside', label_b='Parallel inside'):
    fig, ax = plt.subplots(figsize=(9, 5))
    fig.patch.set_facecolor('white')
    ax.set_facecolor('white')

    colors = {label_a: BLUE, label_b: GREEN}

    for run in runs_a:
        ys = [gflops(n, t) for n, t in zip(sizes, run)]
        ax.scatter(sizes, ys, color=BLUE, s=35, zorder=3, alpha=0.70)
    for run in runs_b:
        ys = [gflops(n, t) for n, t in zip(sizes, run)]
        ax.scatter(sizes, ys, color=GREEN, s=35, zorder=3, alpha=0.70)

    avg_y_a = [gflops(n, t) for n, t in zip(sizes, avg_a)]
    avg_y_b = [gflops(n, t) for n, t in zip(sizes, avg_b)]

    ax.plot(sizes, avg_y_a, color=BLUE,  linewidth=2.2, marker='D', markersize=6,
            markerfacecolor=BLUE,  zorder=4, label=f'{label_a} (avg)')
    ax.plot(sizes, avg_y_b, color=GREEN, linewidth=2.2, marker='s', markersize=6,
            markerfacecolor=GREEN, zorder=4, label=f'{label_b} (avg)')

    ax.set_xlabel('Matrix size (n×n)', fontsize=11, color=GRAY)
    ax.set_ylabel('Performance (GFlop/s)', fontsize=11, color=GRAY)
    ax.set_title(title, fontsize=13, fontweight='bold', pad=12)
    ax.set_xticks(sizes)
    ax.set_xticklabels([f'{n}×{n}' for n in sizes], fontsize=9)
    ax.tick_params(colors=GRAY)
    ax.spines['top'].set_visible(False);  ax.spines['right'].set_visible(False)
    ax.spines['left'].set_color('#dddddd'); ax.spines['bottom'].set_color('#dddddd')
    ax.yaxis.grid(True, color='#eeeeee', zorder=0); ax.set_axisbelow(True)
    ax.legend(fontsize=9, frameon=False)

    plt.tight_layout()
    path = f'/home/claude/charts/{filename}'
    plt.savefig(path, dpi=150, bbox_inches='tight', facecolor='white')
    plt.close()
    print(f'Saved {path}')


def make_speedup_efficiency_chart(filename, title, sizes,
                                   seq_avg, avg_a, avg_b,
                                   label_a='Parallel outside', label_b='Parallel inside'):
    sp_a = speedup(seq_avg, avg_a)
    sp_b = speedup(seq_avg, avg_b)
    eff_a = efficiency(sp_a)
    eff_b = efficiency(sp_b)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5))
    fig.patch.set_facecolor('white')

    for ax in (ax1, ax2):
        ax.set_facecolor('white')
        ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
        ax.spines['left'].set_color('#dddddd'); ax.spines['bottom'].set_color('#dddddd')
        ax.yaxis.grid(True, color='#eeeeee', zorder=0); ax.set_axisbelow(True)
        ax.set_xticks(sizes)
        ax.set_xticklabels([f'{n}×{n}' for n in sizes], fontsize=8)
        ax.tick_params(colors=GRAY)

    # Speedup
    ax1.plot(sizes, sp_a, color=BLUE,  linewidth=2.2, marker='D', markersize=6,
             markerfacecolor=BLUE,  zorder=4, label=label_a)
    ax1.plot(sizes, sp_b, color=GREEN, linewidth=2.2, marker='s', markersize=6,
             markerfacecolor=GREEN, zorder=4, label=label_b)
    ax1.axhline(y=4, color=ORANGE, linewidth=1.5, linestyle='--', label='Ideal (4×)')
    ax1.set_xlabel('Matrix size (n×n)', fontsize=10, color=GRAY)
    ax1.set_ylabel('Speedup', fontsize=10, color=GRAY)
    ax1.set_title('Speedup (vs sequential)', fontsize=11, fontweight='bold', pad=10)
    ax1.legend(fontsize=9, frameon=False)

    # Efficiency
    ax2.plot(sizes, eff_a, color=BLUE,  linewidth=2.2, marker='D', markersize=6,
             markerfacecolor=BLUE,  zorder=4, label=label_a)
    ax2.plot(sizes, eff_b, color=GREEN, linewidth=2.2, marker='s', markersize=6,
             markerfacecolor=GREEN, zorder=4, label=label_b)
    ax2.axhline(y=1.0, color=ORANGE, linewidth=1.5, linestyle='--', label='Ideal (100%)')
    ax2.set_xlabel('Matrix size (n×n)', fontsize=10, color=GRAY)
    ax2.set_ylabel('Efficiency (speedup / 4)', fontsize=10, color=GRAY)
    ax2.set_title('Efficiency', fontsize=11, fontweight='bold', pad=10)
    ax2.legend(fontsize=9, frameon=False)

    fig.suptitle(title, fontsize=13, fontweight='bold', y=1.02)
    plt.tight_layout()
    path = f'/home/claude/charts/{filename}'
    plt.savefig(path, dpi=150, bbox_inches='tight', facecolor='white')
    plt.close()
    print(f'Saved {path}')


def make_single_gflops(filename, title, sizes, runs_list, avgs_list, labels):
    """Generic chart for multiple series."""
    palette = [BLUE, GREEN, ORANGE, '#9B59B6']
    markers = ['D', 's', 'o', '^']
    fig, ax = plt.subplots(figsize=(9, 5))
    fig.patch.set_facecolor('white'); ax.set_facecolor('white')

    for idx, (runs, avg, label) in enumerate(zip(runs_list, avgs_list, labels)):
        c = palette[idx]; m = markers[idx]
        for run in runs:
            ys = [gflops(n, t) for n, t in zip(sizes, run)]
            ax.scatter(sizes, ys, color=c, s=35, zorder=3, alpha=0.65)
        avg_y = [gflops(n, t) for n, t in zip(sizes, avg)]
        ax.plot(sizes, avg_y, color=c, linewidth=2.2, marker=m, markersize=6,
                markerfacecolor=c, zorder=4, label=label)

    ax.set_xlabel('Matrix size (n×n)', fontsize=11, color=GRAY)
    ax.set_ylabel('Performance (GFlop/s)', fontsize=11, color=GRAY)
    ax.set_title(title, fontsize=13, fontweight='bold', pad=12)
    ax.set_xticks(sizes)
    ax.set_xticklabels([f'{n}×{n}' for n in sizes], fontsize=9)
    ax.tick_params(colors=GRAY)
    ax.spines['top'].set_visible(False);  ax.spines['right'].set_visible(False)
    ax.spines['left'].set_color('#dddddd'); ax.spines['bottom'].set_color('#dddddd')
    ax.yaxis.grid(True, color='#eeeeee', zorder=0); ax.set_axisbelow(True)
    ax.legend(fontsize=9, frameon=False)
    plt.tight_layout()
    path = f'/home/claude/charts/{filename}'
    plt.savefig(path, dpi=150, bbox_inches='tight', facecolor='white')
    plt.close()
    print(f'Saved {path}')

# ── DATA ─────────────────────────────────────────────────────────────────────

# Version 1A – Normal Mult, parallel outside (times in seconds)
runs_1a = [
    [0.75, 2.58, 7.28, 17.32, 36.52],
    [0.741, 2.582, 6.904, 17.799, 34.123],
    [0.739, 2.602, 6.853, 17.95, 33.223],
    [0.744, 2.609, 7.026, 17.934, 32.904],
    [0.744, 2.606, 6.857, 17.975, 33.553],
]
avg_1a = [0.74, 2.60, 6.98, 17.79, 34.06]

# Version 1B – Normal Mult, parallel inside (times in seconds)
runs_1b = [
    [5.75, 13.78, 29.80, 49.91, 100.62],
    [6.07, 14.105,28.735,49.924,97.194],
    [6.07, 14.176,28.866,51.022,97.048],
    [6.086,14.079,28.713,49.697,94.143],
    [6.028,13.571,28.597,49.878, 88.603],
]
avg_1b = [6.00, 13.94, 28.94, 50.08, 95.52]

# Sequential reference for Version 1 (from original chart data – avg times):
# Using the original C++ mult data: avg1 = [2.185, 8.042, 24.243, 56.785, 188.836]
seq_1_avg = [2.185, 8.042, 24.243, 56.785, 188.836]

# Version 2A – Linear Mult, parallel outside
runs_2a = [
    [0.14, 0.398,1.289,2.723,4.903],
    [0.133,0.446,1.291,2.746,4.831],
    [0.134,0.425,1.32, 2.723,4.909],
    [0.133,0.425,1.257,2.758,4.954],
    [0.133,0.425,1.323,2.778,5.077],
]
avg_2a = [0.13, 0.42, 1.30, 2.75, 4.93]

# Version 2B – Linear Mult, parallel inside
runs_2b = [
    [3.034,6.821,12.64, 20.564,30.548],
    [2.952,6.749,12.628,20.848,30.18 ],
    [2.981,6.722,12.878,20.562,29.554],
    [2.921,6.761,12.715,20.686,28.932],
    [2.92, 6.864,12.777,20.957,29.219],
]
avg_2b = [2.96, 6.78, 12.73, 20.72, 29.69]

# Sequential reference for Version 2 (linear mult C++ small, avg3):
seq_2_avg = [0.379, 1.282, 3.385, 7.223, 12.606]

# Original / SIMD / Collapse (time values for large sizes – using sizes 1024..3072 mapped
# from the 6-column table; columns appear to be: 1024,1536,2048,2560,3072 + one more)
# The table has 6 numbers per row; last column likely 3072 or extra – using first 5.
sizes6 = [1024, 1536, 2048, 2560, 3072]

runs_orig = [
    [103.058,55.647,61.431,54.703,58.883],
    [106.252,56.021,61.322,54.748,58.632],
    [107.647,61.316,62.04, 61.753,56.595],
    [109.213,58.985,63.324,60.052,60.207],
    [107.436,59.576,60.26, 57.196,58.748],
]
avg_orig = [106.72,58.31,61.68,57.69,58.61]

runs_simd = [
    [73.403,44.436,55.462,49.527,51.314],
    [71.545,44.54, 53.842,45.186,50.38 ],
    [84.361,48.69, 54.226,51.486,49.248],
    [87.254,49.08, 53.593,46.24, 49.025],
    [84.52, 57.682,52.726,49.994,49.751],
]
avg_simd = [80.22,48.89,53.97,48.49,49.94]

runs_collapse = [
    [99.427,59.068,59.602,57.275,58.249],
    [99.226,58.432,59.264,57.426,58.119],
    [96.629,57.698,65.226,57.856,57.868],
    [96.892,57.937,63.481,57.366,57.562],
    [95.42, 58.426,59.35, 57.436,58.238],
]
avg_collapse = [97.52,58.31,61.38,57.47,58.01]

# ── GENERATE ─────────────────────────────────────────────────────────────────

# 1. Version 1 – GFlops comparison
make_gflops_chart(
    'v1_gflops.png',
    'Version 1 – Normal Mult: Parallel Outside vs Inside (GFlop/s)',
    sizes, runs_1a, avg_1a, runs_1b, avg_1b,
    label_a='Parallel outside', label_b='Parallel inside'
)

# 2. Version 1 – Speedup & Efficiency
make_speedup_efficiency_chart(
    'v1_speedup_efficiency.png',
    'Version 1 – Normal Mult: Speedup & Efficiency (4 threads)',
    sizes, seq_1_avg, avg_1a, avg_1b,
    label_a='Parallel outside', label_b='Parallel inside'
)

# 3. Version 2 – GFlops comparison
make_gflops_chart(
    'v2_gflops.png',
    'Version 2 – Linear Mult: Parallel Outside vs Inside (GFlop/s)',
    sizes, runs_2a, avg_2a, runs_2b, avg_2b,
    label_a='Parallel outside', label_b='Parallel inside'
)

# 4. Version 2 – Speedup & Efficiency
make_speedup_efficiency_chart(
    'v2_speedup_efficiency.png',
    'Version 2 – Linear Mult: Speedup & Efficiency (4 threads)',
    sizes, seq_2_avg, avg_2a, avg_2b,
    label_a='Parallel outside', label_b='Parallel inside'
)

# 5. Original / SIMD / Collapse – GFlops comparison
make_single_gflops(
    'v1_variants_gflops.png',
    'Version 1 Variants – GFlop/s Comparison',
    sizes6,
    [runs_orig,  runs_simd,  runs_collapse],
    [avg_orig,   avg_simd,   avg_collapse],
    ['Original', 'SIMD',     'Collapse']
)

print("\nAll new charts done.")