# MediShelf lexicons

Used by `core:nlp` to recognise active ingredients, brand names and manufacturers in OCR text
from medicine packs. One entry per line; blank lines and lines starting with `#` are ignored.

| File | Source |
|---|---|
| `fda_ingredients.txt`, `fda_brands.txt`, `fda_manufacturers.txt` | Derived from the U.S. FDA **Drugs@FDA** dataset (public domain, U.S. Government work): active ingredients with salt-free base names, brand names of NDA/BLA products, sponsor names. |
| `curated_ingredients.txt` | International non-proprietary names (INN) common outside the US. |
| `curated_brands.txt` | Brand names common in the Gulf region, Europe and Asia that are not US brands. |
| `curated_manufacturers.txt` | Global and regional manufacturers, written as printed on packs. |
| `synonyms.txt` | `INN = US name` pairs (e.g. `PARACETAMOL = ACETAMINOPHEN`); both sides are recognised and shown as printed. |

To refresh the FDA files, download https://www.fda.gov/media/89850/download and run the
builder script described in the main README.
