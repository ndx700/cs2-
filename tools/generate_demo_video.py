#!/usr/bin/env python3
"""Regenerate the original offline flow demo. Requires Pillow and ffmpeg; not needed for building."""
from pathlib import Path
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
output = ROOT / 'app/src/main/res/raw/flow_demo.mp4'
output.parent.mkdir(parents=True, exist_ok=True)
font_path = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def font(size):
    try:
        return ImageFont.truetype(font_path, size)
    except OSError:
        return ImageFont.load_default()

proc = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo',
    '-pix_fmt', 'rgb24', '-s', '640x360', '-r', '24', '-i', '-', '-an', '-c:v', 'libx264',
    '-profile:v', 'baseline', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(output)], stdin=subprocess.PIPE)
for frame in range(192):
    im = Image.new('RGB', (640,360), '#10151d')
    d = ImageDraw.Draw(im)
    d.text((30,22), 'UTILITY LAB  /  FLOW DEMO', font=font(22), fill='#f0be78')
    d.text((30,58), 'Original sample - not a CS2 lineup tutorial', font=font(16), fill='#9dadc1')
    d.rounded_rectangle((28,94,612,282), radius=16, fill='#1c2430')
    for x in range(45,610,32): d.line((x,110,x,269), fill='#27313d')
    for y in range(110,280,32): d.line((40,y,601,y), fill='#27313d')
    pts = [(110 + 420*t/80, 246-110*math.sin(math.pi*t/80)) for t in range(81)]
    d.line(pts, fill='#f0be78', width=3)
    phase = (frame % 96)/95
    x,y = 110+420*phase, 246-110*math.sin(math.pi*phase)
    d.ellipse((x-7,y-7,x+7,y+7), fill='#f0be78')
    for x,col,label in [(110,'#85baff','STAND'),(530,'#ff9173','LAND')]:
        d.ellipse((x-13,233,x+13,259), fill=col)
        d.text((x-29,202),label,font=font(14),fill=col)
    d.text((30,307), 'Tap point  >  Check position  >  Play lesson',font=font(18),fill='#efeee7')
    proc.stdin.write(im.tobytes())
proc.stdin.close()
if proc.wait(): raise SystemExit('ffmpeg failed')
print(output)
