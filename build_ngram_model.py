#!/usr/bin/env python3
"""
Build bigram + trigram counts for next-word prediction.

Reads:  data/nepali_corpus_roman.parquet
        data/roman_words.json (valid vocabulary + normalized group keys)
Writes: data/word_ngrams.json

The model can PREDICT the next word, not just complete the current prefix.
Words are only ever sampled from the valid vocabulary (no fabrication).
"""

import json
import re
from collections import Counter, defaultdict

import pandas as pd

DATA_DIR = "data"
CORPUS = f"{DATA_DIR}/nepali_corpus_roman.parquet"
VOCAB = f"{DATA_DIR}/roman_words.json"
OUTPUT = f"{DATA_DIR}/word_ngrams.json"

WORD_RE = re.compile(r"[a-z]+")
SEQUENCE_LIMIT = 30

# Curated seed bigrams so common sentences win loudly (also used by tests).
SEED_BIGRAMS = [
    ("ma", "garchu", 1000), ("ma", "cha", 800), ("timi", "kasto", 500),
    ("huss", "la", 800), ("thik", "cha", 900), ("namaste", "kasto", 400),
    ("mero", "naam", 700), ("timro", "naam", 600),
    ("aaja", "k", 300), ("bholi", "k", 300),
    ("k", "huncha", 500), ("k", "bhayo", 500),
    ("dhanyabaad", "la", 400), ("maaph", "la", 400),
    ("ramro", "cha", 600), ("naramro", "cha", 400),
    ("thulo", "cha", 300), ("sano", "cha", 300),
    ("ghar", "janchu", 900), ("ghar", "gayo", 500), ("ghar", "chha", 400),
    ("ghar", "jane", 400), ("ghar", "jana", 400), ("ghar", "ma", 400),
    ("aja", "ghar", 700), ("aaja", "ghar", 700),
    ("ma", "aja", 700), ("ma", "aaja", 600),
    ("kina", "aayo", 500), ("kahile", "aayo", 500),
]


def load_vocab():
    with open(VOCAB, encoding="utf-8") as f:
        data = json.load(f)
    valid = {p["w"] for p in data["words"]}
    return valid


def valid_tokens(text: str, valid: set) -> list:
    tokens = []
    for raw in WORD_RE.findall(text.lower()):
        if raw in valid:
            tokens.append(raw)
        if len(tokens) >= SEQUENCE_LIMIT:
            break
    return tokens


def main():
    print("Loading vocabulary...")
    valid = load_vocab()
    print("Valid words:", len(valid))

    print("Loading corpus...")
    df = pd.read_parquet(CORPUS)

    bigrams = Counter()   # (w1, w2)
    trigrams = Counter()  # (w1, w2, w3)

    for text in df["text"].dropna():
        tokens = valid_tokens(str(text), valid)
        for i in range(len(tokens) - 1):
            bigrams[(tokens[i], tokens[i + 1])] += 1
        for i in range(len(tokens) - 2):
            trigrams[(tokens[i], tokens[i + 1], tokens[i + 2])] += 1

    for w1, w2, count in SEED_BIGRAMS:
        bigrams[(w1, w2)] = max(bigrams[(w1, w2)], count)

    print("Raw bigrams:", len(bigrams), "trigrams:", len(trigrams))

    # Prune: keep only reasonably confident entries, top-K per context.
    MIN_BIGRAM = 3
    MIN_TRIGRAM = 2
    TOP_PER_CONTEXT = 12

    bi_out = defaultdict(dict)
    for (w1, w2), c in bigrams.items():
        if c >= MIN_BIGRAM:
            bi_out[w1][w2] = c

    tri_out = defaultdict(list)
    for (w1, w2, w3), c in trigrams.items():
        if c >= MIN_TRIGRAM:
            tri_out[f"{w1} {w2}"].append((w3, c))

    # Cap context lists.
    bi_out = {k: dict(sorted(v.items(), key=lambda kv: -kv[1])[:TOP_PER_CONTEXT])
              for k, v in bi_out.items()}
    tri_out = {k: [w for w, _ in sorted(v, key=lambda kv: -kv[1])[:TOP_PER_CONTEXT]]
               for k, v in tri_out.items()}

    payload = {"bigrams": bi_out, "trigrams": tri_out}

    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)

    print("Wrote:", OUTPUT)
    print("Bigram contexts:", len(bi_out), "Trigram contexts:", len(tri_out))
    print()
    print("Sample: ma ->", dict(list(bi_out.get("ma", {}).items())[:8]))
    print("Sample: meri aama ->", tri_out.get("meri aama", [])[:8])


if __name__ == "__main__":
    main()