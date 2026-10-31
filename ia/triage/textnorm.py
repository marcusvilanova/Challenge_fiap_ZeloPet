"""Normalização de texto — espelha exatamente o TextNormalizer.java do backend."""

from __future__ import annotations

import re
import unicodedata

_CLAUSE_BREAK = re.compile(r"[.,;:!?\n\r()\[\]{}…]+")
_NON_ALNUM = re.compile(r"[^a-z0-9| ]+")
_SPACES = re.compile(r"\s+")
_REPEAT = re.compile(r"([a-z])\1{2,}")


def strip_accents(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")


def normalize_pt(text: str) -> str:
    """minúsculas, sem acentos, pontuação de fim de oração -> ' | ', letras repetidas colapsadas."""
    lowered = strip_accents(text.lower())
    lowered = _REPEAT.sub(r"\1", lowered)
    lowered = _CLAUSE_BREAK.sub(" | ", lowered)
    lowered = lowered.replace("'", " ")
    lowered = _NON_ALNUM.sub(" ", lowered)
    return _SPACES.sub(" ", lowered).strip()


def normalize_en(text: str) -> str:
    """Grafia do D2: minúsculas e espaços colapsados."""
    return _SPACES.sub(" ", text.strip().lower())
