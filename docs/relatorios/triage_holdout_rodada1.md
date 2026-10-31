> **Rodada 1 — resultado bruto, ANTES de qualquer ajuste do catálogo.** Este é o número honesto de generalização:
> o conjunto-ouro de desenvolvimento (86 casos) chegou a 100% justamente porque o catálogo foi ajustado olhando para ele.
> Os 56 relatos abaixo foram escritos de forma independente (`ia/triage/eval/holdout_cases.jsonl`).
# Validação do motor de triagem — holdout independente

| Métrica | Valor |
|---|---|
| Casos | 56 |
| Acurácia de prioridade (exata) | 75,0% |
| **Recall de emergência** (17 casos) | **76,5%** |
| Sub-triagem (previu abaixo do esperado) | 23,2% |
| Sobre-triagem (previu acima do esperado) | 1,8% |
| Sintomas — precisão / recall / F1 | 0,89 / 0,71 / 0,79 |

## Divergências

| Caso | Texto | Esperado | Previsto | Sintomas esperados | Sintomas previstos |
|---|---|---|---|---|---|
| H01 | Meu doguinho amanheceu todo molinho, nem quis o café da manhã | alta | baixa | [apatia, perda_apetite] | [] |
| H03 | Ele ficou todo duro, babando e depois caiu de lado batendo as patas | emergencia | media | [convulsao, salivacao_excessiva] | [salivacao_excessiva] |
| H04 | Tá com a barriga muito grande de repente e quer vomitar mas só sai espuma | emergencia | emergencia | [abdomen_agudo, ansia_sem_vomitar] | [abdomen_agudo, vomito] |
| H06 | Minha cachorra está com um corrimento amarelo e fedido saindo da vulva | alta | baixa | [gestacao_problema] | [] |
| H07 | O pet está com o olho super inchado e fechado desde a manhã | alta | alta | [olho_dor_lesao] | [inchaco_facial] |
| H11 | Minha gata está miando de dor e não consegue fazer xixi | emergencia | emergencia | [mudanca_comportamento, nao_urina] | [dor, nao_urina] |
| H13 | Uma abelha picou o focinho dele e está inchando muito | emergencia | emergencia | [picada_peconhento] | [inchaco_facial, picada_peconhento] |
| H17 | Meu cão tem tossido muito à noite, parece que tá engasgado | media | emergencia | [tosse] | [dificuldade_respirar, tosse] |
| H18 | A pele da barriga dele tá vermelha e cheia de bolinhas | media | baixa | [lesao_pele] | [] |
| H19 | Desde ontem à noite ele não parou de espirrar e agora tem sangue no nariz | alta | alta | [espirros, sangramento_nasal] | [sangramento_nasal] |
| H20 | Ela não quer andar, só fica deitada e geme quando pego | alta | media | [apatia, dor] | [apatia] |
| H26 | Estou preocupada porque ele está bebendo água demais e urinando pela casa toda | media | media | [sede_urina_excessiva, xixi_fora_lugar] | [sede_urina_excessiva] |
| H29 | Está com o hálito muito ruim | media | baixa | [problema_oral] | [] |
| H30 | Ele deu um grito agudo quando encostei na coluna e não mexe as patinhas de trás | emergencia | baixa | [dor, paralisia] | [] |
| H32 | Gato com os olhos amarelados e sem comer | alta | alta | [ictericia, perda_apetite] | [ictericia] |
| H33 | Está com muita coceira nas orelhas e mexe a cabeça o tempo todo | media | baixa | [coceira, ouvido_problema] | [coceira] |
| H37 | Passou mal depois de comer grama e vomitou espuma branca | media | media | [vomito] | [mal_estar_inespecifico, vomito] |
| H38 | A língua dele está azul | emergencia | baixa | [gengivas_palidas] | [] |
| H39 | Não para de se coçar e já tem feridas | media | media | [coceira, lesao_pele] | [lesao_pele] |
| H41 | Está respirando muito rápido dormindo | media | baixa | [respiracao_ofegante] | [] |
| H43 | Meu gato está lambendo muito a mesma região e perdendo pelo ali | baixa | baixa | [coceira, queda_pelo] | [queda_pelo] |
| H45 | Estou com medo, ele está muito quieto, nem levanta a cabeça | alta | media | [apatia] | [apatia] |
| H50 | Ele passou o dia todo vomitando e agora não consegue ficar de pé | emergencia | emergencia | [paralisia, vomito_repetido] | [paralisia, vomito] |
| H57 | O filhotinho de um mês e meio não está mamando | alta | baixa | [perda_apetite] | [] |
| H58 | Ela está há um dia sem urinar | emergencia | baixa | [nao_urina] | [] |
| H59 | Tosse de cachorro velho com cansaço ao subir escada, tem problema cardíaco | alta | alta | [intolerancia_exercicio, tosse] | [tosse] |
