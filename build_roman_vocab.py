#!/usr/bin/env python3
"""
Build a clean Roman-Nepali vocabulary with Devanagari-based variant grouping.

Reads:  data/nepali_corpus_roman.parquet   (308k romanized YouTube comments)
Writes: data/roman_words.json              (word list + variant groups)

Pipeline:
  1. Tokenize Latin words from the corpus.
  2. Keep only frequency >= MIN_FREQ, apply junk/English filters.
  3. Merge the hand-curated dictionary (always kept, counts as high-frequency).
  4. Map each word to a canonical Devanagari form so Roman variants
     (aja / aaja / aaj) collapse into one group. Keep top-freq spelling.
"""

import json
import re
from collections import Counter, defaultdict

import pandas as pd

DATA_DIR = "data"
CORPUS = f"{DATA_DIR}/nepali_corpus_roman.parquet"
OUTPUT = f"{DATA_DIR}/roman_words.json"

MIN_FREQ = 8
MAX_WORD_LENGTH = 18

WORD_RE = re.compile(r"[a-z]+")

# Common English words (stop + everyday) that pollute the roman corpus.
# Filtered out so the keyboard favours Nepali content.
ENGLISH_BLOCKLIST = set("""
the and to you is of it in this that for with on as be at are from or was were
not have has had by he she they we but your my me his her their our i can could
will would shall should do does did been being a an so if then than there here
when where why how what which who whom whose all any some many much no yes too
very just about into over after before while because also only more most other
another between through during against these those them such each few both
good great nice best love like want need make made get got go going went come
came see saw look put take took give gave new old big small day time people
year man men woman women child children world work life way back down up out
off again never always always ever every own right left well really just still
even though thing things someone something anything nothing everything nothing
bro brother sister friend friends family one two three four five six seven
eight nine ten hundred thousand please thank thanks ok okay yes yeah no na
hello hi bye hey haha lol lmao omg wow ha hmm um ah oh song video music audio
watch subscribe like share comment comments views view channel youtube google
facebook instagram tiktok twitter whatsapp app phone mobile android iphone
video songs asia nepal india nepalii kathmandu pokhara bhaktapur lalitpur
""".split())

JUNK_RE = re.compile(
    r"(.)\1\1"          # triple repeated char (kkokk, hheeee)
    r"|^[bcdfghjkpqrstvwxz]{4,}$"  # pure consonants
    r"|^[aeiouy]{4,}$"  # pure vowels
)

# Hand-curated words that must always survive, whatever the corpus says.
CURATED = {
    "namaste": 1000, "namaskar": 900, "dhanyabaad": 950, "dhanyawad": 900,
    "thik": 900, "thiik": 500, "thikcha": 400, "thik chha": 850,
    "la": 950, "huss": 900, "hajur": 800,
    "kina": 900, "ke": 1000, "ko": 900, "kaha": 900, "kata": 850,
    "kahile": 850, "kasari": 800, "kati": 880, "kasto": 880, "katai": 600,
    "ke chha": 950, "k bhayo": 850, "k huncha": 800,
    "ma": 1000, "timi": 950, "hami": 900, "tapaai": 850, "tapai": 600,
    "yesto": 800, "tyesto": 750, "yeso": 500, "teso": 600,
    "mero": 950, "mera": 900, "meri": 850, "timro": 900, "hamro": 850,
    "hami ko": 400, "usko": 800, "tesko": 800, "unkor": 400,
    "usle": 780, "uniharu": 700, "unihru": 300, "bhai": 900, "didi": 850,
    "yo": 1000, "tyo": 900, "yaha": 800, "tyaha": 750, "yahaan": 700,
    "utaa": 400, "teta": 400, "eha": 300, "cha": 1000, "chha": 1000,
    "chaina": 950, "chhaina": 900, "chhaina": 700, "thyo": 900, "thiyo": 850,
    "hola": 850, "garchu": 900, "garchha": 800, "garchan": 700, "garchin": 600,
    "gareko": 800, "garnu": 800, "garne": 800, "gara": 800, "gari": 700,
    "garera": 800, "garnubhar": 200, "gaye": 700, "gayo": 850, "gaeko": 700,
    "khana": 800, "khau": 500, "khanu": 600, "khayegan": 200, "khadeko": 300,
    "pini": 850, "pula": 300, "jam": 400, "jaanchu": 700, "janchu": 700,
    "janchhu": 500, "jaau": 600, "janaygay": 200, "aunu": 850, "janu": 800,
    "jane": 850, "jau": 800, "aau": 800, "aayo": 900, "aaye": 800,
    "aayeko": 700, "aakeko": 300, "gumna": 700, "aamaa": 950, "aama": 850,
    "bua": 900, "buba": 850, "dai": 850, "bahini": 900, "kaka": 800,
    "kaki": 800, "mama": 800, "mami": 800, "chhora": 850, "chhori": 850,
    "bou": 400, "sathi": 850, "logne": 700, "swasni": 700, "buhari": 750,
    "jethan": 650, "kanchha": 650, "keta": 850, "keto": 800, "keti": 700,
    "manche": 800, "mans": 200, "ek": 1000, "dui": 950, "tin": 900,
    "char": 850, "panch": 800, "chhaar": 500, "saat": 700, "aath": 650,
    "nau": 600, "dus": 550, "saya": 500, "hajar": 450, "hajaro": 300,
    "aaja": 900, "aja": 500, "aaji": 300, "bholi": 850, "hijo": 800,
    "paraahi": 700, "paradonue": 200, "sombaar": 750, "mangalbaar": 700,
    "budhabaar": 700, "bihibaar": 700, "sukrabaar": 700, "saniibaar": 700,
    "aaitabaar": 650, "beluka": 800, "bihaan": 850, "bihana": 850,
    "rat": 800, "din": 850, "mahina": 700, "barsha": 650,
    "kam": 900, "padh": 850, "lekh": 800, "bol": 850, "her": 800,
    "ja": 900, "aa": 950, "de": 900, "lau": 850, "kha": 800, "pi": 800,
    "soch": 750, "bujh": 700, "sik": 750, "sikhaa": 700,
    "lena": 850, "dinu": 800, "paunu": 800, "basnu": 750, "uthnu": 750,
    "hidnu": 700, "daudanu": 650, "khelnu": 800, "padhnu": 850, "lekhnu": 800,
    "bujhnu": 750, "bolnu": 850, "sunna": 800, "sun": 700, "suna": 750,
    "hernu": 750, "ramro": 900, "naramro": 850, "thulo": 850, "sano": 850,
    "lamo": 800, "choto": 800, "gahro": 750, "sajilo": 800, "naya": 850,
    "purano": 800, "ramailo": 900, "sundar": 800, "sundari": 750,
    "mitho": 850, "guliyo": 750, "tito": 700, "nilo": 700, "rato": 800,
    "pahelo": 750, "hario": 750, "kalo": 800, "seto": 750,
    "ghar": 900, "kamra": 800, "bato": 850, "pasal": 800, "kitab": 750,
    "kalam": 700, "batti": 650, "paani": 850, "panni": 600, "aago": 800,
    "geet": 750, "tara": 700, "chandrama": 700, "suraj": 750, "khet": 700,
    "bagaicha": 700, "maya": 850, "prem": 800, "khusi": 900, "khushi": 850,
    "sukhi": 800, "dukhi": 750, "sanchai": 850, "sancho": 850,
    "dherai": 900, "thorai": 800, "ekdam": 750, "ali": 850, "sadhai": 800,
    "kahilepani": 650, "chitai": 750, "bistarai": 750, "pharkera": 700,
    "sangai": 750, "mathi": 850, "tala": 850, "agadi": 800,
    "pachadi": 750, "najik": 700, "para": 700, "bhitra": 800, "bahira": 800,
    "bichma": 700, "chheu": 700, "hari": 850, "lai": 900, "le": 900,
    "baata": 850, "bata": 850, "sanga": 800, "maa": 850,
    "bhat": 850, "daal": 800, "dahl": 400, "tarkari": 750, "dahi": 750,
    "dudh": 750, "chiya": 800, "paisa": 850, "gheu": 650, "nun": 700,
    "chini": 700, "kat": 300, "gaun": 700, "pahar": 750, "nadi": 700,
    "sahara": 700, "sahari": 700, "jungle": 700, "thaha": 800, "thahah": 300,
    "galti": 850, "galat": 800, "sahi": 900, "bilkul": 800, "laijau": 750,
    "line": 800, "hera": 800, "aadha": 750, "pura": 750, "purnata": 700,
    "ho": 1000, "hoina": 850, "chhu": 750, "hey": 700, "so": 700,
    "bishal": 700, "bichar": 800, "bichara": 750, "kura": 900, "katha": 800,
    "kahani": 850, "jeewan": 800, "jeevan": 750, "samaya": 750, "saman": 700,
    "bhagya": 700, "ekaant": 700, "prasna": 700, "pratibha": 700, "shakti": 700,
    "bishwasa": 750, "sambhanda": 700, "vachan": 700, "sthiti": 700,
    "bhayo": 900, "huncha": 900, "hunchha": 850, "paryo": 800, "parcha": 750,
    "lagyo": 850, "khao": 800, "khan": 750, "du": 700, "haru": 900,
    "bhaneko": 750, "bhanne": 800, "bhane": 750, "gayian": 200, "garaun": 200,
    "na": 900, "ni": 900, "ki": 900, "ra": 900, "ta": 700, "ke ho": 600,
    "hai": 700, "an": 500, "ani": 750, "chai": 800, "pheri": 700,
    "pardi": 400, "mathi": 850, "mujhi": 200, "mero naam": 950,
    "timro naam": 850, "kasto cha": 850, "thik thak": 800, "namaste cha": 800,
    "subha prabhat": 700, "shubha ratri": 750, "dinara": 300, "memor": 200,
}

# Roman -> Devanagari transliteration (longest match first).
_TRANSLIT = [
    ("chh", "\u091b"), ("ksh", "\u0915\u094d\u0937"), ("jn", "\u091c\u094d\u091e"),
    ("kh", "\u0916"), ("gh", "\u0918"), ("ng", "\u0919"), ("ny", "\u091e"),
    ("ch", "\u091a"), ("jh", "\u091d"), ("th", "\u0920"), ("dh", "\u0927"),
    ("ph", "\u092b"), ("bh", "\u092d"), ("sh", "\u0936"), ("t", "\u0924"),
    ("d", "\u0926"), ("n", "\u0928"), ("p", "\u092a"), ("b", "\u092c"),
    ("m", "\u092e"), ("y", "\u092f"), ("r", "\u0930"), ("l", "\u0932"),
    ("v", "\u0935"), ("w", "\u0935"), ("s", "\u0938"), ("h", "\u0939"),
    ("k", "\u0915"), ("g", "\u0917"), ("j", "\u091c"), ("z", "\u091c"),
    ("aa", "\u0906"), ("ii", "\u0908"), ("ee", "\u0908"), ("uu", "\u0902\u090a"),
    ("oo", "\u0909"), ("ai", "\u0910"), ("ei", "\u0910"), ("au", "\u0913"),
    ("ou", "\u0913"), ("a", "\u0905"), ("i", "\u0907"), ("u", "\u0909"),
    ("e", "\u090f"), ("o", "\u0913"), ("c", "\u091a"), ("f", "\u092b"),
    ("x", "\u0915"), ("q", "\u0915"),
]

# Long-vowel collapse for canonical grouping: aaja/aja -> same group.
_LONG_TO_SHORT = {
    "\u0906": "\u0905",  # aa -> a
    "\u0908": "\u0907",  # ii/ee -> i
    "\u0902\u090a": "\u0909",  # uu -> u
    "\u0910": "\u090f",  # ai -> e
    "\u0913": "\u0913",  # o stays
}


def transliterate(word: str) -> str:
    """Approximate Roman -> Devanagari, then collapse long vowels."""
    out = []
    i = 0
    while i < len(word):
        matched = False
        for roman, dev in _TRANSLIT:
            if word.startswith(roman, i):
                out.append(_LONG_TO_SHORT.get(dev, dev))
                i += len(roman)
                matched = True
                break
        if not matched:
            # Skip unknown chars (shouldn't happen for [a-z]+).
            i += 1
    return "".join(out)


def normalize_key(word: str) -> str:
    """Phonetic group key (ASCII). Folds Roman-Nepali spelling variants:
       aaja/aja/aaj -> 'aj', khusi/khushi -> 'khusi', keta/ketaa -> 'ket'.
       Deliberately does NOT collapse distinct phonemes (jh/ph/bh stay) so
       unrelated words like aaja (today) and ajha (still) don't merge.
       This is safer than transliteration: no Unicode, no matra bugs."""
    w = word.lower()
    if not w:
        return ""
    # Long repeated vowels collapse to their short form.
    w = (w.replace("aa", "a").replace("ee", "i").replace("ii", "i")
          .replace("oo", "u").replace("uu", "u")
          .replace("ai", "e").replace("ei", "e")
          .replace("au", "o").replace("ou", "o"))
    # Aspiration alternates that are the same phoneme share a key.
    w = w.replace("chh", "ch").replace("sh", "s")
    # A trailing short 'a' is usually the inherent vowel -> drop it.
    if w.endswith("a") and w != "a":
        w = w[:-1]
    return w.strip()


def is_english(word: str) -> bool:
    return word in ENGLISH_BLOCKLIST


def load_corpus_counts() -> Counter:
    df = pd.read_parquet(CORPUS)
    counts = Counter()
    for text in df["text"].dropna():
        for raw in WORD_RE.findall(str(text).lower()):
            if JUNK_RE.search(raw):
                continue
            counts[raw] += 1
    return counts


def main():
    print("Loading corpus counts...")
    counts = load_corpus_counts()
    print("Raw unique words:", len(counts))

    # Seed with curated dictionary.
    curated = Counter(CURATED)

    # Filter corpus words.
    words = Counter()
    for word, freq in counts.items():
        if len(word) < 2 or len(word) > MAX_WORD_LENGTH:
            continue
        if is_english(word):
            continue
        if freq < MIN_FREQ:
            continue
        words[word] = freq

    # Merge: curated always wins; corpus frequencies otherwise.
    merged = Counter()
    for word, freq in words.items():
        merged[word] = max(freq, curated.get(word, 0))
    for word, freq in curated.items():
        merged[word] = max(freq, merged.get(word, 0))

    # Drop obviously garbage curated leftovers.
    cleaned = Counter()
    for word, freq in merged.items():
        if JUNK_RE.search(word):
            continue
        cleaned[word] = freq

    print("Unique (filtered):", len(cleaned))

    # Sort by frequency desc, then alphabetically for stability.
    ordered = sorted(cleaned.items(), key=lambda kv: (-kv[1], kv[0]))

    # Cap to keep asset reasonable.
    cap = 24000
    ordered = ordered[:cap]

    # Variant groups: normalized key -> list of words (top freq first).
    # The canonical (highest-frequency) spelling of a group is members[0],
    # so it must be included even when key==word.
    groups = defaultdict(list)
    for word, _freq in ordered:
        key = normalize_key(word)
        if key:
            groups[key].append(word)

    words_json = [{"w": w, "f": f} for w, f in ordered]
    groups_json = {k: v for k, v in groups.items() if len(v) > 1}

    payload = {
        "words": words_json,
        "groups": groups_json,
    }

    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)

    print("Wrote:", OUTPUT)
    print("Words:", len(words_json))
    print("Multi-word variant groups:", len(groups_json))
    print()
    print("Sample normalizations:")
    for w in ["aaja", "aja", "aaj", "mero", "khusi", "khushi", "keta", "ketaa"]:
        print(f"  {w:8} -> {normalize_key(w)}")


if __name__ == "__main__":
    main()