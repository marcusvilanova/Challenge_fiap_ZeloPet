# Compliance, Quality Assurance & Tests — Sprint 4

| Entrega | Link |
|---|---|
| **Azure Boards** (projeto CLYVO VET — backlog, tarefas da Sprint 4 e casos de teste) | https://dev.azure.com/RM558771/CLYVO%20VET |
| Consulta: plano de testes manuais | [Sprint 4 — Plano de Testes Manuais](https://dev.azure.com/RM558771/web/qr.aspx?pguid=299a7425-1e77-41c6-9d1b-333d9c6bbdf7&qid=f930bbaf-4182-464d-a1be-6c443ec5b010) |
| Consulta: rastreabilidade PBI × casos de teste | [Sprint 4 — Rastreabilidade](https://dev.azure.com/RM558771/web/qr.aspx?pguid=299a7425-1e77-41c6-9d1b-333d9c6bbdf7&qid=21ce73d1-aef0-4f10-908b-8e053f9f5e3a) |
| Consulta: tarefas da Sprint 4 | [Sprint 4 — Tarefas](https://dev.azure.com/RM558771/web/qr.aspx?pguid=299a7425-1e77-41c6-9d1b-333d9c6bbdf7&qid=7c3d453c-74fe-46bd-9eba-8ec43ef24cb2) |
| **Vídeo da automação de testes** (YouTube) | [Assistir no YouTube (https://www.youtube.com/watch?v=zJiiOhgtjy4)](https://www.youtube.com/watch?v=zJiiOhgtjy4) |
| **Simulador de Gravação & Telas Legendadas** | [`video-guia-apresentacao.html`](video-guia-apresentacao.html) · online em `/video` |
| **Arquivo de Legendas Oficiais para Dublagem** | [`legendas-video.srt`](legendas-video.srt) |
| Sistema testado (versão online) | https://zelopet.vercel.app |
| **Professor Avaliador** | Prof. Felipe Santos (`proffelipe.santos@fiap.com.br`) |
| **Acesso Configurado** | Licença **Basic** na Organização · **Project Administrator** no projeto CLYVO VET |

> Para corrigir, o professor precisa ser **membro da organização com licença Basic** e **administrador do projeto** CLYVO VET (Organization settings → Users → Add users; depois Project settings → Permissions → Project Administrators).

Integrantes: Bruno Ferreira (RM 563489) · Gabriel Robertoni Padilha (RM 566293) · Hebert Lopes do Santos (RM 563192) · Marcus Vinícius Vila Nova da Silva (RM 558771) · Nicolas Monteiro Ramiro (RM 562380)

## Parte A — Teste manual de software

- **20 casos de teste de sistema** cobrindo as funcionalidades principais: conta e login, cadastro de pet, plano de cuidados, triagem (baixa, média, alta, emergência, negação, relato não reconhecido, recusa de remédio), consentimento e compartilhamento, painel e revisão da clínica, revogação, controle de acesso, saída estruturada da IA e análise de foto.
- Cada caso traz **objetivo, dados de entrada, saída esperada e passos** com resultado esperado por passo, com **dados controlados** (contas, pets, textos e datas fixos).
- No Azure Boards: *Test Cases* **#57 a #76**, na iteração *Sprint 4 – Implementação e Validação*, cada um ligado ao PBI que valida (relação *Tests / Tested By*). As tarefas da Sprint 4 (T-11 a T-24, #43 a #56) registram o que foi implementado e testado.
- Documento completo: [`plano-testes-manuais.md`](plano-testes-manuais.md) (gerado de [`testes-manuais.json`](testes-manuais.json)).
- Os casos CT-01 a CT-18 foram executados contra o site real e **todos passaram**; CT-19 e CT-20 exigem o sistema completo local (serviço de visão).

> O Azure Test Plans da organização está na licença **Basic**, que não permite criar *Test Plan*/*Test Suite* (exige "Basic + Test Plans"). Por isso o plano está no **Azure Boards**, como pede o enunciado: *Test Cases* com passos, ligados aos PBIs, e consultas compartilhadas para navegar.

## Parte B — Automação de teste (Selenium IDE)

Projeto: [`selenium/zelo.side`](selenium/zelo.side) — **6 casos automatizados** contra https://zelopet.vercel.app:

| Caso automatizado | Caso manual | O que valida |
|---|---|---|
| TA-01 Login da tutora e saudação personalizada | CT-01 | login, título da conversa, nome no topo, saudação |
| TA-02 Login com e-mail não cadastrado é recusado | CT-02 | mensagem de erro e permanência na tela de entrada |
| TA-03 Sinal de alerta gera emergência imediata | CT-07 | prioridade emergência, ação, motivo e confiança 90% |
| TA-04 Triagem média com perguntas de acompanhamento | CT-06 | duas perguntas, prioridade média, duração ≈ 2 dias |
| TA-05 Pedido de remédio é recusado | CT-12 | recusa sem citar dose |
| TA-06 Compartilhamento chega à fila da clínica | CT-14 | consentimento, cartão compartilhado e fila da clínica |

**Resultado da execução (01/11/2026):** 6 de 6 aprovados (`selenium-side-runner`, Chrome headless).

### Como abrir e rodar no Selenium IDE (para o vídeo)

1. Instale a extensão **Selenium IDE** no Chrome ou Firefox (Chrome Web Store / Firefox Add-ons).
2. Abra o Selenium IDE → **Open an existing project** → escolha `qa/selenium/zelo.side`.
3. Mostre a configuração: URL base `https://zelopet.vercel.app`, a suíte *Zelo — fluxo do tutor e da clínica* e os comandos de cada caso (`open`, `type`, `click`, `sendKeys`, `waitFor…`, `assert…`).
4. Clique em **Run all tests in suite** e mostre o log com os 6 casos aprovados.
5. Mostre um caso falhando de propósito (ex.: troque "Prioridade emergência" por "Prioridade baixa" no TA-03), rode e desfaça — evidencia que os testes realmente verificam o resultado.

### Como rodar pela linha de comando

```bash
npm install -g selenium-side-runner
selenium-side-runner -c "browserName=chrome" qa/selenium/zelo.side
```

O `.side` é gerado por [`selenium/build_side.py`](selenium/build_side.py) (fonte legível dos casos): `python qa/selenium/build_side.py`.

### Roteiro curto do vídeo (≈ 4 min)

1. Abrir o Azure Boards e mostrar os Test Cases da Sprint 4 e a consulta de rastreabilidade (30 s).
2. Abrir o Selenium IDE com o `zelo.side` e explicar a configuração e um caso por dentro (1 min).
3. Executar a suíte inteira e mostrar o resultado 6/6 (1 min).
4. Forçar uma falha e mostrar o relatório do erro (1 min).
5. Encerrar mostrando o repositório na branch `develop` (30 s).

## Outros testes do projeto (além desta disciplina)

195 testes automatizados em Java (unidade + integração, cobertura de 94,7% das linhas), 33 em Python e 8 de paridade do motor JS — ver [`../docs/validacao-e-testes.md`](../docs/validacao-e-testes.md).
