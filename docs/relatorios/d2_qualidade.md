# D2 — relatório de qualidade e cobertura do vocabulário

_Gerado em 2026-10-31 16:42 UTC por `ia/triage/build_knowledge.py`._

## 1. Auditoria

- Registros brutos: **10000** · duplicatas exatas removidas: **20** · registros limpos: **9980**
- Grafias distintas de sintomas: **935** (brutas) → **867** após minúsculas/espaços
- Linhas com sintoma repetido na própria linha: **2841** (28.4%)
- `AnimalName` contém a espécie (Dog/cat), não o nome do animal.

## 2. Cobertura do catálogo canônico (por menção de sintoma)

| Resultado | Menções | % |
|---|---|---|
| Mapeado exato | 40114 | 80.4% |
| Mapeado por similaridade (≥0.88) | 420 | 0.8% |
| Desfecho (death, mortality…) — fora do vocabulário | 649 | 1.3% |
| Não é sintoma (normal appetite…) | 45 | 0.1% |
| Termo de diagnóstico (pneumonia, enteritis…) | 279 | 0.6% |
| Aves/rebanho/produção — fora do escopo cães e gatos | 1366 | 2.7% |
| Não mapeado (cauda longa) | 7027 | 14.1% |

**Cobertura útil do catálogo: 81.2% das menções** viram um sintoma canônico.

## 3. O D2 tem sinal clínico utilizável? (lift e histórico médico)

Pares de sintomas avaliados (≥40 co-ocorrências): **192** · lift mínimo 0.318 · mediana 0.929 · máximo 40.386 · pares com lift > 3: 10.

Lift ≈ 1 = os sintomas aparecem juntos tanto quanto o acaso prevê.

- Os **10 sintomas mais frequentes** (apatia, diarreia, dor, espirros, febre, fraqueza, perda_apetite, perda_peso, tosse, vomito) variam entre lift 0.78 e 1.24: aparecem **de forma independente**.
- Pares com lift alto: mudanca_comportamento+recusa_agua (40.4×, n=43); dificuldade_urinar+salivacao_excessiva (23.1×, n=66); ataxia+dificuldade_urinar (21.2×, n=79); ataxia+salivacao_excessiva (16.0×, n=122); dor_abdominal+nausea (12.1×, n=43). Envolvem termos raros que, no D2, aparecem quase só em gatos — compatível com artefato da expansão sintética, não com padrão clínico confirmado.
- Prevalência de sintomas comuns **entre os valores de `MedicalHistory`** (mín–máx): tosse 21%–27%; febre 31%–38%; vomito 22%–25%; diarreia 24%–31%; apatia 30%–36%. A variação máxima é de 7 p.p. e sem direção clínica coerente (ex.: histórico cardíaco não eleva tosse de forma relevante).

**Conclusão:** o D2 não permite aprender prioridade clínica nem associação histórico→sintoma. A prioridade do Zelo vem de **regras de segurança** (catálogo + regras de contexto), e as regras de histórico/idade são conhecimento clínico da equipe, não estatística do D2.

## 4. Uso do D2 no produto

- Vocabulário e normalização de sintomas (catálogo canônico + aliases).
- Frequência de referência por espécie (`d2.prevalence`) exibida como contexto, nunca como probabilidade de doença.
- Perfil do pet: raças (18) e rótulos de histórico médico (11).
- Geração de relatos de teste (docs/validacao-e-testes.md) para medir cobertura do extrator.
- **Não** é usado para prever diagnóstico: não há alvo confiável e parte dos dados é sintética.

## 5. Maiores grafias não mapeadas (para próxima iteração do catálogo)

| Grafia | Menções |
|---|---|
| drooping ears | 56 |
| sweating | 28 |
| folded ears | 27 |
| underdeveloped muscles | 27 |
| frequent yawning | 27 |
| extending its neck | 26 |
| speech delay | 26 |
| outstretched neck | 24 |
| flatulence | 23 |
| smell of ammonia | 23 |
| nostrils | 22 |
| distended chest | 22 |
| dry air | 21 |
| firm | 21 |
| green dropping | 20 |
