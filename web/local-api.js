// API do Zelo rodando no próprio navegador (versão para a Vercel, que não executa o backend Java).
// Mesma interface do js/api.js original: as telas (login, chat, clínica) funcionam sem mudança.
// Dados ficam no localStorage deste navegador. O motor de triagem é o porte JS em ./engine (paridade
// conferida com o Java). A IA generativa (Gemini) é opcional e roda na função /api/llm da Vercel.
import { KnowledgeBase, normalize, petContext, Priority, priorityFromWire, referenceFrequency, RULES_VERSION, TriageEngine } from "./engine/triage.js";
import { ConversationFlow, composer, initialState, MEDICATION_REFUSAL_MARK } from "./engine/chat.js";

export class ApiError extends Error {
  constructor(status, detail, errors) {
    super(detail);
    this.status = status;
    this.errors = errors || null;
  }
}

const STORE_KEY = "zelo.demo.v1";
const MODEL_VERSION = "zelo-triage-hybrid-1.0";
const DISCLAIMER = "Esta orientação não substitui avaliação veterinária.";
const LOCAL_LLM = "local-fallback";
const CLINIC_VISIBLE = new Set(["triage", "review", "consent", "share", "vision", "visit", "vaccine", "medication"]);
const NEXT_ACTION = {
  baixa: ["registrar_e_observar", "Registrar e observar por 24–48 horas"],
  media: ["agendar_consulta", "Agendar consulta nas próximas 24–72 horas"],
  alta: ["contatar_clinica_veterinaria", "Contatar a clínica veterinária hoje"],
  emergencia: ["buscar_atendimento_urgente", "Buscar atendimento veterinário de urgência agora"],
};

// ------------------------------------------------------------------------- carregamento
let knowledge;
let engine;
let flow;
const ready = (async () => {
  const response = await fetch("/triage/triage_knowledge.json");
  knowledge = new KnowledgeBase(await response.json());
  engine = new TriageEngine(knowledge);
  flow = new ConversationFlow(engine);
})();

let llmStatus = null;
async function llm() {
  if (llmStatus) return llmStatus;
  try {
    const r = await fetch("/api/llm", { headers: { Accept: "application/json" } });
    llmStatus = r.ok ? await r.json() : { enabled: false };
  } catch {
    llmStatus = { enabled: false };
  }
  return llmStatus;
}

async function llmCall(body) {
  try {
    const r = await fetch("/api/llm", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    return r.ok ? await r.json() : null;
  } catch {
    return null;
  }
}

// --------------------------------------------------------------------------- armazenamento
let memory = null;
function load() {
  if (memory) return memory;
  try {
    memory = JSON.parse(localStorage.getItem(STORE_KEY) || "null");
  } catch {
    memory = null;
  }
  if (!memory) memory = seed();
  return memory;
}
function save() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(memory));
  } catch {
    /* modo privado: os dados vivem só nesta aba */
  }
}
const now = () => new Date().toISOString();
const nextId = (db, kind) => (db.seq[kind] = (db.seq[kind] || 0) + 1);
const day = (offsetDays) => { const d = new Date(); d.setHours(12, 0, 0, 0); d.setDate(d.getDate() + offsetDays); return d.toISOString().slice(0, 10); };
const yearsAgo = (y, m = 0) => { const d = new Date(); d.setFullYear(d.getFullYear() - y); d.setMonth(d.getMonth() - m); return d.toISOString().slice(0, 10); };

function seed() {
  const db = { seq: {}, users: [], pets: [], care: [], events: [], sessions: [], messages: [], triages: [], consents: [], reviews: [], session: null };
  const user = (email, name, role) => { const u = { id: nextId(db, "user"), email, name, role, demo: true }; db.users.push(u); return u; };
  const ana = user("ana@zelo.demo", "Ana Souza", "TUTOR");
  const bruno = user("bruno@zelo.demo", "Bruno Lima", "TUTOR");
  user("vet@zelo.demo", "Dra. Helena Prado (Clínica Zelo)", "CLINICA");
  const pet = (owner, name, species, breed, female, birth, weight, tags) => {
    const p = { id: nextId(db, "pet"), owner: owner.id, name, species, breed, female, birth_date: birth, weight_kg: weight, history_tags: tags };
    db.pets.push(p);
    return p;
  };
  const mel = pet(ana, "Mel", "dog", "Labrador Retriever", true, yearsAgo(4), 27.5, ["vacinado"]);
  const thor = pet(ana, "Thor", "cat", "Maine Coon", false, yearsAgo(2, 3), 5.8, ["vacinado"]);
  const rex = pet(bruno, "Rex", "dog", "Bulldog", false, yearsAgo(2, 6), 22.0, ["problemas_pele"]);
  const care = (p, type, name, lastDays, nextDays) => db.care.push({ id: nextId(db, "care"), pet: p.id, type, name, last_done: day(lastDays), next_due: day(nextDays) });
  care(mel, "VACINA", "V10 (polivalente) — reforço anual", -395, -12);
  care(mel, "VACINA", "Antirrábica", -335, 9);
  care(mel, "VERMIFUGO", "Vermífugo", -60, 28);
  care(mel, "CONSULTA", "Check-up anual", -300, 61);
  care(thor, "VACINA", "V4 (quádrupla felina) — reforço anual", -335, 20);
  care(thor, "ANTIPULGAS", "Antipulgas", -25, 5);
  care(rex, "VACINA", "V10 (polivalente)", -365, -3);
  event(db, mel.id, "visit", "Consulta de rotina", "Check-up anual sem alterações. Peso estável.", "clinic");
  event(db, mel.id, "vaccine", "Vacina antirrábica aplicada", "Dose anual registrada pela clínica.", "clinic");
  event(db, thor.id, "visit", "Consulta de rotina", "Sem queixas. Orientações sobre alimentação.", "clinic");
  return db;
}

function event(db, petId, type, title, summary, source, refId = null) {
  db.events.push({ id: nextId(db, "event"), pet: petId, type, title, summary, source, ref_id: refId, occurred_at: now() });
}

// ------------------------------------------------------------------------------- utilidades
const petPublicId = (id) => `PET-${String(id).padStart(4, "0")}`;
const triagePublicId = (id) => `TRI-${String(id).padStart(6, "0")}`;
const parseId = (value, prefix) => {
  const m = new RegExp(`^${prefix}-(\\d+)$`).exec(String(value || ""));
  if (!m) throw new ApiError(404, "Recurso não encontrado");
  return Number(m[1]);
};

function ageMonths(birth) {
  if (!birth) return null;
  const b = new Date(`${birth}T12:00:00`);
  const t = new Date();
  let months = (t.getFullYear() - b.getFullYear()) * 12 + (t.getMonth() - b.getMonth());
  if (t.getDate() < b.getDate()) months--;
  return Math.max(0, months);
}

function me(db) {
  const user = db.users.find((u) => u.id === db.session);
  if (!user) throw new ApiError(401, "Entre para continuar.");
  return user;
}
function requireRole(db, role) {
  const user = me(db);
  if (user.role !== role) throw new ApiError(403, "Acesso negado para este perfil.");
  return user;
}
function ownedPet(db, user, publicId) {
  const pet = db.pets.find((p) => p.id === parseId(publicId, "PET") && p.owner === user.id);
  if (!pet) throw new ApiError(404, "Pet não encontrado");
  return pet;
}
const activeConsent = (db, petId) => db.consents.find((c) => c.pet === petId && !c.revoked_at);

function petResponse(db, pet) {
  return {
    pet_id: petPublicId(pet.id), name: pet.name, species: pet.species, breed: pet.breed, female: pet.female,
    birth_date: pet.birth_date, age_months: ageMonths(pet.birth_date), weight_kg: pet.weight_kg, history_tags: pet.history_tags,
    brachycephalic: knowledge.isBrachycephalic(pet.species, pet.breed), sharing_enabled: Boolean(activeConsent(db, pet.id)),
  };
}

function context(db, pet) {
  return petContext({ petId: petPublicId(pet.id), name: pet.name, species: pet.species, ageMonths: ageMonths(pet.birth_date),
    breed: pet.breed, brachycephalic: knowledge.isBrachycephalic(pet.species, pet.breed), weightKg: pet.weight_kg,
    female: pet.female, historyTags: pet.history_tags });
}

const daysUntil = (iso) => Math.round((new Date(`${iso}T12:00:00`) - new Date(`${day(0)}T12:00:00`)) / 86400000);
const plural = (d) => `${d} ${d === 1 ? "dia" : "dias"}`;
function careItems(db, pet) {
  return db.care.filter((c) => c.pet === pet.id).sort((a, b) => a.next_due.localeCompare(b.next_due));
}
function careContext(db, pet) {
  const items = careItems(db, pet);
  const lines = items.map((i) => {
    const d = daysUntil(i.next_due);
    return `${i.name}: ${d < 0 ? `venceu há ${plural(-d)}` : d === 0 ? "vence hoje" : `vence em ${plural(d)}`}`;
  });
  const urgent = items.find((i) => daysUntil(i.next_due) <= 14);
  let highlight = null;
  if (urgent) {
    const d = daysUntil(urgent.next_due);
    const sentence = d < 0 ? `o item "${urgent.name}" venceu há ${plural(-d)}.` : d === 0 ? `o item "${urgent.name}" vence hoje.` : `o item "${urgent.name}" vence em ${plural(d)}.`;
    highlight = `Aproveitando: ${sentence} Vale agendar com a clínica.`;
  }
  return { highlight, lines };
}

function timeline(db, petId, limit, clinicOnly = false) {
  return db.events.filter((e) => e.pet === petId && (!clinicOnly || CLINIC_VISIBLE.has(e.type)))
    .sort((a, b) => b.occurred_at.localeCompare(a.occurred_at) || b.id - a.id).slice(0, limit)
    .map(({ id, type, title, summary, source, ref_id, occurred_at }) => ({ id, type, title, summary, source, ref_id, occurred_at }));
}

// ------------------------------------------------------------------------------- triagem
function triageResult(pet, a, llmLabel, triageId, createdAt) {
  const details = a.active.map((m) => {
    const f = referenceFrequency(knowledge.symptom(m.id), pet.species);
    return { id: m.id, label: m.label, category: m.category, evidence: m.evidence, reference_frequency: f > 0 ? Math.round(f * 100) / 100 : null };
  });
  const [action, label] = NEXT_ACTION[a.priority.wire];
  const review = a.priority.level >= Priority.ALTA.level || a.confidence < 0.6 || a.vague || a.unrecognized;
  return {
    triage_id: triageId, pet_id: petPublicId(pet.id), symptoms: details.map((d) => d.id), symptom_details: details,
    priority: a.priority.wire, next_action: action, next_action_label: label,
    reason: a.reasons.length === 0 ? "Nenhum sinal de alerta identificado no relato" : a.reasons.slice(0, 2).join("; "),
    reasons: a.reasons, red_flags: a.redFlagIds, context_factors: a.contextFactors, confidence: a.confidence,
    model_version: MODEL_VERSION, engine: { rules: RULES_VERSION, knowledge: knowledge.version, llm: llmLabel },
    human_review_required: review, disclaimer: DISCLAIMER, duration_hours: a.durationHours,
    needs_more_info: a.unrecognized || a.vague || a.confidence < 0.5, created_at: createdAt, shared_with_clinic: false,
  };
}

function recordTriage(db, pet, sessionId, source, inputText, analysis, llmLabel) {
  const id = nextId(db, "triage");
  const createdAt = now();
  const result = triageResult(pet, analysis, llmLabel, triagePublicId(id), createdAt);
  db.triages.push({ id, pet: pet.id, session: sessionId, source, input_text: inputText, result, shared: false, created_at: createdAt });
  const reasons = result.reasons.slice(0, 2).join("; ");
  const symptoms = result.symptom_details.map((d) => d.label).join(", ");
  event(db, pet.id, "triage", `Triagem preliminar: prioridade ${result.priority}`,
    !symptoms ? reasons : !reasons ? symptoms : `${symptoms} — ${reasons}`, source, id);
  return result;
}

function grantConsent(db, user, pet) {
  return activeConsent(db, pet.id) ?? (() => {
    const c = { id: nextId(db, "consent"), pet: pet.id, scope: "triage_and_clinic_sharing", version: "v1-2026-10", recipient: "clinica-zelo-demo", granted_at: now(), revoked_at: null };
    db.consents.push(c);
    event(db, pet.id, "consent", "Compartilhamento com a clínica autorizado", `Escopo: triagens e histórico recente. Versão do termo: ${c.version}`, "tutor", c.id);
    return c;
  })();
}
const consentResponse = (c) => ({ id: c.id, pet_id: petPublicId(c.pet), scope: c.scope, version: c.version, recipient: c.recipient,
  granted_at: c.granted_at, revoked_at: c.revoked_at, active: !c.revoked_at });

function shareTriage(db, triageId) {
  const t = db.triages.find((x) => x.id === triageId);
  if (!t || t.shared) return;
  t.shared = true;
  event(db, t.pet, "share", "Triagem enviada para a clínica", `Prioridade ${t.result.priority}. Aguardando revisão profissional.`, "tutor", t.id);
}

// ----------------------------------------------------------------------------------- chat
const MENU = ["Relatar um sintoma", "Próximos cuidados", "O que você faz?"];

function messageResponse(db, m) {
  let triage = m.triage ?? null;
  if (triage) {
    const stored = db.triages.find((t) => t.result.triage_id === triage.triage_id);
    triage = { ...triage, shared_with_clinic: Boolean(stored?.shared) };
  }
  return { id: m.id, author: m.author, text: m.text, quick_replies: m.quick_replies ?? [], triage, vision: null,
    kind: m.kind ?? null, llm_status: m.llm_status ?? null, created_at: m.created_at };
}

function history(db, session) {
  const messages = db.messages.filter((m) => m.session === session.id).slice(-60);
  return { session_id: session.id, messages: messages.map((m) => messageResponse(db, m)) };
}

function createSession(db, user, pet) {
  const session = { id: nextId(db, "session"), pet: pet.id, user: user.id, state: initialState(), updated_at: now() };
  db.sessions.push(session);
  db.messages.push({ id: nextId(db, "message"), session: session.id, author: "BOT", created_at: now(),
    text: composer.greeting(context(db, pet), careContext(db, pet).highlight), quick_replies: MENU, kind: "SMALLTALK", llm_status: "not_used" });
  return session;
}

async function handleMessage(db, user, body) {
  const pet = ownedPet(db, user, body.pet_id);
  const text = String(body.text || "").trim();
  if (!text) throw new ApiError(400, "Escreva uma mensagem.", { text: "obrigatório" });
  if (text.length > 1000) throw new ApiError(400, "Mensagem muito longa (máximo de 1000 caracteres).");
  const session = body.session_id == null ? createSession(db, user, pet)
    : db.sessions.find((s) => s.id === Number(body.session_id) && s.user === user.id && s.pet === pet.id);
  if (!session) throw new ApiError(404, "Conversa não encontrada");
  db.messages.push({ id: nextId(db, "message"), session: session.id, author: "USER", text, created_at: now() });

  const ctx = context(db, pet);
  const care = careContext(db, pet);
  const status = await llm();
  let extractionUsed = false;
  const assist = status.enabled ? async (t, current) => {
    const allowed = Object.fromEntries(knowledge.symptoms.map((s) => [s.id, s.label]));
    const answer = await llmCall({ task: "extract", text: t, species: pet.species, allowed });
    const known = new Set(current.active.map((m) => m.id));
    const extra = (answer?.symptoms ?? []).filter((id) => !known.has(id) && knowledge.knows(id)).map((id) => engine.aiMention(id, pet.species));
    if (extra.length > 0) extractionUsed = true;
    return extra;
  } : null;

  const turn = await flow.next(session.state, text, ctx, care, assist);
  const polished = await polish(turn, text, pet, status, extractionUsed);

  let triage = null;
  let triageId = turn.nextState.lastTriageId;
  if (turn.triage) {
    triage = recordTriage(db, pet, session.id, "chat", turn.reportText ?? text, turn.triage, polished.llmUsed ? status.model : LOCAL_LLM);
    triageId = parseId(triage.triage_id, "TRI");
  }
  let sharedTriageId = null;
  if (turn.share === "GRANT" && triageId != null) {
    grantConsent(db, user, pet);
    shareTriage(db, triageId);
    sharedTriageId = triagePublicId(triageId);
  }
  session.state = { ...turn.nextState, lastTriageId: triageId };
  session.updated_at = now();
  const bot = { id: nextId(db, "message"), session: session.id, author: "BOT", text: polished.text, quick_replies: turn.quickReplies,
    triage, kind: turn.kind, llm_status: polished.status, created_at: now() };
  db.messages.push(bot);
  return { session_id: session.id, reply: messageResponse(db, bot), stage: turn.nextState.stage, shared_triage_id: sharedTriageId };
}

/** Reescrita só para triagens baixa/média; o guard roda na função /api/llm e qualquer falha mantém o rascunho. */
async function polish(turn, userText, pet, status, extractionUsed) {
  if (!status.enabled) return { text: turn.text, status: "disabled", llmUsed: false };
  if (!turn.draft || !turn.triage) return { text: turn.text, status: extractionUsed ? "extraction_only" : "not_used", llmUsed: extractionUsed };
  if (turn.triage.priority.level >= Priority.ALTA.level) return { text: turn.text, status: "deterministic_high_priority", llmUsed: extractionUsed };
  const answer = await llmCall({ task: "rewrite", text: userText, pet_name: pet.name, species: pet.species, priority: turn.triage.priority.wire, draft: turn.text });
  if (!answer) return { text: turn.text, status: "error_fallback", llmUsed: extractionUsed };
  const keepsRefusal = !turn.text.includes(MEDICATION_REFUSAL_MARK) || /remedio|medicament/.test(normalize(answer.text || ""));
  if (!answer.accepted || !keepsRefusal) return { text: turn.text, status: "rejected_fallback", llmUsed: extractionUsed };
  return { text: answer.text, status: "used", llmUsed: true };
}

// -------------------------------------------------------------------------------- clínica
function clinicVisible(db) {
  return db.triages.filter((t) => t.shared && activeConsent(db, t.pet));
}
function clinicSummary(db, t) {
  const pet = db.pets.find((p) => p.id === t.pet);
  const owner = db.users.find((u) => u.id === pet.owner);
  return { triage_id: t.result.triage_id, pet_id: petPublicId(pet.id), pet_name: pet.name, species: pet.species, pet_age_months: ageMonths(pet.birth_date),
    tutor_name: owner?.name ?? "", priority: t.result.priority, symptoms: t.result.symptoms, symptom_labels: t.result.symptom_details.map((d) => d.label),
    human_review_required: t.result.human_review_required, reviewed: db.reviews.some((r) => r.triage === t.id), created_at: t.created_at };
}
const reviewResponse = (db, r) => ({ id: r.id, triage_id: triagePublicId(r.triage), reviewer: db.users.find((u) => u.id === r.reviewer)?.name ?? "",
  decision: r.decision, final_priority: r.final_priority, note: r.note, created_at: r.created_at });

// --------------------------------------------------------------------------------- rotas
const sha256 = async (text) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)))]
  .map((b) => b.toString(16).padStart(2, "0")).join("");

async function route(method, url, body) {
  await ready;
  const db = load();
  const { pathname, searchParams } = new URL(url, location.origin);
  const parts = pathname.split("/").filter(Boolean).slice(1); // sem o "api"
  const key = `${method} /${parts.map((p, i) => (i > 0 && /^(PET|TRI)-\d+$|^\d+$/.test(p) ? ":id" : p)).join("/")}`;
  const id = parts.find((p, i) => i > 0 && /^(PET|TRI)-\d+$|^\d+$/.test(p));

  switch (key) {
    case "POST /auth/login": {
      const email = String(body.get("email") || "").trim().toLowerCase();
      const user = db.users.find((u) => u.email === email);
      const ok = user && (user.demo || user.password === await sha256(String(body.get("password") || "")));
      if (!ok) throw new ApiError(401, "E-mail ou senha inválidos.");
      db.session = user.id;
      return { id: user.id, name: user.name, email: user.email, role: user.role };
    }
    case "POST /auth/logout":
      db.session = null;
      return null;
    case "POST /auth/register": {
      const email = String(body.email || "").trim().toLowerCase();
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) || !body.name || String(body.password || "").length < 8) {
        throw new ApiError(400, "Dados inválidos.", { email: "Confira nome, e-mail e senha (mínimo de 8 caracteres)." });
      }
      if (db.users.some((u) => u.email === email)) throw new ApiError(409, "Já existe uma conta com esse e-mail.");
      const user = { id: nextId(db, "user"), email, name: String(body.name).trim(), role: "TUTOR", password: await sha256(body.password) };
      db.users.push(user);
      return { id: user.id, name: user.name, email: user.email, role: user.role };
    }
    case "GET /auth/me": {
      const user = me(db);
      return { id: user.id, name: user.name, email: user.email, role: user.role };
    }
    case "GET /system/status": {
      me(db);
      const status = await llm();
      return { knowledge_version: knowledge.version, rules_version: RULES_VERSION, llm: status.enabled ? status.model : LOCAL_LLM,
        llm_enabled: Boolean(status.enabled), vision_available: false, vision_model: null };
    }
    case "GET /pets": {
      const user = requireRole(db, "TUTOR");
      return db.pets.filter((p) => p.owner === user.id).map((p) => petResponse(db, p));
    }
    case "GET /pets/catalog": {
      requireRole(db, "TUTOR");
      const opt = (list) => (list ?? []).map((b) => ({ id: b.id, label: b.label }));
      return { dog_breeds: opt(knowledge.breeds.dog), cat_breeds: opt(knowledge.breeds.cat), history_tags: knowledge.historyTags.map((t) => ({ id: t.id, label: t.label })) };
    }
    case "POST /pets": {
      const user = requireRole(db, "TUTOR");
      if (!body.name || !["dog", "cat"].includes(body.species)) throw new ApiError(400, "Dados do pet inválidos.", { name: "Informe nome e espécie." });
      const pet = { id: nextId(db, "pet"), owner: user.id, name: String(body.name).trim().slice(0, 80), species: body.species, breed: body.breed || null,
        female: Boolean(body.female), birth_date: body.birth_date || null, weight_kg: body.weight_kg ?? null, history_tags: body.history_tags ?? [] };
      db.pets.push(pet);
      return petResponse(db, pet);
    }
    case "GET /pets/:id":
      return petResponse(db, ownedPet(db, requireRole(db, "TUTOR"), id));
    case "GET /pets/:id/care-plan": {
      const pet = ownedPet(db, requireRole(db, "TUTOR"), id);
      return careItems(db, pet).map((i) => {
        const d = daysUntil(i.next_due);
        return { id: i.id, type: i.type, name: i.name, last_done: i.last_done, next_due: i.next_due, status: d < 0 ? "OVERDUE" : d <= 14 ? "DUE_SOON" : "OK", days_until_due: d };
      });
    }
    case "GET /pets/:id/timeline": {
      const pet = ownedPet(db, requireRole(db, "TUTOR"), id);
      return timeline(db, pet.id, Math.min(Number(searchParams.get("limit") || 20), 50));
    }
    case "GET /sharing/consents": {
      const pet = ownedPet(db, requireRole(db, "TUTOR"), searchParams.get("pet_id"));
      return db.consents.filter((c) => c.pet === pet.id).map(consentResponse);
    }
    case "POST /sharing/consents": {
      const user = requireRole(db, "TUTOR");
      return consentResponse(grantConsent(db, user, ownedPet(db, user, body.pet_id)));
    }
    case "DELETE /sharing/consents/:id": {
      const user = requireRole(db, "TUTOR");
      const consent = db.consents.find((c) => c.id === Number(id) && db.pets.some((p) => p.id === c.pet && p.owner === user.id));
      if (!consent) throw new ApiError(404, "Consentimento não encontrado");
      if (!consent.revoked_at) {
        consent.revoked_at = now();
        db.triages.filter((t) => t.pet === consent.pet).forEach((t) => { t.shared = false; }); // revogar desfaz o compartilhamento
        event(db, consent.pet, "consent", "Compartilhamento com a clínica revogado", "A clínica deixa de ver as triagens deste pet.", "tutor", consent.id);
      }
      return null;
    }
    case "POST /chat/sessions": {
      const user = requireRole(db, "TUTOR");
      const pet = ownedPet(db, user, body.pet_id);
      const existing = body.fresh ? null : db.sessions.filter((s) => s.pet === pet.id && s.user === user.id).sort((a, b) => b.updated_at.localeCompare(a.updated_at))[0];
      return history(db, existing ?? createSession(db, user, pet));
    }
    case "POST /chat/messages":
      return handleMessage(db, requireRole(db, "TUTOR"), body);
    case "GET /chat/sessions/:id/messages": {
      const user = requireRole(db, "TUTOR");
      const session = db.sessions.find((s) => s.id === Number(id) && s.user === user.id);
      if (!session) throw new ApiError(404, "Conversa não encontrada");
      return history(db, session);
    }
    case "POST /vision/analyze":
      requireRole(db, "TUTOR");
      throw new ApiError(503, "A análise de foto está indisponível nesta versão online (o classificador roda no serviço Python do projeto). A conversa segue funcionando.");
    case "GET /clinic/triages": {
      requireRole(db, "CLINICA");
      return clinicVisible(db).map((t) => clinicSummary(db, t))
        .sort((a, b) => priorityFromWire(b.priority).level - priorityFromWire(a.priority).level || b.created_at.localeCompare(a.created_at));
    }
    case "GET /clinic/triages/:id": {
      requireRole(db, "CLINICA");
      const t = clinicVisible(db).find((x) => x.id === parseId(id, "TRI"));
      if (!t) throw new ApiError(404, "Triagem não encontrada");
      const pet = db.pets.find((p) => p.id === t.pet);
      return { summary: clinicSummary(db, t), triage: { ...t.result, shared_with_clinic: true }, pet: petResponse(db, pet), input_text: t.input_text,
        timeline: timeline(db, pet.id, 15, true), reviews: db.reviews.filter((r) => r.triage === t.id).map((r) => reviewResponse(db, r)) };
    }
    case "POST /clinic/reviews": {
      const vet = requireRole(db, "CLINICA");
      const t = clinicVisible(db).find((x) => x.id === parseId(body.triage_id, "TRI"));
      if (!t) throw new ApiError(404, "Triagem não encontrada");
      const final = priorityFromWire(body.final_priority);
      if (!final || !["CONFIRMADA", "AJUSTADA"].includes(body.decision)) throw new ApiError(400, "Revisão inválida.");
      const review = { id: nextId(db, "review"), triage: t.id, reviewer: vet.id, decision: body.decision, final_priority: final.wire,
        note: body.note ? String(body.note).trim().slice(0, 1000) : null, created_at: now() };
      db.reviews.push(review);
      event(db, t.pet, "review", body.decision === "CONFIRMADA" ? `Clínica confirmou a prioridade ${final.wire}` : `Clínica ajustou a prioridade para ${final.wire}`,
        review.note ?? "Revisão registrada pela clínica.", "clinic", t.id);
      return reviewResponse(db, review);
    }
    default:
      throw new ApiError(404, "Rota não encontrada.");
  }
}

async function request(method, url, body) {
  try {
    const result = await route(method, url, body);
    save();
    return result;
  } catch (error) {
    save();
    if (error instanceof ApiError) throw error;
    throw new ApiError(500, "Não foi possível concluir a operação.");
  }
}

export const api = {
  get: (url) => request("GET", url),
  post: (url, json) => request("POST", url, json ?? {}),
  del: (url) => request("DELETE", url),
  upload: (url) => request("POST", url, {}),
  login: (email, password) => request("POST", "/api/auth/login", new URLSearchParams({ email, password })),
  logout: () => request("POST", "/api/auth/logout"),
};

/** Sem servidor, não há cookie CSRF a garantir. */
export async function ensureCsrf() {}
