"""
normalize_labels.py — Rule-based LVIS canonicalization engine.

LVIS is used ONLY as a raw word source.
NO model training. NO YOLO/LVIS integration. Vocabulary extraction only.
"""

import re
from typing import Optional

# ─────────────────────────────────────────────────────────────────────────────
# CANONICAL MAP  (LVIS raw label → canonical label)
# ─────────────────────────────────────────────────────────────────────────────
CANONICAL_MAP: dict[str, str] = {
    # Electronics
    "cellular telephone/cellular phone/cellphone/mobile phone/smart phone": "smartphone",
    "cellular telephone": "smartphone",
    "cellphone": "smartphone",
    "mobile phone": "smartphone",
    "smart phone": "smartphone",
    "laptop computer/notebook computer": "laptop",
    "laptop computer": "laptop",
    "notebook computer": "laptop",
    "television set/tv/tv set": "television",
    "television set": "television",
    "tv": "television",
    "computer keyboard/keyboard/keyboard computer": "keyboard",
    "computer keyboard": "keyboard",
    "mouse/mouse computer equipment/computer mouse": "computer mouse",
    "computer mouse": "computer mouse",
    "monitor/monitor computer equipment": "computer monitor",
    "speaker/speaker stereo equipment": "speaker",
    "earphone/earpiece/headphone": "earphones",
    "record player/phonograph/phonograph record player/turntable": "record player",
    "radio receiver/radio set/radio/tuner/tuner radio": "radio",
    "router/router computer equipment": "router",
    "camcorder": "video camera",
    "beeper/pager": "pager",
    # Furniture
    "sofa/couch/lounge": "sofa",
    "couch": "sofa",
    "chaise longue/chaise/daybed": "chaise longue",
    "recliner/reclining chair/lounger/lounger chair": "recliner",
    "highchair/feeding chair": "highchair",
    "deck chair/beach chair": "deck chair",
    "footstool/footrest": "footstool",
    "ottoman/pouf/pouffe/hassock": "ottoman",
    "coffee table/cocktail table": "coffee table",
    "armoire": "wardrobe",
    "locker/storage locker": "locker",
    "cupboard/closet": "cupboard",
    "crib/cot": "crib",
    "music stool/piano stool": "piano stool",
    # Kitchenware
    "frying pan/frypan/skillet": "frying pan",
    "frypan": "frying pan",
    "skillet": "frying pan",
    "pan/pan for cooking/cooking pan": "frying pan",
    "stove/kitchen stove/range/range kitchen appliance/kitchen range/cooking stove": "kitchen stove",
    "coffee maker/coffee machine": "coffee maker",
    "kettle/boiler": "electric kettle",
    "blender/liquidizer/liquidiser": "blender",
    "mixer/mixer kitchen tool/stand mixer": "stand mixer",
    "colander/cullender": "colander",
    "chopping board/cutting board/chopping block": "cutting board",
    "glass/glass drink container/drinking glass": "drinking glass",
    "flute glass/champagne flute": "champagne flute",
    "eggbeater/eggwhisk": "whisk",
    "reamer/reamer juicer/juicer/juice reamer": "juicer",
    "can opener/tin opener": "can opener",
    "corkscrew/bottle screw": "corkscrew",
    "peeler/peeler tool for fruit and vegetables": "peeler",
    "pitcher/pitcher vessel for liquid/ewer": "pitcher",
    "cream pitcher": "pitcher",
    "casserole": "casserole dish",
    "dishwasher/dishwashing machine": "dishwasher",
    "measuring stick/ruler/ruler measuring stick/measuring rod": "ruler",
    # Clothing
    "jersey/T-shirt/tee shirt": "t-shirt",
    "tee shirt": "t-shirt",
    "jean/blue jean/denim": "jeans",
    "short pants/shorts/shorts clothing/trunks/trunks clothing": "shorts",
    "trousers/pants/pants clothing": "trousers",
    "sweat pants": "sweatpants",
    "legging/legging clothing/leging/leging clothing/leg covering": "leggings",
    "dress/frock": "dress",
    "blazer/sport jacket/sport coat/sports jacket/sports coat": "blazer",
    "raincoat/waterproof jacket": "raincoat",
    "nightshirt/nightwear/sleepwear/nightclothes": "pajamas",
    "pajamas/pyjamas": "pajamas",
    "swimsuit/swimwear/bathing suit/swimming costume/bathing costume/swimming trunks/bathing trunks": "swimsuit",
    "underwear/underclothes/underclothing/underpants": "underwear",
    "tights/tights clothing/leotards": "tights",
    "pantyhose": "tights",
    "shoe/sneaker/sneaker type of shoe/tennis shoe": "sneaker",
    "flip-flop/flip-flop sandal": "flip-flop",
    "sandal/sandal type of shoe": "sandal",
    "slipper/slipper footwear/carpet slipper/carpet slipper footwear": "slipper",
    "arctic/arctic type of shoe/galosh/golosh/rubber/rubber type of shoe/gumshoe": "rain boot",
    "baseball cap/jockey cap/golf cap": "baseball cap",
    "beanie/beany": "beanie",
    "cowboy hat/ten-gallon hat": "cowboy hat",
    "brassiere/bra/bandeau": "bra",
    "polo shirt/sport shirt": "polo shirt",
    "turtleneck/turtleneck clothing/polo-neck": "turtleneck",
    "vest/waistcoat": "vest",
    "suit/suit clothing": "suit",
    "tux/tuxedo": "tuxedo",
    "overalls/overalls clothing": "overalls",
    "coverall": "overalls",
    "necktie/tie/tie necktie": "necktie",
    "bow-tie/bowtie": "bow tie",
    "spectacles/specs/eyeglasses/glasses": "eyeglasses",
    "bracelet/bangle": "bracelet",
    "handbag/purse/pocketbook": "handbag",
    "suitcase/baggage/luggage": "suitcase",
    "backpack/knapsack/packsack/rucksack/haversack": "backpack",
    "duffel bag/duffle bag/duffel/duffle": "duffel bag",
    "wallet/billfold": "wallet",
    "watch/wristwatch": "watch",
    "cincture/sash/waistband/waistcloth": "sash",
    # Office
    "binder/ring-binder": "binder",
    "stapler/stapler stapling machine": "stapler",
    "file cabinet/filing cabinet": "filing cabinet",
    "bulletin board/notice board": "bulletin board",
    "thumbtack/drawing pin/pushpin": "thumbtack",
    "rubber band/elastic band": "rubber band",
    "pencil box/pencil case": "pencil case",
    # Bathroom
    "shaver/shaver electric/electric shaver/electric razor": "electric shaver",
    "hair curler/hair roller/hair crimper": "hair curler",
    "dental floss/floss": "dental floss",
    "washbasin/basin/basin for washing/washbowl/washstand/handbasin": "bathroom sink",
    "automatic washer/washing machine": "washing machine",
    "toilet tissue/toilet paper/bathroom tissue": "toilet paper",
    "gargle/mouthwash": "mouthwash",
    # Tools
    "handsaw/carpenter's saw": "handsaw",
    "ax/axe": "axe",
    "pliers/plyers": "pliers",
    "crowbar/wrecking bar/pry bar": "crowbar",
    "barrow/garden cart/lawn cart/wheelbarrow": "wheelbarrow",
    "clippers/clippers for plants": "pruning shears",
    "iron/iron for clothing/smoothing iron/smoothing iron for clothing": "clothes iron",
    "tape measure/measuring tape": "measuring tape",
    "file/file tool": "file tool",
    # Household
    "clock/timepiece/timekeeper": "clock",
    "heater/warmer": "space heater",
    "candle/candlestick": "candle",
    "curtain/drapery": "curtain",
    "trash can/garbage can/wastebin/dustbin/trash barrel/trash bin": "trash can",
    "clothes hamper/laundry basket/clothes basket": "laundry basket",
    "coat hanger/clothes hanger/dress hanger": "clothes hanger",
    "coatrack/hatrack": "coat rack",
    "fire alarm/smoke alarm": "smoke alarm",
    "fire extinguisher/extinguisher": "fire extinguisher",
    "bedspread/bedcover/bed covering/counterpane/spread": "bedspread",
    "quilt/comforter": "quilt",
    "flashlight/torch": "flashlight",
    "oil lamp/kerosene lamp/kerosine lamp": "oil lamp",
    "lampshade": "lamp shade",
    "flowerpot": "flower pot",
    # Food
    "orange/orange fruit": "orange",
    "cantaloup/cantaloupe": "cantaloupe",
    "date/date fruit": "date",
    "kiwi fruit": "kiwi",
    "fig/fig fruit": "fig",
    "bell pepper/capsicum": "bell pepper",
    "chili/chili vegetable/chili pepper/chili pepper vegetable/chilli": "chili pepper",
    "cayenne/cayenne spice/cayenne pepper/cayenne pepper spice/red pepper/red pepper spice": "chili pepper",
    "edible corn/corn/maize": "corn",
    "bean curd/tofu": "tofu",
    "chickpea/garbanzo": "chickpea",
    "green onion/spring onion/scallion": "spring onion",
    "radish/daikon": "radish",
    "hamburger/beefburger/burger": "hamburger",
    "baguet/baguette": "baguette",
    "crescent roll/croissant": "croissant",
    "cookie/cooky/biscuit/biscuit cookie": "cookie",
    "hummus/humus/hommos/hoummos/humous": "hummus",
    "gelatin/jelly": "jelly",
    "omelet/omelette": "omelet",
    "lasagna/lasagne": "lasagna",
    "pop/pop soda/soda/soda pop/tonic/soft drink": "soda",
    "cocoa/cocoa beverage/hot chocolate/hot chocolate beverage/drinking chocolate": "hot chocolate",
    "cappuccino/coffee cappuccino": "cappuccino",
    "soya milk/soybean milk/soymilk": "soy milk",
    # Outdoor
    "bicycle/bike/bike bicycle": "bicycle",
    "bus/bus vehicle/autobus/charabanc/double-decker/motorbus/motorcoach": "bus",
    "car/car automobile/auto/auto automobile/automobile": "car",
    "motor scooter/scooter": "scooter",
    "cab/cab taxi/taxi/taxicab": "taxi",
    "fireplug/fire hydrant/hydrant": "fire hydrant",
    "mailbox/mailbox at home/letter box/letter box at home": "mailbox",
    "postbox/postbox public/mailbox/mailbox public": "mailbox",
    "streetlight/street lamp": "street lamp",
    "soccer ball": "soccer ball",
    "tennis racket": "tennis racket",
    "football/football American": "football",
    "hamburger/beefburger/burger": "hamburger",
}

# ─────────────────────────────────────────────────────────────────────────────
# EXCLUSION LIST — labels that must NOT enter the taxonomy
# ─────────────────────────────────────────────────────────────────────────────
EXCLUDED_LABELS: set[str] = {
    # Weapons
    "machine gun", "rifle", "pistol", "handgun", "gun", "dagger", "sword",
    "spear", "lance", "bow", "bulletproof vest",
    # Harmful
    "electric chair", "handcuff", "gasmask", "projectile", "missile",
    # Abstract / non-visual
    "award", "accolade", "garbage", "crumb", "coloring material", "condiment",
    "legume", "pet", "rodent", "cub", "motor", "motor vehicle",
    "automotive vehicle", "musical instrument", "cooking utensil",
    "sportswear", "athletic wear", "activewear", "leather", "fabric",
    # People
    "person", "baby", "child", "boy", "girl", "man", "woman", "human",
    # Animals (out of Phase 1 scope)
    "dog", "cat", "horse", "cow", "sheep", "goat", "pig", "bird", "fish",
    "rabbit", "bear", "lion", "tiger", "elephant", "giraffe", "monkey",
    "gorilla", "dolphin", "shark", "snake", "lizard", "frog", "turtle",
    "crab", "deer", "zebra", "panda", "koala", "penguin", "eagle",
    "falcon", "owl", "parrot", "duck", "goose", "pigeon", "crow",
    "butterfly", "beetle", "cockroach", "spider", "bee", "hornet",
    "hamster", "squirrel", "rat", "ferret", "baboon", "alligator",
    "camel", "cougar", "gazelle", "hippopotamus", "rhinoceros", "walrus",
    "manatee", "wolf", "ostrich", "flamingo", "octopus", "squid",
    # Prehistoric / fantasy
    "mammoth", "gargoyle",
}


def normalize(raw_label: str) -> Optional[str]:
    """Normalize a raw LVIS label to its canonical form. Returns None if excluded."""
    cleaned = raw_label.strip().lower()
    first_variant = cleaned.split("/")[0].strip()

    if first_variant in EXCLUDED_LABELS or cleaned in EXCLUDED_LABELS:
        return None

    if cleaned in CANONICAL_MAP:
        return CANONICAL_MAP[cleaned]

    if first_variant in CANONICAL_MAP:
        return CANONICAL_MAP[first_variant]

    # Default: use first variant, normalize whitespace
    result = re.sub(r"\s+", " ", first_variant).strip()
    return result if result else None


def build_alias_map(concepts: list[dict]) -> dict[str, str]:
    """Return {alias → canonical_label} reverse lookup from concept list."""
    alias_map: dict[str, str] = {}
    for concept in concepts:
        canonical = concept["canonical_label"]
        for alias in concept.get("aliases", "").split("|"):
            alias = alias.strip().lower()
            if alias and alias != canonical:
                alias_map[alias] = canonical
    return alias_map


def is_excluded(label: str) -> bool:
    """Return True if label is on the exclusion list."""
    cleaned = label.strip().lower()
    return cleaned.split("/")[0].strip() in EXCLUDED_LABELS or cleaned in EXCLUDED_LABELS


if __name__ == "__main__":
    tests = [
        ("cellular telephone/cellular phone/cellphone/mobile phone/smart phone", "smartphone"),
        ("sofa/couch/lounge", "sofa"),
        ("laptop computer/notebook computer", "laptop"),
        ("frying pan/frypan/skillet", "frying pan"),
        ("spectacles/specs/eyeglasses/glasses", "eyeglasses"),
        ("television set/tv/tv set", "television"),
        ("automatic washer/washing machine", "washing machine"),
        ("machine gun", None),
        ("person", None),
        ("mammoth", None),
    ]
    print("normalize_labels — smoke test")
    print("=" * 55)
    passed = 0
    for raw, expected in tests:
        result = normalize(raw)
        ok = result == expected
        passed += ok
        icon = "✅" if ok else "❌"
        print(f"  {icon}  '{raw[:40]}' → '{result}'")
    print(f"\n{passed}/{len(tests)} passed")
