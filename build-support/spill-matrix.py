#!/usr/bin/env python3
"""Measure allocator chapters, check cohort membership, and render scaled spills."""
import argparse
from collections import Counter, defaultdict
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
CHAPTERS = range(20, 26)
START = "<!-- spill-matrix:start -->"
END = "<!-- spill-matrix:end -->"


def read_log(path):
    data = path.read_bytes()
    return data.decode("utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8")


def read_results(log_dir):
    results = {}
    for chapter in CHAPTERS:
        path = log_dir / f"spill-matrix-{chapter}.log"
        text = read_log(path)
        if re.search(r"FAILED|(?:[Ff]ailures|Spill golden failures): [1-9]", text):
            raise ValueError(f"Chapter {chapter} failed; see {path}")
        if not re.search(r"(?:Tests:|Historical allocations:).*failures: 0", text):
            raise ValueError(f"Chapter {chapter} has no successful completion record")
        cohorts = defaultdict(list)
        sums = {}
        for line in text.splitlines():
            if line.startswith("allocation,"):
                _, cohort, test, cpu, abi, moves, scaled = line.split(",")
                cohort = int(cohort.removeprefix("Chapter"))
                # These two Chapter 20 programs moved into Chapter20Test in 21.
                if cohort == 20 and test in ("BrainFuckTest.testBrainfuck", "MergeSortTest.testMergeSort"):
                    test = "Chapter20Test." + test.split(".")[1]
                abi = "win64" if abi.lower() == "win64" else abi
                cohorts[cohort].append((test, cpu, abi, int(moves), int(scaled)))
            elif re.match(r"Chapter\d+,ALL,ALL,", line):
                cohort, _, _, count, moves, scaled = line.split(",")
                sums[int(cohort.removeprefix("Chapter"))] = (int(count), int(moves), int(scaled))
        expected = set(range(20, chapter + 1))
        if set(cohorts) != expected or set(sums) != expected:
            raise ValueError(f"Chapter {chapter}: missing or unexpected cohorts")
        for cohort, rows in cohorts.items():
            measured = (len(rows), sum(r[3] for r in rows), sum(r[4] for r in rows))
            if measured != sums[cohort]:
                raise ValueError(f"Chapter {chapter}, cohort {cohort}: inconsistent report totals")
        results[chapter] = cohorts
    # A count alone can hide a missing case replaced by a different one.
    for cohort in CHAPTERS:
        baseline = Counter(r[:3] for r in results[cohort][cohort])
        for chapter in range(cohort + 1, 26):
            actual = Counter(r[:3] for r in results[chapter][cohort])
            if actual != baseline:
                raise ValueError(f"Chapter {chapter}, cohort {cohort}: membership differs\n"
                                 f"  missing: {baseline - actual}\n  extra: {actual - baseline}")
    return results


def table(results, last=25):
    chapters = range(20, last + 1)
    lines = ["| Test cohort | # tests | " + " | ".join(f"Ch {c}" for c in chapters) + " |",
             "|---|---:|" + "---:|" * len(chapters)]
    for cohort in chapters:
        cells = [f"Ch {cohort}", str(len(results[cohort][cohort]))]
        cells += ["" if c < cohort else f"{sum(r[4] for r in results[c][cohort]):,}" for c in chapters]
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines)


def update_doc(path, matrix):
    text = path.read_text(encoding="utf-8")
    if text.count(START) != 1 or text.count(END) != 1:
        raise ValueError(f"Missing or duplicate matrix markers in {path}")
    before, rest = text.split(START)
    _, after = rest.split(END)
    path.write_text(before + START + "\n" + matrix + "\n" + END + after,
                    encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log-dir", type=Path, default=ROOT / "build/spill-stats")
    parser.add_argument("--reuse", action="store_true", help="read completed logs without rerunning tests")
    parser.add_argument("--update-docs", action="store_true", help="refresh the marked tables in docs and READMEs")
    args = parser.parse_args()
    args.log_dir.mkdir(parents=True, exist_ok=True)
    if not args.reuse:
        for chapter in CHAPTERS:
            path = args.log_dir / f"spill-matrix-{chapter}.log"
            print(f"Measuring chapter {chapter}: {path}", flush=True)
            with path.open("wb") as log:
                run = subprocess.run(["make", "-C", str(ROOT / f"chapter{chapter}"), "spill-stats"],
                                     stdout=log, stderr=subprocess.STDOUT)
            if run.returncode:
                raise ValueError(f"Chapter {chapter} failed (exit {run.returncode}); see {path}")
    results = read_results(args.log_dir)
    matrix = table(results)
    (args.log_dir / "matrix.md").write_text(matrix + "\n", encoding="utf-8", newline="\n")
    if args.update_docs:
        paths = [(ROOT / "docs/regalloc-spills.md", matrix)]
        paths += [(ROOT / f"chapter{c}/README.md", table(results, c)) for c in CHAPTERS]
        # Validate all destinations before changing any document.
        for path, _ in paths:
            text = path.read_text(encoding="utf-8")
            if text.count(START) != 1 or text.count(END) != 1:
                raise ValueError(f"Missing or duplicate matrix markers in {path}")
        for path, value in paths:
            update_doc(path, value)
    print(matrix)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        sys.exit(str(error))
