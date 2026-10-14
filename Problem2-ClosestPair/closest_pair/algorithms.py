"""Closest pair of points: brute force O(N^2) and divide and conquer O(N log N).

Both algorithms compare squared distances (dx*dx + dy*dy) and take a single square
root at the end. For the same pair of points they therefore compute bit-identical
values, so their minimum distances agree exactly (no floating-point tolerance needed).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Iterable, List, Optional, Sequence, Tuple

Point = Tuple[float, float]


@dataclass
class ClosestPairResult:
    algorithm: str
    distance: float                         # math.inf when fewer than 2 points
    pair: Optional[Tuple[Point, Point]]     # None when fewer than 2 points
    comparisons: int = 0                    # number of distance evaluations


@dataclass
class SplitStep:
    """One node of the divide-and-conquer recursion (recorded for plots/animation)."""
    depth: int
    x_min: float            # x-range covered by this sub-problem
    x_max: float
    mid_x: float            # dividing line
    size: int
    delta: float            # d = min(d_left, d_right) before the strip is examined
    strip: List[Point] = field(default_factory=list)   # points with |x - mid_x| < d
    best: float = math.inf  # distance after the strip check (result of this node)
    pair: Optional[Tuple[Point, Point]] = None


def validate_points(points: Iterable[Sequence[float]]) -> List[Point]:
    """Returns the points as (float, float) tuples; rejects malformed or non-finite input."""
    out: List[Point] = []
    for i, p in enumerate(points):
        if len(p) != 2:
            raise ValueError(f"point #{i + 1} must have exactly 2 coordinates, got {p!r}")
        x, y = p
        if isinstance(x, bool) or isinstance(y, bool) or not isinstance(x, (int, float)) or not isinstance(y, (int, float)):
            raise ValueError(f"point #{i + 1} has non-numeric coordinates: {p!r}")
        if not (math.isfinite(x) and math.isfinite(y)):
            raise ValueError(f"point #{i + 1} has a non-finite coordinate: {p!r}")
        out.append((x, y))
    return out


def _d2(p: Point, q: Point) -> float:
    dx = p[0] - q[0]
    dy = p[1] - q[1]
    return dx * dx + dy * dy


# --------------------------------------------------------------------------- brute force

def brute_force(points: Iterable[Sequence[float]]) -> ClosestPairResult:
    """Checks all N(N-1)/2 pairs. Time O(N^2), extra space O(1)."""
    pts = validate_points(points)
    n = len(pts)
    best = math.inf
    pair = None
    comparisons = 0
    for i in range(n - 1):
        xi, yi = pts[i]
        for j in range(i + 1, n):
            dx = xi - pts[j][0]
            dy = yi - pts[j][1]
            d = dx * dx + dy * dy
            comparisons += 1
            if d < best:
                best = d
                pair = (pts[i], pts[j])
    if pair is None:
        return ClosestPairResult("Brute-Force", math.inf, None, comparisons)
    return ClosestPairResult("Brute-Force", math.sqrt(best), pair, comparisons)


# --------------------------------------------------------------------------- divide and conquer

def divide_and_conquer(points: Iterable[Sequence[float]],
                       trace: Optional[List[SplitStep]] = None) -> ClosestPairResult:
    """Shamos-Hoey divide and conquer.

    The points are sorted by x once. Each call returns its points sorted by y, built by
    *merging* the two y-sorted halves (linear time), so no sort happens inside the recursion:
    T(N) = 2T(N/2) + O(N) = O(N log N). (Re-sorting the strip in every call would give
    O(N log^2 N).)

    If ``trace`` is a list, one SplitStep per recursion node is appended (post-order; the
    last element is the root, i.e. the top-level dividing line and strip).
    """
    pts = validate_points(points)
    n = len(pts)
    if n < 2:
        return ClosestPairResult("Divide-and-Conquer", math.inf, None, 0)
    px = sorted(pts)                       # by x, ties by y
    counter = [0]
    best, pair, _ = _recurse(px, 0, n, counter, trace, 0)
    return ClosestPairResult("Divide-and-Conquer", math.sqrt(best), pair, counter[0])


def _recurse(px: List[Point], lo: int, hi: int, counter: List[int],
             trace: Optional[List[SplitStep]], depth: int):
    n = hi - lo
    if n <= 3:                             # base case: brute force on <= 3 points
        best = math.inf
        pair = None
        for i in range(lo, hi - 1):
            for j in range(i + 1, hi):
                d = _d2(px[i], px[j])
                counter[0] += 1
                if d < best:
                    best = d
                    pair = (px[i], px[j])
        py = sorted(px[lo:hi], key=lambda p: p[1])
        return best, pair, py

    mid = (lo + hi) // 2
    mid_x = px[mid][0]                     # vertical dividing line x = mid_x
    dl, pl, yl = _recurse(px, lo, mid, counter, trace, depth + 1)
    dr, pr, yr = _recurse(px, mid, hi, counter, trace, depth + 1)
    best, pair = (dl, pl) if dl <= dr else (dr, pr)

    # merge the two y-sorted lists (linear time)
    py: List[Point] = []
    i = j = 0
    while i < len(yl) and j < len(yr):
        if yl[i][1] <= yr[j][1]:
            py.append(yl[i]); i += 1
        else:
            py.append(yr[j]); j += 1
    py.extend(yl[i:])
    py.extend(yr[j:])

    delta2 = best
    # strip of width 2d around the dividing line, already in y order
    strip = [p for p in py if (p[0] - mid_x) * (p[0] - mid_x) < best]
    m = len(strip)
    for a in range(m):
        pa = strip[a]
        for b in range(a + 1, m):
            dy = strip[b][1] - pa[1]
            if dy * dy >= best:            # later points are even farther in y: at most 7 checks
                break
            d = _d2(pa, strip[b])
            counter[0] += 1
            if d < best:
                best = d
                pair = (pa, strip[b])

    if trace is not None:
        trace.append(SplitStep(depth=depth, x_min=px[lo][0], x_max=px[hi - 1][0], mid_x=mid_x, size=n,
                               delta=math.sqrt(delta2), strip=strip, best=math.sqrt(best), pair=pair))
    return best, pair, py


def distance(p: Point, q: Point) -> float:
    return math.sqrt(_d2(p, q))
