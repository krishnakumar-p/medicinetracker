"""Builds MediShelf lexicons from Drugs@FDA (public domain). Usage: build_lexicon.py <fda_dir> <out_dir>"""
import csv, os, re, sys
fda, out = sys.argv[1], sys.argv[2]
csv.field_size_limit(10**7)

def rows(name):
    with open(os.path.join(fda, name), encoding="latin-1", newline="") as f:
        yield from csv.DictReader(f, delimiter="\t")

SALTS = {"HYDROCHLORIDE","HCL","SODIUM","POTASSIUM","CALCIUM","MAGNESIUM","SULFATE","SULPHATE","MALEATE","CITRATE",
         "ACETATE","TARTRATE","BITARTRATE","BESYLATE","BESILATE","MESYLATE","MESILATE","FUMARATE","SUCCINATE","PHOSPHATE",
         "HYDROBROMIDE","BROMIDE","CHLORIDE","NITRATE","TRIHYDRATE","DIHYDRATE","MONOHYDRATE","HYDRATE","ANHYDROUS",
         "DIHYDROCHLORIDE","LACTATE","GLUCONATE","PROPIONATE","DIPROPIONATE","VALERATE","BUTYRATE","XINAFOATE","TROMETHAMINE",
         "MALATE","OXALATE","PAMOATE","STEARATE","BENZOATE","SALICYLATE","TOSYLATE","NAPSYLATE","DISODIUM","HEMIHYDRATE",
         "MAGNESIUM TRIHYDRATE","ESTOLATE","ETHYLSUCCINATE","CYPIONATE","ENANTHATE","FUROATE","PIVALATE","CALCIUM DIHYDRATE"}
def clean(s):
    s = re.sub(r"\(.*?\)", " ", s.upper())
    s = re.sub(r"[^A-Z0-9 \-]", " ", s)
    return re.sub(r"\s+", " ", s).strip()
def base(name):
    words = name.split()
    while len(words) > 1 and words[-1] in SALTS:
        words = words[:-1]
    return " ".join(words)

appl_type, sponsors = {}, set()
for r in rows("Applications.txt"):
    appl_type[r["ApplNo"]] = r["ApplType"]
    s = clean(r["SponsorName"])
    if len(s) >= 3:
        sponsors.add(s)

ingredients, brands_raw = set(), set()
for r in rows("Products.txt"):
    parts = [clean(p) for p in re.split(r"[;,]", r["ActiveIngredient"] or "")]
    for p in parts:
        if 4 <= len(p) <= 60 and not any(c.isdigit() for c in p):
            ingredients.add(p)
            b = base(p)
            if len(b) >= 4:
                ingredients.add(b)
    if appl_type.get(r["ApplNo"]) in ("NDA", "BLA"):
        name = clean(r["DrugName"])
        # Keep the name before any strength/version token: "AUGMENTIN 250" -> "AUGMENTIN".
        head = []
        for w in name.split():
            if any(c.isdigit() for c in w):
                break
            head.append(w)
        if head:
            brands_raw.add(" ".join(head))

ingredient_words = {w for i in ingredients for w in i.split()}
brands = {b for b in brands_raw
          if 4 <= len(b) <= 30 and not any(c.isdigit() for c in b)
          and b not in ingredients and base(b) not in ingredients
          and not all(w in ingredient_words or w in SALTS for w in b.split())}

for name, data in (("ingredients", ingredients), ("brands", brands), ("manufacturers", sponsors)):
    with open(os.path.join(out, f"{name}.txt"), "w") as f:
        f.write("\n".join(sorted(data)) + "\n")
    print(name, len(data))
