#!/usr/bin/env bash
# Thin wrapper; logic lives in table.py (see its docstring).
exec python3 "$(dirname "$0")/table.py" "$@"
