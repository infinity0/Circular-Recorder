#!/bin/bash
shopt -s nullglob globstar

name="${1:-Circular Recorder}"
desc="${2:-Circular Sound Recorder}"
notif="${3:-Circular Recording}"
sed -i -r \
  -e 's,<string name="app_name">([^<]+)</string>,<string name="app_name">'"$name"'</string>,g' \
  -e 's,<string name="sound_notification_title">([^<]+)</string>,<string name="sound_notification_title">'"$desc"'</string>,g' \
  app/src/main/res/**/*.xml
