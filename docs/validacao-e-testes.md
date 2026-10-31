# Validação e testes

Como foi medido, o que os números significam e o que **não** provam. Tudo abaixo é reproduzível com os comandos da seção 6.

## 1. Pirâmide de testes

| Camada | O que cobre | Qtde | Onde |
|---|---|---|---|
| Unidade — núcleo de triagem | normalização, correção de digitação, extração com negação, duração × idade, regras de prioridade, confiança | 89 | `backend/src/test/java/br/com/zelo/ai` |
| Unidade — conversa | máquina de estados, intenções (consentimento estrito), perguntas de acompanhamento | 41 | `.../chat` |
| Unidade — LLM | formato da requisição do SDK oficial contra servidor simulado, guard de resposta, mascaramento de dados | 19 | `.../llm` |
| Integração — API completa | login, CSRF, chat, triagem, compartilhamento, clínica, revisão, visão (serviço simulado), LLM simulado, limite de requisições, regressões de segurança | 42 | `.../api` |
| Segurança / visão (unidade) | limitador de requisições, assinatura de imagem | 4 | `.../security`, `.../vision` |
| **Java — total** | | **195** | `.\mvnw.cmd test` |
| Python | pipeline do D2 (limpeza, mapeamento, cobertura) e serviço de visão (`/classify`, `/health`, limites) | 33 | `tests/ia` |
| Ponta a ponta | `scripts/smoke_test.py`: tutora → conversa → compartilhar → clínica → revisão; valida a saída no JSON Schema; 401/403/404 | 17 verificações | `scripts/` |

**Cobertura (JaCoCo, `.\mvnw.cmd verify`): 94,7% das linhas (1.787/1.888) e 81,1% dos ramos (791/975).** Pacotes de regra clínica e de segurança ficam entre 95% e 98%.

## 2. Portão de qualidade clínico

`TriageGoldSetTest` roda 86 casos de desenvolvimento a cada build e **falha se o recall de emergência ficar abaixo de 100%**, se a sub-triagem passar do limite ou se acurácia/F1 de sintomas caírem. É um teste de **regressão**: impede que um ajuste conserte um caso e quebre outro. Não mede generalização.

## 3. Generalização do texto (a parte que importa)

Protocolo: o conjunto-ouro (86 casos) foi usado para construir o catálogo. Em seguida, dois conjuntos escritos **de forma independente** foram avaliados **uma única vez, antes de qualquer ajuste** — o resultado dessa primeira rodada é o número honesto.

| Conjunto | Casos | 1ª rodada (antes de ajustar) | Após os ajustes | Leitura |
|---|---|---|---|---|
| Ouro (desenvolvimento) | 86 | — | 100% · recall de emergência 100% | regressão, não generalização |
| Holdout independente | 56 | **acurácia 75,0% · recall de emergência 76,5%** · sub-triagem 23,2% | 100% | depois virou desenvolvimento → otimista |
| Cego | 35 | **acurácia 60,0% · recall de emergência 57,1%** · sub-triagem 37,1% | 100% | melhor estimativa **sem LLM**; também virou desenvolvimento |

O que se aprendeu e virou correção: o léxico inicial era frágil para relatos coloquiais ("amanheceu todo molinho", "respiração pesada"), e **ausência de sintoma reconhecido virava prioridade baixa**. Hoje relato não reconhecido vira **média + revisão humana**, e o LLM (quando ligado) pode complementar o léxico — sempre sem reduzir a prioridade.

Relatórios completos, com cada divergência: `docs/relatorios/triage_*.md`. As fontes dos casos estão em `ia/triage/eval/*.jsonl`.

> **Como citar no vídeo:** "no primeiro teste com relatos que a equipe não tinha visto, o motor local acertou 75% (holdout) e 60% (cego); a taxa de emergências reconhecidas foi 76% e 57%. Corrigimos, e a regra agora é não tratar relato desconhecido como seguro. Os 100% atuais são de regressão, não de generalização."

### 3.1 Revisão de código e de segurança

Uma revisão independente (agentes de revisão de código e de segurança) rodou sobre o projeto e achou problemas reais que os conjuntos acima não pegavam. Os principais, **todos corrigidos e cobertos por teste** (`ReviewRegressionTest`, `SecurityHardeningIntegrationTest`, `RateLimitIntegrationTest`):

| Achado | Gravidade | Correção |
|---|---|---|
| "não quero compartilhar com a clínica" podia ser lido como consentimento | alta | reconhecimento estrito de "sim"/pedido; qualquer negação perto de "compartilhar/clínica" é recusa |
| Limitador de requisições do login nunca executava (estava depois do filtro de login) | alta | movido para antes; também cobre o cadastro e caminhos codificados |
| "a convulsão **não parou**" / "o vômito não passou" eram lidos como sintoma resolvido (prioridade baixa) | crítica | marcadores de resolução respeitam negação, "ainda", recorrência ("parou mas voltou") |
| Família de sintomas resolvida antes da negação: "vomitou, sem sangue" apagava o vômito | alta | negação primeiro; depois o mais grave entre os **presentes** |
| "não está animado/brincando" contava como frase tranquilizadora | alta | frase negada sai antes do teste de tranquilidade → média + revisão humana |
| "está com 3 dias sem comer" era lido como idade do pet | alta | "com" só indica idade quando colado a "filhote/cachorro/gato…" |
| "vomitou sem sangue" acionava emergência (regra de "vômito com sangue") | média | lacuna temperada no padrão: nenhuma negação dentro do trecho casado |
| Reescrita do LLM podia amenizar alta/emergência | alta | alta/emergência **não** passam por reescrita; guard ampliado |
| Revogar e conceder de novo reexibia triagens antigas | média | revogar "desmarca" as triagens compartilhadas |
| Clínica via anotações livres do tutor | média | só tipos de evento permitidos (minimização) |
| Histórico do chat mostrava o cartão como "não compartilhado" depois de recarregar | média | estado atual vem do banco |
| Pet "tab" trocado durante uma resposta misturava conversas; fila da clínica perdia o foco | média | abas travadas durante a resposta; foco preservado |

## 4. Visão computacional (D3)

| Medida | Resultado | Como ler |
|---|---|---|
| Teste original (433 imagens) | acurácia 91,5% · macro-F1 0,89 | **inflado**: há fontes do Roboflow compartilhadas entre treino, validação e teste |
| Teste "limpo" (49 imagens sem fontes vistas) | acurácia 85,7% · macro-F1 0,79 | amostra pequena (algumas classes com 2 imagens) |
| **Validação cruzada agrupada por imagem-fonte** | **acurácia 82,2% ± 1,3 · macro-F1 0,77** | **o número a citar** |
| Modelo | MobileNetV3 congelado + regressão logística, ONNX de 11 MB, 6 classes, só cães | transfer learning, sem ajuste fino |

Relatório de auditoria e treino (vazamento, seleção de backbone, matrizes de confusão): `docs/relatorios/d3_auditoria_e_treino.md`.

## 5. O que os testes **não** provam

- Que as regras estão clinicamente corretas — falta revisão veterinária.
- Que o motor funciona com relatos reais de tutores — os conjuntos foram escritos pela equipe.
- Que o LLM real se comporta como o simulado — não foi rodado contra a API real.
- Que a visão funciona fora de fotos parecidas com as do D3 (iluminação, ângulo, pelo longo).
- Desempenho sob carga e acessibilidade com leitor de tela.

## 6. Como reproduzir

```powershell
# Java: testes + cobertura (relatório em backend\target\site\jacoco\index.html)
cd backend; .\mvnw.cmd verify

# Python: pipeline do D2 e serviço de visão
ia\.venv\Scripts\python -m pytest tests\ia

# Ponta a ponta (backend e visão no ar, seed de demonstração ligado)
ia\.venv\Scripts\python -m pip install jsonschema
ia\.venv\Scripts\python scripts\smoke_test.py

# Reconstruir o conhecimento a partir do D2 (precisa do CSV; veja data\README.md)
ia\.venv\Scripts\python ia\triage\build_knowledge.py --d2 <caminho\veterinary_clinical_data.csv>

# Retreinar o classificador (~10 min em CPU; precisa do D3)
ia\.venv\Scripts\python ia\vision\training\train_vision.py --dataset <pasta Dataset\3> --out ia\vision\service\model --report-dir docs\relatorios
```
