# Sprint 4 — Disruptive Architectures: IoT, IoB & Generative IA

Como este repositório atende a cada item pedido na Sprint 4, o que foi **de fato** implementado a partir do desenho da Sprint 3 e o que ficou de fora.

## 1. Requisitos da sprint × onde estão

| O que a sprint pede | Atendido? | Onde ver |
|---|---|---|
| **Implementar a solução de IA definida na Sprint 3** | Sim (chatbot híbrido + visão). IoB: não | seção 2 |
| **Integrar a IA ao app** (HTML + Java) | Sim: front-end estático servido pelo Spring Boot, API REST, banco, autenticação, painel da clínica | [`arquitetura.md`](arquitetura.md), [`api.md`](api.md) |
| **Testar e validar** | Sim: 195 testes Java, 33 Python, ponta a ponta, conjuntos de avaliação com protocolo honesto | [`validacao-e-testes.md`](validacao-e-testes.md) |
| **Evidências** (telas, fluxos, relatórios) | Sim | `docs/evidencias/` (17 telas) e `docs/relatorios/` |
| **Vídeo pitch de ~5 min** | Roteiro pronto; **a gravação e o upload são da equipe** | [`roteiro-video-pitch.md`](roteiro-video-pitch.md) |
| **.zip com link do YouTube (não listado) e do GitHub, com README** | Estrutura e script prontos; **os links são inseridos pela equipe** | [`checklist-entrega.md`](checklist-entrega.md), `scripts/build-zip.ps1` |

## 2. Da Sprint 3 ao código

| Decisão da Sprint 3 | Implementação | Estado |
|---|---|---|
| **IA híbrida:** regras de segurança primeiro, LLM subordinado | `PriorityEngine` (regras R0–R14) define a prioridade; o LLM só soma sintomas ou reescreve texto de prioridade baixa/média e nunca reduz a prioridade (`TriageAnalysis.withFloor`) | **Feito** |
| **Agente conversacional** (relato → perguntas → orientação) | `ConversationFlow`: máquina de estados, no máximo 2 perguntas de acompanhamento (duração, ingestão, respiração), sinal de alerta encerra as perguntas | **Feito** |
| **Entendimento de linguagem natural** | normalização, correção de digitação, 72 sintomas canônicos com padrões em PT-BR, **negação** ("não vomitou", "a tosse passou", "não parou de espirrar"), duração × idade do pet | **Feito** |
| **Contexto do pet** (espécie, idade, histórico) | `PetContext` alimenta regras: filhote, idoso, braquicefálico, histórico cardíaco/renal, gato sem comer, suspeita de torção | **Feito** |
| **Saída estruturada e rastreável** | JSON com `priority`, `next_action`, `confidence`, `human_review_required`, `model_version`… validado no [JSON Schema](../data/schemas/triage-result.schema.json) | **Feito** |
| **Humano no circuito** | painel da clínica: confirma ou ajusta a prioridade; o retorno aparece na linha do tempo do tutor | **Feito** |
| **Consentimento (LGPD)** | "sim" estrito, escopo e versão, revogação que desfaz o compartilhamento | **Feito** |
| **Plano de cuidados (Ciclo Zelo)** | vacinas/vermífugo/antipulgas/check-up com status vencido, em breve, em dia — entra na conversa | **Feito** |
| **IA generativa (LLM)** | SDK oficial `anthropic-java`; modelo padrão `claude-opus-5-5` (troque com `ZELO_LLM_MODEL`); sem chave, o motor local responde sozinho; resposta passa pelo `ResponseGuard` | **Feito** (validado com servidor simulado, não com a API real) |
| **Visão computacional (D3)** | transfer learning (MobileNetV3 congelado + regressão logística), ONNX, microsserviço FastAPI; resultado marcado como experimental; só cães | **Feito** |
| **D2 — preparação de dados** | `build_knowledge.py`: auditoria, limpeza, mapeamento para o catálogo, cobertura 81,2% | **Feito** |
| **IoT / IoB** (sensores, mudança de rotina) | — | **Não feito** (sem dataset de sensores; ver [`ia/iob/README.md`](../ia/iob/README.md)) |
| **D1, D4, D5** | — | Não usados nesta entrega |

## 3. Como o chatbot decide (visão de uma mensagem)

```
mensagem do tutor
  └─ IntentClassifier ── saudação / ajuda / cuidados / pedido de remédio / sim–não de consentimento / relato
        └─ (relato) TriageEngine
              ├─ normaliza + corrige digitação
              ├─ extrai sintomas e negações (família → o mais grave entre os presentes)
              ├─ estima a duração
              ├─ PriorityEngine: severidade do sintoma, sinais de alerta, combinações, contexto do pet
              └─ relato não reconhecido → MÉDIA + revisão humana (nunca "baixa")
        └─ LLM (opcional, só se houver chave): complementa sintomas; texto de baixa/média pode ser reescrito
              └─ ResponseGuard reprova dose, remédio, diagnóstico, minimização → cai no texto seguro
        └─ ReplyComposer: texto + cartão (motivos, confiança, evidência de cada sintoma)
        └─ persiste triagem + linha do tempo; pergunta se pode avisar a clínica (consentimento)
```

Os diagramas completos (componentes, fluxo, estados, modelo de dados) estão em [`arquitetura.md`](arquitetura.md).

## 4. Prioridades e próximos passos (o que o motor entrega)

| Prioridade | Próximo passo sugerido | Exemplos que acionam |
|---|---|---|
| Baixa | Registrar e observar por 24–48 h | espirros isolados, coceira leve |
| Média | Agendar consulta nas próximas 24–72 h | tosse, coceira nas orelhas, vômito e diarreia sem outros sinais, relato que o léxico não reconhece |
| Alta | Contatar a clínica hoje | abatimento com vômitos, gato sem comer há 2 dias |
| Emergência | Buscar atendimento de urgência agora | convulsão, não consegue urinar, gengivas pálidas, vômito com sangue, dificuldade de respirar, barriga inchada com ânsia sem vomitar |

Regras de contexto (filhote, idoso, braquicefálico, histórico cardíaco/renal) **sobem** um nível com teto em alta; só um sinal de alerta leva a emergência. Todas as regras aguardam revisão por médico-veterinário (ver [`governanca-e-limitacoes.md`](governanca-e-limitacoes.md)).

## 5. Evidências

| Arquivo | Mostra |
|---|---|
| `docs/evidencias/01-login.png` | login com perfis de tutor e clínica |
| `02-chat-boas-vindas.png` | saudação personalizada, plano de cuidados e consentimento |
| `03-triagem-media-com-explicacao.png` | duas perguntas de acompanhamento, cartão de prioridade média com motivos e confiança |
| `04-triagem-emergencia.png` | convulsão → emergência na hora, sem perguntas, com pedido de consentimento |
| `05-compartilhado-com-consentimento.png` | "sim" explícito → resumo enviado à clínica; linha do tempo registra |
| `06-recusa-de-medicamento.png` | "posso dar dipirona?" → recusa e redirecionamento |
| `07-analise-de-foto-experimental.png` | foto da pele → classificação experimental, só o hash é guardado |
| `08-triagem-gato-obstrucao-urinaria.png` | regra específica de gato (não consegue urinar) → emergência |
| `09-clinica-fila-por-prioridade.png` | fila da clínica (casos fictícios de vários tutores) da prioridade maior para a menor, ao lado do painel de atendimento: indicadores, prioridades, últimos 7 dias e sintomas |
| `10-clinica-detalhe-e-revisao.png` · `10b-…` | relato, conversa tutor ↔ Zelo que gerou a triagem, triagem da IA, histórico autorizado e revisão profissional registrada |
| `11-celular-chat.png` | versão para celular |
| `12-painel-do-tutor.png` | Painel do Tutor (IBM Carbon) com dados reais: aviso de triagem aguardando revisão, abas por pet, perfil |
| `13-painel-historico-de-atendimento.png` | histórico com prioridade e status (revisada pela clínica, enviada, registrada) e exportação em PDF |
| `14-painel-gerenciar-plano-de-cuidados.png` | plano de cuidados em modo de gestão: adicionar, marcar como feito, remover; resumo de saúde |
| `15-painel-editar-dados-do-pet.png` | edição dos dados do pet (espécie, raça, nascimento, peso, histórico de saúde) |
| `16-clinica-conversa-da-triagem.png` | detalhe na clínica com a conversa tutor ↔ Zelo que gerou a triagem (só até a resposta com a triagem) |

Gerar de novo: `scripts/capture_evidence.py` (ver o cabeçalho do script).
