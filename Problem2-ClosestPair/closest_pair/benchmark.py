"""Measures real running times of both algorithms for increasing N and checks agreement."""
from __future__ import annotations

import csv
import math
import os
import statistics
import time
from typing import List, Optional

from .algorithms import brute_force, divide_and_conquer
from .points import generate

DEFAULT_SIZES = [10, 50, 100, 250, 500, 1000, 2000, 3000, 4000, 8000, 16000, 32000, 64000, 128000]
BRUTE_FORCE_MAX_N = 4000   # O(N^2) in pure Python becomes very slow beyond this


def _time_ms(fn, pts, repeats: int):
    times = []
    result = None
    for _ in range(repeats):
        t0 = time.perf_counter()
        result = fn(pts)
        times.append((time.perf_counter() - t0) * 1000.0)
    return statistics.median(times), result


def run(sizes: Optional[List[int]] = None, seed: int = 2026, verbose: bool = True) -> List[dict]:
    sizes = sizes or DEFAULT_SIZES
    rows = []
    if verbose:
        print(f"{'N':>8} {'BF ms':>12} {'D&C ms':>10} {'BF/N^2 (ns)':>12} {'DC/NlogN (ns)':>14} {'speed-up':>9} {'agree':>6}")
    for n in sizes:
        pts = generate(n, seed=seed + n, kind="float", low=0, high=10 * n)
        repeats = 5 if n <= 1000 else (3 if n <= 16000 else 1)
        dc_ms, dc = _time_ms(divide_and_conquer, pts, repeats)
        bf_ms, bf, agree = None, None, None
        if n <= BRUTE_FORCE_MAX_N:
            bf_ms, bf = _time_ms(brute_force, pts, repeats if n <= 1000 else 1)
            agree = bf.distance == dc.distance
            if not agree:
                raise AssertionError(f"algorithms disagree for N={n}: {bf.distance} vs {dc.distance}")
        row = {
            "n": n,
            "bf_ms": bf_ms,
            "dc_ms": dc_ms,
            "distance": dc.distance,
            "bf_comparisons": bf.comparisons if bf else None,
            "dc_comparisons": dc.comparisons,
            "agree": agree,
        }
        rows.append(row)
        if verbose:
            nlogn = n * math.log2(n)
            print(f"{n:>8} {bf_ms if bf_ms is not None else float('nan'):>12.3f} {dc_ms:>10.3f} "
                  f"{(bf_ms * 1e6 / (n * n)) if bf_ms else float('nan'):>12.2f} {dc_ms * 1e6 / nlogn:>14.2f} "
                  f"{(bf_ms / dc_ms) if bf_ms else float('nan'):>9.1f} {str(agree) if agree is not None else '-':>6}")
    return rows


def write_csv(rows: List[dict], path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["N", "brute_force_ms", "divide_conquer_ms", "min_distance", "bf_distance_evaluations",
                    "dc_distance_evaluations", "both_agree"])
        for r in rows:
            w.writerow([r["n"], "" if r["bf_ms"] is None else f"{r['bf_ms']:.3f}", f"{r['dc_ms']:.3f}",
                        f"{r['distance']:.6f}", "" if r["bf_comparisons"] is None else r["bf_comparisons"],
                        r["dc_comparisons"], "" if r["agree"] is None else r["agree"]])


def agreement_check(trials: int = 300, seed: int = 7) -> int:
    """Runs both algorithms on many random sets (incl. all corner-case families); returns #checked."""
    import random
    from .points import CASES
    rnd = random.Random(seed)
    for t in range(trials):
        n = rnd.randint(0, 300)
        case = CASES[t % len(CASES)]
        kind = "int" if t % 2 == 0 else "float"
        pts = generate(n, seed=rnd.randint(0, 10**9), kind=kind, low=-50, high=50, case=case)
        bf, dc = brute_force(pts), divide_and_conquer(pts)
        if bf.distance != dc.distance:
            raise AssertionError(f"disagree on trial {t} ({case}, N={n}): {bf.distance} vs {dc.distance}")
    return trials
