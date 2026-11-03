# 🎙️ GUIA PRÁTICO DE GRAVAÇÃO E FALA — COMPLIANCE & QA (SPRINT 4)

Este documento foi criado para você deixar aberto ao lado da sua tela durante a gravação do vídeo para a disciplina **Compliance, Quality Assurance & Tests (Prof. Felipe Santos)**.

---

## 🧭 PASSO 0: ONDE ACESSAR E DEIXAR PRONTO ANTES DE GRAVAR

Abra as seguintes abas no seu navegador (Chrome ou Edge):

| Aba | O que abrir | Link direto |
|---|---|---|
| **Aba 1** | **Azure DevOps** (Boards / Casos de Teste) | [dev.azure.com/RM558771/CLYVO VET](https://dev.azure.com/RM558771/CLYVO%20VET) |
| **Aba 2** | **GitHub** (Pasta QA na branch `develop`) | [github.com/.../tree/develop/qa](https://github.com/marcusvilanova/Challenge_fiap_ZeloPet/tree/develop/qa) |
| **Aba 3** | **Sistema Zelo** (Produção Vercel) | [zelopet.vercel.app](https://zelopet.vercel.app) |
| **Janela Extra** | **Extensão Selenium IDE** | Com o arquivo `qa/selenium/zelo.side` aberto |

> 💡 **Como começar a gravar no Windows:**  
> Aperte o atalho **`Windows + Alt + R`** no teclado.  
> Uma pequena barra com cronômetro aparecerá no canto da tela indicando que o vídeo e o microfone estão gravando.

---

## 🎬 ROTEIRO DE TELA E FALA (PASSO A PASSO)

---

### ⏱️ MOMENTO 1 (0:00 até 0:45) — Azure Boards (Testes Manuais)

#### 🖥️ Onde você deve estar na tela:
* Mostre a **Aba 1** do Azure DevOps (`CLYVO VET`).
* Clique em **Boards** > **Work Items** (ou na Query de Casos de Teste).
* Dê um clique duplo para abrir o Caso de Teste **#57 (CT-01 - Login com sucesso e perfil do tutor)**.
* Mostre a aba **Steps** com os passos 1, 2, 3 e os resultados esperados.
* Mostre no topo direito ou nas configurações que o professor `proffelipe.santos@fiap.com.br` é membro da organização e Administrador do projeto.

#### 🗣️ O que você deve falar (leia com calma e clareza):
> *"Olá professor Felipe Santos e banca avaliadora.  
> Meu nome é Marcus Vinícius, RM 558771, e estou apresentando a entrega da Sprint 4 da matéria de Compliance, Quality Assurance and Tests do projeto Zelo, desenvolvido para a Clyvo Vet.  
>  
> Começando pela Parte A — Testes Manuais de Software: conforme as diretrizes do edital, elaboramos o plano completo de testes dentro do Azure Boards.  
> Cadastramos 20 Casos de Teste formais, identificados do número 57 ao 76, cobrindo todos os fluxos críticos da nossa solução: autenticação, triagem clínica por inteligência artificial, regras éticas e compartilhamento de dados com a clínica.  
>  
> Como podemos ver aqui no Caso de Teste 57, cada item possui objetivo bem definido, dados de entrada controlados, passos estruturados e as saídas esperadas.  
> As permissões do professor também já foram concedidas com licença Basic e acesso de Administrador do Projeto."*

---

### ⏱️ MOMENTO 2 (0:45 até 1:15) — Repositório GitHub (Branch `develop`)

#### 🖥️ Onde você deve estar na tela:
* Mude para a **Aba 2** do GitHub.
* Aponte com o mouse para a branch: mostre que está selecionada a branch **`develop`**.
* Mostre os arquivos da pasta **`/qa`**: `README.md`, `plano-testes-manuais.md` e a pasta `selenium/`.

#### 🗣️ O que você deve falar:
> *"Seguindo para a estrutura de entrega do repositório no GitHub:  
> Estamos na branch 'develop', conforme a exigência do enunciado.  
>  
> Dentro do diretório 'qa', organizamos toda a documentação de suporte:  
> Temos o 'README.md' com a matriz de rastreabilidade completa ligando as histórias de usuário e PBIs aos testes, o arquivo detalhado 'plano-testes-manuais.md' e o pacote de automação na pasta 'selenium'.  
>  
> Tudo está versionado, público e com total rastreabilidade entre o Azure Boards e o código-fonte."*

---

### ⏱️ MOMENTO 3 (1:15 até 1:55) — Configuração do Selenium IDE (Parte B)

#### 🖥️ Onde você deve estar na tela:
* Mude para a janela da extensão **Selenium IDE**.
* Mostre a URL base configurada: `https://zelopet.vercel.app`.
* Aponte na barra lateral esquerda os 6 casos de testes automatizados:
  1. `TA-01`
  2. `TA-02`
  3. `TA-03`
  4. `TA-04`
  5. `TA-05`
  6. `TA-06`
* Clique no teste `TA-03` para mostrar a tabela de comandos (`open`, `type`, `click`, `assertText`).

#### 🗣️ O que você deve falar:
> *"Passando agora para a Parte B — Automação de Teste:  
> Utilizamos o Selenium IDE na modalidade Record and Playback para validar a aplicação web em produção na Vercel.  
>  
> O edital solicitava um mínimo de 4 casos automatizados. Nossa equipe desenvolveu 6 casos de teste automatizados cobrindo os cenários mais importantes:  
> - TA-01: Autenticação da tutora e validação visual do Design System IBM Carbon;  
> - TA-02: Bloqueio correto de credenciais inválidas;  
> - TA-03: Triagem de emergência imediata ao relatar tosse e engasgo da pet Mel;  
> - TA-04: Triagem de prioridade média com perguntas complementares;  
> - TA-05: Guardrail ético recusando prescrição ou dosagem de medicamentos;  
> - E TA-06: Compartilhamento seguro da triagem com a fila de recepção da clínica.  
>  
> Todos os testes utilizam seletores consistentes e asserções automáticas de tela."*

---

### ⏱️ MOMENTO 4 (1:55 até 2:50) — Execução ao Vivo da Automação

#### 🖥️ Onde você deve estar na tela:
* No topo do Selenium IDE, clique no botão **Run all tests in suite** (ícone de play duplo ou play).
* Deixe a janela do navegador aberta ao lado para que a gravação capture o robô preenchendo as telas sozinho:
  * A tela de login IBM Carbon abrindo e logando;
  * O chat da pet Mel enviando a mensagem de sintoma;
  * Os cards de prioridade sendo avaliados;
  * O teste de medicação sendo recusado.

#### 🗣️ O que você deve falar:
> *"Vou iniciar agora a execução ao vivo de toda a suíte de testes.  
>  
> Acompanhem na janela do navegador: o Selenium executa cada comando de forma 100% automatizada.  
>  
> Ele realiza o login no sistema com a identidade visual IBM Carbon v11, valida as mensagens de boas-vindas da pet Mel, submete os relatos de saúde no chat, valida a classificação de risco em tempo real e verifica os bloqueios de segurança farmacológica.  
>  
> Por fim, valida a integração com o painel da clínica veterinária sem qualquer intervenção manual."*

---

### ⏱️ MOMENTO 5 (2:50 até 3:20) — Resultados e Encerramento

#### 🖥️ Onde você deve estar na tela:
* Volte o foco para a janela do Selenium IDE.
* Mostre a lista com todos os 6 testes marcados em **verde** com status de **OK / Passed**.
* Mostre a barra inferior de log sem erros (0 failures).

#### 🗣️ O que você deve falar:
> *"Como podemos verificar no log de resultados do Selenium IDE:  
> Os 6 testes automatizados foram executados com sucesso e obtiveram status aprovado, sem nenhuma falha.  
>  
> Com isso, comprovamos o atendimento de 100% dos requisitos de Compliance e Quality Assurance da Sprint 4:  
> Os 20 testes manuais no Azure Boards com dados controlados, a permissão administrativa do professor, a automação completa no Selenium IDE com evidência gravada e o repositório público na branch develop.  
>  
> Muito obrigado pela atenção!"*

---

## ⏹️ PASSO FINAL: FINALIZAR E ENVIAR

1. Aperte **`Windows + Alt + R`** para parar a gravação.
2. Seu arquivo de vídeo estará na pasta:  
   📂 `C:\Users\marcu\Videos\Captures` (ou `Vídeos\Capturas`).
3. Acesse o **YouTube Studio** ([studio.youtube.com](https://studio.youtube.com)) e faça o upload do vídeo.
4. Defina a visibilidade como: **"Não listado"** (Unlisted).
5. Copie o link do vídeo do YouTube e envie aqui no chat para atualizarmos o `README.md` do repositório!
