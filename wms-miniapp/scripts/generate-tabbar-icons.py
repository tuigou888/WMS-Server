# 生成 tabBar 图标（81x81 透明底 PNG，灰色/蓝色两态）
# 用法: python scripts/generate-tabbar-icons.py
from PIL import Image, ImageDraw
import os

S = 324  # 4x 超采样画布，最后缩到 81 抗锯齿
GRAY = (153, 153, 153, 255)   # #999999 未选中
BLUE = (22, 119, 255, 255)    # #1677ff 选中
CLEAR = (0, 0, 0, 0)
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'static', 'tabbar')


def draw_home(color):
    im = Image.new('RGBA', (S, S), CLEAR)
    d = ImageDraw.Draw(im)
    d.polygon([(162, 36), (46, 156), (278, 156)], fill=color)   # 屋顶
    d.rectangle([70, 140, 254, 282], fill=color)                # 房身
    d.rectangle([138, 196, 186, 282], fill=CLEAR)               # 门
    return im


def draw_scan(color):
    im = Image.new('RGBA', (S, S), CLEAR)
    d = ImageDraw.Draw(im)
    w, arm = 26, 84
    L, T, R, B = 40, 40, 284, 284
    for x0, x1 in [(L, L + arm), (R - arm, R)]:                 # 四角括号
        d.rectangle([x0, T, x1, T + w], fill=color)
        d.rectangle([x0, B - w, x1, B], fill=color)
    for y0, y1 in [(T, T + arm), (B - arm, B)]:
        d.rectangle([L, y0, L + w, y1], fill=color)
        d.rectangle([R - w, y0, R, y1], fill=color)
    d.rectangle([L + 20, 150, R - 20, 174], fill=color)         # 扫描线
    return im


def draw_box(color):
    im = Image.new('RGBA', (S, S), CLEAR)
    d = ImageDraw.Draw(im)
    d.polygon([(162, 34), (282, 92), (282, 238), (162, 292), (42, 238), (42, 92)], fill=color)
    w = 16
    d.line([(42, 92), (162, 150)], fill=CLEAR, width=w)         # 顶棱
    d.line([(282, 92), (162, 150)], fill=CLEAR, width=w)
    d.line([(162, 150), (162, 292)], fill=CLEAR, width=w)       # 竖棱
    return im


def draw_user(color):
    im = Image.new('RGBA', (S, S), CLEAR)
    d = ImageDraw.Draw(im)
    d.ellipse([108, 44, 216, 152], fill=color)                  # 头
    d.pieslice([58, 176, 266, 348], 180, 360, fill=color)       # 肩
    return im


ICONS = {
    'home': draw_home,
    'scan': draw_scan,
    'inventory': draw_box,
    'mine': draw_user,
}

os.makedirs(OUT, exist_ok=True)
for name, fn in ICONS.items():
    for suffix, color in [('', GRAY), ('-active', BLUE)]:
        im = fn(color).resize((81, 81), Image.LANCZOS)
        path = os.path.join(OUT, f'{name}{suffix}.png')
        im.save(path)
        print('生成', os.path.normpath(path))
