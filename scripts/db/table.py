#!/usr/bin/env python3
"""Print only the parts of the generated schema map that a task needs.

schema-map.md is ~300 KB (100+ tables). Reading it whole wastes AI context and
human attention; this tool extracts single table sections instead.

Usage:
  scripts/db/table.sh members memberships   # full section of each table
  scripts/db/table.sh --list                # table names, one per line
  scripts/db/table.sh --find promoter       # tables whose name contains the text
  scripts/db/table.sh --column member_id    # tables that have a column with that name
  scripts/db/table.sh --refs members        # FKs that point to the table
  scripts/db/table.sh --columns members     # only the column list of a table
"""
import argparse
import re
import sys
from pathlib import Path

MAP = Path(__file__).resolve().parents[2] / "src/main/resources/db/snapshot/schema-map.md"


def load_sections(text):
    """Return {table_name: section_text} for every '## <table>' under '# Tables'."""
    tables_start = text.find("\n# Tables")
    body = text[tables_start:] if tables_start >= 0 else text
    sections = {}
    for chunk in re.split(r"\n(?=## )", body):
        if chunk.startswith("## "):
            name = chunk[3:].split("\n", 1)[0].strip().strip("`")
            sections[name] = chunk.rstrip() + "\n"
    return sections


def columns_of(section):
    match = re.search(r"\*\*Columns\*\*\n(.*?)(?:\n\n|\Z)", section, re.S)
    return match.group(1) if match else ""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tables", nargs="*", help="exact table names")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--find", metavar="TEXT")
    parser.add_argument("--column", metavar="NAME")
    parser.add_argument("--refs", metavar="TABLE")
    parser.add_argument("--columns", action="store_true", help="print only the column list of the given tables")
    args = parser.parse_args()

    text = MAP.read_text(encoding="utf-8")
    sections = load_sections(text)

    if args.list:
        print("\n".join(sorted(sections)))
    if args.find:
        print("\n".join(n for n in sorted(sections) if args.find.lower() in n.lower()))
    if args.column:
        pattern = re.compile(r"^- `" + re.escape(args.column) + r"` (.*)$", re.M)
        for name in sorted(sections):
            for match in pattern.finditer(columns_of(sections[name])):
                print(f"{name}.{args.column} {match.group(1)}")
    if args.refs:
        needle = f"→ `{args.refs}`."
        print("\n".join(line for line in text.splitlines() if line.startswith("- `") and needle in line))

    missing = [t for t in args.tables if t not in sections]
    for table in args.tables:
        if table in sections:
            print(f"## {table}\n" + columns_of(sections[table]) if args.columns else sections[table])
    if missing:
        print(f"Unknown table(s): {', '.join(missing)}. Try --find.", file=sys.stderr)
        return 1
    if not (args.tables or args.list or args.find or args.column or args.refs):
        parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
