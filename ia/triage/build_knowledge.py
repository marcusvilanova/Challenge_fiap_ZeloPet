"""Pipeline de preparação do D2 (veterinary_clinical_data.csv) e geração do conhecimento de triagem.

Entradas : D2 (CSV) + symptom_catalog.py (curadoria da equipe)
Saídas   : backend/src/main/resources/triage/triage_knowledge.json  (consumido pelo motor Java)
           docs/relatorios/d2_qualidade.md e d2_mapeamento_sintomas.csv
           data/samples/d2_clean_sample.csv (amostra controlada)

O D2 NÃO possui diagnóstico-alvo confiável (ver Sprint 3). Por isso ele não treina um classificador
de doenças: serve para (a) auditar e normalizar o vocabulário de sintomas, (b) medir cobertura do
catálogo, (c) fornecer frequência de referência por espécie e (d) definir o perfil do pet
(raças e histórico). A prioridade clínica vem de regras de segurança, não do D2.

Uso:
  python build_knowledge.py --d2 <veterinary_clinical_data.csv> [--repo-root <raiz do repositório>]
"""

from __future__ import annotations

import argparse
import csv
import difflib
import itertools
import json
import logging
import re
import statistics
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from symptom_catalog import (
    BRACHYCEPHALIC_BREEDS,
    D2_EXCLUDED,
    D2_NON_COMPANION_HINTS,
    SYMPTOMS,
    Symptom,
)
from textnorm import normalize_en

LOG = logging.getLogger("zelo.triage.build")
KNOWLEDGE_VERSION = "1.0.0"
SYMPTOM_COLUMNS = [f"Symptom_{i}" for i in range(1, 6)]
FUZZY_CUTOFF = 0.88
MIN_PAIR_COUNT = 40

SPECIES = {"dog": "dog", "cat": "cat"}
HISTORY_TAGS = {
    "Vaccinated": ("vacinado", "Vacinação em dia"),
    "Not vaccinated": ("nao_vacinado", "Sem vacinação em dia"),
    "Chronic illness": ("doenca_cronica", "Doença crônica"),
    "Previous heart condition": ("cardiopatia", "Problema cardíaco prévio"),
    "Previous kidney disease": ("doenca_renal", "Doença renal prévia"),
    "Allergies": ("alergias", "Alergias"),
    "Dental issues": ("problemas_dentarios", "Problemas dentários"),
    "Parasite history": ("parasitas", "Histórico de parasitas"),
    "Recent surgery": ("cirurgia_recente", "Cirurgia recente"),
    "Skin conditions history": ("problemas_pele", "Problemas de pele prévios"),
    "No previous conditions": ("sem_historico", "Sem condições prévias"),
}
BREED_PT = {"Mixed Breed": "SRD (sem raça definida)"}


def build_alias_index() -> dict[str, str]:
    index: dict[str, str] = {}
    for sym in SYMPTOMS:
        for alias in sym.en:
            key = normalize_en(alias)
            if key in index and index[key] != sym.id:
                raise ValueError(f"alias '{key}' mapeado para {index[key]} e {sym.id}")
            index[key] = sym.id
    return index


def validate_catalog() -> None:
    for sym in SYMPTOMS:
        if not 1 <= sym.severity <= 4:
            raise ValueError(f"{sym.id}: severidade inválida")
        for pattern in sym.patterns:
            re.compile(pattern)  # falha cedo se o regex for inválido
    LOG.info("catálogo válido: %d sintomas, %d padrões PT", len(SYMPTOMS),
             sum(len(s.patterns) for s in SYMPTOMS))


def classify_raw(raw: str, index: dict[str, str]) -> tuple[str, str | None]:
    """Retorna (tipo, id_canônico). tipo ∈ mapped | fuzzy | outcome | non_symptom | diagnosis | non_companion | unmapped."""
    key = normalize_en(raw)
    if key in index:
        return "mapped", index[key]
    for kind, terms in D2_EXCLUDED.items():
        if key in terms:
            return kind, None
    close = difflib.get_close_matches(key, index.keys(), n=1, cutoff=FUZZY_CUTOFF)
    if close:
        return "fuzzy", index[close[0]]
    padded = f" {key} "
    if any(hint in padded for hint in D2_NON_COMPANION_HINTS):
        return "non_companion", None
    return "unmapped", None


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def clean_rows(rows: list[dict[str, str]], index: dict[str, str]) -> tuple[list[dict], Counter, Counter]:
    """Remove duplicatas exatas, normaliza espécie e mapeia sintomas; um sintoma por linha."""
    seen: set[tuple] = set()
    kinds: Counter = Counter()
    raw_counts: Counter = Counter()
    cleaned: list[dict] = []
    duplicates = 0
    for row in rows:
        signature = tuple(row.values())
        if signature in seen:
            duplicates += 1
            continue
        seen.add(signature)
        symptoms: list[str] = []
        for column in SYMPTOM_COLUMNS:
            raw = row[column]
            kind, canonical = classify_raw(raw, index)
            kinds[kind] += 1
            raw_counts[(normalize_en(raw), kind, canonical or "")] += 1
            if canonical and canonical not in symptoms:
                symptoms.append(canonical)
        cleaned.append({
            "species": SPECIES[row["AnimalName"].strip().lower()],
            "breed": row["Breed"].strip(),
            "age": float(row["Age"]),
            "weight": float(row["Weight_kg"]),
            "history": row["MedicalHistory"].strip(),
            "symptoms": symptoms,
        })
    kinds["_duplicate_rows"] = duplicates
    return cleaned, kinds, raw_counts


def prevalence(cleaned: list[dict]) -> tuple[dict, dict]:
    totals = Counter(r["species"] for r in cleaned)
    present: dict[str, Counter] = {sp: Counter() for sp in totals}
    for r in cleaned:
        for s in r["symptoms"]:
            present[r["species"]][s] += 1
    return totals, present


def cooccurrence(cleaned: list[dict]) -> dict:
    n = len(cleaned)
    singles: Counter = Counter()
    pairs: Counter = Counter()
    for r in cleaned:
        uniq = sorted(set(r["symptoms"]))
        singles.update(uniq)
        pairs.update(itertools.combinations(uniq, 2))
    lifts = []
    for (a, b), count in pairs.items():
        if count >= MIN_PAIR_COUNT:
            lifts.append((count * n / (singles[a] * singles[b]), a, b, count))
    lifts.sort(reverse=True)
    values = [v[0] for v in lifts]
    frequent = {s for s, _ in singles.most_common(10)}
    frequent_lifts = [v for v, a, b, _ in lifts if a in frequent and b in frequent]
    return {
        "pairs_evaluated": len(lifts),
        "lift_min": round(min(values), 3) if values else None,
        "lift_median": round(statistics.median(values), 3) if values else None,
        "lift_max": round(max(values), 3) if values else None,
        "pairs_lift_gt_3": sum(1 for v in values if v > 3),
        "top10_symptoms": sorted(frequent),
        "top10_lift_min": round(min(frequent_lifts), 2) if frequent_lifts else None,
        "top10_lift_max": round(max(frequent_lifts), 2) if frequent_lifts else None,
        "top": [{"a": a, "b": b, "count": c, "lift": round(v, 1)} for v, a, b, c in lifts[:5]],
    }


def history_dependence(cleaned: list[dict], symptoms: tuple[str, ...]) -> dict[str, tuple[float, float]]:
    """Faixa (mín, máx) da prevalência de cada sintoma entre os valores de MedicalHistory."""
    totals = Counter(r["history"] for r in cleaned)
    result: dict[str, tuple[float, float]] = {}
    for symptom in symptoms:
        counts = Counter(r["history"] for r in cleaned if symptom in r["symptoms"])
        rates = [counts[h] / totals[h] for h in totals]
        result[symptom] = (round(min(rates), 3), round(max(rates), 3))
    return result


def symptom_entry(sym: Symptom, totals: Counter, present: dict, mentions: Counter) -> dict:
    return {
        "id": sym.id, "label": sym.label, "category": sym.category, "severity": sym.severity,
        "severityCat": sym.severity_cat, "severityDog": sym.severity_dog, "redFlag": sym.red_flag,
        "family": sym.family, "patterns": list(sym.patterns), "typoWords": list(sym.typo),
        "d2": {
            "mentions": mentions.get(sym.id, 0),
            "prevalence": {sp: round(present[sp].get(sym.id, 0) / totals[sp], 4) for sp in totals},
        },
    }


def build_knowledge(cleaned: list[dict], kinds: Counter, mentions: Counter, rows_raw: int) -> dict:
    totals, present = prevalence(cleaned)
    breeds: dict[str, list[dict]] = {}
    for sp in totals:
        names = sorted({r["breed"] for r in cleaned if r["species"] == sp})
        breeds[sp] = [{"id": b, "label": BREED_PT.get(b, b),
                       "brachycephalic": b.lower() in BRACHYCEPHALIC_BREEDS} for b in names]
    return {
        "version": KNOWLEDGE_VERSION,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "d2": {
            "rowsRaw": rows_raw, "rowsClean": len(cleaned),
            "duplicateRowsRemoved": kinds["_duplicate_rows"],
            "rowsPerSpecies": dict(totals),
        },
        "symptoms": [symptom_entry(s, totals, present, mentions) for s in SYMPTOMS],
        "breeds": breeds,
        "historyTags": [{"id": tag, "label": label, "d2Value": raw}
                        for raw, (tag, label) in HISTORY_TAGS.items()],
    }


def write_report(path: Path, kinds: Counter, raw_counts: Counter, cleaned: list[dict], rows_raw: int,
                 co: dict, rows: list[dict[str, str]]) -> None:
    hist = history_dependence(cleaned, ("tosse", "febre", "vomito", "diarreia", "apatia"))
    mentions_total = sum(v for k, v in kinds.items() if not k.startswith("_"))
    pct = lambda k: f"{100 * kinds[k] / mentions_total:.1f}%"  # noqa: E731
    repeated = sum(1 for r in rows if len({normalize_en(r[c]) for c in SYMPTOM_COLUMNS}) < 5)
    distinct_raw = len({r[c] for r in rows for c in SYMPTOM_COLUMNS})
    distinct_norm = len({normalize_en(r[c]) for r in rows for c in SYMPTOM_COLUMNS})
    covered = kinds["mapped"] + kinds["fuzzy"]
    lines = [
        "# D2 — relatório de qualidade e cobertura do vocabulário", "",
        f"_Gerado em {datetime.now(timezone.utc):%Y-%m-%d %H:%M UTC} por `ia/triage/build_knowledge.py`._", "",
        "## 1. Auditoria", "",
        f"- Registros brutos: **{rows_raw}** · duplicatas exatas removidas: **{kinds['_duplicate_rows']}** · registros limpos: **{len(cleaned)}**",
        f"- Grafias distintas de sintomas: **{distinct_raw}** (brutas) → **{distinct_norm}** após minúsculas/espaços",
        f"- Linhas com sintoma repetido na própria linha: **{repeated}** ({100 * repeated / rows_raw:.1f}%)",
        "- `AnimalName` contém a espécie (Dog/cat), não o nome do animal.", "",
        "## 2. Cobertura do catálogo canônico (por menção de sintoma)", "",
        "| Resultado | Menções | % |", "|---|---|---|",
        f"| Mapeado exato | {kinds['mapped']} | {pct('mapped')} |",
        f"| Mapeado por similaridade (≥{FUZZY_CUTOFF}) | {kinds['fuzzy']} | {pct('fuzzy')} |",
        f"| Desfecho (death, mortality…) — fora do vocabulário | {kinds['outcome']} | {pct('outcome')} |",
        f"| Não é sintoma (normal appetite…) | {kinds['non_symptom']} | {pct('non_symptom')} |",
        f"| Termo de diagnóstico (pneumonia, enteritis…) | {kinds['diagnosis']} | {pct('diagnosis')} |",
        f"| Aves/rebanho/produção — fora do escopo cães e gatos | {kinds['non_companion']} | {pct('non_companion')} |",
        f"| Não mapeado (cauda longa) | {kinds['unmapped']} | {pct('unmapped')} |", "",
        f"**Cobertura útil do catálogo: {100 * covered / mentions_total:.1f}% das menções** viram um sintoma canônico.", "",
        "## 3. O D2 tem sinal clínico utilizável? (lift e histórico médico)", "",
        f"Pares de sintomas avaliados (≥{MIN_PAIR_COUNT} co-ocorrências): **{co['pairs_evaluated']}** · "
        f"lift mínimo {co['lift_min']} · mediana {co['lift_median']} · máximo {co['lift_max']} · "
        f"pares com lift > 3: {co['pairs_lift_gt_3']}.", "",
        "Lift ≈ 1 = os sintomas aparecem juntos tanto quanto o acaso prevê.", "",
        f"- Os **10 sintomas mais frequentes** ({', '.join(co['top10_symptoms'])}) variam entre lift "
        f"{co['top10_lift_min']} e {co['top10_lift_max']}: aparecem **de forma independente**.",
        "- Pares com lift alto: " + "; ".join(f"{t['a']}+{t['b']} ({t['lift']}×, n={t['count']})" for t in co["top"]) +
        ". Envolvem termos raros que, no D2, aparecem quase só em gatos — compatível com artefato da "
        "expansão sintética, não com padrão clínico confirmado.",
        "- Prevalência de sintomas comuns **entre os valores de `MedicalHistory`** (mín–máx): " +
        "; ".join(f"{s} {lo:.0%}–{hi:.0%}" for s, (lo, hi) in hist.items()) +
        f". A variação máxima é de {max(hi - lo for lo, hi in hist.values()) * 100:.0f} p.p. e sem direção clínica "
        "coerente (ex.: histórico cardíaco não eleva tosse de forma relevante).", "",
        "**Conclusão:** o D2 não permite aprender prioridade clínica nem associação histórico→sintoma. "
        "A prioridade do Zelo vem de **regras de segurança** (catálogo + regras de contexto), "
        "e as regras de histórico/idade são conhecimento clínico da equipe, não estatística do D2.", "",
        "## 4. Uso do D2 no produto", "",
        "- Vocabulário e normalização de sintomas (catálogo canônico + aliases).",
        "- Frequência de referência por espécie (`d2.prevalence`) exibida como contexto, nunca como probabilidade de doença.",
        "- Perfil do pet: raças (18) e rótulos de histórico médico (11).",
        "- Geração de relatos de teste (docs/validacao-e-testes.md) para medir cobertura do extrator.",
        "- **Não** é usado para prever diagnóstico: não há alvo confiável e parte dos dados é sintética.", "",
        "## 5. Maiores grafias não mapeadas (para próxima iteração do catálogo)", "",
        "| Grafia | Menções |", "|---|---|",
    ]
    unmapped = Counter()
    for (raw, kind, _), count in raw_counts.items():
        if kind == "unmapped":
            unmapped[raw] += count
    lines += [f"| {raw} | {count} |" for raw, count in unmapped.most_common(15)]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_mapping_csv(path: Path, raw_counts: Counter) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["grafia_d2", "resultado", "sintoma_canonico", "mencoes"])
        for (raw, kind, canonical), count in sorted(raw_counts.items(), key=lambda kv: -kv[1]):
            writer.writerow([raw, kind, canonical, count])


def write_sample(path: Path, cleaned: list[dict], size: int = 200) -> None:
    step = max(1, len(cleaned) // size)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["species", "breed", "age_years", "weight_kg", "history", "canonical_symptoms"])
        for r in cleaned[::step][:size]:
            writer.writerow([r["species"], r["breed"], r["age"], r["weight"], r["history"],
                             ";".join(r["symptoms"])])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--d2", type=Path, required=True, help="caminho do veterinary_clinical_data.csv")
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    validate_catalog()
    index = build_alias_index()
    rows = load_rows(args.d2)
    cleaned, kinds, raw_counts = clean_rows(rows, index)
    mentions = Counter(s for r in cleaned for s in r["symptoms"])
    co = cooccurrence(cleaned)

    root = args.repo_root
    knowledge_path = root / "backend/src/main/resources/triage/triage_knowledge.json"
    knowledge_path.parent.mkdir(parents=True, exist_ok=True)
    knowledge = build_knowledge(cleaned, kinds, mentions, len(rows))
    knowledge_path.write_text(json.dumps(knowledge, ensure_ascii=False, indent=1), encoding="utf-8")

    reports = root / "docs/relatorios"
    reports.mkdir(parents=True, exist_ok=True)
    write_report(reports / "d2_qualidade.md", kinds, raw_counts, cleaned, len(rows), co, rows)
    write_mapping_csv(reports / "d2_mapeamento_sintomas.csv", raw_counts)
    samples = root / "data/samples"
    samples.mkdir(parents=True, exist_ok=True)
    write_sample(samples / "d2_clean_sample.csv", cleaned)
    LOG.info("conhecimento gerado: %s (%d sintomas)", knowledge_path, len(knowledge["symptoms"]))
    LOG.info("cobertura: %s", {k: v for k, v in kinds.items()})


if __name__ == "__main__":
    main()
