#!/bin/bash

while sleep 10; do
  if [ $(pgrep apt | wc -l) -lt 1 ]; then
    echo "apt process not running, continuing on"
    break
  else
    echo "apt process still running"
  fi
done
