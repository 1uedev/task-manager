#!/bin/sh
# The archive command of the database (TASK-135, finding F3 of the review of TASK-131):
#   archive_command = '/opt/taskmanager/archive-wal.sh %p %f'
# A segment reaches the archive under its final name only when it is complete and on disk: it is written under a
# temporary name, flushed and then renamed in one step. A leftover temporary file of an aborted copy is replaced by the
# next attempt; it never blocks it. A file of the same name that is already there is accepted only when it has the same
# content, as PostgreSQL asks of an archive command.
set -eu
source_path=$1
name=$2
archive=${TASK_MANAGER_WAL_ARCHIVE:-/archive}
target=$archive/$name
if [ -f "$target" ]; then
  if cmp -s "$source_path" "$target"; then
    exit 0
  fi
  echo "archive-wal: $name is already archived with a different content" >&2
  exit 1
fi
temporary=$archive/.$name.partial
rm -f "$temporary"
cp "$source_path" "$temporary"
sync "$temporary"
mv "$temporary" "$target"
sync "$archive"
