"""Testes do catálogo de sintomas e do pipeline de preparação do D2."""

from __future__ import annotations

import re

import pytest

import build_knowledge as bk
from symptom_catalog import SYMPTOMS
from textnorm import normalize_pt


def _matches(text: str) -> set[str]:
    norm = normalize_pt(text)
    return {s.id for s in SYMPTOMS if any(re.search(p, norm) for p in s.patterns)}


class TestCatalog:
    def test_ids_are_unique(self) -> None:
        ids = [s.id for s in SYMPTOMS]
        assert len(ids) == len(set(ids))

    def test_every_pattern_compiles_and_severity_is_valid(self) -> None:
        bk.validate_catalog()

    def test_red_flags_are_exactly_severity_four(self) -> None:
        assert all(s.red_flag == (s.severity == 4) for s in SYMPTOMS)
        assert {s.id for s in SYMPTOMS if s.red_flag} >= {
            "convulsao", "dificuldade_respirar", "colapso", "ingestao_toxica", "nao_urina",
            "vomito_sangue", "trauma", "paralisia", "ansia_sem_vomitar"}

    def test_d2_aliases_do_not_collide(self) -> None:
        bk.build_alias_index()  # lança ValueError em colisão


class TestNormalization:
    @pytest.mark.parametrize("raw, expected", [
        ("Vômito!", "vomito |"),
        ("Não está comendo, nem bebendo água.", "nao esta comendo | nem bebendo agua |"),
        ("Convulsãooo", "convulsao"),
        ("  CÃO   abatido  ", "cao abatido"),
    ])
    def test_normalize_pt(self, raw: str, expected: str) -> None:
        assert normalize_pt(raw).strip() == expected


class TestPatterns:
    @pytest.mark.parametrize("text, expected", [
        ("meu cachorro vomitou três vezes hoje", {"vomito_repetido"}),
        ("ele está muito abatido e sem apetite", {"apatia", "perda_apetite"}),
        ("a gata não consegue fazer xixi", {"nao_urina"}),
        ("está com falta de ar e a língua roxa", {"dificuldade_respirar", "gengivas_palidas"}),
        ("comeu chocolate ontem", {"ingestao_toxica"}),
        ("teve uma convulsão", {"convulsao"}),
        ("tosse seca e espirros", {"tosse", "espirros"}),
        ("fezes com sangue", {"diarreia_sangue"}),
    ])
    def test_expected_symptoms_are_detected(self, text: str, expected: set[str]) -> None:
        assert expected <= _matches(text)


class TestD2Mapping:
    @pytest.fixture(scope="class")
    def index(self) -> dict[str, str]:
        return bk.build_alias_index()

    @pytest.mark.parametrize("raw, kind, canonical", [
        ("Vomiting", "mapped", "vomito"),
        ("vomitting", "mapped", "vomito"),
        ("Loss of  appetite", "mapped", "perda_apetite"),
        ("Seizuers", "mapped", "convulsao"),
        ("Difficulty in breathing", "mapped", "dificuldade_respirar"),
        ("Death", "outcome", None),
        ("Normal appetite", "non_symptom", None),
        ("egg production decreases", "non_companion", None),
        ("ruffled feathers", "non_companion", None),
        ("pneumonia", "diagnosis", None),
    ])
    def test_classify_raw(self, index: dict[str, str], raw: str, kind: str, canonical: str | None) -> None:
        assert bk.classify_raw(raw, index) == (kind, canonical)

    def test_unknown_term_is_unmapped_not_forced(self, index: dict[str, str]) -> None:
        assert bk.classify_raw("drooping ears", index)[0] == "unmapped"

    def test_clean_rows_removes_exact_duplicates_and_repeated_symptoms(self, index: dict[str, str]) -> None:
        row = {"AnimalName": "Dog", "Breed": "Beagle", "Age": "3.0", "Weight_kg": "10.0",
               "MedicalHistory": "Vaccinated", "Symptom_1": "Fever", "Symptom_2": "fever",
               "Symptom_3": "Vomiting", "Symptom_4": "Death", "Symptom_5": "Fever"}
        cleaned, kinds, _ = bk.clean_rows([row, dict(row)], index)
        assert len(cleaned) == 1
        assert kinds["_duplicate_rows"] == 1
        assert cleaned[0]["symptoms"] == ["febre", "vomito"]
        assert kinds["outcome"] == 1
