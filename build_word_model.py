import pandas as pd
import re
from collections import Counter

INPUT_FILE = "data/nepali_corpus_roman.parquet"
OUTPUT_FILE = "data/roman_nepali_words.txt"

print("Loading dataset...")
df = pd.read_parquet(INPUT_FILE)

print("Rows:", len(df))

words = Counter()

print("Extracting words...")

for text in df["text"].dropna():
    text = str(text).lower()

    # Keep normal Roman/Latin words.
    found = re.findall(r"[a-z]+", text)

    for word in found:
        # Ignore extremely short/noisy tokens.
        if len(word) >= 2:
            words[word] += 1

print("Unique words:", len(words))

with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    for word, frequency in words.most_common():
        f.write(f"{word}\t{frequency}\n")

print()
print("DONE!")
print("Saved:", OUTPUT_FILE)
print()
print("Top 50 words:")

for word, frequency in words.most_common(50):
    print(f"{word:20} {frequency}")
