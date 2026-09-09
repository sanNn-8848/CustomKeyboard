import re
from collections import Counter
import json

INPUT_FILE = "data/roman_nepali_words.txt"
OUTPUT_FILE = "data/char_model.json"

# Character n-gram size.
N = 3

# Only keep reasonably useful words.
MIN_FREQ = 2
MAX_WORD_LENGTH = 30

print("Loading word-frequency file...")

words = []

with open(INPUT_FILE, "r", encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")

        if len(parts) != 2:
            continue

        word, freq_text = parts

        try:
            frequency = int(freq_text)
        except ValueError:
            continue

        # Only alphabetic Roman words.
        if not re.fullmatch(r"[a-z]+", word):
            continue

        if len(word) < 2 or len(word) > MAX_WORD_LENGTH:
            continue

        if frequency < MIN_FREQ:
            continue

        words.append((word, frequency))

print("Words used:", len(words))

# Character n-gram counts.
ngrams = Counter()

# Total counts by prefix/context.
contexts = Counter()

for word, frequency in words:
    padded = "^" + word + "$"

    for i in range(len(padded) - N + 1):
        gram = padded[i:i + N]
        context = gram[:-1]

        # Frequency-weighted training.
        ngrams[gram] += frequency
        contexts[context] += frequency

print("Unique n-grams:", len(ngrams))

# Convert counts to probability-like scores.
model = {}

for gram, count in ngrams.items():
    context = gram[:-1]
    total = contexts[context]

    if total > 0:
        model[gram] = count / total

result = {
    "n": N,
    "ngrams": model
}

with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False)

print()
print("DONE!")
print("Saved:", OUTPUT_FILE)
print("Model n-grams:", len(model))
