> **Conjunto-ouro (desenvolvimento, 86 casos).** Foi usado para ajustar o catálogo; por isso 100% aqui NÃO mede generalização. Serve como teste de regressão (portão de qualidade do CI).

# Validação do motor de triagem — conjunto-ouro

| Métrica | Valor |
|---|---|
| Casos | 86 |
| Acurácia de prioridade (exata) | 100,0% |
| **Recall de emergência** (27 casos) | **100,0%** |
| Sub-triagem (previu abaixo do esperado) | 0,0% |
| Sobre-triagem (previu acima do esperado) | 0,0% |
| Sintomas — precisão / recall / F1 | 0,97 / 0,99 / 0,98 |

## Divergências

| Caso | Texto | Esperado | Previsto | Sintomas esperados | Sintomas previstos |
|---|---|---|---|---|---|
| A06 | Está com dor na barriga, fica encolhido e geme | alta | alta | [dor_abdominal] | [dor, dor_abdominal] |
| A10 | Tosse seca e muito cansaço, ela tem problema no coração | alta | alta | [tosse] | [intolerancia_exercicio, tosse] |
| N05 | Não sei se foi convulsão ou susto, ficou duro e tremendo | emergencia | emergencia | [convulsao, tremores] | [convulsao] |
| T03 | CONVULSÃOOO!!! SOCORRO | emergencia | emergencia | [convulsao] | [convulsao, urgencia_relatada] |
