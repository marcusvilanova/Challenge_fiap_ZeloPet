> **Conjunto cego (35 casos) após os ajustes da rodada 3.** A rodada 1 (antes dos ajustes: 60,0% de acurácia, 57,1% de recall de emergência) está em `triage_cego_rodada1.md`. Depois do ajuste também virou desenvolvimento.

# Validação do motor de triagem — conjunto-ouro

| Métrica | Valor |
|---|---|
| Casos | 35 |
| Acurácia de prioridade (exata) | 100,0% |
| **Recall de emergência** (7 casos) | **100,0%** |
| Sub-triagem (previu abaixo do esperado) | 0,0% |
| Sobre-triagem (previu acima do esperado) | 0,0% |
| Sintomas — precisão / recall / F1 | 0,93 / 0,98 / 0,95 |

## Divergências

| Caso | Texto | Esperado | Previsto | Sintomas esperados | Sintomas previstos |
|---|---|---|---|---|---|
| B05 | Tem uns dois dias que ele está sem vontade de comer e bebe pouca água | alta | alta | [perda_apetite] | [perda_apetite, recusa_agua] |
| B25 | O gatinho está espirrando e com secreção amarela no olho e no nariz | media | media | [espirros, olho_vermelho_secrecao, secrecao_nasal] | [espirros, olho_vermelho_secrecao] |
| B32 | Deitou na calçada no passeio e não quis mais andar, tremendo e ofegante | alta | alta | [respiracao_ofegante, tremores] | [apatia, respiracao_ofegante, tremores] |
| B37 | Ele ficou todo mole e não quer levantar | alta | alta | [apatia] | [apatia, prostracao_grave] |
| B38 | Ela está há 3 dias só dormindo, não come e a gengiva está pálida | emergencia | emergencia | [gengivas_palidas, perda_apetite] | [apatia, gengivas_palidas, perda_apetite] |
