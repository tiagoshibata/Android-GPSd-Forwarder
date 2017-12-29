#!/bin/env python3
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--icon', type=str, help='App icon')
parser.add_argument('--round-icon', type=str, help='App icon')
args = parser.parse_args()


def convert(source, destination, resolution):
    subprocess.check_call(['convert', '-resize', '{0}x{0}'.format(resolution), source, destination])


resolutions = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}
root = Path(__file__).resolve().parent
res = root / 'app/src/main/res'

if args.icon:
    for name, resolution in resolutions.items():
        convert(args.icon, res / 'mipmap-{}/ic_launcher.png'.format(name), resolution)

if args.round_icon:
    for name, resolution in resolutions.items():
        convert(args.round_icon, res / 'mipmap-{}/ic_launcher_round.png'.format(name), resolution)


def pngquant(source):
    try:
        subprocess.check_call(['pngquant', '-f', '--ext', '.png', '--strip', '--skip-if-larger', source])
    except subprocess.CalledProcessError as e:
        if e.returncode != 98:  # --skip-if-larger
            raise


for png in root.glob('**/*.png'):
    pngquant(png)
