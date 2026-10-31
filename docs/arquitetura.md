# Arquitetura do Zelo (Sprint 4)

Diagramas em Mermaid (renderizam no GitHub). Versão em imagem para slides/vídeo: [`diagramas/arquitetura.svg`](diagramas/arquitetura.svg).

## 1. Visão de componentes

```mermaid
flowchart LR
  subgraph Navegador
    T["Tutor<br/>chat.html"]
    C["Clínica<br/>clinic.html"]
  end

  subgraph Backend["Backend · Spring Boot 3.5 · Java 21"]
    SEC["Spring Security<br/>sessão · CSRF · perfis · rate limit"]
    API["API REST /api/*<br/>validação · RFC 7807"]
    FLOW["ConversationFlow<br/>máquina de estados"]
    CORE["Núcleo de triagem (Java puro)<br/>normalizador · extrator + negação<br/>regras de segurança · confiança"]
    GUARD["LlmClient + ResponseGuard<br/>(SDK oficial Anthropic)"]
    VC["VisionClient"]
    SVC["Serviços<br/>pet · triagem · consentimento<br/>clínica · cuidados"]
  end

  DB[("Banco relacional<br/>H2 (demo) · Flyway")]
  KB[("triage_knowledge.json<br/>catálogo de sintomas + D2")]
  PY["Serviço de visão<br/>FastAPI + ONNX Runtime<br/>MobileNetV3 (D3)"]
  LLM[("API Anthropic<br/>opcional")]

  T --> SEC
  C --> SEC
  SEC --> API
  API --> FLOW
  API --> SVC
  FLOW --> CORE
  CORE --- KB
  FLOW --> GUARD
  GUARD -.->|"só se houver chave"| LLM
  API --> VC
  VC -->|"POST /classify"| PY
  SVC --> DB
```

**Princípios**

- O **núcleo determinístico** (`br.com.zelo.ai`) é Java puro: roda sem Spring, sem rede, e é testável em milissegundos.
- O **LLM é opcional e subordinado**: pode *somar* sintomas e *reescrever* o texto de prioridade **baixa/média**; nunca reduz a prioridade (o resultado determinístico é o piso). Alta/emergência não passam por reescrita, e toda reescrita passa por um `ResponseGuard`. Saudações, agradecimentos e respostas de sim/não nem chegam ao LLM.
- **Falha graciosa**: sem chave, sem serviço de visão ou com erro de rede, o chat continua respondendo pelo motor local.
- **Dados mínimos para a IA**: espécie, idade, raça, peso, histórico, nome do pet e o texto do relato, com e-mail, telefone, CPF e CEP mascarados. O nome do tutor não é enviado (mas nomes digitados no relato não são removidos).

## 2. Fluxo de uma mensagem do tutor

```mermaid
sequenceDiagram
  autonumber
  participant U as Tutor (navegador)
  participant A as API + Security
  participant F as ConversationFlow
  participant N as Núcleo determinístico
  participant L as LLM (opcional)
  participant D as Banco

  U->>A: POST /api/chat/messages {pet_id, text}
  A->>A: autentica, valida CSRF e posse do pet
  A->>F: next(estado, texto, contexto do pet)
  F->>N: analyze(texto, pet, sintomas anteriores)
  N-->>F: sintomas, prioridade, confiança
  alt léxico fraco (vago, não reconhecido ou confiança baixa)
    F->>L: extractSymptoms(texto, ids permitidos)
    L-->>F: ids do catálogo (só somam)
    F->>N: reanalisa com os sintomas extras
  end
  alt emergência ou prioridade alta
    F-->>A: fecha a triagem agora (sem perguntas)
  else prioridade baixa/média
    F-->>A: no máx. 2 perguntas (duração, ingestão, respiração)
  end
  opt triagem concluída de prioridade baixa/média
    A->>L: rewriteReply(rascunho seguro)
    L-->>A: texto reescrito
    A->>A: ResponseGuard (dose, remédio, diagnóstico, urgência)
    A->>D: salva triagem + JSON estruturado + evento na linha do tempo
  end
  A-->>U: resposta + cartão de triagem + respostas rápidas
```

## 3. Compartilhamento com a clínica e revisão humana

```mermaid
sequenceDiagram
  autonumber
  participant U as Tutor
  participant A as API
  participant D as Banco
  participant V as Clínica

  U->>A: "Sim, compartilhar com a clínica"
  A->>D: Consent(escopo, versão do termo, destinatário) + Triage.shared = true
  V->>A: GET /api/clinic/triages (perfil CLINICA)
  A->>D: triagens compartilhadas COM consentimento ativo
  A-->>V: fila ordenada por prioridade
  V->>A: POST /api/clinic/reviews (confirmar ou ajustar)
  A->>D: ClinicReview + evento na linha do tempo do pet
  U->>A: DELETE /api/sharing/consents/{id}
  A->>D: revoked_at preenchido
  Note over V,D: a triagem some da fila da clínica imediatamente
```

## 4. Estados da conversa

```mermaid
stateDiagram-v2
  [*] --> IDLE
  IDLE --> COLLECTING: sintoma leve ou médio, faz pergunta
  IDLE --> AWAITING_SHARE: sinal de alerta ou prioridade alta, fecha na hora
  COLLECTING --> COLLECTING: nova informação, no máximo 2 perguntas
  COLLECTING --> AWAITING_SHARE: triagem concluída, prioridade média ou maior
  COLLECTING --> DONE: triagem concluída, prioridade baixa
  AWAITING_SHARE --> DONE: tutor responde sim ou agora não
  AWAITING_SHARE --> IDLE: outro assunto
  DONE --> IDLE: nova mensagem
```

## 5. Modelo de dados

```mermaid
erDiagram
  APP_USER ||--o{ PET : "é tutor de"
  PET ||--o{ CARE_ITEM : tem
  PET ||--o{ CHAT_SESSION : tem
  CHAT_SESSION ||--o{ CHAT_MESSAGE : contém
  PET ||--o{ TRIAGE : recebe
  CHAT_SESSION ||--o{ TRIAGE : origina
  TRIAGE ||--o{ CLINIC_REVIEW : "revisada em"
  PET ||--o{ CONSENT : "autoriza"
  PET ||--o{ TIMELINE_EVENT : registra
  PET ||--o{ IMAGE_ANALYSIS : "analisa (só hash)"
  APP_USER ||--o{ CLINIC_REVIEW : "revisa"

  APP_USER {
    bigint id PK
    varchar email UK
    varchar role "TUTOR ou CLINICA"
  }
  PET {
    bigint id PK
    varchar species "DOG ou CAT"
    varchar history_tags
  }
  TRIAGE {
    bigint id PK
    varchar priority
    clob result_json
    boolean shared_with_clinic
  }
  CONSENT {
    bigint id PK
    varchar scope
    varchar version
    timestamp revoked_at
  }
  IMAGE_ANALYSIS {
    bigint id PK
    varchar sha256
    clob result_json
  }
```

DDL completo e versionado: `backend/src/main/resources/db/migration/V1__schema_inicial.sql` (Flyway).

## 6. Controle de acesso

| Rota | Anônimo | TUTOR | CLINICA |
|---|---|---|---|
| `/`, `/index.html`, `/css|js|fonts|img/**` | ✅ | ✅ | ✅ |
| `POST /api/auth/login`, `/api/auth/register` | ✅ | ✅ | ✅ |
| `/chat.html`, `/api/pets/**`, `/api/chat/**`, `/api/triage`, `/api/vision/**`, `/api/sharing/**` | 401 | ✅ (só os próprios dados) | 403 |
| `/clinic.html`, `/api/clinic/**` | 401 | 403 | ✅ (só triagens compartilhadas com consentimento ativo) |
| `/api/system/**`, `/api/auth/me` | 401 | ✅ | ✅ |

Acesso a pet/conversa/triagem de outro tutor devolve **404** (não 403), para não revelar a existência do recurso.

## 7. Decisões técnicas e por quê

| Decisão | Motivo |
|---|---|
| Triagem determinística como **camada primária** | Sprint 3: um LLM sozinho pode ser impreciso, não determinístico e confiante demais. Regras explícitas dão consistência clínica e auditoria. |
| LLM só **soma** sintomas / **reescreve** texto de baixa/média | Garante que nenhuma saída generativa reduza a prioridade; o guard barra dose, remédio, diagnóstico afirmado e perda de urgência. |
| "Sem sintoma reconhecido" ≠ "baixo risco" | Achado do conjunto cego: relatos fora do léxico geravam "baixa". Agora viram **média + revisão humana**. |
| SDK oficial da Anthropic | Evita reimplementar HTTP/retentativas; testado contra servidor falso (formato real da requisição). |
| Serviço de visão separado (Python/ONNX) | Mantém o backend Java leve e permite trocar/versionar o modelo sem tocar na experiência do usuário. |
| H2 + Flyway com SQL portável | Demo sem instalar banco; o grupo usa Oracle na outra disciplina (ver ressalvas em `governanca-e-limitacoes.md`). |
| Sessão + CSRF (cookie) em vez de JWT | Front-end estático same-origin: menos superfície de ataque, logout real e cookie `HttpOnly`/`SameSite`. |
