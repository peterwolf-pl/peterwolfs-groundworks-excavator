#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw

def create_excavator_texture():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # ── Industrial Palette ─────────────────────────────────────────────
    c_yellow_base = (245, 184, 0, 255)       # Rich CAT / Komatsu construction yellow
    c_yellow_light = (255, 210, 50, 255)     # Highlight bevel edge
    c_yellow_dark = (195, 142, 0, 255)       # Shaded yellow panel recess
    c_yellow_line = (150, 108, 0, 255)       # Panel separation groove

    c_dark_iron = (44, 45, 48, 255)          # Heavy matte chassis & frame
    c_iron_light = (70, 72, 78, 255)         # Cast iron edge highlight
    c_iron_recess = (24, 25, 27, 255)        # Deep recess shadow

    c_track_belt = (32, 33, 36, 255)         # Heavy crawler track belt
    c_track_cleat = (56, 58, 62, 255)        # Steel grouser bar / cleat
    c_track_roller = (75, 78, 85, 255)       # Track guide rollers & sprockets

    c_glass_tint = (138, 200, 232, 200)      # Tinted safety cabin glass
    c_glass_glare = (190, 230, 255, 220)     # Glass reflection stripe
    c_glass_frame = (24, 24, 26, 255)        # Rubber window gasket
    c_wiper = (20, 20, 22, 255)              # Windshield wiper blade

    c_counterweight = (36, 37, 40, 255)      # Heavy ballast counterweight
    c_hazard_yellow = (245, 184, 0, 255)     # Safety hazard diagonal stripe yellow
    c_hazard_black = (20, 20, 22, 255)       # Safety hazard diagonal stripe black

    c_beacon_base = (28, 29, 32, 255)        # Strobe beacon mount
    c_beacon_amber = (255, 146, 0, 255)      # Amber strobe lens dome
    c_beacon_bright = (255, 220, 40, 255)    # Flashing bright core bulb
    c_beacon_glow = (255, 180, 20, 255)

    c_exhaust_metal = (55, 56, 60, 255)      # Heat-treated exhaust pipe
    c_exhaust_soot = (18, 18, 20, 255)       # Exhaust tip carbon soot
    c_mirror_frame = (25, 26, 28, 255)       # Mirror housing
    c_mirror_glass = (195, 215, 235, 255)    # Reflective rearview mirror

    c_hydraulic_cyl = (38, 40, 44, 255)      # Cylinder outer barrel
    c_hydraulic_chrome = (230, 238, 248, 255)# Chrome mirror rod
    c_hydraulic_gland = (60, 62, 68, 255)    # Cylinder gland nut

    c_bucket_body = (52, 54, 58, 255)        # Forged scoop shell
    c_bucket_lip = (85, 90, 98, 255)         # Cutting lip plate
    c_bucket_wear = (175, 182, 192, 255)     # Freshly scraped steel teeth & edge wear
    c_teeth_core = (195, 202, 212, 255)      # Hardened chisel tooth tips
    c_dirt = (134, 90, 61, 255)              # Granular soil inside bucket
    c_dirt_dark = (102, 68, 45, 255)

    # ── 1. Undercarriage & Center Frame (0, 0) to (76, 40) ─────────────
    draw.rectangle([0, 0, 76, 40], fill=c_dark_iron)
    for y in range(0, 40, 4):
        draw.line([(0, y), (76, y)], fill=c_iron_recess)
    for x in range(0, 76, 6):
        draw.line([(x, 0), (x, 40)], fill=c_iron_light)

    # Turntable ring (0, 46) to (32, 48)
    draw.rectangle([0, 46, 32, 48], fill=c_iron_light)

    # ── 2. Tracks & Rollers (0, 64) to (100, 80) ──────────────────────
    draw.rectangle([0, 64, 76, 80], fill=c_track_belt)
    for x in range(0, 76, 3):
        draw.line([(x, 64), (x, 80)], fill=c_track_cleat)
        draw.line([(x + 1, 64), (x + 1, 80)], fill=c_iron_recess)
    # Sprockets & guide rollers: (76, 64) to (100, 80)
    draw.rectangle([76, 64, 100, 80], fill=c_track_roller)
    draw.rectangle([78, 66, 98, 78], outline=c_iron_light)

    # ── 3. Rear Counterweight & Hazard Stripes (0, 47) to (36, 63) ────
    draw.rectangle([0, 47, 36, 63], fill=c_counterweight)
    # Diagonal safety hazard chevrons across lower counterweight
    for i in range(-10, 45, 6):
        draw.polygon([(i, 63), (i + 4, 63), (i + 8, 54), (i + 4, 54)], fill=c_hazard_yellow)
    draw.rectangle([0, 53, 36, 54], fill=c_iron_light)

    # ── 4. Engine Hood & Machinery House (80, 0) to (128, 44) ─────────
    draw.rectangle([80, 0, 128, 44], fill=c_yellow_base)
    draw.rectangle([80, 0, 127, 43], outline=c_yellow_light)
    # Radiator ventilation louver louvers
    draw.rectangle([86, 6, 122, 28], fill=c_iron_recess)
    for y in range(8, 28, 3):
        draw.line([(88, y), (120, y)], fill=c_iron_light)
    # Service door handles
    draw.rectangle([86, 34, 94, 36], fill=c_iron_light)
    draw.rectangle([112, 34, 120, 36], fill=c_iron_light)

    # ── 5. Operator Cabin (80, 44) to (128, 68) ───────────────────────
    draw.rectangle([80, 44, 128, 68], fill=c_yellow_base)
    # Large Front & Side Windows
    draw.rectangle([83, 47, 101, 64], fill=c_glass_tint)
    draw.rectangle([83, 47, 101, 64], outline=c_glass_frame)
    draw.line([(85, 49), (99, 62)], fill=c_glass_glare) # Glare stripe
    draw.line([(92, 48), (92, 63)], fill=c_wiper)       # Wiper blade

    draw.rectangle([104, 47, 125, 64], fill=c_glass_tint)
    draw.rectangle([104, 47, 125, 64], outline=c_glass_frame)
    draw.line([(106, 49), (123, 62)], fill=c_glass_glare)

    # Cab Roof & Sun Visor: (76, 84) to (108, 94)
    draw.rectangle([76, 84, 108, 94], fill=c_dark_iron)
    draw.rectangle([76, 84, 108, 86], fill=c_iron_light)

    # ── 6. Flashing Warning Beacon ("Kogut") (112, 68) to (128, 92) ───
    # Beacon Base: (112, 68) to (128, 74)
    draw.rectangle([112, 68, 128, 74], fill=c_beacon_base)
    draw.rectangle([114, 69, 126, 73], fill=c_iron_light)
    # Amber Dome Lens: (112, 74) to (128, 85)
    draw.rectangle([112, 74, 128, 85], fill=c_beacon_amber)
    draw.rectangle([114, 76, 126, 83], fill=c_beacon_glow)
    # Bright Rotating Reflector Core: (112, 86) to (128, 92)
    draw.rectangle([112, 86, 128, 92], fill=c_beacon_bright)
    draw.rectangle([116, 88, 124, 90], fill=(255, 255, 255, 255))

    # ── 7. Exhaust Stack & Rearview Mirror (112, 92) to (128, 112) ────
    # Exhaust pipe: (112, 92) to (128, 103)
    draw.rectangle([112, 92, 128, 103], fill=c_exhaust_metal)
    draw.rectangle([112, 92, 128, 94], fill=c_exhaust_soot)
    # Rearview Mirror: (112, 104) to (128, 112)
    draw.rectangle([112, 104, 128, 112], fill=c_mirror_frame)
    draw.rectangle([114, 106, 126, 110], fill=c_mirror_glass)

    # ── 8. Main Gooseneck Boom (0, 80) to (76, 115) ───────────────────
    draw.rectangle([0, 80, 76, 115], fill=c_yellow_base)
    # Welded reinforcement gusset plates
    draw.rectangle([4, 83, 32, 101], fill=c_yellow_dark)
    draw.rectangle([4, 83, 32, 101], outline=c_yellow_line)
    draw.rectangle([44, 83, 72, 101], fill=c_yellow_dark)
    draw.rectangle([44, 83, 72, 101], outline=c_yellow_line)
    # Pivot pin grease nipples (dark steel circles)
    draw.ellipse([6, 85, 12, 91], fill=c_iron_recess, outline=c_iron_light)
    draw.ellipse([66, 85, 72, 91], fill=c_iron_recess, outline=c_iron_light)

    # Hydraulic Lift & Stick Cylinders (76, 110) to (110, 128)
    draw.rectangle([76, 110, 110, 120], fill=c_hydraulic_cyl)
    draw.rectangle([80, 112, 106, 118], fill=c_hydraulic_chrome)
    draw.rectangle([78, 111, 82, 119], fill=c_hydraulic_gland)

    draw.rectangle([48, 115, 76, 125], fill=c_hydraulic_cyl)
    draw.rectangle([52, 117, 72, 123], fill=c_hydraulic_chrome)

    # ── 9. Dipper Stick (0, 120) to (48, 128) ─────────────────────────
    draw.rectangle([0, 120, 48, 128], fill=c_yellow_base)
    draw.rectangle([8, 122, 40, 126], fill=c_yellow_dark)
    draw.ellipse([2, 122, 6, 126], fill=c_iron_recess)

    # ── 10. Heavy Backhoe Scoop Bucket (74, 94) to (128, 128) ─────────
    draw.rectangle([74, 94, 128, 128], fill=c_bucket_body)
    # Welded wear strips on scoop cheeks
    draw.rectangle([76, 96, 88, 124], fill=c_bucket_lip)
    # Cutting lip plate
    draw.rectangle([90, 108, 128, 116], fill=c_bucket_lip)
    # Fresh metallic scrape marks on cutting edge
    draw.rectangle([90, 114, 126, 118], fill=c_bucket_wear)
    # Hardened chisel teeth (90, 119) to (118, 123)
    draw.rectangle([90, 119, 118, 123], fill=c_teeth_core)
    draw.rectangle([90, 121, 118, 123], fill=c_bucket_wear)

    # Soil fill texture inside bucket: (90, 123) to (128, 128)
    draw.rectangle([90, 123, 128, 128], fill=c_dirt)
    for x in range(90, 128, 2):
        for y in range(123, 128, 2):
            if (x + y) % 3 == 0:
                img.putpixel((x, y), c_dirt_dark)

    os.makedirs("src/main/resources/assets/pw_groundworks_excavator/textures/entity", exist_ok=True)
    img.save("src/main/resources/assets/pw_groundworks_excavator/textures/entity/excavator.png")
    print("Upgraded 128x128 entity texture created.")

def create_item_texture():
    # 32x32 inventory icon
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    c_yellow = (245, 184, 0, 255)
    c_yellow_hi = (255, 215, 60, 255)
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

    # Cab
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
