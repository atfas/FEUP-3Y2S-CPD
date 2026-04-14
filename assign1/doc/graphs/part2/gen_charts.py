import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np
import os

os.makedirs('/home/claude/charts', exist_ok=True)

BLUE = '#378ADD'
ORANGE = '#D85A30'
GRAY = '#888780'

def gflops(n, t):
    return (2 * n**3) / t / 1e9

def make_chart(filename, title, sizes, raw_runs, avgs, size_labels=None):
    fig, ax = plt.subplots(figsize=(8, 4.5))
    fig.patch.set_facecolor('white')
    ax.set_facecolor('white')

    # scatter all runs
    for run in raw_runs:
        ys = [gflops(n, t) for n, t in zip(sizes, run)]
        ax.scatter(sizes, ys, color=BLUE, s=40, zorder=3, alpha=0.85)

    # average line
    avg_y = [gflops(n, t) for n, t in zip(sizes, avgs)]
    ax.plot(sizes, avg_y, color=ORANGE, linewidth=2.2, marker='D',
            markersize=6, markerfacecolor=ORANGE, zorder=4, label='Average')

    ax.set_xlabel('Matrix size (n×n)', fontsize=11, color=GRAY)
    ax.set_ylabel('Performance (GFlop/s)', fontsize=11, color=GRAY)
    ax.set_title(title, fontsize=13, fontweight='bold', pad=12)

    if size_labels:
        ax.set_xticks(sizes)
        ax.set_xticklabels(size_labels, fontsize=9)
    else:
        ax.set_xticks(sizes)
        ax.set_xticklabels([f'{n}×{n}' for n in sizes], fontsize=9)

    ax.tick_params(colors=GRAY)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_color('#dddddd')
    ax.spines['bottom'].set_color('#dddddd')
    ax.yaxis.grid(True, color='#eeeeee', zorder=0)
    ax.set_axisbelow(True)

    from matplotlib.lines import Line2D
    from matplotlib.patches import Patch
    legend_elements = [
        Line2D([0],[0], marker='o', color='w', markerfacecolor=BLUE, markersize=8, label='Measured runs'),
        Line2D([0],[0], color=ORANGE, linewidth=2, marker='D', markersize=6, markerfacecolor=ORANGE, label='Average'),
    ]
    ax.legend(handles=legend_elements, fontsize=9, frameon=False)

    plt.tight_layout()
    path = f'/home/claude/charts/{filename}'
    plt.savefig(path, dpi=150, bbox_inches='tight', facecolor='white')
    plt.close()
    print(f'Saved {path}')

# 1. Multiplication C++
sizes1 = [1024, 1536, 2048, 2560, 3072]
raw1 = [
    [2.093, 7.945, 23.903, 56.531, 125.307],
    [2.185, 7.969, 23.112, 56.508, 188.922],
    [2.196, 8.657, 24.331, 57.194, 192.844],
    [2.102, 8.042, 25.111, 56.785, 183.719],
    [2.191, 8.699, 24.243, 56.878, 188.836],
]
avg1 = [2.185, 8.042, 24.243, 56.785, 188.836]
make_chart('chart1_cpp.png', 'Matrix Multiplication – C++', sizes1, raw1, avg1)

# 2. Multiplication Java
raw2 = [
    [2.245, 10.113, 26.615, 64.351, 91.712],
    [2.209, 8.572, 27.615, 64.152, 91.547],
    [2.257, 8.621, 26.728, 64.101, 91.612],
    [2.163, 8.199, 29.303, 64.552, 91.423],
    [2.236, 8.263, 29.283, 64.924, 91.572],
]
avg2 = [2.236, 8.572, 27.615, 64.351, 91.572]
make_chart('chart2_java.png', 'Matrix Multiplication – Java', sizes1, raw2, avg2)

# 3. Linear Mult C++ (small)
raw3 = [
    [0.379, 1.028, 3.714, 7.293, 12.606],
    [0.406, 1.498, 3.736, 7.223, 12.58],
    [0.349, 1.282, 3.385, 7.221, 12.544],
    [0.412, 1.22,  3.311, 7.245, 13.759],
    [0.337, 1.369, 3.35,  7.154, 13.104],
]
avg3 = [0.379, 1.282, 3.385, 7.223, 12.606]
make_chart('chart3_lin_cpp_small.png', 'Linear Matrix Multiplication – C++ (small sizes)', sizes1, raw3, avg3)

# 4. Linear Mult Java
raw4 = [
    [0.353, 1.272, 3.825, 7.661, 14.237],
    [0.394, 1.264, 3.525, 7.971, 13.69],
    [0.359, 1.439, 3.569, 7.696, 13.721],
    [0.389, 1.381, 3.423, 7.751, 13.472],
    [0.375, 1.462, 3.905, 7.648, 13.559],
]
avg4 = [0.375, 1.381, 3.569, 7.696, 13.69]
make_chart('chart4_lin_java.png', 'Linear Matrix Multiplication – Java', sizes1, raw4, avg4)

# 5. Linear Mult C++ (large)
sizes5 = [4096, 6144, 8192, 10240]
raw5 = [
    [32.121, 112.72,  277.952, 521.793],
    [32.135, 112.718, 268.049, 523.316],
    [32.232, 112.413, 280.897, 529.614],
]
avg5 = [32.135, 112.718, 277.952, 523.316]
make_chart('chart5_lin_cpp_large.png', 'Linear Matrix Multiplication – C++ (large sizes)', sizes5, raw5, avg5)

# 6. Block Mult C++ block=128
sizes6 = [4096, 6144, 8192, 10240]
raw6 = [
    [29.027, 72.547, 206.331, 341.754],
    [27.628, 72.671, 206.709, 341.686],
    [27.55,  72.621, 206.481, 341.636],
]
avg6 = [27.628, 72.621, 206.481, 341.686]
make_chart('chart6_block128.png', 'Block Matrix Multiplication – C++ (block=128)', sizes6, raw6, avg6)

# 7. Block Mult C++ block=256
raw7 = [
    [21.787, 73.217, 177.295, 340.081],
    [21.818, 73.031, 177.427, 340.308],
    [21.87,  73.31,  177.277, 340.351],
]
avg7 = [21.818, 73.217, 177.295, 340.308]
make_chart('chart7_block256.png', 'Block Matrix Multiplication – C++ (block=256)', sizes6, raw7, avg7)

# 8. Block Mult C++ block=512
raw8 = [
    [20.27,  70.151, 171.824, 319.582],
    [20.258, 70.474, 172.518, 320.078],
    [20.51,  70.106, 172.186, 319.862],
]
avg8 = [20.27, 70.151, 172.186, 319.862]
make_chart('chart8_block512.png', 'Block Matrix Multiplication – C++ (block=512)', sizes6, raw8, avg8)

print("All charts done.")
