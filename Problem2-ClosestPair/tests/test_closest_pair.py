"""Automated tests for Problem 2 (standard-library unittest).

Run from the project root:
    python -m unittest discover -s python/Problem2-ClosestPair/tests -v
"""
import math
import os
import random
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from closest_pair.algorithms import brute_force, distance, divide_and_conquer  # noqa: E402
from closest_pair.points import CASES, generate, read_csv, write_csv  # noqa: E402


class ClosestPairTests(unittest.TestCase):
    def both(self, pts):
        bf, dc = brute_force(pts), divide_and_conquer(pts)
        self.assertEqual(bf.distance, dc.distance, f"algorithms disagree on {pts}")
        for r in (bf, dc):
            if r.pair is not None:
                self.assertEqual(distance(*r.pair), r.distance, "reported pair does not have the reported distance")
                self.assertIn(r.pair[0], pts)
                self.assertIn(r.pair[1], pts)
        return bf, dc

    # ---- N < 2 and N = 2 ----
    def test_empty(self):
        bf, dc = self.both([])
        self.assertTrue(math.isinf(bf.distance) and bf.pair is None and dc.pair is None)

    def test_single_point(self):
        bf, dc = self.both([(3, 4)])
        self.assertTrue(math.isinf(dc.distance) and dc.pair is None)

    def test_two_points(self):
        bf, dc = self.both([(0, 0), (3, 4)])
        self.assertEqual(dc.distance, 5.0)
        self.assertEqual(set(dc.pair), {(0, 0), (3, 4)})

    def test_three_points_base_case(self):
        _, dc = self.both([(0, 0), (10, 0), (10, 1)])
        self.assertEqual(dc.distance, 1.0)

    # ---- duplicates / collinear / shared coordinates ----
    def test_duplicate_points_distance_zero(self):
        pts = [(5, 5), (1, 2), (9, 9), (1, 2), (7, 3)]
        _, dc = self.both(pts)
        self.assertEqual(dc.distance, 0.0)
        self.assertEqual(dc.pair, ((1, 2), (1, 2)))

    def test_all_points_identical(self):
        _, dc = self.both([(2.5, 2.5)] * 10)
        self.assertEqual(dc.distance, 0.0)

    def test_collinear_diagonal(self):
        pts = [(x, 2 * x + 1) for x in (0, 7, 3, 12, 20, 4)]
        _, dc = self.both(pts)
        self.assertAlmostEqual(dc.distance, math.sqrt(5), places=12)

    def test_all_same_x_vertical_line(self):
        pts = [(4, y) for y in (10, 1, 7, 30, 15, 8)]
        _, dc = self.both(pts)
        self.assertEqual(dc.distance, 1.0)

    def test_all_same_y_horizontal_line(self):
        pts = [(x, -3) for x in (10, 1, 7, 30, 15, 8.5)]   # sorted gaps: 6, 1.5, 1.5, 5, 15
        _, dc = self.both(pts)
        self.assertEqual(dc.distance, 1.5)

    def test_closest_pair_straddles_dividing_line(self):
        # left and right halves are internally far apart; the answer crosses the middle
        pts = [(0, 0), (1, 100), (2, 200), (49.9, 50), (50.1, 50.05), (98, 0), (99, 100), (100, 200)]
        _, dc = self.both(pts)
        self.assertAlmostEqual(dc.distance, math.hypot(0.2, 0.05), places=12)

    def test_many_points_share_dividing_x(self):
        pts = [(5, y) for y in range(0, 100, 7)] + [(4, 3.5), (6, 50)]
        self.both(pts)

    def test_negative_and_float_coordinates(self):
        pts = [(-1.5, -2.25), (-1.4, -2.2), (3.3, 7.1), (-8.0, 0.0), (0.0, 0.0)]
        _, dc = self.both(pts)
        self.assertAlmostEqual(dc.distance, math.hypot(0.1, 0.05), places=12)

    def test_grid_with_ties(self):
        pts = [(i, j) for i in range(10) for j in range(10)]
        _, dc = self.both(pts)
        self.assertEqual(dc.distance, 1.0)

    # ---- invalid input ----
    def test_invalid_points_rejected(self):
        for bad in ([(1, 2, 3)], [(1,)], [("a", 2)], [(float("nan"), 1)], [(1, float("inf"))], [(True, 1)]):
            with self.assertRaises(ValueError):
                brute_force(bad)
            with self.assertRaises(ValueError):
                divide_and_conquer(bad)

    def test_generator_rejects_bad_arguments(self):
        with self.assertRaises(ValueError):
            generate(-1)
        with self.assertRaises(ValueError):
            generate(5, kind="complex")
        with self.assertRaises(ValueError):
            generate(5, case="spiral")

    # ---- randomised comparison on identical point sets ----
    def test_random_sets_agree(self):
        rnd = random.Random(123)
        for t in range(400):
            n = rnd.randint(0, 200)
            case = CASES[t % len(CASES)]
            pts = generate(n, seed=rnd.randint(0, 10**9), kind=("int", "float")[t % 2], low=-100, high=100, case=case)
            self.both(pts)

    def test_large_set_agrees(self):
        pts = generate(3000, seed=99, kind="float", low=0, high=1e6)
        self.both(pts)

    def test_reproducible_seed(self):
        self.assertEqual(generate(50, seed=5), generate(50, seed=5))
        self.assertNotEqual(generate(50, seed=5), generate(50, seed=6))

    # ---- efficiency: O(N log N) distance evaluations ----
    def test_divide_and_conquer_evaluations_are_n_log_n(self):
        for n in (1000, 8000):
            pts = generate(n, seed=n, kind="float", low=0, high=1e6)
            dc = divide_and_conquer(pts)
            self.assertLess(dc.comparisons, 8 * n * math.log2(n), "too many distance evaluations")
            self.assertLess(dc.comparisons, n * (n - 1) / 2 / 10)

    def test_trace_records_dividing_line_and_strip(self):
        pts = generate(64, seed=3, kind="float")
        trace = []
        dc = divide_and_conquer(pts, trace=trace)
        root = trace[-1]
        self.assertEqual(root.depth, 0)
        self.assertEqual(root.size, 64)
        self.assertAlmostEqual(root.best, dc.distance)
        for p in root.strip:   # every strip point lies within d of the dividing line
            self.assertLess(abs(p[0] - root.mid_x), root.delta + 1e-12)

    # ---- CSV ----
    def test_csv_round_trip(self):
        pts = [(1, 2), (3.5, -4.25), (0, 0)]
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "p.csv")
            write_csv(pts, path)
            self.assertEqual(read_csv(path), pts)


if __name__ == "__main__":
    unittest.main(verbosity=2)
