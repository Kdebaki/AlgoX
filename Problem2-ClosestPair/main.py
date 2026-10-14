"""Problem 2 - Closest Pair of Points: command-line entry point.

Run from the project root (with the virtual environment activated):
    python python/Problem2-ClosestPair/main.py                        # reads N from standard input
    python python/Problem2-ClosestPair/main.py --n 50 --seed 7 --plot
    python python/Problem2-ClosestPair/main.py --csv input/p2_points_sample.csv --plot
    python python/Problem2-ClosestPair/main.py --n 40 --case duplicates --plot
    python python/Problem2-ClosestPair/main.py --n 32 --animate
    python python/Problem2-ClosestPair/main.py --benchmark
"""
from __future__ import annotations

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from closest_pair.algorithms import brute_force, divide_and_conquer  # noqa: E402
from closest_pair.points import CASES, fmt_point, generate, read_csv, write_csv  # noqa: E402


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Closest pair of points: brute force vs divide and conquer")
    src = p.add_mutually_exclusive_group()
    src.add_argument("--n", type=int, help="number of random points (if omitted and no --csv, N is read from stdin)")
    src.add_argument("--csv", help="read points from a CSV file with columns x,y")
    p.add_argument("--seed", type=int, default=42, help="random seed (reproducible point sets)")
    p.add_argument("--type", choices=["int", "float"], default="int", help="integer or floating-point coordinates")
    p.add_argument("--low", type=float, default=0, help="minimum coordinate")
    p.add_argument("--high", type=float, default=1000, help="maximum coordinate")
    p.add_argument("--case", choices=CASES, default="random", help="corner-case family of points")
    p.add_argument("--save-points", help="write the point set to this CSV file")
    p.add_argument("--out", default=None, help="result CSV (default output_csv/p2_result_<name>.csv)")
    p.add_argument("--plot", action="store_true", help="show the points / closest pair / strip plot")
    p.add_argument("--save-plot", help="save the plot as PNG (works without --plot)")
    p.add_argument("--animate", action="store_true", help="animate the recursive splitting and merging")
    p.add_argument("--save-gif", help="save the animation as a GIF")
    p.add_argument("--no-show", action="store_true", help="never open windows (only save files)")
    p.add_argument("--benchmark", action="store_true", help="measure running time vs N and plot it")
    return p.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    if args.no_show:
        import matplotlib
        matplotlib.use("Agg")

    if args.benchmark:
        from closest_pair import benchmark
        from closest_pair.visualize import plot_runtime
        print("Agreement check on", benchmark.agreement_check(), "random point sets (all corner-case families): OK")
        rows = benchmark.run()
        benchmark.write_csv(rows, "reports/benchmarks/p2_benchmark.csv")
        plot_runtime(rows, save="reports/screenshots/p2_runtime_vs_n.png", show=not args.no_show)
        print("Written reports/benchmarks/p2_benchmark.csv and reports/screenshots/p2_runtime_vs_n.png")
        return 0

    if args.csv:
        points = read_csv(args.csv)
        name = os.path.splitext(os.path.basename(args.csv))[0]
    else:
        n = args.n
        if n is None:
            try:
                n = int(input("Enter N (number of points): ").strip().lstrip("﻿"))  # BOM from PowerShell pipes
            except ValueError:
                print("Error: N must be an integer", file=sys.stderr)
                return 1
        if n < 0:
            print("Error: N must be >= 0", file=sys.stderr)
            return 1
        points = generate(n, seed=args.seed, kind=args.type, low=args.low, high=args.high, case=args.case)
        name = f"n{n}_{args.case}_{args.type}_seed{args.seed}"
    if args.save_points:
        write_csv(points, args.save_points)

    t0 = time.perf_counter()
    bf = brute_force(points)
    bf_ms = (time.perf_counter() - t0) * 1000
    trace = []
    t0 = time.perf_counter()
    dc = divide_and_conquer(points)
    dc_ms = (time.perf_counter() - t0) * 1000
    divide_and_conquer(points, trace=trace)          # second, untimed run that records the recursion

    print(f"N = {len(points)}")
    if bf.pair is None:
        print("Brute-Force: no pair exists (N < 2)")
        print("Divide-and-Conquer: no pair exists (N < 2)")
    else:
        print(f"Brute-Force: {bf.distance:.6f} {fmt_point(bf.pair[0])} {fmt_point(bf.pair[1])}")
        print(f"Divide-and-Conquer: {dc.distance:.6f} {fmt_point(dc.pair[0])} {fmt_point(dc.pair[1])}")
    print(f"Execution time (ms): Brute-Force = {bf_ms:.3f}, Divide-and-Conquer = {dc_ms:.3f}")
    agree = bf.distance == dc.distance
    print(f"Both algorithms agree on the minimum distance: {'YES' if agree else 'NO'}"
          f"  (distance evaluations: brute force {bf.comparisons}, divide and conquer {dc.comparisons})")

    out = args.out or f"output_csv/p2_result_{name}.csv"
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="") as f:
        f.write("algorithm,min_distance,p1_x,p1_y,p2_x,p2_y,time_ms,distance_evaluations\n")
        for r, ms in ((bf, bf_ms), (dc, dc_ms)):
            if r.pair is None:
                f.write(f"{r.algorithm},inf,,,,,{ms:.3f},{r.comparisons}\n")
            else:
                (a, b), (c, d) = r.pair
                f.write(f"{r.algorithm},{r.distance:.6f},{a},{b},{c},{d},{ms:.3f},{r.comparisons}\n")
    print(f"Results written to {out}")

    if (args.plot or args.save_plot) and len(points) >= 2:
        from closest_pair.visualize import plot_result
        plot_result(points, bf, dc, trace, title=f"Closest pair, {name}", save=args.save_plot,
                    show=args.plot and not args.no_show)
        if args.save_plot:
            print(f"Plot saved to {args.save_plot}")
    if (args.animate or args.save_gif) and len(points) >= 4:
        from closest_pair.visualize import animate_recursion
        animate_recursion(points, trace, save=args.save_gif, show=args.animate and not args.no_show)
        if args.save_gif:
            print(f"Animation saved to {args.save_gif}")
    return 0 if agree else 2


if __name__ == "__main__":
    sys.exit(main())
