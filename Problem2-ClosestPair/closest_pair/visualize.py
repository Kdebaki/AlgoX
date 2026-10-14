"""Matplotlib visualisations for Problem 2.

* plot_result      - all points, closest pair of each algorithm (different colours, joining
                     lines), the top-level dividing line and the strip of width 2d.
* animate_recursion - optional GIF/window animation of the recursive splitting and merging.
* plot_runtime     - running time vs N for both algorithms (see benchmark.py).
"""
from __future__ import annotations

import os
from typing import List, Optional, Sequence

import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
from matplotlib.patches import Rectangle

from .algorithms import ClosestPairResult, Point, SplitStep
from .points import fmt_point

BF_COLOR = "#d62728"   # red
DC_COLOR = "#1f77b4"   # blue
STRIP_COLOR = "#2ca02c"


def _ensure_dir(path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)


def plot_result(points: Sequence[Point], bf: ClosestPairResult, dc: ClosestPairResult,
                trace: List[SplitStep], title: str = "", save: Optional[str] = None, show: bool = True) -> None:
    fig, (ax, zoom) = plt.subplots(1, 2, figsize=(14, 8), gridspec_kw={"width_ratios": [3, 1.25]})
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    size = 28 if len(points) <= 200 else max(4, 4000 / len(points))
    ax.scatter(xs, ys, s=size, color="#555555", zorder=2, label=f"all points (N = {len(points)})")

    root = trace[-1] if trace else None
    if root is not None and dc.pair is not None:
        d = root.delta
        ax.axvline(root.mid_x, color=STRIP_COLOR, linestyle="--", linewidth=1.6, zorder=1,
                   label=f"dividing line x = {root.mid_x:.3f}")
        if d > 0:
            ax.axvspan(root.mid_x - d, root.mid_x + d, color=STRIP_COLOR, alpha=0.12, zorder=0,
                       label=f"strip of width 2d (d = min(dL, dR) = {d:.6f})")
        if root.strip:
            ax.scatter([p[0] for p in root.strip], [p[1] for p in root.strip], s=size * 1.6, facecolors="none",
                       edgecolors=STRIP_COLOR, linewidths=1.4, zorder=3, label=f"points in strip ({len(root.strip)})")

    if bf.pair is not None:
        (x1, y1), (x2, y2) = bf.pair
        ax.plot([x1, x2], [y1, y2], color=BF_COLOR, linewidth=4.5, alpha=0.75, zorder=4,
                label=f"Brute-Force pair: d = {bf.distance:.6f}")
        ax.scatter([x1, x2], [y1, y2], s=size * 4, color=BF_COLOR, zorder=5)
    if dc.pair is not None:
        (x1, y1), (x2, y2) = dc.pair
        ax.plot([x1, x2], [y1, y2], color=DC_COLOR, linewidth=2, linestyle="--", zorder=6,
                label=f"Divide-and-Conquer pair: d = {dc.distance:.6f}")
        ax.scatter([x1, x2], [y1, y2], s=size * 7, facecolors="none", edgecolors=DC_COLOR, linewidths=2, marker="s", zorder=6)
        # side panel: zoom on the closest pair (it is often tiny compared with the whole plot)
        span = max(max(xs) - min(xs), max(ys) - min(ys), 1e-9)
        cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
        r = max(dc.distance * 2.5, span / 200)
        zoom.scatter(xs, ys, s=40, color="#555555")
        if root is not None and root.delta > 0:
            zoom.axvline(root.mid_x, color=STRIP_COLOR, linestyle="--")
            zoom.axvspan(root.mid_x - root.delta, root.mid_x + root.delta, color=STRIP_COLOR, alpha=0.12)
        zoom.plot([bf.pair[0][0], bf.pair[1][0]], [bf.pair[0][1], bf.pair[1][1]], color=BF_COLOR, linewidth=6, alpha=0.7,
                  label="Brute-Force")
        zoom.plot([x1, x2], [y1, y2], color=DC_COLOR, linewidth=2.5, linestyle="--", label="Divide-and-Conquer")
        zoom.set_xlim(cx - r, cx + r)
        zoom.set_ylim(cy - r, cy + r)
        zoom.set_aspect("equal", adjustable="box")
        zoom.set_title(f"zoom on the closest pair\nd = {dc.distance:.6f}", fontsize=10)
        zoom.grid(True, alpha=0.3)
        zoom.legend(fontsize=8, loc="upper right")
    else:
        zoom.axis("off")

    text = [f"Brute-Force:        {bf.distance:.6f}  {fmt_point(bf.pair[0]) if bf.pair else ''} {fmt_point(bf.pair[1]) if bf.pair else ''}",
            f"Divide-and-Conquer: {dc.distance:.6f}  {fmt_point(dc.pair[0]) if dc.pair else ''} {fmt_point(dc.pair[1]) if dc.pair else ''}"]
    ax.set_title((title + "\n" if title else "") + "\n".join(text), fontsize=10, family="monospace", loc="left")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    ax.grid(True, alpha=0.3)
    ax.set_aspect("equal", adjustable="datalim")
    handles, labels = ax.get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower center", ncol=3, fontsize=9, framealpha=0.95)
    fig.tight_layout(rect=(0, 0.08, 1, 1))
    if save:
        _ensure_dir(save)
        fig.savefig(save, dpi=120)
    if show:
        plt.show()
    plt.close(fig)


def animate_recursion(points: Sequence[Point], trace: List[SplitStep], save: Optional[str] = None,
                      show: bool = True, interval_ms: int = 700) -> None:
    """One frame per recursion node (post-order = the order in which sub-problems are merged)."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    ymin, ymax = min(ys), max(ys)
    pad = (ymax - ymin) * 0.05 + 1e-9
    fig, ax = plt.subplots(figsize=(9, 7))

    def draw(k: int):
        ax.clear()
        ax.scatter(xs, ys, s=22, color="#888888")
        step = trace[k]
        ax.add_patch(Rectangle((step.x_min, ymin - pad), step.x_max - step.x_min, ymax - ymin + 2 * pad,
                               facecolor="#ffdd57", alpha=0.25, edgecolor="#c9a400"))
        ax.axvline(step.mid_x, color=STRIP_COLOR, linestyle="--")
        if step.delta > 0 and step.delta != float("inf"):
            ax.axvspan(step.mid_x - step.delta, step.mid_x + step.delta, color=STRIP_COLOR, alpha=0.15)
        if step.strip:
            ax.scatter([p[0] for p in step.strip], [p[1] for p in step.strip], s=60, facecolors="none",
                       edgecolors=STRIP_COLOR, linewidths=1.5)
        if step.pair:
            (x1, y1), (x2, y2) = step.pair
            ax.plot([x1, x2], [y1, y2], color=DC_COLOR, linewidth=2.5)
        ax.set_title(f"Merge step {k + 1}/{len(trace)}: depth {step.depth}, {step.size} points, dividing x = {step.mid_x:.2f}\n"
                     f"d = min(dL, dR) = {step.delta:.4f}, strip has {len(step.strip)} points, "
                     f"best after strip = {step.best:.4f}", fontsize=10)
        ax.set_xlim(min(xs) - pad, max(xs) + pad)
        ax.set_ylim(ymin - pad, ymax + pad)
        ax.grid(True, alpha=0.3)

    anim = FuncAnimation(fig, draw, frames=len(trace), interval=interval_ms, repeat=False)
    if save:
        _ensure_dir(save)
        anim.save(save, writer=PillowWriter(fps=max(1, 1000 // interval_ms)))
    if show:
        plt.show()
    plt.close(fig)


def plot_runtime(rows: List[dict], save: Optional[str] = None, show: bool = True) -> None:
    """rows: dicts with keys n, bf_ms (None if skipped), dc_ms."""
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5.2))
    bf = [(r["n"], r["bf_ms"]) for r in rows if r["bf_ms"] is not None]
    dc = [(r["n"], r["dc_ms"]) for r in rows]
    for ax, log in ((ax1, False), (ax2, True)):
        lim = max(n for n, _ in bf) if not log else None
        dcs = [(n, t) for n, t in dc if lim is None or n <= lim]
        ax.plot([n for n, _ in bf], [t for _, t in bf], "o-", color=BF_COLOR, label="Brute-Force  O(N^2)")
        ax.plot([n for n, _ in dcs], [t for _, t in dcs], "s-", color=DC_COLOR, label="Divide-and-Conquer  O(N log N)")
        ax.set_xlabel("N (number of points)")
        ax.set_ylabel("running time (ms)")
        ax.grid(True, which="both", alpha=0.3)
        ax.legend()
        if log:
            ax.set_xscale("log")
            ax.set_yscale("log")
            ax.set_title("log-log scale (slope ~2 vs ~1)")
        else:
            ax.set_title("linear scale")
    fig.suptitle("Closest pair: measured running time vs N (Python, median of repeated runs)")
    fig.tight_layout()
    if save:
        _ensure_dir(save)
        fig.savefig(save, dpi=120)
    if show:
        plt.show()
    plt.close(fig)
