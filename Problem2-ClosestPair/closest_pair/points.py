"""Point generation (reproducible with a seed) and CSV input/output."""
from __future__ import annotations

import csv
import os
import random
from typing import List, Optional

from .algorithms import Point, validate_points

CASES = ("random", "duplicates", "collinear", "same-x", "same-y", "grid", "clustered")


def generate(n: int, seed: Optional[int] = 42, kind: str = "int", low: float = 0, high: float = 1000,
             case: str = "random") -> List[Point]:
    """N random points; ``kind`` is "int" or "float"; ``case`` selects a corner-case family."""
    if n < 0:
        raise ValueError("N must be >= 0")
    if kind not in ("int", "float"):
        raise ValueError("kind must be 'int' or 'float'")
    if case not in CASES:
        raise ValueError(f"case must be one of {', '.join(CASES)}")
    rnd = random.Random(seed)

    def coord() -> float:
        return rnd.randint(int(low), int(high)) if kind == "int" else rnd.uniform(low, high)

    if case == "random":
        return [(coord(), coord()) for _ in range(n)]
    if case == "duplicates":                      # about 10% exact copies of earlier points
        pts: List[Point] = []
        for _ in range(n):
            pts.append(rnd.choice(pts) if pts and rnd.random() < 0.1 else (coord(), coord()))
        return pts
    if case == "collinear":                       # all on the line y = 2x + 1
        xs = [coord() for _ in range(n)]
        return [(x, 2 * x + 1) for x in xs]
    if case == "same-x":                          # vertical line
        x0 = coord()
        return [(x0, coord()) for _ in range(n)]
    if case == "same-y":                          # horizontal line
        y0 = coord()
        return [(coord(), y0) for _ in range(n)]
    if case == "grid":                            # many equal distances (ties)
        side = max(1, int(n ** 0.5 + 0.999))
        return [(float(i % side) * 10 if kind == "float" else (i % side) * 10,
                 float(i // side) * 10 if kind == "float" else (i // side) * 10) for i in range(n)]
    # clustered: points around a few centres
    centres = [(coord(), coord()) for _ in range(max(1, n // 20))]
    out = []
    for _ in range(n):
        cx, cy = rnd.choice(centres)
        if kind == "int":
            out.append((cx + rnd.randint(-5, 5), cy + rnd.randint(-5, 5)))
        else:
            out.append((cx + rnd.uniform(-5, 5), cy + rnd.uniform(-5, 5)))
    return out


def read_csv(path: str) -> List[Point]:
    """Reads "x,y" rows (an optional header row is skipped)."""
    pts = []
    with open(path, newline="", encoding="utf-8-sig") as f:
        for lineno, row in enumerate(csv.reader(f), start=1):
            if not row or all(not c.strip() for c in row) or row[0].strip().startswith("#"):
                continue
            if len(row) < 2:
                raise ValueError(f"{path}:{lineno}: expected x,y")
            try:
                x, y = float(row[0]), float(row[1])
            except ValueError:
                if lineno == 1:
                    continue                      # header
                raise ValueError(f"{path}:{lineno}: non-numeric value in {row!r}")
            pts.append((int(x) if x.is_integer() and "." not in row[0] else x,
                        int(y) if y.is_integer() and "." not in row[1] else y))
    return validate_points(pts)


def write_csv(points: List[Point], path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["x", "y"])
        w.writerows(points)


def fmt_coord(v: float) -> str:
    return str(v) if isinstance(v, int) else f"{v:.6f}"


def fmt_point(p: Point) -> str:
    return f"({fmt_coord(p[0])}, {fmt_coord(p[1])})"
