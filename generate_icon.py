#!/usr/bin/env python3
"""Generate Android launcher icon resources from a source image."""
import os
import shutil
from PIL import Image

SOURCE = r"C:\Users\ksluy\Pictures\King.jpg"
RES_DIR = r"app\src\main\res"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

def center_crop_to_square(img):
    w, h = img.size
    size = min(w, h)
    left = (w - size) // 2
    top = (h - size) // 2
    return img.crop((left, top, left + size, top + size))

def create_foreground(img, size):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content_size = int(size * 72 / 108)
    padding = (size - content_size) // 2
    resized = img.resize((content_size, content_size), Image.LANCZOS)
    canvas.paste(resized, (padding, padding))
    return canvas

def create_legacy_icon(img, size):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    padding = int(size * 0.04)
    content_size = size - 2 * padding
    resized = img.resize((content_size, content_size), Image.LANCZOS)
    canvas.paste(resized, (padding, padding))
    return canvas

def main():
    # Clean up old drawable-density directories
    for density_name in DENSITIES:
        d = os.path.join(RES_DIR, f"drawable-{density_name}")
        if os.path.exists(d):
            shutil.rmtree(d)
            print(f"  Removed: drawable-{density_name}/")

    img = Image.open(SOURCE).convert("RGBA")
    print(f"Source: {img.size}")
    square = center_crop_to_square(img)

    for density_name, scale in DENSITIES.items():
        mipmap_dir = os.path.join(RES_DIR, f"mipmap-{density_name}")
        os.makedirs(mipmap_dir, exist_ok=True)

        # Legacy icon
        legacy_size = int(48 * scale)
        legacy = create_legacy_icon(square, legacy_size)
        legacy.save(os.path.join(mipmap_dir, "ic_launcher.png"), "PNG")

        # Adaptive icon foreground
        fg_size = int(108 * scale)
        foreground = create_foreground(square, fg_size)
        foreground.save(os.path.join(mipmap_dir, "ic_launcher_foreground.png"), "PNG")

        # Adaptive icon background (white)
        bg_size = int(108 * scale)
        background = Image.new("RGBA", (bg_size, bg_size), (255, 255, 255, 255))
        background.save(os.path.join(mipmap_dir, "ic_launcher_background.png"), "PNG")

        print(f"  mipmap-{density_name}/ done")

    print("\nDone! Now update XML to reference @mipmap/ instead of @drawable/")

if __name__ == "__main__":
    main()