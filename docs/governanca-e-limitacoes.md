# Governança, privacidade e limitações

Este documento diz o que o Zelo **faz para ser seguro**, e — com o mesmo peso — o que ele **não** faz e onde pode errar. A revisão de código e de segurança feita nesta sprint está refletida aqui: o que foi corrigido virou teste; o que não foi corrigido está listado como limitação.

## 1. Princípios de IA responsável aplicados

| Princípio | Como está no produto | Onde |
|---|---|---|
| **Não diagnosticar, não prescrever** | O texto fala em *prioridade* e *próximo passo*. Pedido de remédio/dose recebe recusa fixa. O guard barra nome de medicamento, dose e doença afirmada na resposta gerada | `ReplyComposer`, `IntentClassifier`, `ResponseGuard` |
| **Humano no circuito** | Toda triagem de prioridade média ou maior pede revisão profissional; a clínica **confirma ou ajusta** e isso fica registrado e visível ao tutor | `ClinicService`, `clinic.html` |
| **Determinístico por cima do generativo** | A prioridade vem sempre das regras. O LLM só pode *somar* sintomas (o piso é a análise determinística) ou reescrever texto de prioridade baixa/média | `PriorityEngine`, `TriageAnalysis.withFloor`, `ChatService.polish` |
| **Ausência de sinal ≠ baixo risco** | Relato que o léxico não entende vira **média + revisão humana** (R13), nunca "baixa" | `TriageEngine` |
| **Transparência** | Cada cartão mostra a confiança da interpretação, os motivos, a evidência de cada sintoma (inclusive "identificado pela IA") e a versão do modelo/regras; a saída estruturada fica disponível | `triage-card.js`, `TriageResult` |
| **Rastreabilidade** | Toda saída é gravada com `model_version`, regras, base de conhecimento e se houve LLM | `TriageService` |
| **Aviso claro** | "Triagem preliminar, não substitui o médico-veterinário" em todo cartão e no rodapé do chat; foto = "experimental, não é diagnóstico" | front-end, `VisionResult.DISCLAIMER` |

## 2. Privacidade e LGPD

- **Consentimento explícito e revogável.** Nada vai para a clínica sem um "sim" claro do tutor. O reconhecimento do "sim" é estrito (frase curta ou pedido explícito, sem negação e sem ser pergunta) — "não quero compartilhar com a clínica" **nunca** concede. Cada consentimento guarda escopo e versão do texto aceito.
- **Revogar desfaz.** Revogar oculta as triagens já enviadas, e conceder de novo **não** as reexibe (testado).
- **Minimização para a clínica.** A clínica só vê o que o tutor enviou, mais o histórico dos tipos `triage, review, consent, share, vision, visit, vaccine, medication`. Anotações livres e de comportamento do tutor **não** são enviadas.
- **Foto não é armazenada.** Só o hash SHA-256 e o resultado. A imagem é validada (assinatura do arquivo, tamanho, limite de pixels) e descartada.
- **Dados enviados ao LLM** (somente com `ANTHROPIC_API_KEY`): o texto do relato e o contexto do pet, sem e-mail, telefone, CPF e CEP (máscara `PiiScrubber`). **Nomes digitados no texto não são removidos.** O app avisa na tela quando a IA generativa está ligada. Em produção seria preciso contrato de tratamento de dados e, se couber, transferência internacional avaliada pelo jurídico.
- **Dados de demonstração** são fictícios, desligados por padrão (`ZELO_SEED_DEMO=false`) e o app recusa subir com a senha padrão se os cookies estiverem em modo HTTPS.
- **Sem segredos no repositório.** Chave de API e senha só por variável de ambiente (`.env.example` tem apenas nomes). `toString()` de credencial e de chave mascara o valor.

## 3. Segurança da aplicação

Cobertos por testes de integração (`SecurityHardeningIntegrationTest`, `RateLimitIntegrationTest`, `ChatApiIntegrationTest`):

- Sessão no servidor (cookie `HttpOnly`, `SameSite`), **CSRF** por cookie + cabeçalho, senha com BCrypt, perfis `TUTOR` e `CLINICA` separados (clínica não acessa rotas do tutor e vice-versa).
- Recurso de outro tutor responde `404` (sem vazar existência). Fila e detalhe da clínica só mostram triagens **compartilhadas com consentimento ativo**.
- **Limite de requisições** por endereço, aplicado *antes* do filtro de login (contra força bruta) e imune a caminho codificado (`/api/%74riage`).
- **CSP restrita** (sem script ou estilo inline, fontes locais) e front-end sem `innerHTML`: todo texto entra como `textContent`.
- Erros no formato RFC 7807 sem pilha de execução; documentação da API exige login.
- Serviço de visão: limite de 12 megapixels e de tamanho, `DecompressionBomb` tratado como erro, endpoint síncrono para não travar o `/health`.

## 4. Limitações conhecidas (leia antes de apresentar)

### 4.1 Do conteúdo clínico

1. **As regras ainda não foram validadas por um médico-veterinário.** Foram escritas pela equipe com base na Sprint 3; os limiares (por exemplo "gato sem comer 48 h = alta") são plausíveis, não oficiais. É a pendência nº 1 antes de qualquer uso real.
2. **O D2 não ensina medicina ao sistema.** Ele serve para normalizar o vocabulário e medir cobertura; não tem diagnóstico-alvo confiável, é parcialmente sintético e os sintomas comuns têm lift ≈ 1. Por isso a prioridade vem de regras de segurança, não de um modelo treinado no D2.
3. **Generalização do texto é menor que o número do conjunto de desenvolvimento.** No primeiro contato com relatos novos, sem LLM: **acurácia 75% e recall de emergência 76,5% (holdout de 56 casos)** e **60% / 57,1% (conjunto cego de 35 casos)**. Depois dos ajustes o catálogo chegou a 100% nos três conjuntos, mas isso é otimista porque eles viraram dados de desenvolvimento. A revisão de código ainda achou — e corrigimos — casos que enganavam o motor ("a convulsão **não parou**" virava baixa prioridade). Isso indica que existem outros casos não descobertos.
4. **Português coloquial é um alvo móvel.** Gírias, regionalismos e frases longas com várias negações podem escapar. O desenho assume isso: na dúvida, o sintoma conta como presente e o relato não reconhecido vira média com revisão humana.
5. **A guarda de resposta é lista de bloqueio** (dose, remédio, doença, minimização). É uma heurística, não prova de segurança; por isso o resultado estruturado nunca vem do texto gerado, e prioridade alta/emergência não passa por reescrita.

### 4.2 Da visão computacional

6. **Só cães, 6 classes, uso experimental.** Treinado com o D3 (transfer learning, MobileNetV3 congelado + regressão logística, exportado em ONNX).
7. **Há vazamento entre os splits originais do D3** (a mesma foto-fonte do Roboflow aparece com *augmentations* em treino, validação e teste). A acurácia de 91,5% no teste original é inflada. O número a citar é a **validação cruzada agrupada por imagem-fonte: 82,2% ± 1,3 (macro-F1 0,77)**; no teste "limpo" (49 imagens, sem fontes vistas) a acurácia foi 85,7%, com amostra pequena demais para conclusões por classe.
8. **O modelo pode errar com confiança alta.** Em testes com as 12 fotos de amostra, 3 saíram erradas — inclusive uma de hipersensibilidade classificada como "saudável" com 100%. Por isso "saudável" só vira prioridade *baixa* com confiança suficiente e a mensagem sempre lembra que foto não substitui o veterinário; qualquer outra classe vira *média* e orienta a consulta. As fotos de amostra vêm do próprio D3 e **não são evidência de desempenho**.
9. **Não há detecção de "isto não é uma foto de pele".** Uma imagem qualquer recebe alguma classe.

### 4.3 Da engenharia

10. **Ordem da fila da clínica:** a consulta pega as 100 triagens compartilhadas mais recentes e ordena por prioridade em memória; com mais de 100 itens, uma emergência antiga ainda não revisada poderia sair da lista. Cada atualização da fila (a cada 10 s por aba aberta) faz algumas consultas por linha. Na demonstração (dezenas de itens) é imperceptível; em produção a ordenação e o filtro iriam para a consulta.
11. **Chamadas ao LLM dentro da transação.** Com o LLM ligado, a transação do chat fica aberta durante a chamada externa (timeout de 15 s, 1 nova tentativa). Com carga alta o *pool* de conexões (10) poderia esgotar. O desenho certo é chamar o LLM fora da transação; foi deixado como melhoria por não afetar a demonstração.
12. **O caminho com o LLM real foi validado só com servidor simulado.** Os testes cobrem o formato da requisição do SDK oficial, a extração, a reescrita, a reprovação pelo guard e a queda para o motor local; **não rodamos contra a API real** (a chave não faz parte do repositório). Antes de apresentar com o LLM ligado, faça um ensaio com a chave.
13. **Limitador de requisições e sessões ficam na memória** de um único servidor; para várias instâncias seria preciso um repositório compartilhado (Redis, por exemplo).
14. **Banco:** H2 em arquivo para a demonstração. O SQL do Flyway é portável, mas não foi executado em Oracle (que o grupo usa na outra disciplina); tipos e sequências devem ser conferidos.
15. **Autocadastro do tutor sem verificação de e-mail**, e a conta de clínica existe só no seed de demonstração. Em produção, a clínica seria provisionada por convite.
16. **Acessibilidade:** teclado, foco, rótulos ARIA e contraste foram considerados no desenho, mas **não houve teste com leitor de tela** nem auditoria automatizada (axe/Lighthouse). A revisão apontou que regiões `aria-live` (cartão do pet, painel de detalhe da clínica) são reescritas por inteiro e podem ser relidas pelo leitor de tela a cada atualização.

## 5. O que não foi feito nesta sprint

| Item | Situação |
|---|---|
| **IoT / IoB** (sensores, mudança de rotina) | **Não implementado.** A pasta de dados não trazia o dataset de sensores; o enunciado aceita agente conversacional + classificação + LLM. O contrato proposto na Sprint 3 está em `ia/iob/README.md` |
| Datasets D1, D4 e D5 | Não usados nesta entrega (D2 e D3 cobrem triagem textual e visão) |
| Validação por médico-veterinário | Pendente |
| Teste com tutores reais | Não feito |
| Execução contra a API real do LLM | Não feita (ver 4.3 nº 12) |
| Oracle, HTTPS e implantação em nuvem | Fora do escopo; `ZELO_COOKIE_SECURE=true` e o aviso de senha padrão já preparam o terreno |

## 6. Plano de mitigação sugerido (próximos passos)

1. Revisão das regras e dos textos por veterinário(a); transformar a revisão em casos novos no conjunto cego e reavaliar.
2. Coletar relatos reais (com consentimento) para um conjunto de teste que a equipe **nunca** use para ajustar o catálogo.
3. Mover as chamadas ao LLM para fora da transação e ordenar a fila no banco.
4. Rodar o ensaio com a API real e medir latência, custo e taxa de reprovação do guard.
5. Retreinar o classificador com divisão agrupada por imagem-fonte e adicionar uma classe/limiar "não é pele".
