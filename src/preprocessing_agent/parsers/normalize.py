"""Lossless text normalization helpers.

Normalization is used for comparisons and derived text only. Parsed models keep
the extracted source text verbatim so provenance offsets remain meaningful.
"""

import unicodedata


def normalize_text(value: str) -> str:
    if not isinstance(value, str):
        raise TypeError("value must be a string")
    return unicodedata.normalize("NFC", value).replace("\r\n", "\n").replace("\r", "\n")


def normalized_key(value: str) -> str:
    return " ".join(normalize_text(value).split()).casefold()
