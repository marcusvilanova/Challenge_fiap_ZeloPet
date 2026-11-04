# API do Zelo

Documentação interativa (OpenAPI/Swagger): **http://localhost:8080/swagger-ui.html** — exige login (use o botão "Entrar" do app antes; a documentação não é pública). A especificação em JSON fica em `/v3/api-docs`.

## Convenções

- **Autenticação:** sessão (cookie `JSESSIONID`, `HttpOnly`) criada por `POST /api/auth/login` (formulário `email` + `password`). Perfis: `TUTOR` e `CLINICA`.
- **CSRF:** o servidor devolve o cookie `XSRF-TOKEN`; toda requisição que altera estado precisa enviar o mesmo valor no cabeçalho `X-XSRF-TOKEN` (o front-end faz isso em `js/api.js`).
- **JSON em `snake_case`** (ex.: `pet_id`, `human_review_required`).
- **Erros** no formato RFC 7807 (`application/problem+json`): `title`, `status`, `detail` e, em validação, `errors` por campo. Nunca expõem pilha de execução.
- **Limite de requisições** por endereço de origem (padrão: 40/min na API e 10/min no login/cadastro) → `429` com `Retry-After`.
- **IDs públicos** legíveis: `PET-0001`, `TRI-000001`. Recurso de outro tutor responde `404` (não `403`), para não revelar que existe.

## Rotas

| Método e caminho | Perfil | O que faz |
|---|---|---|
| `POST /api/auth/login` · `POST /api/auth/logout` | público / logado | Entra e sai (sessão) |
| `POST /api/auth/register` | público | Cadastra tutor (senha de 8 a 72 caracteres) |
| `GET /api/auth/me` | logado | Perfil da sessão |
| `GET /api/system/status` | logado | Estado dos componentes: motor, LLM ligado/desligado e modelo, serviço de visão |
| `GET /api/pets` · `POST /api/pets` | tutor | Lista e cadastra pets |
| `GET /api/pets/catalog` | tutor | Raças e tags de histórico aceitas |
| `GET /api/pets/{petId}` · `PUT /api/pets/{petId}` | tutor | Detalhe e edição dos dados do pet (a edição fica registrada na linha do tempo) |
| `GET /api/pets/{petId}/timeline` | tutor | Linha do tempo (triagens, fotos, vacinas, retornos da clínica) |
| `POST /api/pets/{petId}/events` | tutor | Registra evento manual (anotação, medicação etc.) |
| `GET /api/pets/{petId}/care-plan` · `POST` | tutor | Plano de cuidados (Ciclo Zelo): vencido / em breve / em dia; `POST` adiciona item (`type`: VACINA, VERMIFUGO, ANTIPULGAS ou CONSULTA; `name`; `next_due`; `last_done` opcional) |
| `POST /api/pets/{petId}/care-plan/{itemId}/done` | tutor | Marca o cuidado como feito hoje; a próxima data é `next_due` ou o intervalo padrão do tipo (vacina/consulta 365 d, vermífugo 90 d, antipulgas 30 d) |
| `DELETE /api/pets/{petId}/care-plan/{itemId}` | tutor | Remove o item do plano (o histórico na linha do tempo é mantido) |
| `POST /api/chat/sessions` | tutor | Abre ou retoma a conversa do pet (`fresh: true` cria uma nova) |
| `POST /api/chat/messages` | tutor | Envia uma mensagem; devolve a resposta do bot, o estado e, se houve, o cartão de triagem |
| `GET /api/chat/sessions/{id}/messages` | tutor | Histórico (o estado "compartilhado" vem do banco, não do instantâneo) |
| `POST /api/triage` | tutor | Triagem avulsa (sem conversa): relato → saída estruturada |
| `POST /api/vision/analyze` | tutor | Foto da pele (multipart; só cães; JPEG/PNG/WebP até 5 MB) → classificação **experimental** |
| `POST /api/sharing/consents` · `GET` · `DELETE /{id}` | tutor | Concede, lista e **revoga** o compartilhamento com a clínica (revogar oculta as triagens já enviadas) |
| `GET /api/clinic/triages` | clínica | Fila de triagens compartilhadas, da prioridade mais alta para a mais baixa |
| `GET /api/clinic/triages/{triageId}` | clínica | Relato, triagem, histórico **autorizado**, revisões anteriores e `conversation`: a conversa tutor ↔ Zelo que gerou a triagem (só até a resposta com a triagem; vazia em triagem avulsa) |
| `POST /api/clinic/reviews` | clínica | Registra a revisão profissional (confirma ou ajusta a prioridade) |
| `GET /actuator/health` | público | Saúde do serviço |

### Telas (mesmos endereços no backend e na Vercel)

| Endereço | Perfil | Tela |
|---|---|---|
| `/` · `/index.html` · `/login` | público | Entrada e cadastro (no backend, `/` abre a entrada; na Vercel, `/` abre o painel e redireciona para a entrada se não houver login) |
| `/chat.html` | tutor | Conversa com a Zelo |
| `/dashboard` · `/painel` · `/tutor` | tutor | Painel do Tutor (IBM Carbon): perfil, edição do pet, histórico com exportação em PDF, gestão do plano de cuidados |
| `/clinic.html` | clínica | Fila, painel de atendimento (indicadores, prioridades, últimos 7 dias, sintomas), conversa da triagem e revisão profissional |
| `/apresentacao` · `/pitch` · `/slides` · `/video` · `/demonstracao` | público | Apresentação da Sprint 4 e vídeo-guia de QA |

## Contrato da triagem (saída da IA)

`POST /api/triage` e o cartão do chat devolvem o mesmo objeto, validado em testes contra [`data/schemas/triage-result.schema.json`](../data/schemas/triage-result.schema.json):

```json
{
  "triage_id": "TRI-000004", "pet_id": "PET-0001",
  "symptoms": ["convulsao"],
  "priority": "emergencia", "next_action": "buscar_atendimento_urgente",
  "reason": "Sinal de alerta relatado: convulsão",
  "confidence": 0.90, "model_version": "zelo-triage-hybrid-1.0",
  "engine": { "rules": "zelo-rules-1.0", "knowledge": "1.0.0", "llm": "local-fallback" },
  "human_review_required": true,
  "disclaimer": "Esta orientação não substitui avaliação veterinária."
}
```

Campos extras (`symptom_details` com a evidência de cada sintoma, `duration_hours`, `context_factors`, `needs_more_info`, `shared_with_clinic`) estão no esquema. Valores de `priority`: `baixa` · `media` · `alta` · `emergencia`.

## Exemplo rápido (curl, com o backend e o seed de demonstração no ar)

```bash
# 1) entra e guarda os cookies (a senha do seed é a de ZELO_DEMO_PASSWORD)
curl -c jar.txt -X POST http://localhost:8080/api/auth/login -d "email=ana@zelo.demo" -d "password=$ZELO_DEMO_PASSWORD"
# 2) o cookie XSRF-TOKEN foi gravado em jar.txt: reenvie o valor no cabeçalho
TOKEN=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
curl -b jar.txt -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/triage \
  -d '{"pet_id":"PET-0001","text":"ela teve uma convulsão agora há pouco"}'
```
