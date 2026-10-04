#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw

def create_excavator_texture():
    # 128x128 texture
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Base palette
    c_yellow = (235, 175, 20, 255)       # Construction equipment yellow
    c_yellow_dark = (195, 140, 15, 255)  # Shaded yellow
    c_yellow_light = (250, 200, 45, 255) # Highlight yellow

    c_dark_chassis = (45, 45, 48, 255)   # Charcoal dark frame
    c_chassis_metal = (65, 65, 70, 255)  # Heavy cast steel

    c_track_rubber = (30, 30, 32, 255)   # Track belt dark rubber/steel
    c_track_pads = (50, 50, 55, 255)     # Track cleats / shoes
    c_sprocket = (75, 75, 80, 255)       # Sprockets / idlers

    c_glass = (120, 180, 220, 180)       # Tinted cab glass
    c_glass_frame = (25, 25, 28, 255)    # Window frames
    c_seat = (35, 35, 38, 255)           # Seat leather

    c_counterweight = (35, 35, 40, 255)  # Heavy rear ballast
    c_counterweight_acc = (235, 175, 20, 255)

    c_hydraulic_chrome = (210, 215, 225, 255) # Hydraulic piston chrome
    c_hydraulic_black = (40, 42, 45, 255)     # Piston seals

    c_bucket_iron = (55, 58, 62, 255)    # Excavator scoop cast iron
    c_bucket_wear = (90, 95, 102, 255)   # Scraped metal wear on lip
    c_teeth = (180, 185, 195, 255)       # Hardened steel cutting teeth

    c_dirt = (134, 96, 67, 255)          # Granular soil texture

    # 1. Fill sections corresponding to UV layouts in ExcavatorModel
    # Undercarriage: (0, 0) to (76, 40)
    draw.rectangle([0, 0, 76, 40], fill=c_dark_chassis)
    for x in range(0, 76, 4):
        draw.line([(x, 0), (x, 40)], fill=c_chassis_metal)

    # Turntable ring (0, 46) to (32, 48)
    draw.rectangle([0, 46, 32, 48], fill=c_chassis_metal)

    # Tracks: (0, 64) to (76, 80)
    draw.rectangle([0, 64, 76, 80], fill=c_track_rubber)
    for x in range(0, 76, 3):
        draw.line([(x, 64), (x, 80)], fill=c_track_pads)
    # Sprockets: (76, 64) to (100, 80)
    draw.rectangle([76, 64, 100, 80], fill=c_sprocket)

    # Upper body deck: (0, 0) to (36, 44) (re-used region / upper deck)
    # Counterweight: (0, 47) to (36, 62)
    draw.rectangle([0, 47, 36, 62], fill=c_counterweight)
    draw.rectangle([2, 49, 34, 52], fill=c_yellow) # Yellow chevron / safety stripe

    # Engine compartment & pump house: (80, 0) to (128, 44)
    draw.rectangle([80, 0, 128, 44], fill=c_yellow)
    # Louver air vents for radiator/engine
    for y in range(4, 30, 4):
        draw.line([(88, y), (116, y)], fill=c_chassis_metal)

    # Cab: (80, 44) to (128, 68)
    draw.rectangle([80, 44, 128, 68], fill=c_yellow)
    # Windows
    draw.rectangle([84, 48, 100, 62], fill=c_glass)
    draw.rectangle([104, 48, 124, 62], fill=c_glass)
    draw.rectangle([83, 47, 101, 63], outline=c_glass_frame)
    draw.rectangle([103, 47, 125, 63], outline=c_glass_frame)

    # Cab Roof: (76, 84) to (108, 92)
    draw.rectangle([76, 84, 108, 92], fill=c_chassis_metal)

    # Boom: (0, 80) to (76, 115)
    draw.rectangle([0, 80, 76, 115], fill=c_yellow)
    # Boom reinforcement plates
    draw.rectangle([4, 84, 30, 100], fill=c_yellow_dark)
    draw.rectangle([48, 84, 72, 100], fill=c_yellow_dark)

    # Hydraulic cylinders: (76, 110) to (110, 120) and (48, 115) to (76, 125)
    draw.rectangle([76, 110, 110, 120], fill=c_chassis_metal)
    draw.rectangle([80, 112, 106, 118], fill=c_hydraulic_chrome)
    draw.rectangle([48, 115, 76, 125], fill=c_chassis_metal)
    draw.rectangle([52, 117, 72, 123], fill=c_hydraulic_chrome)

    # Stick: (0, 120) to (48, 128)
    draw.rectangle([0, 120, 48, 128], fill=c_yellow)
    draw.rectangle([10, 122, 38, 126], fill=c_yellow_dark)

    # Bucket: (74, 98) to (128, 128)
    draw.rectangle([74, 98, 128, 128], fill=c_bucket_iron)
    # Lip scraping wear
    draw.rectangle([90, 114, 126, 118], fill=c_bucket_wear)
    # Teeth (90, 119) to (115, 123)
    draw.rectangle([90, 119, 115, 123], fill=c_teeth)
    # Soil fill texture inside bucket: (90, 123) to (128, 128)
    draw.rectangle([90, 123, 128, 128], fill=c_dirt)
    for x in range(90, 128, 2):
        for y in range(123, 128, 2):
            if (x + y) % 3 == 0:
                img.putpixel((x, y), (105, 73, 50, 255))

    os.makedirs("src/main/resources/assets/pw_groundworks_excavator/textures/entity", exist_ok=True)
    img.save("src/main/resources/assets/pw_groundworks_excavator/textures/entity/excavator.png")
    print("Entity texture created.")

def create_item_texture():
    # 32x32 item icon of a yellow excavator
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    c_yellow = (235, 175, 20, 255)
    c_dark = (45, 45, 48, 255)
    c_track = (30, 30, 35, 255)
    c_glass = (140, 200, 240, 255)
    c_iron = (90, 95, 102, 255)

    # Tracks
    draw.rectangle([4, 25, 26, 29], fill=c_track)
    for x in range(5, 25, 3):
        draw.point((x, 26), fill=(70, 70, 75, 255))

    # Chassis & Counterweight
    draw.rectangle([6, 21, 22, 24], fill=c_dark)
    draw.rectangle([5, 14, 10, 21], fill=c_dark) # counterweight

    # Engine hood
    draw.rectangle([10, 16, 17, 21], fill=c_yellow)

    # Cab
    draw.rectangle([13, 11, 19, 21], fill=c_yellow)
    draw.rectangle([14, 13, 18, 17], fill=c_glass) # Window

    # Boom (reaching up/right)
    draw.line([(18, 18), (24, 8)], fill=c_yellow, width=2)
    # Stick (reaching down/right)
    draw.line([(24, 8), (28, 16)], fill=c_yellow, width=2)
    # Bucket scoop
    draw.rectangle([26, 17, 30, 20], fill=c_iron)

    os.makedirs("src/main/resources/assets/pw_groundworks_excavator/textures/item", exist_ok=True)
    img.save("src/main/resources/assets/pw_groundworks_excavator/textures/item/excavator.png")
    print("Item texture created.")

if __name__ == "__main__":
    create_excavator_texture()
    create_item_texture()
