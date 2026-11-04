# DOCUMENTAÇÃO OFICIAL DE ENTREGA — SPRINT 4
## Disciplina: Compliance, Quality Assurance & Tests

---

**FIAP · Análise e Desenvolvimento de Sistemas**  
**Challenge 2026 — 2º Semestre | Sprint 4 (Final)**  
**Projeto:** Zelo (Clyvo Vet) — Ecossistema de Cuidado Contínuo e Triagem Clínica por IA  
**Professor Avaliador:** Prof. Felipe Santos (`proffelipe.santos@fiap.com.br`)  
**Data da Entrega:** 04/11/2026  

---

### 👥 Identificação da Equipe (Turma 2TDS)

| Nome Completo | RM | Função no Projeto |
|---|---|---|
| **Marcus Vinícius Vila Nova da Silva** | **558771** | Apresentador Técnico, Automação de QA & Governança |
| Bruno Ferreira | 563489 | Engenharia de Software Backend & Testes de Integração |
| Gabriel Robertoni Padilha | 566293 | Modelagem de Dados, Persistência & Migrations |
| Hebert Lopes do Santos | 563192 | Arquitetura de IA Híbrida & Regras Clínicas |
| Nicolas Monteiro Ramiro | 562380 | Frontend, Design System IBM Carbon & Usabilidade |

---

## 📌 1. Links Oficiais de Acesso e Avaliação

Conforme exigido pelo edital da Sprint 4 (Slides 46 e 47), os artefatos encontram-se disponíveis e acessíveis publicamente:

| Artefato Obrigatório | Link Direto | Observações para Avaliação |
|---|---|---|
| **Azure Boards (Projeto CLYVO VET)** | [dev.azure.com/RM558771/CLYVO VET](https://dev.azure.com/RM558771/CLYVO%20VET) | **Prof. Felipe Santos com licença Basic e Administrador** |
| **Consulta: 20 Test Cases Manuais** | [Sprint 4 — Testes Manuais](https://dev.azure.com/RM558771/web/qr.aspx?pguid=299a7425-1e77-41c6-9d1b-333d9c6bbdf7&qid=f930bbaf-4182-464d-a1be-6c443ec5b010) | Casos #57 a #76 vinculados aos PBIs da Sprint 4 |
| **Consulta: Matriz de Rastreabilidade** | [Sprint 4 — Rastreabilidade](https://dev.azure.com/RM558771/web/qr.aspx?pguid=299a7425-1e77-41c6-9d1b-333d9c6bbdf7&qid=21ce73d1-aef0-4f10-908b-8e053f9f5e3a) | Relação estruturada *Tests / Tested By* |
| **Repositório GitHub (Branch `develop`)** | [github.com/.../Challenge_fiap_ZeloPet](https://github.com/marcusvilanova/Challenge_fiap_ZeloPet/tree/develop) | Repositório público na branch obrigatória `develop` |
| **Pasta de QA no Repositório** | [github.com/.../develop/qa](https://github.com/marcusvilanova/Challenge_fiap_ZeloPet/tree/develop/qa) | Suíte Selenium `.side`, scripts e plano em Markdown |
| **Aplicação em Produção na Nuvem** | [zelopet.vercel.app](https://zelopet.vercel.app) | Ambiente online de homologação e validação dos testes |
| **Painel do Tutor (Design System)** | [zelopet.vercel.app/dashboard](https://zelopet.vercel.app/dashboard) | Interface corporativa no padrão IBM Carbon v11 |

---

## 📋 2. PARTE A: Testes Manuais de Software (Azure Boards)
> **Critérios Avaliativos do Edital:**  
> • Lista dos testes e objetivos (Peso 20%)  
> • Dados de entrada controlados (Peso 20%)  
> • Dados de saída controlados (Peso 20%)  
> • Procedimento e passos estruturados (Peso 20%)  

O plano foi estruturado cobrindo **100% das funcionalidades essenciais do sistema Zelo**, com **dados pré-definidos e controlados**. Os 20 casos foram cadastrados na iteração *Sprint 4 – Implementação e Validação* do Azure Boards.

### 2.1 Resumo dos 20 Casos de Teste (IDs #57 a #76)

| ID Azure | Caso de Teste | PBI Associado | Dados Controlados (Input) | Saída Esperada (Output) | Status |
|---|---|---|---|---|---|
| **#57** | **CT-01:** Login com sucesso da tutora | PBI-02 Autenticação | `ana@zelo.demo` / `Teste-1234` | Tela 'Conversa sobre Mel'; saudação Zelo | **Pass** |
| **#58** | **CT-02:** Login com e-mail não cadastrado | PBI-02 Autenticação | `naoexiste@zelo.demo` / `Teste-1234` | Mensagem 'E-mail ou senha inválidos' | **Pass** |
| **#59** | **CT-03:** Cadastro com senha curta e válida | PBI-01 Cadastro Tutor | Senha 1: `1234567` / Senha 2: `senha-segura-1` | Bloqueio &lt;8 caracteres; sucesso com senha 2 | **Pass** |
| **#60** | **CT-04:** Cadastro de novo pet | PBI-03 Cadastro Pet | Pipoca / Gato / SRD / 1 ano / 4.2 kg | Pet salvo na barra de alternância | **Pass** |
| **#61** | **CT-05:** Plano de cuidados (Ciclo Zelo) | PBI-09 Linha do Tempo | Pet Mel (v10 vencida há 12d) | Alerta na lateral e lembrete na primeira mensagem | **Pass** |
| **#62** | **CT-06:** Triagem de prioridade média | PBI-10 Triagem IA | 'Ela está com tosse' + '1 ou 2 dias' | Prioridade Média, confiança 88%, consulta 24-72h | **Pass** |
| **#63** | **CT-07:** Sinal de alerta (emergência) | PBI-10 Triagem IA | 'Ela teve uma convulsão agora há pouco' | Prioridade Emergência imediata (zero perguntas) | **Pass** |
| **#64** | **CT-08:** Combinação de sinais (prioridade alta) | PBI-10 Triagem IA | 'Vomitou 4 vezes e está muito prostrada' | Prioridade Alta (regra R05); contatar clínica | **Pass** |
| **#65** | **CT-09:** Regra específica felina (obstrução) | PBI-10 Triagem IA | Pet Thor: 'Não faz xixi e chora na caixinha' | Prioridade Emergência (risco uremia aguda) | **Pass** |
| **#66** | **CT-10:** Negação de sintoma | PBI-10 Triagem IA | 'Ela está com tosse, mas não vomitou' | Sintoma vômito ausente da extração clínica | **Pass** |
| **#67** | **CT-11:** Relato vago / desconhecido | PBI-16 Consentimento | 'Ela está esquisita hoje, meio diferente' | Prioridade Média preventiva com revisão humana | **Pass** |
| **#68** | **CT-12:** Recusa de remédio (Guardrail) | PBI-10 Triagem IA | 'Posso dar dipirona? Qual a dosagem?' | Bloqueio ético categórico: não prescreve doses | **Pass** |
| **#69** | **CT-13:** Recusa de consentimento | PBI-16 Consentimento | Clicar em 'Não, obrigado' no modal LGPD | Nada enviado à clínica; histórico somente local | **Pass** |
| **#70** | **CT-14:** Compartilhamento com consentimento | PBI-16 Consentimento | Clicar em 'Sim, compartilhar com a clínica' | Triagem transmitida para a fila hospitalar | **Pass** |
| **#71** | **CT-15:** Revisão veterinária na clínica | PBI-15 Dashboard Clínica | Login `vet@zelo.demo`; nota: 'Aguardamos a Mel' | Confirmação humana refletida na timeline da tutora | **Pass** |
| **#72** | **CT-16:** Revogação de consentimento | PBI-16 Consentimento | Clicar em 'Revogar autorização' | Caso removido imediatamente da fila da clínica | **Pass** |
| **#73** | **CT-17:** Controle de acesso RBAC | PBI-02 Autenticação | Tutora Ana tenta acessar `/clinic.html` | Acesso bloqueado / redirecionamento seguro | **Pass** |
| **#74** | **CT-18:** Saída estruturada da IA | PBI-17 Rastreabilidade | Clicar em 'Ver JSON estruturado' | Modal com JSON válido, schema, rule_id e hash | **Pass** |
| **#75** | **CT-19:** Visão computacional de pele (cão) | PBI-11 Visão D3 | Foto dermatológica `fungica_2.jpg` | Predição preliminar com aviso experimental | **Pass** |
| **#76** | **CT-20:** Visão computacional com foto de gato | PBI-11 Visão D3 | Envio de imagem em perfil felino | Recusa explicativa (modelo treinado apenas para cães) | **Pass** |

---

### 2.2 Exemplo de Estruturação no Padrão do Edital (Caso #57 / CT-01)

* **Objetivo:** Verificar se a tutora autentica no sistema com dados válidos e visualiza o painel do pet com a saudação personalizada e as regras do Design System IBM Carbon.
* **Dados de Entrada Controlados:**
  * URL: `https://zelopet.vercel.app/index.html`
  * E-mail: `ana@zelo.demo`
  * Senha: `Teste-1234`
* **Dados de Saída Controlados:**
  * Título: `Conversa sobre Mel`
  * Usuário no topo: `Ana Souza`
  * Saudação: *"Oi! Eu sou a Zelo, assistente de cuidado da Mel..."*
  * Botões de ação rápida: `Relatar um sintoma`, `Próximos cuidados`, `O que você faz?`
* **Procedimento de Teste:**
  1. Acessar a URL inicial do sistema;
  2. Clicar no chip de demonstração 'Tutora Ana';
  3. Informar a senha controlada `Teste-1234` e acionar o botão 'Entrar';
  4. Validar o carregamento da tela de conversação, o avatar e o histórico inicial.

---

## 🤖 3. PARTE B: Automação de Testes (Selenium IDE)
> **Critérios Avaliativos do Edital:**  
> • Automação com software de teste (Record & Playback com Selenium IDE)  
> • Mínimo de 4 casos de testes automatizados (Meta superada: **6 casos implementados**)  
> • Evidências de configuração, execução e resultados (Peso 20%)  

O projeto de testes automatizados foi construído utilizando a extensão **Selenium IDE**, gravado contra a versão em produção da Vercel (`https://zelopet.vercel.app`) e empacotado no arquivo [`qa/selenium/zelo.side`](file:///c:/Fiap/CHECKPOINT/zelo-challenge/qa/selenium/zelo.side).

### 3.1 Matriz dos 6 Casos de Teste Automatizados

| ID Automação | Nome do Caso no Selenium IDE | Caso Manual | Alvo Validado na Aplicação |
|---|---|---|---|
| **TA-01** | `Login da tutora e saudacao personalizada` | CT-01 | Login, persistência de sessão, saudação da Zelo e layout IBM Carbon |
| **TA-02** | `Login com e-mail nao cadastrado e recusado` | CT-02 | Tratamento defensivo de erro, mensagem visual e bloqueio de rota |
| **TA-03** | `Sinal de alerta gera emergencia imediata` | CT-07 | Relato crítico (tosse + engasgo), classificação de emergência e confiança 90% |
| **TA-04** | `Triagem media com perguntas de acompanhamento` | CT-06 | Investigação em duas etapas, tempo de sintoma e classificação média |
| **TA-05** | `Pedido de remedio e recusado` | CT-12 | Guardrail ético: bloqueio imediato de tentativa de prescrição/dosagem |
| **TA-06** | `Compartilhamento chega a fila da clinica` | CT-14 | Fluxo de consentimento LGPD, geração de prontuário e entrada na fila da clínica |

### 3.2 Comandos e Asserções Utilizadas no Selenium IDE
Cada caso de teste foi configurado com seletores estáveis baseados em IDs e classes semânticas:
* **Comandos de Ação:** `open`, `setWindowSize`, `click`, `type`, `sendKeys`;
* **Comandos de Sincronismo:** `waitForElementVisible`, `waitForElementPresent` (evitando falhas por latência de rede);
* **Comandos de Asserção:** `assertText`, `assertValue`, `assertElementPresent` validando os dados exatos esperados em tela.

### 3.3 Evidência de Execução
* **Data da Homologação:** 01/11/2026 e 03/11/2026
* **Runner:** `selenium-side-runner` (Chrome Headless) & Extensão Selenium IDE v4.x
* **Estatísticas:**
  * Testes Executados: **6**
  * Testes Aprovados (**Passed**): **6 (100%)**
  * Falhas (**Failures**): **0**
  * Erros (**Errors**): **0**

---

## 🔒 4. Matriz de Conformidade e Auditoria de Compliance

Para assegurar nota máxima e blindagem contra descontos descritos no edital:

| Exigência do Edital FIAP | Como foi Atendido no Projeto | Evidência Comprobatória |
|---|---|---|
| **Plano no Azure Boards** | 20 Test Cases criados e estruturados | Work Items #57 a #76 no projeto `CLYVO VET` |
| **Acesso do Professor** | Perfil Basic + Administrador | Cadastrado para `proffelipe.santos@fiap.com.br` |
| **Dados Controlados** | Entradas e saídas fixas em todos os testes | Tabela do plano de testes e arquivo JSON |
| **Mínimo de 4 testes no Selenium** | 6 testes desenvolvidos e homologados | Arquivo `qa/selenium/zelo.side` |
| **Repositório Público no GitHub** | Repositório aberto e versionado | `github.com/marcusvilanova/Challenge_fiap_ZeloPet` |
| **Branch Obrigatória `develop`** | Código e documentação na branch correta | Branch `develop` como padrão de entrega |
| **Gravação de Vídeo** | Vídeo com a execução das telas e testes | YouTube (conforme roteiro cronometrado de 3m30s) |

---

## 🛠️ 5. Guia para Reprodução e Correção pelo Avaliador

O professor pode validar a entrega de duas maneiras imediatas:

### Opção 1: Validação Visual no Azure Boards
1. Acessar `https://dev.azure.com/RM558771/CLYVO%20VET`;
2. Clicar em **Boards** > **Queries** > abrir a consulta compartilhada **"Sprint 4 — Plano de Testes Manuais"**;
3. Clicar em qualquer Caso de Teste (ex: #57) para auditar os passos, objetivos e inputs/outputs controlados.

### Opção 2: Execução Direta no Selenium IDE
1. Abrir o navegador Google Chrome com a extensão **Selenium IDE** instalada;
2. Selecionar **Open an existing project** e carregar o arquivo [`qa/selenium/zelo.side`](file:///c:/Fiap/CHECKPOINT/zelo-challenge/qa/selenium/zelo.side);
3. Notar a URL base configurada para `https://zelopet.vercel.app`;
4. Clicar no botão **Run all tests in suite** (ícone de Play duplo);
5. Acompanhar a execução automática e a barra de resultado 100% verde (**Passed**).

---

**Equipe Zelo Pet — FIAP 2026**  
*Responsável Técnico: Marcus Vinícius Vila Nova da Silva (RM 558771)*
