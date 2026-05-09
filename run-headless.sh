#!/usr/bin/env bash
# CLI mode — no GUI window. Useful for servers.
# Usage: ./run-headless.sh [--count 10000] [--threads 8] [--file puzzles.txt]
java -jar sudoku-solver.jar --headless "$@"
