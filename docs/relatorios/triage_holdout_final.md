> **Holdout (56 casos) após os ajustes da rodada 2.** A rodada 1 (antes dos ajustes: 75,0% de acurácia, 76,5% de recall de emergência) está em `triage_holdout_rodada1.md`. Depois do ajuste este conjunto virou desenvolvimento; 100% é otimista.

# Validação do motor de triagem — conjunto-ouro

| Métrica | Valor |
|---|---|
| Casos | 56 |
| Acurácia de prioridade (exata) | 100,0% |
| **Recall de emergência** (17 casos) | **100,0%** |
| Sub-triagem (previu abaixo do esperado) | 0,0% |
| Sobre-triagem (previu acima do esperado) | 0,0% |
| Sintomas — precisão / recall / F1 | 0,98 / 0,98 / 0,98 |

## Divergências

| Caso | Texto | Esperado | Previsto | Sintomas esperados | Sintomas previstos |
|---|---|---|---|---|---|
| H33 | Está com muita coceira nas orelhas e mexe a cabeça o tempo todo | media | media | [coceira, ouvido_problema] | [ouvido_problema] |
| H36 | Ela está sangrando pela vagina e é castrada | alta | alta | [sangramento] | [gestacao_problema] |
| H45 | Estou com medo, ele está muito quieto, nem levanta a cabeça | alta | alta | [apatia] | [apatia, prostracao_grave] |
