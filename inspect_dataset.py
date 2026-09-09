import pandas as pd

df = pd.read_parquet("data/nepali_corpus_roman.parquet")

print("ROWS:", len(df))
print("COLUMNS:", list(df.columns))
print()
print(df.head(10))
