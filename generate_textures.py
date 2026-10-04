#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw

def create_excavator_texture():
    # 512x512 texture with 100% non-overlapping UV allocations
    img = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # ── Industrial Palette ─────────────────────────────────────────────
    c_yellow_base = (245, 184, 0, 255)       # CAT/Komatsu construction yellow
    c_yellow_light = (255, 210, 50, 255)     # Highlight bevel edge
    c_yellow_dark = (195, 142, 0, 255)       # Shaded yellow panel recess
    c_yellow_line = (150, 108, 0, 255)       # Panel separation groove

    c_dark_iron = (44, 45, 48, 255)          # Heavy matte chassis & frame
    c_iron_light = (70, 72, 78, 255)         # Cast iron edge highlight
    c_iron_recess = (24, 25, 27, 255)        # Deep recess shadow

    c_track_belt = (32, 33, 36, 255)         # Heavy crawler track belt
    c_track_cleat = (56, 58, 62, 255)        # Steel grouser bar / cleat
    c_track_roller = (75, 78, 85, 255)       # Track guide rollers & sprockets

    # Crystal clear safety glass: only alpha=35 (86% transparent)
    c_glass_pane = (140, 210, 245, 35)       # Subtle realistic blue tint
    c_glass_frame = (24, 24, 26, 255)        # Thin rubber gasket seal
    c_glass_streak = (220, 245, 255, 60)     # Faint sun reflection line

    c_counterweight = (36, 37, 40, 255)      # Heavy ballast counterweight
    c_hazard_yellow = (245, 184, 0, 255)     # Safety hazard diagonal stripe yellow
    c_hazard_black = (20, 20, 22, 255)       # Safety hazard diagonal stripe black

    c_beacon_base = (28, 29, 32, 255)        # Strobe beacon mount
    c_beacon_amber = (255, 146, 0, 255)      # Amber strobe lens dome
    c_beacon_bright = (255, 220, 40, 255)    # Flashing bright core bulb

    c_exhaust_metal = (55, 56, 60, 255)      # Exhaust stack
    c_mirror_frame = (25, 26, 28, 255)
    c_mirror_glass = (195, 215, 235, 255)

    c_hydraulic_cyl = (38, 40, 44, 255)      # Cylinder outer barrel
    c_hydraulic_chrome = (230, 238, 248, 255)# Chrome mirror rod
    c_hydraulic_gland = (60, 62, 68, 255)

    c_bucket_body = (52, 54, 58, 255)        # Forged scoop shell
    c_bucket_lip = (85, 90, 98, 255)         # Cutting lip plate
    c_bucket_wear = (175, 182, 192, 255)     # Freshly scraped steel
    c_teeth_core = (205, 212, 222, 255)      # Hardened chisel tooth tips
    c_dirt = (134, 90, 61, 255)              # Granular soil inside bucket
    c_dirt_dark = (102, 68, 45, 255)
    c_seat_leather = (35, 36, 40, 255)

    # ── ROW 1 (v = 0 .. 70) ───────────────────────────────────────────
    # 1. "track_belt": u=0, v=0, w=132, h=70
    draw.rectangle([0, 0, 132, 70], fill=c_track_belt)
    for x in range(0, 132, 4):
        draw.line([(x, 0), (x, 70)], fill=c_track_cleat)
        draw.line([(x + 1, 0), (x + 1, 70)], fill=c_iron_recess)

    # 2. "deck_plate": u=136, v=0, w=160, h=47
    draw.rectangle([136, 0, 136 + 160, 47], fill=c_dark_iron)
    for x in range(136, 136 + 160, 8):
        draw.line([(x, 0), (x, 47)], fill=c_iron_light)

    # 3. "chassis_frame": u=300, v=0, w=120, h=46
    draw.rectangle([300, 0, 300 + 120, 46], fill=c_dark_iron)
    draw.rectangle([300, 0, 300 + 120, 46], outline=c_iron_light)

    # ── ROW 2 (v = 74 .. 118) ─────────────────────────────────────────
    # 4. "engine_house": u=0, v=74, w=104, h=44
    draw.rectangle([0, 74, 104, 74 + 44], fill=c_yellow_base)
    draw.rectangle([0, 74, 103, 74 + 43], outline=c_yellow_light)
    # Radiator louvers
    draw.rectangle([10, 80, 50, 110], fill=c_iron_recess)
    for y in range(82, 110, 3):
        draw.line([(12, y), (48, y)], fill=c_iron_light)

    # 5. "stick": u=108, v=74, w=86, h=43
    draw.rectangle([108, 74, 108 + 86, 74 + 43], fill=c_yellow_base)
    draw.rectangle([112, 78, 108 + 82, 74 + 39], outline=c_yellow_dark)

    # 6. "boom_lower": u=198, v=74, w=78, h=40
    draw.rectangle([198, 74, 198 + 78, 74 + 40], fill=c_yellow_base)
    draw.rectangle([202, 78, 198 + 74, 74 + 36], outline=c_yellow_dark)
    # Pivot pin
    draw.ellipse([204, 82, 214, 92], fill=c_iron_recess, outline=c_iron_light)

    # 7. "boom_upper": u=280, v=74, w=64, h=33
    draw.rectangle([280, 74, 280 + 64, 74 + 33], fill=c_yellow_base)
    draw.rectangle([284, 78, 280 + 60, 74 + 29], outline=c_yellow_dark)

    # 8. "glass_side": u=348, v=74, w=38, h=32
    draw.rectangle([348, 74, 348 + 38, 74 + 32], fill=c_glass_pane)
    draw.rectangle([348, 74, 348 + 37, 74 + 31], outline=c_glass_frame)
    draw.line([(352, 74 + 28), (370, 74 + 4)], fill=c_glass_streak, width=1)

    # 9. "cylinders": u=390, v=74, w=56, h=28
    draw.rectangle([390, 74, 390 + 56, 74 + 28], fill=c_hydraulic_cyl)
    draw.rectangle([398, 78, 390 + 48, 74 + 22], fill=c_hydraulic_chrome)

    # ── ROW 3 (v = 122 .. 148) ────────────────────────────────────────
    # 10. "cab_roof": u=0, v=122, w=80, h=26
    draw.rectangle([0, 122, 80, 122 + 26], fill=c_dark_iron)
    draw.rectangle([0, 122, 79, 122 + 25], outline=c_iron_light)

    # 11. "bucket_lrg": u=84, v=122, w=70, h=26
    draw.rectangle([84, 122, 84 + 70, 122 + 26], fill=c_bucket_body)
    draw.rectangle([84, 122, 84 + 69, 122 + 25], outline=c_bucket_lip)

    # 12. "counterweight": u=158, v=122, w=92, h=24
    draw.rectangle([158, 122, 158 + 92, 122 + 24], fill=c_counterweight)
    # Hazard stripes
    for i in range(158, 158 + 92, 8):
        draw.polygon([(i, 122 + 24), (i + 4, 122 + 24), (i + 8, 122 + 12), (i + 4, 122 + 12)], fill=c_hazard_yellow)

    # 13. "cab_frame": u=254, v=122, w=32, h=24
    draw.rectangle([254, 122, 254 + 32, 122 + 24], fill=c_yellow_base)
    draw.rectangle([254, 122, 254 + 31, 122 + 23], outline=c_yellow_dark)

    # 14. "bucket_std": u=290, v=122, w=52, h=24
    draw.rectangle([290, 122, 290 + 52, 122 + 24], fill=c_bucket_body)
    draw.rectangle([290, 122, 290 + 51, 122 + 23], outline=c_bucket_lip)

    # 15. "sprockets": u=346, v=122, w=38, h=20
    draw.rectangle([346, 122, 346 + 38, 122 + 20], fill=c_track_roller)

    # 16. "soil": u=388, v=122, w=56, h=19
    draw.rectangle([388, 122, 388 + 56, 122 + 19], fill=c_dirt)
    for x in range(388, 388 + 56, 3):
        for y in range(122, 122 + 19, 3):
            if (x + y) % 4 == 0:
                img.putpixel((x, y), c_dirt_dark)

    # 17. "turntable_ring": u=448, v=122, w=64, h=18
    draw.rectangle([448, 122, 448 + 64, 122 + 18], fill=c_iron_light)

    # ── ROW 4 (v = 152 .. 170) ────────────────────────────────────────
    # 18. "glass_windshield": u=0, v=152, w=26, h=17
    draw.rectangle([0, 152, 26, 152 + 17], fill=c_glass_pane)
    draw.rectangle([0, 152, 25, 152 + 16], outline=c_glass_frame)
    draw.line([(4, 152 + 14), (20, 152 + 2)], fill=c_glass_streak, width=1)

    # 19. "seat": u=30, v=152, w=32, h=16
    draw.rectangle([30, 152, 30 + 32, 152 + 16], fill=c_seat_leather)

    # 20. "exhaust": u=66, v=152, w=16, h=14
    draw.rectangle([66, 152, 66 + 16, 152 + 14], fill=c_exhaust_metal)

    # 21. "beacon": u=86, v=152, w=40, h=20
    # Base mount: u=86, v=152
    draw.rectangle([86, 152, 86 + 20, 152 + 8], fill=c_beacon_base)
    draw.rectangle([88, 153, 86 + 18, 152 + 7], fill=c_iron_light)
    # Amber dome lens: u=86, v=160
    draw.rectangle([86, 160, 86 + 20, 160 + 10], fill=c_beacon_amber)
    # Strobe core flash OFF (dim amber): u=108, v=152
    draw.rectangle([108, 152, 108 + 16, 152 + 8], fill=(180, 95, 0, 255))
    # Strobe core flash ON (brilliant electric warning yellow/gold): u=108, v=160
    draw.rectangle([108, 160, 108 + 16, 160 + 8], fill=(255, 235, 60, 255))
    draw.rectangle([111, 162, 108 + 13, 160 + 6], fill=(255, 255, 200, 255))

    # 22. "mirror": u=110, v=152, w=10, h=8
    draw.rectangle([110, 152, 110 + 10, 152 + 8], fill=c_mirror_frame)
    draw.rectangle([111, 153, 110 + 8, 152 + 6], fill=c_mirror_glass)

    # 23. "teeth": u=124, v=152, w=16, h=8
    draw.rectangle([124, 152, 124 + 16, 152 + 8], fill=c_teeth_core)

    # 24. Heavy Tensioner Idler Wheel & Recoil Spring Hub (Bulldozer tensioner): u=160, v=152, w=48, h=28
    draw.rectangle([160, 152, 160 + 48, 152 + 28], fill=c_iron_light)
    draw.rectangle([162, 154, 160 + 46, 152 + 26], fill=c_dark_iron)
    # Heavy spring coils & central grease cylinder tensioner
    draw.rectangle([166, 158, 160 + 40, 152 + 22], fill=c_track_roller)
    for x in range(168, 160 + 38, 4):
        draw.line([(x, 158), (x + 2, 152 + 22)], fill=(210, 160, 20, 255), width=2)

    # 25. Dual Flanged Heavy Road Rollers: u=214, v=152, w=44, h=24
    draw.rectangle([214, 152, 214 + 44, 152 + 24], fill=c_track_roller)
    draw.rectangle([216, 154, 214 + 42, 152 + 22], outline=c_iron_light)
    draw.ellipse([228, 158, 244, 170], fill=c_dark_iron, outline=c_iron_light)

    os.makedirs("src/main/resources/assets/pw_groundworks_excavator/textures/entity", exist_ok=True)
    img.save("src/main/resources/assets/pw_groundworks_excavator/textures/entity/excavator.png")
    print("512x512 non-overlapping master entity texture created successfully.")

def create_item_texture():
    # 32x32 inventory icon
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    c_yellow = (245, 184, 0, 255)
    c_dark = (44, 45, 48, 255)
    c_track = (32, 33, 36, 255)
    c_glass = (140, 205, 240, 255)
    c_iron = (75, 78, 85, 255)
    c_beacon = (255, 150, 0, 255)

    # Tracks
    draw.rectangle([4, 25, 27, 29], fill=c_track)
    for x in range(5, 27, 3):
        draw.point((x, 26), fill=(75, 78, 85, 255))

    # Chassis & Counterweight
    draw.rectangle([6, 21, 23, 24], fill=c_dark)
    draw.rectangle([5, 14, 10, 21], fill=c_dark) # counterweight

    # Engine hood
    draw.rectangle([10, 16, 17, 21], fill=c_yellow)
    draw.line([(12, 13), (12, 16)], fill=(50, 52, 55, 255), width=1) # exhaust

    # Cab with open glass
    draw.rectangle([13, 11, 19, 21], fill=c_yellow)
    draw.rectangle([14, 13, 18, 17], fill=c_glass) # Window
    # Flashing warning beacon on cab roof!
    draw.rectangle([15, 9, 17, 10], fill=c_beacon)

    # Boom (reaching up/right)
    draw.line([(18, 18), (24, 7)], fill=c_yellow, width=2)
    # Stick (reaching down/right)
    draw.line([(24, 7), (28, 16)], fill=c_yellow, width=2)
    # Bucket scoop pointing back/down
    draw.rectangle([25, 16, 29, 20], fill=c_iron)

    os.makedirs("src/main/resources/assets/pw_groundworks_excavator/textures/item", exist_ok=True)
    img.save("src/main/resources/assets/pw_groundworks_excavator/textures/item/excavator.png")
    print("Upgraded 32x32 item icon created.")

if __name__ == "__main__":
    create_excavator_texture()
    create_item_texture()
