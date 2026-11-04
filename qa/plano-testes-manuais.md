# Plano de testes manuais — Sprint 4

Testes de **sistema** das funcionalidades principais do Zelo, com dados controlados (entradas fixas e saída esperada predefinida). Os mesmos 20 casos estão no Azure Boards como *Test Case* (IDs #57 a #76), cada um ligado ao PBI que valida.

- **Ambiente:** Versão online: https://zelopet.vercel.app (dados de demonstração no navegador; contas de exemplo entram com qualquer senha). Os casos CT-19 e CT-20 usam o sistema completo local (backend Java + serviço de visão): .\scripts\run-local.ps1 e http://localhost:8080.
- **Preparação:** Antes de cada execução, abrir o site numa janela anônima (dados limpos). Datas relativas (vacinas) são calculadas a partir do dia da execução.
- **Verificação:** os casos CT-01 a CT-18 foram executados contra https://zelopet.vercel.app em 01/11/2026 e todos passaram. CT-19 e CT-20 foram verificados no sistema local.

## Resumo

| Caso | Azure | PBI | Prioridade | Automatizado |
|---|---|---|---|---|
| CT-01 — Login com conta de demonstração da tutora | #57 | PBI-02 Autenticação e preferências | 1 | TA-01 |
| CT-02 — Login com e-mail não cadastrado é recusado | #58 | PBI-02 Autenticação e preferências | 2 | TA-02 |
| CT-03 — Cadastro de tutor: senha curta recusada e cadastro válido aceito | #59 | PBI-01 Cadastro do tutor | 2 | — |
| CT-04 — Cadastro de pet | #60 | PBI-03 Cadastro do pet | 2 | — |
| CT-05 — Plano de cuidados (Ciclo Zelo) na conversa e no painel lateral | #61 | PBI-09 Registro da linha do tempo | 2 | — |
| CT-06 — Triagem de prioridade média com perguntas de acompanhamento | #62 | PBI-10 Triagem textual estruturada | 1 | TA-04 |
| CT-07 — Sinal de alerta gera emergência imediata | #63 | PBI-10 Triagem textual estruturada | 1 | TA-03 |
| CT-08 — Combinação de sinais eleva a prioridade para alta | #64 | PBI-10 Triagem textual estruturada | 1 | — |
| CT-09 — Regra específica de gato: não consegue urinar | #65 | PBI-10 Triagem textual estruturada | 1 | — |
| CT-10 — Negação: sintoma negado não entra na triagem | #66 | PBI-10 Triagem textual estruturada | 2 | — |
| CT-11 — Relato não reconhecido vira média com revisão humana e pode ser enviado | #67 | PBI-16 Consentimento e compartilhamento | 2 | — |
| CT-12 — Pedido de remédio é recusado | #68 | PBI-10 Triagem textual estruturada | 1 | TA-05 |
| CT-13 — Recusa do compartilhamento: nada vai para a clínica | #69 | PBI-16 Consentimento e compartilhamento | 1 | — |
| CT-14 — Compartilhamento com consentimento aparece na fila da clínica | #70 | PBI-16 Consentimento e compartilhamento | 1 | TA-06 |
| CT-15 — Revisão profissional pela clínica e retorno ao tutor | #71 | PBI-15 Dashboard da clínica | 1 | — |
| CT-16 — Revogar o consentimento oculta as triagens da clínica | #72 | PBI-16 Consentimento e compartilhamento | 1 | — |
| CT-17 — Controle de acesso: tutor não abre o painel da clínica | #73 | PBI-02 Autenticação e preferências | 2 | — |
| CT-18 — Saída estruturada da IA e rastreabilidade | #74 | PBI-17 Métricas e rastreabilidade | 2 | — |
| CT-19 — Análise de foto da pele (cão) — resultado experimental | #75 | PBI-11 Visão computacional D3 | 2 | — |
| CT-20 — Foto de gato é recusada com explicação | #76 | PBI-11 Visão computacional D3 | 3 | — |

## CT-01 — Login com conta de demonstração da tutora

- **Azure Boards:** Test Case #57 · testa PBI-02 Autenticação e preferências
- **Objetivo:** Verificar que a tutora entra no sistema e chega à conversa do primeiro pet com a saudação personalizada.
- **Dados de entrada:** E-mail: ana@zelo.demo · Senha: Teste-1234
- **Saída esperada:** Tela 'Conversa sobre Mel'; nome 'Ana Souza' no topo; mensagem 'Oi! Eu sou a Zelo, assistente de cuidado da Mel...'; atalhos 'Relatar um sintoma', 'Próximos cuidados', 'O que você faz?'.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Abrir https://zelopet.vercel.app | Tela de entrada com as abas 'Entrar' e 'Criar conta' |
| 2 | Clicar no atalho 'Tutora Ana' | Campo E-mail preenchido com ana@zelo.demo |
| 3 | Digitar a senha Teste-1234 e clicar em 'Entrar' | Abre a tela 'Conversa sobre Mel' com 'Ana Souza' no topo |
| 4 | Ler a primeira mensagem do bot | Saudação 'Oi! Eu sou a Zelo, assistente de cuidado da Mel...' e os três atalhos de resposta rápida |

## CT-02 — Login com e-mail não cadastrado é recusado

- **Azure Boards:** Test Case #58 · testa PBI-02 Autenticação e preferências
- **Objetivo:** Verificar que credenciais inválidas não dão acesso e geram mensagem clara.
- **Dados de entrada:** E-mail: naoexiste@zelo.demo · Senha: Teste-1234
- **Saída esperada:** Mensagem 'E-mail ou senha inválidos'; usuário continua na tela de entrada.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Abrir https://zelopet.vercel.app | Tela de entrada |
| 2 | Digitar o e-mail naoexiste@zelo.demo e a senha Teste-1234 | Campos preenchidos |
| 3 | Clicar em 'Entrar' | Mensagem 'E-mail ou senha inválidos' abaixo do formulário; a URL continua /index.html |

## CT-03 — Cadastro de tutor: senha curta recusada e cadastro válido aceito

- **Azure Boards:** Test Case #59 · testa PBI-01 Cadastro do tutor
- **Objetivo:** Validar a regra de senha mínima (8 caracteres) e a criação de conta de tutor.
- **Dados de entrada:** Nome: Carla Teste · E-mail: carla.teste@zelo.demo · Senha 1: 1234567 (7 caracteres) · Senha 2: senha-segura-1
- **Saída esperada:** Com a senha 1: 'Preencha nome, e-mail e uma senha com ao menos 8 caracteres.' Com a senha 2: conta criada, tela do chat abre e pede o cadastro do primeiro pet.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Abrir https://zelopet.vercel.app e clicar na aba 'Criar conta' | Formulário com Nome, E-mail e Senha |
| 2 | Preencher Carla Teste / carla.teste@zelo.demo / 1234567 e clicar em 'Criar conta' | Mensagem 'Preencha nome, e-mail e uma senha com ao menos 8 caracteres.' |
| 3 | Trocar a senha para senha-segura-1 e clicar em 'Criar conta' | Entra no chat da tutora 'Carla Teste' |
| 4 | Observar a tela | Abre o cadastro do primeiro pet (a conta nova não tem pets) |

## CT-04 — Cadastro de pet

- **Azure Boards:** Test Case #60 · testa PBI-03 Cadastro do pet
- **Objetivo:** Verificar que o tutor cadastra um pet e ele passa a ter conversa própria.
- **Dados de entrada:** Logado como ana@zelo.demo · Nome: Luna · Espécie: Gato · Sexo: Fêmea · Nascimento: 01/05/2025 · Peso: 3,2 kg
- **Saída esperada:** Aba 'Luna' no topo; título 'Conversa sobre Luna'; cartão do pet 'Gato · fêmea'; aviso 'Luna foi cadastrado.'

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo (CT-01) | Conversa sobre Mel |
| 2 | Clicar em 'Novo' na barra de pets | Abre o formulário de cadastro de pet |
| 3 | Preencher Luna, Gato, Fêmea, 01/05/2025, 3,2 e clicar em 'Cadastrar' | Formulário fecha e aparece o aviso 'Luna foi cadastrado.' |
| 4 | Conferir a barra de pets e o cartão lateral | Aba 'Luna' selecionada, 'Conversa sobre Luna' e cartão 'Gato · fêmea' |

## CT-05 — Plano de cuidados (Ciclo Zelo) na conversa e no painel lateral

- **Azure Boards:** Test Case #61 · testa PBI-09 Registro da linha do tempo
- **Objetivo:** Verificar que os cuidados vencidos e próximos aparecem na conversa e no painel.
- **Dados de entrada:** Logado como ana@zelo.demo, pet Mel · Mensagem: Quais as próximas vacinas?
- **Saída esperada:** Resposta 'Estes são os próximos cuidados da Mel:' com 'V10 (polivalente) — reforço anual: venceu há 12 dias', 'Antirrábica: vence em 9 dias', 'Vermífugo: vence em 28 dias' e 'Check-up anual'; no painel, V10 marcada como 'Vencido'.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo e selecionar Mel | Conversa sobre Mel |
| 2 | Enviar a mensagem 'Quais as próximas vacinas?' | Bot lista os cuidados da Mel, começando por 'V10 (polivalente) — reforço anual: venceu há 12 dias' |
| 3 | Conferir o painel 'Ciclo Zelo · próximos cuidados' | V10 com selo 'Vencido', Antirrábica 'Em breve', Vermífugo e Check-up 'Em dia' |

## CT-06 — Triagem de prioridade média com perguntas de acompanhamento

- **Azure Boards:** Test Case #62 · testa PBI-10 Triagem textual estruturada
- **Objetivo:** Verificar que um relato leve gera no máximo duas perguntas e termina em prioridade média com explicação.
- **Dados de entrada:** Pet Mel · Mensagens: 'Mel está com tosse' → atalho 'Há 1 ou 2 dias' → atalho 'Respira normalmente'
- **Saída esperada:** Perguntas 'Desde quando ela está assim?' e 'Como está a respiração da Mel?'; cartão 'Prioridade média', 'Agendar consulta nas próximas 24–72 horas', sintoma 'Tosse', duração '≈ 2 dias', confiança 58%, selo 'Revisão humana'; pergunta se pode enviar o resumo à clínica.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo, Mel, clicar em 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Mel está com tosse' | Bot: 'Entendi: tosse. Desde quando ela está assim?' com atalhos de tempo |
| 3 | Clicar em 'Há 1 ou 2 dias' | Bot pergunta sobre a respiração da Mel |
| 4 | Clicar em 'Respira normalmente' | Cartão 'Prioridade média', 'Agendar consulta nas próximas 24–72 horas', sintoma Tosse, ≈ 2 dias, confiança 58% |
| 5 | Ler o fim da mensagem e os atalhos | 'Quer que eu envie este resumo para a clínica?' e atalhos 'Sim, compartilhar com a clínica' / 'Agora não' |

## CT-07 — Sinal de alerta gera emergência imediata

- **Azure Boards:** Test Case #63 · testa PBI-10 Triagem textual estruturada
- **Objetivo:** Verificar que um sinal de alerta encerra as perguntas e indica atendimento de urgência com primeiros cuidados.
- **Dados de entrada:** Pet Mel · Mensagem: 'Ela teve uma convulsão agora há pouco'
- **Saída esperada:** Sem perguntas; texto 'Atenção: isso pode ser uma emergência. Leve a Mel agora a um pronto-atendimento veterinário...'; primeiros cuidados para convulsão; cartão 'Prioridade emergência', 'Buscar atendimento veterinário de urgência agora', 'Por quê: Sinal de alerta relatado: convulsão', confiança 90%.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Ela teve uma convulsão agora há pouco' | Resposta imediata, sem perguntas de acompanhamento |
| 3 | Conferir o texto | 'Atenção: isso pode ser uma emergência...' e 'Afaste objetos que possam machucar, não coloque a mão na boca...' |
| 4 | Conferir o cartão | 'Prioridade emergência', 'Buscar atendimento veterinário de urgência agora', sintoma Convulsão, confiança 90% |

## CT-08 — Combinação de sinais eleva a prioridade para alta

- **Azure Boards:** Test Case #64 · testa PBI-10 Triagem textual estruturada
- **Objetivo:** Verificar a regra de combinação (abatimento + vômito) e a duração extraída do texto.
- **Dados de entrada:** Pet Mel · Mensagem: 'Ela está muito abatida e vomitou duas vezes desde ontem'
- **Saída esperada:** Cartão 'Prioridade alta', 'Contatar a clínica veterinária hoje', sintomas 'Apatia / abatimento' e 'Vômito', duração '≈ 24 h', motivo 'Abatimento junto de outros sinais indica que o organismo está sendo afetado', confiança 78%.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Ela está muito abatida e vomitou duas vezes desde ontem' | Triagem fecha sem perguntas (prioridade alta) |
| 3 | Conferir o cartão | 'Prioridade alta', 'Contatar a clínica veterinária hoje', Apatia / abatimento + Vômito, ≈ 24 h, confiança 78% |

## CT-09 — Regra específica de gato: não consegue urinar

- **Azure Boards:** Test Case #65 · testa PBI-10 Triagem textual estruturada
- **Objetivo:** Verificar a regra de espécie (obstrução urinária em gato é emergência).
- **Dados de entrada:** Pet Thor (gato) · Mensagem: 'Ele tenta fazer xixi e não sai nada, está miando muito'
- **Saída esperada:** Cartão 'Prioridade emergência' com sintomas 'Não consegue urinar' e 'Mudança de comportamento'; texto com 'Não espere: a obstrução urinária pode ser fatal em poucas horas.'

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo e clicar na aba 'Thor' | Conversa sobre Thor |
| 2 | Clicar em 'Nova conversa' e enviar 'Ele tenta fazer xixi e não sai nada, está miando muito' | Resposta de emergência sem perguntas |
| 3 | Conferir texto e cartão | 'Não espere: a obstrução urinária pode ser fatal em poucas horas.'; 'Prioridade emergência'; sintomas Não consegue urinar e Mudança de comportamento |

## CT-10 — Negação: sintoma negado não entra na triagem

- **Azure Boards:** Test Case #66 · testa PBI-10 Triagem textual estruturada
- **Objetivo:** Verificar que 'não vomitou' não é contado como vômito.
- **Dados de entrada:** Pet Mel · Mensagens: 'Ela não vomitou, só espirrou algumas vezes hoje' → atalho 'Respira normalmente'
- **Saída esperada:** Pergunta sobre a respiração; cartão 'Prioridade baixa', 'Registrar e observar por 24–48 horas', sintoma identificado apenas 'Espirros' (sem Vômito).

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Ela não vomitou, só espirrou algumas vezes hoje' | Bot pergunta sobre a respiração da Mel |
| 3 | Clicar em 'Respira normalmente' | Cartão 'Prioridade baixa', 'Registrar e observar por 24–48 horas' |
| 4 | Conferir 'Sintomas identificados' | Somente 'Espirros'; Vômito não aparece |

## CT-11 — Relato não reconhecido vira média com revisão humana e pode ser enviado

- **Azure Boards:** Test Case #67 · testa PBI-16 Consentimento e compartilhamento
- **Objetivo:** Verificar que ausência de sintoma reconhecido não é tratada como baixo risco.
- **Dados de entrada:** Pet Mel · Mensagens: 'Ela está com um comportamento que não sei descrever direito' → atalho 'Enviar para a clínica avaliar'
- **Saída esperada:** Primeiro: 'Não consegui entender isso como um sinal de saúde da Mel...' com orientação de sinais de urgência. Depois: cartão 'Prioridade média' com motivo 'Não consegui identificar sinais específicos no relato...', selo 'Revisão humana' e mensagem 'Pronto. Enviei o resumo da Mel para a clínica...'.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Ela está com um comportamento que não sei descrever direito' | Bot pede outras palavras e lista os sinais de urgência; atalho 'Enviar para a clínica avaliar' |
| 3 | Clicar em 'Enviar para a clínica avaliar' | Cartão 'Prioridade média', 'Revisão humana', motivo 'Não consegui identificar sinais específicos no relato...' |
| 4 | Ler o fim da resposta | 'Pronto. Enviei o resumo da Mel para a clínica...' |

## CT-12 — Pedido de remédio é recusado

- **Azure Boards:** Test Case #68 · testa PBI-10 Triagem textual estruturada
- **Objetivo:** Verificar que o chatbot nunca indica medicamento nem dose.
- **Dados de entrada:** Pet Mel · Mensagem: 'Posso dar dipirona para ela?'
- **Saída esperada:** 'Não posso indicar remédios nem doses. Vários medicamentos humanos, como dipirona, paracetamol e ibuprofeno, são perigosos para cães e gatos...'; nenhuma dose é citada.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Posso dar dipirona para ela?' | Bot responde 'Não posso indicar remédios nem doses...' |
| 3 | Conferir a resposta | Explica o risco de remédios humanos e orienta procurar o veterinário; não cita dose |

## CT-13 — Recusa do compartilhamento: nada vai para a clínica

- **Azure Boards:** Test Case #69 · testa PBI-16 Consentimento e compartilhamento
- **Objetivo:** Verificar o consentimento explícito (LGPD): recusar não compartilha.
- **Dados de entrada:** Pet Mel · 'Ela teve uma convulsão agora há pouco' → atalho 'Agora não' · Depois: login vet@zelo.demo
- **Saída esperada:** 'Tudo bem, não compartilhei nada com a clínica...'; na fila da clínica não aparece nenhuma triagem da Mel (só os casos fictícios de outros tutores: Nina, Bidu, Simba, Max, Luna, Mia, Pipoca, Fred).

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Em janela anônima, entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Ela teve uma convulsão agora há pouco' | Triagem de emergência com atalhos de compartilhamento |
| 3 | Clicar em 'Agora não' | 'Tudo bem, não compartilhei nada com a clínica...' |
| 4 | Clicar em 'Sair' e entrar como vet@zelo.demo (atalho 'Clínica Zelo') | Painel 'Fila de atenção' sem nenhum item 'Mel' (a fila mostra só os casos fictícios de outros tutores) |

## CT-14 — Compartilhamento com consentimento aparece na fila da clínica

- **Azure Boards:** Test Case #70 · testa PBI-16 Consentimento e compartilhamento
- **Objetivo:** Verificar o fluxo tutor → consentimento → clínica, com prioridade na fila.
- **Dados de entrada:** Pet Mel · 'Ela teve uma convulsão agora há pouco' → atalho 'Sim, compartilhar com a clínica' · Depois: login vet@zelo.demo
- **Saída esperada:** Tutor: 'Pronto. Enviei o resumo da Mel para a clínica...', cartão com 'Compartilhada com a clínica', painel 'Compartilhamento com a clínica' ativo. Clínica: item 'Mel · EMERGÊNCIA · Convulsão · Aguardando revisão' no topo da fila.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Em janela anônima, entrar como ana@zelo.demo, Mel, 'Nova conversa' | Saudação do bot |
| 2 | Enviar 'Ela teve uma convulsão agora há pouco' | Triagem de emergência |
| 3 | Clicar em 'Sim, compartilhar com a clínica' | 'Pronto. Enviei o resumo da Mel para a clínica...'; cartão marcado 'Compartilhada com a clínica' |
| 4 | Conferir o painel 'Compartilhamento com a clínica' | 'Ativo desde ...' e botão 'Revogar compartilhamento' |
| 5 | Sair e entrar como vet@zelo.demo | Fila mostra 'Mel', selo 'EMERGÊNCIA', 'Convulsão', 'Aguardando revisão' |

## CT-15 — Revisão profissional pela clínica e retorno ao tutor

- **Azure Boards:** Test Case #71 · testa PBI-15 Dashboard da clínica
- **Objetivo:** Verificar o humano no circuito: a clínica confirma a prioridade e o tutor vê o retorno.
- **Dados de entrada:** Pré-condição: CT-14 executado · Clínica: decisão 'Confirmar prioridade' · Observação: 'Trazer a Mel agora.'
- **Saída esperada:** Clínica: aviso 'Revisão registrada...', seção 'Revisões registradas' com 'Confirmada · Emergência' e a observação; item da fila 'Revisada'. Tutor: linha do tempo com 'Clínica confirmou a prioridade emergência' e a observação.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Logado como vet@zelo.demo, clicar no item 'Mel' da fila | Detalhe com relato, triagem da IA e formulário 'Revisão profissional' |
| 2 | Manter 'Confirmar prioridade', escrever 'Trazer a Mel agora.' e clicar em 'Registrar revisão' | Aviso 'Revisão registrada...' e 'Confirmada · Emergência' em 'Revisões registradas' |
| 3 | Conferir a fila | Item com selo 'Revisada' |
| 4 | Sair, entrar como ana@zelo.demo e abrir a 'Linha do tempo' da Mel | Evento 'Clínica confirmou a prioridade emergência' com 'Trazer a Mel agora.' |

## CT-16 — Revogar o consentimento oculta as triagens da clínica

- **Azure Boards:** Test Case #72 · testa PBI-16 Consentimento e compartilhamento
- **Objetivo:** Verificar que a revogação desfaz o compartilhamento (LGPD).
- **Dados de entrada:** Pré-condição: CT-14 executado · Tutor clica em 'Revogar compartilhamento'
- **Saída esperada:** Aviso 'Compartilhamento revogado. A clínica deixou de ver as triagens.'; painel 'Desativado'; na clínica a triagem some da fila.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Logado como ana@zelo.demo, Mel, painel 'Compartilhamento com a clínica' | Status 'Ativo desde ...' |
| 2 | Clicar em 'Revogar compartilhamento' | Aviso 'Compartilhamento revogado. A clínica deixou de ver as triagens.' |
| 3 | Conferir o painel | 'Desativado. Nada é enviado à clínica sem a sua autorização.' |
| 4 | Sair e entrar como vet@zelo.demo | As triagens da Mel não aparecem mais na fila |

## CT-17 — Controle de acesso: tutor não abre o painel da clínica

- **Azure Boards:** Test Case #73 · testa PBI-02 Autenticação e preferências
- **Objetivo:** Verificar a separação de perfis TUTOR e CLÍNICA.
- **Dados de entrada:** Logado como ana@zelo.demo · URL: https://zelopet.vercel.app/clinic.html
- **Saída esperada:** O painel da clínica não é exibido; o usuário é redirecionado para a área do tutor (Conversa sobre Mel).

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Entrar como ana@zelo.demo | Conversa sobre Mel |
| 2 | Digitar na barra de endereço https://zelopet.vercel.app/clinic.html | A fila da clínica não aparece |
| 3 | Aguardar o redirecionamento | Volta para a tela do tutor (Conversa sobre Mel) |

## CT-18 — Saída estruturada da IA e rastreabilidade

- **Azure Boards:** Test Case #74 · testa PBI-17 Métricas e rastreabilidade
- **Objetivo:** Verificar que a triagem expõe o contrato JSON com versão do modelo, confiança, revisão humana e aviso.
- **Dados de entrada:** Triagem do CT-07 · Ação: abrir 'Ver saída JSON estruturada'
- **Saída esperada:** JSON com "priority": "emergencia", "next_action": "buscar_atendimento_urgente", "confidence": 0.9, "model_version": "zelo-triage-hybrid-1.0", "engine"."rules": "zelo-rules-1.0", "human_review_required": true e "disclaimer": "Esta orientação não substitui avaliação veterinária."

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Executar o CT-07 | Cartão de emergência |
| 2 | Clicar em 'Ver saída JSON estruturada' no cartão | Bloco JSON é exibido |
| 3 | Conferir os campos | priority=emergencia, next_action=buscar_atendimento_urgente, confidence=0.9, model_version=zelo-triage-hybrid-1.0, human_review_required=true, disclaimer presente |

## CT-19 — Análise de foto da pele (cão) — resultado experimental

- **Azure Boards:** Test Case #75 · testa PBI-11 Visão computacional D3
- **Objetivo:** Verificar a integração do classificador de imagens (D3) com o chat. Ambiente: sistema completo local.
- **Dados de entrada:** http://localhost:8080 · ana@zelo.demo (senha de demonstração do README) · Pet Mel · Arquivo: data/samples/d3/fungica_2.jpg
- **Saída esperada:** Cartão 'Análise de foto · Experimental' com 'Infecção fúngica' 96%, selo 'MÉDIA', texto 'só o hash da imagem é guardado; a foto não é armazenada' e aviso 'Não é diagnóstico'; linha do tempo com 'Análise de foto (experimental): Infecção fúngica'.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Subir o sistema com .\scripts\run-local.ps1 e entrar como ana@zelo.demo | Conversa sobre Mel; selo 'Visão ativa' no topo |
| 2 | Clicar no ícone de câmera e escolher data/samples/d3/fungica_2.jpg | Mensagem 'Enviei uma foto para análise.' |
| 3 | Conferir o cartão de resultado | 'Infecção fúngica' 96%, selo 'MÉDIA', aviso de classificação experimental |
| 4 | Conferir a linha do tempo | Evento 'Análise de foto (experimental): Infecção fúngica' |

## CT-20 — Foto de gato é recusada com explicação

- **Azure Boards:** Test Case #76 · testa PBI-11 Visão computacional D3
- **Objetivo:** Verificar o limite do classificador (treinado só com cães). Ambiente: sistema completo local.
- **Dados de entrada:** http://localhost:8080 · ana@zelo.demo (senha de demonstração do README) · Pet Thor (gato) · Arquivo: data/samples/d3/fungica_2.jpg
- **Saída esperada:** Aviso 'O classificador visual foi treinado apenas com cães. Para gatos, descreva os sinais no chat que eu faço a triagem.'; nenhum resultado de classificação.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Com o sistema local no ar, entrar como ana@zelo.demo e selecionar Thor | Conversa sobre Thor |
| 2 | Clicar no ícone de câmera e escolher data/samples/d3/fungica_2.jpg | Aviso 'O classificador visual foi treinado apenas com cães...' |
| 3 | Conferir o chat | Nenhum cartão de análise de foto é criado |
