# Roteiro Oficial de Apresentação — Compliance, Quality Assurance & Tests (Sprint 4)

> **Projeto:** Zelo (Clyvo Vet)  
> **Disciplina:** Compliance, Quality Assurance & Tests (Prof. Felipe Santos)  
> **Tempo estimado de vídeo:** ~3 minutos e 30 segundos  
> **Formato sugerido:** Gravação de tela (1080p ou 720p) com narração por voz (ou dublagem posterior)  
> **Branch oficial de entrega:** `develop`  
> **Repositório:** `https://github.com/marcusvilanova/Challenge_fiap_ZeloPet/tree/develop`  
> **Azure DevOps:** `https://dev.azure.com/RM558771/CLYVO%20VET`  
> **Sistema em produção:** `https://zelopet.vercel.app`

---

## 📋 Checklist Pré-Gravação (Deixe estas abas abertas no navegador)

1. **Aba 1 - Azure Boards:** `https://dev.azure.com/RM558771/CLYVO%20VET/_boards/directory` (ou tela de Test Cases / Queries)
2. **Aba 2 - GitHub:** `https://github.com/marcusvilanova/Challenge_fiap_ZeloPet/tree/develop/qa`
3. **Aba 3 - Aplicação Vercel:** `https://zelopet.vercel.app`
4. **Janela do Selenium IDE:** Aberta com o projeto `qa/selenium/zelo.side` carregado

---

## 🎬 Roteiro Passo a Passo Cronometrado

### CENA 1: Introdução e Plano de Testes Manuais no Azure Boards (Parte A)
⏱️ **Tempo:** `0:00 - 0:45`  
🖥️ **O que mostrar na tela:** Navegador aberto no Azure DevOps (`https://dev.azure.com/RM558771/CLYVO%20VET`).

🗣️ **O que falar:**
> *"Olá professor Felipe Santos e banca avaliadora. Meu nome é Marcus Vinícius, RM 558771, e estou apresentando a entrega da Sprint 4 da disciplina de Compliance, Quality Assurance & Tests do projeto Zelo para a Clyvo Vet.*
>
> *Começando pela Parte A — Testes Manuais de Software: conforme solicitado pelo edital, estruturamos todo o plano de testes no Azure Boards. Cadastramos 20 Casos de Teste formais, identificados do item 57 ao 76, cobrindo 100% dos fluxos centrais: autenticação, triagem de sintomas por IA, diretrizes clínicas e compartilhamento de prontuário.*
>
> *Como podemos ver no Caso de Teste número 57, cada item possui objetivo claro, dados de entrada controlados, passos ordenados de execução e os resultados esperados de saída. Além disso, as permissões do professor já foram configuradas com nível Basic e perfil de Administrador do Projeto."*

---

### CENA 2: Rastreabilidade e Repositório GitHub na Branch Develop
⏱️ **Tempo:** `0:45 - 1:15`  
🖥️ **O que mostrar na tela:** Mudar para a aba do GitHub na pasta `/qa` da branch `develop`.

🗣️ **O que falar:**
> *"Na estrutura do nosso repositório no GitHub, estamos posicionados na branch 'develop', conforme exigido nas normas de entrega.*
>
> *Dentro da pasta 'qa', disponibilizamos o arquivo 'README.md' com a matriz de rastreabilidade completa vinculando os PBIs aos casos de teste, o 'plano-testes-manuais.md' detalhado, e a pasta 'selenium' contendo o arquivo de automação 'zelo.side'.*
>
> *Dessa forma, garantimos total conformidade, versionamento e transparência entre o planejamento do Azure Boards e o código versionado."*

---

### CENA 3: Configuração da Automação de Testes (Parte B - Selenium IDE)
⏱️ **Tempo:** `1:15 - 1:55`  
🖥️ **O que mostrar na tela:** Janela do Selenium IDE com os testes listados à esquerda e comandos à direita.

🗣️ **O que falar:**
> *"Passando para a Parte B — Automação de Testes: utilizamos o padrão Record & Playback com o Selenium IDE para validar a interface web hospedada em produção na Vercel.*
>
> *O edital exigia no mínimo 4 casos automatizados; nós desenvolvemos e automatizamos 6 casos de teste de ponta a ponta:*
> - *TA-01: Login da tutora com validação de sessão e interface IBM Carbon;*
> - *TA-02: Bloqueio e tratamento de credenciais inválidas;*
> - *TA-03: Classificação correta de sinal de alerta clínico gerando emergência imediata;*
> - *TA-04: Triagem de prioridade média com perguntas investigativas;*
> - *TA-05: Guardrail ético recusando prescrição indevida de medicamentos;*
> - *TA-06: Compartilhamento seguro de triagem com a fila de atendimento da clínica.*
>
> *Todos os comandos foram parametrizados com seletores estáveis, comandos 'type', 'click', 'waitFor' e asserções rigorosas de texto."*

---

### CENA 4: Execução da Suíte de Testes ao Vivo
⏱️ **Tempo:** `1:55 - 2:55`  
🖥️ **O que mostrar na tela:** Clicar no botão 'Run all tests' (executar toda a suíte) no Selenium IDE e acompanhar a janela do navegador executando os passos automaticamente.

🗣️ **O que falar:**
> *"Vou disparar agora a execução completa da suíte de testes.*
>
> *Observem o Selenium IDE interagindo diretamente com o ambiente web:*
> - *Ele realiza o login do usuário, valida os tokens de design do sistema IBM Carbon v11;*
> - *Em seguida, submete o relato de tosse e engasgo da pet Mel, validando o acionamento do protocolo de emergência em tempo real;*
> - *No fluxo seguinte, testa a recusa responsável de medicação, garantindo que o assistente respeite as regras de compliance veterinário sem prescrever dosagens;*
> - *E por fim, confirma a emissão do resumo e o envio dos dados para a fila da clínica Clyvo Vet.*
>
> *Como podemos notar, a automação executa sem nenhuma intervenção manual, simulando fielmente a jornada do tutor."*

---

### CENA 5: Resultados, Evidências e Encerramento
⏱️ **Tempo:** `2:55 - 3:30`  
🖥️ **O que mostrar na tela:** Voltar ao Selenium IDE mostrando a barra 100% verde e a lista de 6 testes com status de 'Passed' (sucesso), depois mostrar o README.md com o link final.

🗣️ **O que falar:**
> *"Concluída a execução: temos 6 de 6 casos automatizados executados e 100% aprovados, com zero falhas no console.*
>
> *Com isso, atendemos integralmente a todos os critérios avaliativos da Sprint 4:*
> 1. *Plano detalhado de testes manuais no Azure Boards com dados controlados;*
> 2. *Convite e permissão administrativa ao professor;*
> 3. *Automação completa via Selenium IDE demonstrando configuração, execução e evidências;*
> 4. *Repositório público com branch develop e documentação técnica.*
>
> *Agradeço pela atenção e estamos à disposição para dúvidas na avaliação!"*

---

## 🎙️ Dica de Gravação Rápida no Windows (Sem instalar nada):

1. Pressione o atalho: **`Windows + Alt + R`** (Gravação nativa do Windows).
2. O Windows começará a gravar a tela e o microfone imediatamente.
3. Siga o roteiro acima alternando entre as abas.
4. Quando terminar, pressione **`Windows + Alt + R`** novamente para encerrar.
5. O vídeo em formato `.mp4` estará salvo na pasta: `C:\Users\marcu\Videos\Captures` (ou `Vídeos\Capturas`).
6. Suba o vídeo no YouTube em modo **"Não listado"** e cole o link no README!
