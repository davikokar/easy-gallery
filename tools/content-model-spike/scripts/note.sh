#!/usr/bin/env bash
# usage: note.sh <file>   appends <file> to ../NOTES.md with CRLF endings (NOTES.md is CRLF), then deletes <file>
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
printf '\n' | sed 's/\r*$/\r/' >> "$HERE/NOTES.md"
sed 's/\r*$/\r/' "$1" >> "$HERE/NOTES.md"
rm -f "$1"
