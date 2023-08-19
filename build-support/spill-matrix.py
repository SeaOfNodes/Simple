#!/usr/bin/env python3
"""Measure fixed cohorts: dynamic ARM/RISC-V ops, allocator ops, and x86 estimates."""
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


def read_dynamic(results):
    dynamic = {}
    values = {}
    records = []
    for chapter in range(21, 26):
        path = ROOT / f"build/dynamic-stats/{chapter}/dynamic.log"
        text = read_log(path)
        if "FAILED," in text:
            raise ValueError(f"Dynamic measurement failed; see {path}")
        completion = re.search(r"Dynamic cases: (\d+); failures: 0", text)
        if not completion:
            raise ValueError(f"Incomplete dynamic measurement; see {path}")
        cohorts = defaultdict(list)
        for line in text.splitlines():
            if not line.startswith("dynamic,"):
                continue
            _, cohort, test, cpu, count, ops, ra, value, inputs = line.split(",")
            cohort = int(cohort.removeprefix("Chapter"))
            test = test.removeprefix("Chapter25Test.") if cohort == 25 else test
            row = (test, cpu, inputs, int(count), int(ops), int(ra))
            cohorts[cohort].append(row)
            identity = (cohort, test, inputs)
            if identity in values and values[identity] != value:
                raise ValueError(f"Dynamic results disagree: {identity}: {values[identity]} vs {value}")
            values[identity] = value
            records.append(f"{chapter},{cohort},{test},{cpu},{inputs},{count},{ops},{ra},{value}")
        if set(cohorts) != set(range(20, chapter+1)):
            raise ValueError(f"Missing dynamic cohort in Chapter {chapter}")
        if sum(map(len, cohorts.values())) != int(completion[1]):
            raise ValueError(f"Dynamic record count differs from completion record in {path}")
        for cohort, rows in cohorts.items():
            available = Counter((r[0], r[1]) for r in results[chapter][cohort])
            measured = Counter(r[:2] for r in rows)
            if measured - available:
                raise ValueError(f"Dynamic cases absent from static cohort {chapter}/{cohort}")
        dynamic[chapter] = cohorts
    for cohort in CHAPTERS:
        first = max(21, cohort)
        baseline = Counter(r[:4] for r in dynamic[first][cohort])
        for chapter in range(first+1, 26):
            if Counter(r[:4] for r in dynamic[chapter][cohort]) != baseline:
                raise ValueError(f"Dynamic membership/inputs changed: {chapter}/{cohort}")
    header = "compiler,cohort,test,cpu,inputs,runs,instructions,allocator_instructions,result\n"
    (ROOT / "build/spill-stats/dynamic-counts.csv").write_text(header+"\n".join(records)+"\n", encoding="utf-8", newline="\n")
    return dynamic


def table(results, dynamic, last=25):
    chapters = range(20, last + 1)
    lines = ["| Test cohort | Cases D / X | " + " | ".join(f"Ch {c}: Ops / RA / X" for c in chapters) + " |",
             "|---|---:|" + "---:|" * len(chapters)]
    for cohort in chapters:
        count = len(dynamic[max(21,cohort)][cohort])
        nx = sum(r[1]=="x86_64_v2" for r in results[cohort][cohort])
        cells = [f"Ch {cohort}", f"{count} / {nx}"]
        for c in chapters:
            if c < cohort:
                cells.append("")
                continue
            x = sum(r[4] for r in results[c][cohort] if r[1]=="x86_64_v2")
            ops = f"{sum(r[4] for r in dynamic[c][cohort]):,}" if c>=21 else "—"
            ra = f"{sum(r[5] for r in dynamic[c][cohort]):,}" if c>=21 else "—"
            cells.append(f"{ops} / {ra} / {x:,}")
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
        for chapter in range(21, 26):
            if chapter < 25:
                subprocess.run([sys.executable, str(ROOT / "build-support/capture-alloc-sources.py"), str(chapter)], check=True)
            subprocess.run([sys.executable, str(ROOT / "build-support/measure-alloc.py"), str(chapter)], check=True)
    results = read_results(args.log_dir)
    dynamic = read_dynamic(results)
    matrix = table(results, dynamic)
    (args.log_dir / "matrix.md").write_text(matrix + "\n", encoding="utf-8", newline="\n")
    if args.update_docs:
        paths = [(ROOT / "docs/regalloc-spills.md", matrix)]
        paths += [(ROOT / f"chapter{c}/README.md", table(results, dynamic, c)) for c in CHAPTERS]
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
