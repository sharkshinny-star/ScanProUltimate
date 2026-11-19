#!/usr/bin/env python3
"""Rebuild the "Open items by priority" block of REMAINING_WORK.md from the Open table of AUDIT_FINDINGS.md.

    python3 -I scripts/remaining-work.py           rewrite the block
    python3 -I scripts/remaining-work.py --check   write nothing; exit 1 if the block is out of date

AUDIT_FINDINGS.md is the source of truth. Only the text between the two marker comments in REMAINING_WORK.md is
replaced; everything else in that file is written by hand and left alone. For each open row the block gives the ID,
its category and location, the start of the Problem (its first sentence, and the next ones while that is still short),
and the Recommendation as the next step. A row with no Recommendation keeps its checks in the Problem: there the first
sentence is the summary and the rest is the next step. The output holds no date, so the same table always gives the
same block.

No network, no commits. Exit code 1 on a malformed table, missing markers or (with --check) a stale block.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FINDINGS = ROOT / "AUDIT_FINDINGS.md"
REMAINING = ROOT / "REMAINING_WORK.md"

BEGIN = "<!-- BEGIN open-items: written by scripts/remaining-work.py from AUDIT_FINDINGS.md; do not edit by hand -->"
END = "<!-- END open-items -->"

PRIORITIES = ("P0", "P1", "P2", "P3")
SUMMARY_MIN = 120  # a shorter opening sentence says too little on its own ("Nothing verified on a device.")
SUMMARY_LIMIT = 300
LOCATION_LIMIT = 100

# The end of a sentence: a full stop followed by a space and something that starts a sentence. "e.g." and "i.e." are
# not ends; "1.3x" and file names have no space after the stop.
SENTENCE_END = re.compile(r"(?<!e\.g)(?<!i\.e)\.(?=\s+[A-Z0-9(`\"'“‘])")
UNESCAPED_PIPE = re.compile(r"(?<!\\)\|")
ROW_ID = re.compile(r"^[A-Z]+\d+$")


def fail(message: str) -> None:
    print(f"remaining-work: {message}", file=sys.stderr)
    sys.exit(1)


def open_rows(text: str) -> list[dict[str, str]]:
    """The rows of the table under "## Open", in the table's order."""
    match = re.search(r"^## Open\s*$(.*?)^## ", text, re.MULTILINE | re.DOTALL)
    if match is None:
        fail(f'no "## Open" section followed by another section in {FINDINGS.name}')
    rows = []
    seen: set[str] = set()
    for line in match.group(1).splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip().replace("\\|", "|") for cell in UNESCAPED_PIPE.split(line)[1:-1]]
        if not cells or not ROW_ID.match(cells[0]):
            continue  # the header and its rule
        if len(cells) == 5:
            cells.append("")  # a row written without its Recommendation cell
        if len(cells) != 6:
            fail(f"row {cells[0]} has {len(cells)} cells, expected 6 (escape a | inside a cell as \\|)")
        row_id, priority, category, location, problem, recommendation = cells
        if row_id in seen:
            print(f"remaining-work: warning: {row_id} appears more than once in the Open table", file=sys.stderr)
        seen.add(row_id)
        rows.append(
            {
                "id": row_id,
                "priority": priority,
                "category": category,
                "location": location,
                "problem": problem,
                "recommendation": recommendation,
            }
        )
    return rows


def shorten(text: str, limit: int, breaks: str, tail: str) -> str:
    """[text] cut at the last of [breaks] before [limit] that is outside brackets, with [tail] added; unchanged when
    it fits."""
    if len(text) <= limit:
        return text
    cut, depth = 0, 0
    for index, char in enumerate(text[:limit]):
        if char == "(":
            depth += 1
        elif char == ")":
            depth = max(0, depth - 1)
        elif depth == 0 and text.startswith(breaks, index):
            cut = index
    if cut == 0:
        cut = limit
    return text[:cut].rstrip(" ,;:") + tail


def split_problem(problem: str, extend: bool) -> tuple[str, str]:
    """The opening of the Problem and whatever follows it. The opening is the first sentence; with [extend], the
    sentences after it are added while it is shorter than SUMMARY_MIN."""
    end = 0
    for match in SENTENCE_END.finditer(problem):
        end = match.end()
        if not extend or end >= SUMMARY_MIN:
            break
    else:
        return problem, ""  # no sentence end, or the whole Problem is still short
    return problem[:end], problem[end:].strip()


def item(row: dict[str, str]) -> str:
    summary, rest = split_problem(row["problem"], extend=bool(row["recommendation"]))
    summary = shorten(summary, SUMMARY_LIMIT, " ", "… (more in the row)")
    next_step = row["recommendation"] or rest or "See the row."
    where = shorten(row["location"], LOCATION_LIMIT, ", ", ", …")
    label = "; ".join(part for part in (row["category"], where) if part)
    return f"- **{row['id']}** ({label}). {summary}\n  - Next: {next_step}"


def block(rows: list[dict[str, str]]) -> str:
    groups: dict[str, list[dict[str, str]]] = {priority: [] for priority in PRIORITIES}
    for row in rows:
        groups.setdefault(row["priority"], []).append(row)  # an unknown priority gets its own group, last
    counts = ", ".join(f"{len(found)} {priority}" for priority, found in groups.items() if found)
    lines = [BEGIN, "", f"{len(rows)} open items: {counts}." if rows else "No open items.", ""]
    for priority, found in groups.items():
        if not found:
            continue
        lines += [f"### {priority}", ""]
        lines += [item(row) for row in found]
        lines.append("")
    lines.append(END)
    return "\n".join(lines)


def main() -> None:
    check = sys.argv[1:] == ["--check"]
    if sys.argv[1:] and not check:
        fail("usage: remaining-work.py [--check]")
    current = REMAINING.read_text(encoding="utf-8")
    if current.count(BEGIN) != 1 or current.count(END) != 1 or current.index(BEGIN) > current.index(END):
        fail(f"{REMAINING.name} must hold these two lines once each, in this order:\n{BEGIN}\n{END}")
    rows = open_rows(FINDINGS.read_text(encoding="utf-8"))
    updated = current[: current.index(BEGIN)] + block(rows) + current[current.index(END) + len(END) :]
    if updated == current:
        print(f"remaining-work: {REMAINING.name} is up to date ({len(rows)} open items)")
        return
    if check:
        fail(f"{REMAINING.name} is out of date; run python3 -I scripts/remaining-work.py")
    REMAINING.write_text(updated, encoding="utf-8")
    print(f"remaining-work: {REMAINING.name} rewritten ({len(rows)} open items)")


if __name__ == "__main__":
    main()
