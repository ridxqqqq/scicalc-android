# -*- coding: utf-8 -*-
"""生成"科学计算器"的 Launcher 图标。

只使用 Python 标准库（zlib + struct）直接写 PNG，无需 Pillow。
做法：对每个目标像素做 4x4 超采样 + alpha 加权平均，得到平滑的圆角与边缘。
"""
import os
import struct
import zlib

SS = 4  # 超采样倍数

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def rr(px, py, x0, y0, x1, y1, r):
    """点 (px,py) 是否落在圆角矩形内（坐标为 0~1 归一化）"""
    if px < x0 or px > x1 or py < y0 or py > y1:
        return False
    cx = min(max(px, x0 + r), x1 - r)
    cy = min(max(py, y0 + r), y1 - r)
    dx = px - cx
    dy = py - cy
    return dx * dx + dy * dy <= r * r


def lerp(a, b, t):
    return a + (b - a) * t


def color_at(u, v):
    """归一化坐标 -> (r,g,b,a)"""
    # 外层圆角底板（蓝色渐变）
    if not rr(u, v, 0.02, 0.02, 0.98, 0.98, 0.22):
        return (0, 0, 0, 0)
    col = (int(lerp(33, 10, v)), int(lerp(150, 60, v)), int(lerp(243, 170, v)), 255)

    # 计算器机身
    if rr(u, v, 0.20, 0.12, 0.80, 0.88, 0.075):
        col = (246, 249, 252, 255)

    # 显示屏
    if rr(u, v, 0.27, 0.18, 0.73, 0.35, 0.03):
        col = (16, 24, 32, 255)
        # 屏内的结果条
        if rr(u, v, 0.45, 0.245, 0.69, 0.29, 0.02):
            col = (0, 200, 160, 255)

    # 按键 3x3
    for i in range(3):
        for j in range(3):
            bx0 = 0.27 + i * 0.16
            by0 = 0.42 + j * 0.15
            if rr(u, v, bx0, by0, bx0 + 0.12, by0 + 0.105, 0.022):
                col = (21, 101, 192, 255)
                if i == 2 and j == 2:      # 右下角按键做成橙色，暗示 "="
                    col = (245, 124, 0, 255)
    return col


def make_sampler(size, ss=SS):
    def get(ix, iy):
        rs = gs = bs = asum = 0
        for sy in range(ss):
            for sx in range(ss):
                u = (ix + (sx + 0.5) / ss) / size
                v = (iy + (sy + 0.5) / ss) / size
                r, g, b, a = color_at(u, v)
                rs += r * a
                gs += g * a
                bs += b * a
                asum += a
        if asum == 0:
            return (0, 0, 0, 0)
        n = ss * ss
        return (round(rs / asum), round(gs / asum), round(bs / asum), round(asum / n))
    return get


def write_png(path, size, get):
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type 0 (None)
        for x in range(size):
            r, g, b, a = get(x, y)
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.join(here, os.pardir, "res")
    for name, size in DENSITIES.items():
        out_dir = os.path.join(res, "mipmap-" + name)
        if not os.path.isdir(out_dir):
            os.makedirs(out_dir)
        out = os.path.join(out_dir, "ic_launcher.png")
        write_png(out, size, make_sampler(size))
        print("icon %-8s %3dpx -> %s" % (name, size, out))

    # 额外输出一张 256 商店预览图（纯 Python 渲染，控制耗时）
    store = os.path.join(here, "icon-256.png")
    write_png(store, 256, make_sampler(256, 3))
    print("icon store   256px -> %s" % store)


if __name__ == "__main__":
    main()
