#!/bin/env python3
from pathlib import Path
import subprocess


def convert(source, destination, resolution):
    destination = Path(destination)
    if destination.is_dir():
        destination = destination / Path(source).name
    if not isinstance(resolution, tuple):
        resolution = (resolution, resolution)
    subprocess.check_call(['convert', '-resize', '{}x{}'.format(*resolution), source, str(destination)])


def pngquant(source):
    try:
        subprocess.check_call(['pngquant', '-f', '--ext', '.png', '--strip', '--skip-if-larger', source])
    except subprocess.CalledProcessError as e:
        if e.returncode != 98:  # --skip-if-larger
            raise

root = Path(__file__).resolve().parent
assets_src = root / 'assets_src'
res = root / 'app/src/main/res'


def mipmap(specifier):
    return res / 'mipmap-{}'.format(specifier)


def drawable(specifier):
    return res / 'drawable-{}'.format(specifier)


def main():
    print('Converting app icons')
    resolutions = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192,
    }
    for dpi, resolution in resolutions.items():
        convert(assets_src / 'ic_launcher.png', mipmap(dpi), resolution)
        convert(assets_src / 'ic_launcher_round.png', mipmap(dpi), resolution)
    print('Converting drawables')
    # Taken from:
    # https://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html#size9
    notification_resolutions = {
        'ldpi': (12, 19),
        'mdpi': (16, 25),
        'hdpi': (24, 38),
    }
    for dpi, resolution in notification_resolutions.items():
        convert(assets_src / 'notification_icon.png', drawable(dpi), resolution)
    print('Compressing PNG')
    for png in (f for directory in (res, assets_src) for f in directory.glob('**/*.png') ):
        pngquant(png)


if __name__ == '__main__':
    main()
