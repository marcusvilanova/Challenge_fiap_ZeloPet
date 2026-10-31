> **Conjunto cego — rodada 1: resultado bruto, ANTES de qualquer ajuste.** 35 relatos escritos depois do ajuste do catálogo na rodada 2
> (`ia/triage/eval/blind_cases.jsonl`) e avaliados uma única vez. É a melhor estimativa de generalização do motor **sem LLM**.
> Depois desta rodada o conjunto passou a ser usado no desenvolvimento (ver `docs/validacao-e-testes.md`).
# Validação do motor de triagem — conjunto cego

| Métrica | Valor |
|---|---|
| Casos | 35 |
| Acurácia de prioridade (exata) | 60,0% |
| **Recall de emergência** (7 casos) | **57,1%** |
| Sub-triagem (previu abaixo do esperado) | 37,1% |
| Sobre-triagem (previu acima do esperado) | 2,9% |
| Sintomas — precisão / recall / F1 | 0,86 / 0,60 / 0,70 |

## Divergências

| Caso | Texto | Esperado | Previsto | Sintomas esperados | Sintomas previstos |
|---|---|---|---|---|---|
| B01 | Acordei e a Luna estava toda tremendo e sem querer sair da caminha | alta | media | [apatia, tremores] | [tremores] |
| B04 | Peguei meu gato dormindo e percebi que a respiração dele está muito pesada | emergencia | baixa | [dificuldade_respirar] | [] |
| B05 | Tem uns dois dias que ele está sem vontade de comer e bebe pouca água | alta | baixa | [perda_apetite] | [] |
| B06 | Minha poodle fez coco com um pouco de sangue vivo hoje | alta | alta | [diarreia_sangue] | [sangramento] |
| B07 | Ele está se coçando tanto que já arrancou o pelo | baixa | baixa | [coceira, queda_pelo] | [coceira] |
| B08 | Minha gata está muito magra e bebe água o dia inteiro | alta | alta | [perda_peso, sede_urina_excessiva] | [perda_peso] |
| B09 | O cachorro está com o olho esquerdo azulado e lacrimejando | alta | media | [olho_dor_lesao] | [olho_vermelho_secrecao] |
| B10 | Quando ele corre fica sem fôlego e tosse | media | baixa | [intolerancia_exercicio, tosse] | [] |
| B11 | Ela não está fazendo xixi desde ontem | emergencia | baixa | [nao_urina] | [] |
| B12 | Meu pastor alemão está com a barriga estufada e não para quieto, anda de um lado para o outro | emergencia | alta | [abdomen_distendido, mudanca_comportamento] | [abdomen_distendido] |
| B15 | Ele caiu da cama e agora chora quando encosto na pata | media | baixa | [dor] | [] |
| B16 | A gata está sem vontade de brincar, quietinha, e comeu pouco hoje | alta | media | [apatia, perda_apetite] | [apatia] |
| B19 | Ele está respirando de um jeito estranho, fazendo muito barulho | alta | media | [sibilo] | [mal_estar_inespecifico] |
| B25 | O gatinho está espirrando e com secreção amarela no olho e no nariz | media | alta | [espirros, olho_vermelho_secrecao, secrecao_nasal] | [espirros, ictericia] |
| B28 | Meu cachorro está com a pele toda descascando | media | baixa | [lesao_pele] | [] |
| B29 | Meu gato está com muita baba escorrendo e a boca cheia de feridinhas | media | media | [problema_oral, salivacao_excessiva] | [salivacao_excessiva] |
| B32 | Deitou na calçada no passeio e não quis mais andar, tremendo e ofegante | alta | media | [respiracao_ofegante, tremores] | [respiracao_ofegante, tremores] |
| B37 | Ele ficou todo mole e não quer levantar | alta | baixa | [apatia] | [] |
| B39 | Minha cachorra idosa está urinando sangue | alta | alta | [sangue_urina] | [sangramento] |
| B40 | Gato com mau cheiro no ouvido e inclinando a cabeça | alta | alta | [andar_circulos, ouvido_problema] | [andar_circulos] |
