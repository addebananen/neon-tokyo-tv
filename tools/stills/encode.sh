#!/usr/bin/env bash
# Encodes a picture as a one frame HEVC still for the TV's video chip (see HomePlane.kt):
#   encode.sh picture.png app/src/main/res/raw/home_still_<name>.mp4 [crf]
# 4K photo stills used crf 14, 1080p painted ones crf 12. BT.709 throughout, as the TV expects.
set -euo pipefail
ffmpeg -v error -y -i "$1" -frames:v 1 -vf "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p" \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv \
  -c:v libx265 -preset slow -crf "${3:-14}" -tag:v hvc1 -x265-params log-level=error -movflags +faststart "$2"
