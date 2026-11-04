# Roteiro do vídeo pitch (≈ 5 minutos)

> 🔴 **Link Oficial do Vídeo Pitch no YouTube (IoT & IA):** [https://youtu.be/x7SFaB4W2Eo?si=m0uE-kcxOw0PyOFZ](https://youtu.be/x7SFaB4W2Eo?si=m0uE-kcxOw0PyOFZ)

O vídeo vale 20 pontos e deve mostrar o chatbot **funcionando de verdade**. Este roteiro já está no ritmo de 5 minutos. A gravação e a publicação (YouTube, **não listado**) são da equipe.

## Antes de gravar (10 minutos)

1. Suba tudo: `.\scripts\run-local.ps1` (visão na 8001 + backend na 8080 com o seed de demonstração).
2. Abra duas janelas do navegador, **uma anônima**: tutora (Ana) em `http://localhost:8080` e clínica (`vet@zelo.demo`) em outra janela. A senha de demonstração está no README.
3. Em Mel, clique em **Nova conversa** para começar limpo. Tenha a foto `data/samples/d3/fungica_2.jpg` à mão.
4. Feche abas e notificações; zoom do navegador em 110%; microfone testado.
5. Ensaie uma vez. Se for mostrar o LLM ligado, ensaie com a chave **antes** (o caminho real não foi testado contra a API) — ou grave com o motor local, que é o caminho validado.

## Roteiro com tempos

| Tempo | Tela | Fala sugerida |
|---|---|---|
| **0:00–0:30** | Slide ou README: logo Zelo, nomes do grupo | "Somos [nomes]. O Zelo é um assistente de cuidado contínuo para pets: entende o relato do tutor, indica a prioridade e avisa a clínica antes de ele chegar. Hoje mostramos a Sprint 4: o chatbot funcionando, com IA híbrida." |
| **0:30–1:00** | Diagrama `docs/diagramas/arquitetura.svg` | "Camada 1: regras de segurança determinísticas definem a prioridade. Camada 2: um LLM opcional só complementa — nunca reduz a prioridade. Um serviço de visão classifica fotos de pele. E tudo passa por consentimento e por revisão de um veterinário." |
| **1:00–2:00** | Chat da Ana, pet **Mel** | Digite: *"Ela está com tosse"*. "O bot pergunta há quanto tempo e se respira normal — no máximo duas perguntas." Clique **Há 1 ou 2 dias** → **Respira normalmente**. Mostre o cartão: **prioridade média**, "por quê", confiança, versão do modelo, **Ver saída JSON estruturada**. |
| **2:00–2:45** | Mesma tela | Nova conversa: *"Ela teve uma convulsão agora há pouco"*. "Sinal de alerta: sem perguntas, vai direto para **emergência** e pergunta se pode avisar a clínica." Clique **Sim, compartilhar com a clínica**. Mostre a linha do tempo. |
| **2:45–3:15** | Janela da **clínica** | "Do outro lado, a clínica vê a fila ordenada por prioridade, o relato, o histórico **autorizado**." Abra a triagem, escreva uma observação e **Registrar revisão** (humano no circuito). Volte à Ana: o retorno aparece na linha do tempo. |
| **3:15–3:45** | Chat, **Thor** (gato) e uma pergunta de remédio | *"Ele tenta fazer xixi e não sai nada, está miando muito"* → emergência (regra específica de gato). Depois: *"Posso dar dipirona?"* → "o Zelo **não** indica remédio nem diagnostica". |
| **3:45–4:15** | Câmera → foto de `data/samples/d3/fungica_2.jpg` em Mel | "Visão computacional: um MobileNetV3 treinado com 4.315 fotos de pele de cães. O resultado é **experimental**; guardamos só o hash, não a foto." |
| **4:15–4:50** | Slide com os números (abaixo) | "Medimos com honestidade. Em relatos que não tínhamos visto, o motor acertou 75% e 60%; a taxa de emergências reconhecidas foi 76% e 57%. Isso nos levou a corrigir e a nunca tratar relato desconhecido como seguro. O classificador de imagens: 82% na validação cruzada agrupada, porque o conjunto original tem vazamento." |
| **4:50–5:15** | Slide final | "O que falta: validação por veterinário, teste com tutores reais, IoT/IoB. Código e documentação no GitHub. Obrigado." |

## Números para citar (todos estão em `docs/validacao-e-testes.md`)

| Para dizer | Número | Cuidado |
|---|---|---|
| Testes automatizados | 195 em Java + 33 em Python; cobertura de 94,7% das linhas | — |
| Generalização do texto (sem LLM) | holdout: **75%** de acerto, **76,5%** de recall de emergência; cego: **60%**, **57%** | **Não** cite os 100% como acurácia: são de regressão |
| Visão | **82,2% ± 1,3** (validação cruzada agrupada) | Não cite os 91,5% do teste original (vazamento) |
| Cobertura do vocabulário do D2 | 81,2% das menções | — |

## Frases a evitar

- "Diagnostica / detecta doenças" → use "triagem preliminar" e "apoio à decisão".
- "100% de acurácia" → só "100% nos testes de regressão".
- "Já validado por veterinários" → ainda **não** foi.
- "Usa IoT/IoB" → **não** nesta sprint; está documentado como próximo passo.

## Checklist de gravação

- [ ] ~5 minutos (entre 4:30 e 5:30), áudio claro, tela legível (≥ 1080p)
- [ ] Todos do grupo aparecem ou falam em algum trecho
- [ ] Mostrou: conversa, emergência, compartilhamento, painel da clínica, foto, JSON estruturado
- [ ] Citou limitações (revisão veterinária, IoT/IoB não feito)
- [x] Publicado no YouTube: [https://youtu.be/x7SFaB4W2Eo?si=m0uE-kcxOw0PyOFZ](https://youtu.be/x7SFaB4W2Eo?si=m0uE-kcxOw0PyOFZ)
