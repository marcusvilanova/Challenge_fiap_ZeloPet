import { api, ApiError, ensureCsrf } from "./api.js";
import { renderTriageCard, renderVisionCard, markCardShared } from "./triage-card.js";
import { clear, el, eventTitle, formatAge, formatDateTime, formatDay, icon, initials, prefs, put, speciesLabel, toast } from "./ui.js";

const $ = (id) => document.getElementById(id);
const LAST_PET_KEY = "zelo.lastPet";

/** Estado único da página; cada atualização troca valores, não mutações espalhadas. */
const state = {
  me: null,
  pets: [],
  pet: null,
  sessionId: null,
  catalog: null,
  consent: null,
  busy: false,
};

// ---------------------------------------------------------------------------- inicialização
async function init() {
  $("brand-mark").append(icon("paw", 18));
  $("attach").append(icon("camera", 20));
  $("send").append(icon("send", 20));
  wireEvents();

  await ensureCsrf();
  try {
    state.me = await api.get("/api/auth/me");
  } catch {
    location.replace("/index.html");
    return;
  }
  if (state.me.role !== "TUTOR") {
    location.replace("/index.html?acesso=negado");
    return;
  }
  $("user-name").textContent = state.me.name;

  const [status, pets, catalog] = await Promise.all([
    api.get("/api/system/status").catch(() => null),
    api.get("/api/pets"),
    api.get("/api/pets/catalog"),
  ]);
  state.catalog = catalog;
  renderStatus(status);
  state.pets = pets;
  renderPetSwitch();

  if (pets.length === 0) {
    openPetDialog();
    return;
  }
  const remembered = prefs.get(LAST_PET_KEY);
  await selectPet(pets.find((p) => p.pet_id === remembered)?.pet_id ?? pets[0].pet_id);
}

function wireEvents() {
  $("composer").addEventListener("submit", (event) => {
    event.preventDefault();
    sendFromComposer();
  });
  $("message").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      sendFromComposer();
    }
  });
  $("message").addEventListener("input", autoGrow);
  $("attach").addEventListener("click", () => $("photo").click());
  $("photo").addEventListener("change", onPhotoChosen);
  $("new-chat").addEventListener("click", () => openConversation(true));
  $("logout").addEventListener("click", async () => {
    await api.logout().catch(() => undefined);
    location.assign("/index.html");
  });
  $("consent-toggle").addEventListener("click", toggleConsent);
  document.querySelectorAll(".tabbar button").forEach((button) =>
    button.addEventListener("click", () => {
      document.body.dataset.tab = button.dataset.tab;
      document.querySelectorAll(".tabbar button").forEach((b) => b.removeAttribute("aria-current"));
      button.setAttribute("aria-current", "page");
    }),
  );
  wirePetDialog();
}

function autoGrow() {
  const box = $("message");
  box.style.height = "auto";
  box.style.height = `${Math.min(box.scrollHeight, 140)}px`;
}

// --------------------------------------------------------------------------------- status
function renderStatus(status) {
  const holder = $("status");
  clear(holder);
  $("llm-notice").hidden = !status?.llm_enabled; // transparência (LGPD): avisa quando o texto sai para um provedor externo
  if (!status) return;
  holder.append(
    el("span", { class: `badge ${status.llm_enabled ? "badge--ok" : "badge--warn"}`, title: "Camada de linguagem natural" }, el("i", { class: "dot" }),
      status.llm_enabled ? `IA generativa: ${status.llm}` : "Motor local (sem LLM)"),
    el("span", { class: `badge ${status.vision_available ? "badge--ok" : "badge--off"}`, title: "Classificador de pele (cães)" }, el("i", { class: "dot" }),
      status.vision_available ? "Visão ativa" : "Visão offline"),
  );
}

// ----------------------------------------------------------------------------------- pets
function renderPetSwitch() {
  const nav = $("pet-switch");
  clear(nav);
  for (const pet of state.pets) {
    nav.append(
      el("button", { class: "pet-tab", type: "button", "aria-pressed": String(pet.pet_id === state.pet?.pet_id), onclick: () => selectPet(pet.pet_id) },
        el("span", { class: "pet-tab__avatar", text: initials(pet.name) }), el("span", { text: pet.name })),
    );
  }
  nav.append(el("button", { class: "pet-tab", type: "button", "aria-label": "Cadastrar novo pet", onclick: openPetDialog }, el("span", { class: "pet-tab__avatar" }, icon("plus", 16)), el("span", { text: "Novo" })));
}

async function selectPet(petId) {
  state.pet = state.pets.find((p) => p.pet_id === petId);
  prefs.set(LAST_PET_KEY, petId);
  renderPetSwitch();
  renderPetCard(null);
  $("chat-title").textContent = `Conversa sobre ${state.pet.name}`;
  await Promise.all([loadCare(), loadTimeline(), loadConsent(), openConversation(false)]);
}

function renderPetCard(priority) {
  const pet = state.pet;
  const holder = $("pet-card");
  clear(holder);
  const avatar = el("div", { class: "pet-avatar", "aria-hidden": "true", text: initials(pet.name) });
  if (priority) avatar.dataset.priority = priority;
  const details = [
    pet.breed === "Mixed Breed" ? "SRD" : pet.breed,
    formatAge(pet.age_months),
    pet.weight_kg ? `${pet.weight_kg} kg` : null,
  ].filter(Boolean);
  put(holder,
    avatar,
    el("h2", { text: pet.name }),
    el("p", { class: "pet-card__meta", text: `${speciesLabel(pet.species)} · ${pet.female ? "fêmea" : "macho"}` }),
    el("p", { class: "pet-card__meta", text: details.join(" · ") }),
    pet.history_tags.length || pet.brachycephalic
      ? el("div", { class: "chips" },
          pet.brachycephalic ? el("span", { class: "chip chip--static", text: "focinho curto" }) : null,
          pet.history_tags.map((id) => el("span", { class: "chip chip--static", text: historyLabel(id) })))
      : null,
  );
}

function historyLabel(id) {
  return state.catalog?.history_tags.find((t) => t.id === id)?.label ?? id;
}

// -------------------------------------------------------------------- cuidados e histórico
async function loadCare() {
  const items = await api.get(`/api/pets/${state.pet.pet_id}/care-plan`).catch(() => []);
  const list = $("care-list");
  clear(list);
  if (items.length === 0) {
    list.append(el("li", { class: "hint", text: "Nenhum cuidado cadastrado ainda." }));
    return;
  }
  const label = { OVERDUE: ["Vencido", "badge--warn"], DUE_SOON: ["Em breve", "badge--warn"], OK: ["Em dia", "badge--ok"] };
  for (const item of items) {
    const [text, tone] = label[item.status];
    const when = item.days_until_due < 0 ? `venceu há ${-item.days_until_due} d` : item.days_until_due === 0 ? "vence hoje" : `em ${item.days_until_due} d`;
    list.append(
      el("li", { class: `care-item${item.status === "OVERDUE" ? " care-item--overdue" : ""}` },
        el("strong", { text: item.name }), el("span", { class: `badge ${tone}`, text: text }),
        el("span", { text: `${formatDay(item.next_due)} · ${when}` })),
    );
  }
}

async function loadTimeline() {
  const events = await api.get(`/api/pets/${state.pet.pet_id}/timeline?limit=20`).catch(() => []);
  const list = $("timeline");
  clear(list);
  if (events.length === 0) {
    list.append(el("li", { class: "hint", text: "Os eventos do pet aparecem aqui." }));
    return;
  }
  for (const event of events) {
    list.append(
      el("li", { class: "timeline__item", "data-type": event.type },
        el("time", { datetime: event.occurred_at, text: formatDateTime(event.occurred_at) }),
        el("strong", { text: eventTitle(event.title) }),
        event.summary ? el("p", { text: event.summary }) : null),
    );
  }
  const latestTriage = events.find((e) => e.type === "triage");
  const match = latestTriage?.title.match(/prioridade (\w+)/);
  renderPetCard(match ? match[1] : null);
}

// ---------------------------------------------------------------------------- consentimento
async function loadConsent() {
  const consents = await api.get(`/api/sharing/consents?pet_id=${state.pet.pet_id}`).catch(() => []);
  state.consent = consents.find((c) => c.active) ?? null;
  renderConsent();
}

function renderConsent() {
  const active = Boolean(state.consent);
  $("consent-text").textContent = active
    ? `Ativo desde ${formatDay(state.consent.granted_at.slice(0, 10))}. A clínica vê apenas as triagens que você enviar. Você pode revogar quando quiser.`
    : "Desativado. Nada é enviado à clínica sem a sua autorização.";
  const button = $("consent-toggle");
  button.textContent = active ? "Revogar compartilhamento" : "Autorizar compartilhamento";
}

async function toggleConsent() {
  try {
    if (state.consent) {
      await api.del(`/api/sharing/consents/${state.consent.id}`);
      toast("Compartilhamento revogado. A clínica deixou de ver as triagens.");
    } else {
      await api.post("/api/sharing/consents", { pet_id: state.pet.pet_id });
      toast("Compartilhamento autorizado.");
    }
    await Promise.all([loadConsent(), loadTimeline()]);
  } catch (error) {
    toast(error.message, "error");
  }
}

// ---------------------------------------------------------------------------------- conversa
async function openConversation(fresh) {
  const requestedFor = state.pet;
  try {
    const history = await api.post("/api/chat/sessions", { pet_id: requestedFor.pet_id, fresh });
    if (state.pet !== requestedFor) return; // o tutor já trocou de pet: descarta a resposta atrasada
    state.sessionId = history.session_id;
    renderHistory(history.messages);
  } catch (error) {
    toast(error.message, "error");
  }
}

function renderHistory(messages) {
  const log = $("log");
  clear(log);
  messages.forEach((message) => log.append(renderMessage(message)));
  const last = messages[messages.length - 1];
  renderQuick(last?.author === "BOT" ? last.quick_replies : []);
  scrollToEnd();
}

function renderMessage(message) {
  const isBot = message.author === "BOT";
  const body = el("div", { class: "msg__body" }, el("div", { class: "msg__bubble" }, paragraphs(message.text)));
  if (message.triage) body.append(renderTriageCard(message.triage));
  if (message.vision) body.append(renderVisionCard(message.vision));
  body.append(el("p", { class: "msg__time", text: formatDateTime(message.created_at) }));
  return el("div", { class: `msg ${isBot ? "msg--bot" : "msg--user"}`, "data-id": message.id },
    isBot ? el("span", { class: "msg__avatar", "aria-hidden": "true" }, icon("paw", 18)) : null, body);
}

/** Texto do bot → parágrafos (linha em branco) e quebras de linha; sempre como texto, nunca HTML. */
function paragraphs(text) {
  return text.split("\n\n").map((block) => {
    const lines = block.split("\n");
    const p = el("p");
    lines.forEach((line, i) => {
      if (i > 0) p.append(document.createElement("br"));
      p.append(document.createTextNode(line));
    });
    return p;
  });
}

function renderQuick(replies) {
  const box = $("quick");
  clear(box);
  for (const text of replies) {
    box.append(el("button", { class: "chip", type: "button", text, onclick: () => send(text) }));
  }
}

function scrollToEnd() {
  const log = $("log");
  log.scrollTop = log.scrollHeight;
}

function showTyping() {
  const node = el("div", { class: "msg msg--bot", id: "typing" },
    el("span", { class: "msg__avatar", "aria-hidden": "true" }, icon("paw", 18)),
    el("div", { class: "msg__body" }, el("div", { class: "msg__bubble", role: "status", "aria-label": "A Zelo está digitando" },
      el("span", { class: "typing" }, el("i"), el("i"), el("i")))));
  $("log").append(node);
  scrollToEnd();
}

function hideTyping() {
  document.getElementById("typing")?.remove();
}

function sendFromComposer() {
  const text = $("message").value.trim();
  if (!text || state.busy || !state.pet) return; // não apaga o que foi digitado se o envio não vai acontecer
  $("message").value = "";
  autoGrow();
  send(text);
}

async function send(text) {
  if (state.busy || !state.pet) return;
  state.busy = true;
  setComposerDisabled(true);
  renderQuick([]);
  $("log").append(renderMessage({ author: "USER", text, created_at: new Date().toISOString() }));
  showTyping();
  try {
    const reply = await api.post("/api/chat/messages", { pet_id: state.pet.pet_id, session_id: state.sessionId, text });
    state.sessionId = reply.session_id;
    hideTyping();
    $("log").append(renderMessage(reply.reply));
    renderQuick(reply.reply.quick_replies);
    if (reply.shared_triage_id) {
      document.querySelectorAll(`[data-triage-id="${reply.shared_triage_id}"]`).forEach(markCardShared);
    }
    await Promise.all([loadTimeline(), loadConsent()]);
  } catch (error) {
    hideTyping();
    $("log").append(renderMessage({ author: "BOT", text: friendlyError(error), created_at: new Date().toISOString() }));
    toast(error.message, "error");
  } finally {
    state.busy = false;
    setComposerDisabled(false);
    scrollToEnd();
    $("message").focus();
  }
}

function friendlyError(error) {
  if (error instanceof ApiError && error.status === 429) return "Você enviou muitas mensagens em pouco tempo. Aguarde um minuto e tente de novo.";
  if (error instanceof ApiError && error.status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não consegui responder agora. Tente novamente em instantes. Se for urgente, procure um pronto-atendimento veterinário.";
}

function setComposerDisabled(disabled) {
  // trocar de pet no meio de uma resposta misturaria a conversa de um pet com a sessão de outro
  document.querySelectorAll(".pet-tab").forEach((tab) => { tab.disabled = disabled; });
  $("send").disabled = disabled;
  $("attach").disabled = disabled;
  $("message").disabled = disabled;
}

// -------------------------------------------------------------------------------------- foto
async function onPhotoChosen(event) {
  const file = event.target.files[0];
  event.target.value = "";
  if (!file || state.busy) return;
  if (file.size > 5 * 1024 * 1024) {
    toast("A imagem excede o limite de 5 MB.", "error");
    return;
  }
  state.busy = true;
  setComposerDisabled(true);
  $("log").append(renderMessage({ author: "USER", text: "Enviei uma foto para análise.", created_at: new Date().toISOString() }));
  showTyping();
  try {
    const form = new FormData();
    form.append("file", file);
    form.append("pet_id", state.pet.pet_id);
    form.append("session_id", String(state.sessionId));
    await api.upload("/api/vision/analyze", form);
    const history = await api.get(`/api/chat/sessions/${state.sessionId}/messages`);
    renderHistory(history.messages);
    await loadTimeline();
  } catch (error) {
    hideTyping();
    toast(error.message, "error");
    $("log").append(renderMessage({ author: "BOT", text: error.message, created_at: new Date().toISOString() }));
  } finally {
    state.busy = false;
    setComposerDisabled(false);
    scrollToEnd();
  }
}

// ------------------------------------------------------------------------- cadastro do pet
function wirePetDialog() {
  const dialog = $("pet-dialog");
  $("pet-species").addEventListener("change", fillBreeds);
  $("pet-cancel").addEventListener("click", () => dialog.close());
  $("pet-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await submitPet();
  });
}

function openPetDialog() {
  $("pet-error").hidden = true;
  fillBreeds();
  const holder = $("pet-history");
  clear(holder);
  for (const tag of state.catalog.history_tags) {
    holder.append(el("label", { class: "check" }, el("input", { type: "checkbox", value: tag.id }), tag.label));
  }
  $("pet-birth").max = new Date().toISOString().slice(0, 10);
  $("pet-dialog").showModal();
  $("pet-name").focus();
}

function fillBreeds() {
  const species = $("pet-species").value;
  const breeds = species === "cat" ? state.catalog.cat_breeds : state.catalog.dog_breeds;
  const select = $("pet-breed");
  clear(select);
  select.append(el("option", { value: "", text: "Não sei / outra" }));
  breeds.forEach((b) => select.append(el("option", { value: b.id, text: b.label })));
}

let petSubmitting = false;

async function submitPet() {
  if (petSubmitting) return; // duplo clique / Enter repetido não cadastra o pet duas vezes
  const name = $("pet-name").value.trim();
  if (!name) {
    showPetError("Informe o nome do pet.");
    return;
  }
  petSubmitting = true;
  const weight = $("pet-weight").value;
  const payload = {
    name,
    species: $("pet-species").value,
    breed: $("pet-breed").value || null,
    female: $("pet-sex").value === "F",
    birth_date: $("pet-birth").value || null,
    weight_kg: weight ? Number(weight) : null,
    history_tags: [...document.querySelectorAll("#pet-history input:checked")].map((i) => i.value),
  };
  try {
    const pet = await api.post("/api/pets", payload);
    $("pet-dialog").close();
    $("pet-form").reset();
    state.pets = await api.get("/api/pets");
    await selectPet(pet.pet_id);
    toast(`${pet.name} foi cadastrado.`);
  } catch (error) {
    const details = error instanceof ApiError && error.errors ? Object.values(error.errors).join(" ") : "";
    showPetError(details || error.message);
  } finally {
    petSubmitting = false;
  }
}

function showPetError(message) {
  const node = $("pet-error");
  node.textContent = message;
  node.hidden = false;
}

init();
