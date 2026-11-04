// Painel do Tutor (IBM Carbon): perfil, histórico, plano de cuidados e resumo de saúde, com os dados reais da API.
// Funciona igual no backend Java e na versão online (Vercel), porque usa o mesmo cliente ./api.js.
import { api, ApiError, ensureCsrf } from "./api.js";
import { clear, el, formatAge, formatDateTime, formatDay, initials, PRIORITY_LABEL, PRIORITY_LEVEL, prefs, put, speciesLabel, toast } from "./ui.js";

const $ = (id) => document.getElementById(id);
const LAST_PET_KEY = "zelo.lastPet"; // o mesmo da conversa: o pet escolhido aqui abre direto no chat
const SVG_NS = "http://www.w3.org/2000/svg";
/** Fotos das contas de demonstração; os demais pets aparecem com a inicial do nome. */
const DEMO_PHOTOS = { "mel:dog": "/pet_mel.png", "thor:cat": "/pet_thor.png" };
const CARE_TYPE_LABEL = { VACINA: "Vacina", VERMIFUGO: "Vermífugo", ANTIPULGAS: "Antipulgas", CONSULTA: "Consulta" };
const EVENT_LABEL = {
  triage: "Triagem", review: "Revisão da clínica", consent: "Consentimento", share: "Envio à clínica", vision: "Análise de foto",
  visit: "Consulta", vaccine: "Vacina", medication: "Medicação / preventivo", note: "Anotação", behavior: "Comportamento",
};
const SOURCE_LABEL = { tutor: "informado pelo tutor", clinic: "registrado pela clínica", chat: "conversa com a Zelo", api: "triagem avulsa", vision: "análise de foto" };

const state = {
  me: null,
  pets: [],
  catalog: null,
  data: new Map(), // pet_id → { care, timeline }
  petId: null,
  manageCare: false,
  editingPetId: null, // null = cadastro de pet novo
  busy: false,
};

// ---------------------------------------------------------------------------- inicialização
async function init() {
  wireStaticEvents();
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
  renderUser();
  try {
    const [pets, catalog] = await Promise.all([api.get("/api/pets"), api.get("/api/pets/catalog")]);
    state.pets = pets;
    state.catalog = catalog;
    await loadAllPetData();
  } catch (error) {
    showLoadError(error);
    return;
  }
  const remembered = prefs.get(LAST_PET_KEY);
  state.petId = state.pets.find((p) => p.pet_id === remembered)?.pet_id ?? state.pets[0]?.pet_id ?? null;
  renderAll();
  const section = location.hash.replace("#", "");
  if (section) scrollToSection(section);
}

async function loadAllPetData() {
  const entries = await Promise.all(state.pets.map(async (pet) => {
    const [care, timeline] = await Promise.all([
      api.get(`/api/pets/${pet.pet_id}/care-plan`),
      api.get(`/api/pets/${pet.pet_id}/timeline?limit=50`),
    ]);
    return [pet.pet_id, { care, timeline }];
  }));
  state.data = new Map(entries);
}

async function reloadPet(petId) {
  const [pets, care, timeline] = await Promise.all([
    api.get("/api/pets"),
    api.get(`/api/pets/${petId}/care-plan`),
    api.get(`/api/pets/${petId}/timeline?limit=50`),
  ]);
  state.pets = pets;
  state.data.set(petId, { care, timeline });
  renderAll();
}

function showLoadError(error) {
  const holder = $("pet-content");
  clear(holder);
  holder.append(el("div", { class: "empty-state" },
    el("h2", { text: "Não foi possível carregar o painel" }),
    el("p", { text: error instanceof ApiError ? error.message : "Tente recarregar a página." }),
    el("button", { class: "cds-btn cds-btn--primary", type: "button", text: "Tentar de novo", onclick: () => location.reload() })));
}

function wireStaticEvents() {
  $("logout").addEventListener("click", () => logoutTo("/index.html"));
  $("action-switch").addEventListener("click", () => logoutTo("/index.html"));
  $("nav-clinic").addEventListener("click", () => logoutTo("/index.html?demo=vet%40zelo.demo"));
  $("action-triage").addEventListener("click", () => startTriage(state.petId));
  $("action-new-pet").addEventListener("click", () => openPetDialog(null));
  $("notif-close").addEventListener("click", () => {
    $("main-notif").hidden = true;
    try { sessionStorage.setItem("zelo.dashboard.notif", $("notif-title").textContent); } catch { /* sem armazenamento */ }
  });
  document.querySelectorAll("[data-section]").forEach((button) =>
    button.addEventListener("click", () => scrollToSection(button.dataset.section)));
  wirePetDialog();
  wireCareDialog();
  window.addEventListener("afterprint", () => { document.title = "Zelo — Painel do Tutor"; });
}

async function logoutTo(url) {
  await api.logout().catch(() => undefined);
  location.assign(url);
}

function startTriage(petId) {
  if (petId) prefs.set(LAST_PET_KEY, petId);
  location.assign("/chat.html");
}

// ------------------------------------------------------------------------------------ render
function renderAll() {
  renderTutor();
  renderTabs();
  renderPet();
  renderNotification();
}

function renderUser() {
  $("shell-avatar").textContent = initials(state.me.name);
  $("shell-user-name").textContent = state.me.name;
  $("tutor-avatar").textContent = initials(state.me.name);
  $("tutor-name").textContent = state.me.name;
}

function allEvents() {
  return [...state.data.values()].flatMap((d) => d.timeline);
}

function renderTutor() {
  const data = $("tutor-data");
  clear(data);
  const sharing = state.pets.filter((p) => p.sharing_enabled).length;
  const rows = [
    ["E-mail", state.me.email],
    ["ID do tutor", `TUT-${String(state.me.id).padStart(5, "0")}`],
    ["Compartilhando", `${sharing} de ${state.pets.length} ${state.pets.length === 1 ? "pet" : "pets"}`],
  ];
  for (const [key, value] of rows) {
    data.append(el("div", { class: "tutor-data-item" }, el("dt", { class: "tutor-data-key", text: key }), el("dd", { class: "tutor-data-val", text: value })));
  }
  const events = allEvents();
  $("stat-triages").textContent = String(events.filter((e) => e.type === "triage").length);
  $("stat-pets").textContent = String(state.pets.length);
  $("stat-reviews").textContent = String(events.filter((e) => e.type === "review").length);
}

function renderTabs() {
  const bar = $("pet-tabs");
  clear(bar);
  for (const pet of state.pets) {
    const status = petStatus(pet);
    const active = pet.pet_id === state.petId;
    bar.append(el("button", {
      class: `pet-tab-btn${active ? " active" : ""}`, type: "button", role: "tab", "aria-selected": String(active),
      "data-pet-id": pet.pet_id, onclick: () => selectPet(pet.pet_id),
    }, petAvatar(pet, "pet-tab-avatar"), pet.name, el("span", { class: `tab-tag ${status.tabClass}`, text: status.tab })));
  }
  bar.append(el("button", { class: "pet-tab-btn pet-tab-btn--add", type: "button", onclick: () => openPetDialog(null) }, "+ Novo pet"));
}

function selectPet(petId) {
  state.petId = petId;
  state.manageCare = false;
  prefs.set(LAST_PET_KEY, petId);
  renderTabs();
  renderPet();
}

function currentPet() {
  return state.pets.find((p) => p.pet_id === state.petId) ?? null;
}

function petAvatar(pet, className) {
  const photo = DEMO_PHOTOS[`${pet.name.toLowerCase()}:${pet.species}`];
  if (photo) {
    const img = el("img", { src: photo, alt: "", class: className });
    img.addEventListener("error", () => img.replaceWith(el("span", { class: `${className} avatar-fallback`, "aria-hidden": "true", text: initials(pet.name) })));
    return img;
  }
  return el("span", { class: `${className} avatar-fallback`, "aria-hidden": "true", text: initials(pet.name) });
}

/** Prioridade técnica a partir do título do evento ("... prioridade alta", "... para emergencia"). */
function priorityOf(event) {
  const match = event.title.match(/(baixa|media|alta|emergencia)\s*$/);
  return match ? match[1] : null;
}

/** Quais triagens a clínica revisou e quais ainda estão com ela (revogar o consentimento desfaz o envio). */
function triageState(timeline) {
  const reviewed = new Set(timeline.filter((e) => e.type === "review").map((e) => String(e.ref_id)));
  const shared = new Set();
  for (const event of [...timeline].reverse()) { // do mais antigo para o mais recente
    if (event.type === "share") shared.add(String(event.ref_id));
    else if (event.type === "consent" && /revogado/i.test(event.title)) shared.clear();
  }
  return { reviewed, shared };
}

function petStatus(pet) {
  const { care, timeline } = state.data.get(pet.pet_id) ?? { care: [], timeline: [] };
  const lastTriage = timeline.find((e) => e.type === "triage");
  const lastReview = timeline.find((e) => e.type === "review");
  const reviewedLevel = lastReview && lastTriage && String(lastReview.ref_id) === String(lastTriage.ref_id) ? priorityOf(lastReview) : null;
  const level = reviewedLevel ?? (lastTriage ? priorityOf(lastTriage) : null);
  const overdue = care.filter((c) => c.status === "OVERDUE").length;
  if (level && PRIORITY_LEVEL[level] >= 3) {
    return { tab: PRIORITY_LABEL[level], tabClass: "tag-alert", pill: `Última triagem: ${PRIORITY_LABEL[level]}`, pillClass: "pill-red" };
  }
  if (overdue > 0) {
    return { tab: "Cuidados", tabClass: "tag-warn", pill: `${overdue} ${overdue === 1 ? "cuidado vencido" : "cuidados vencidos"}`, pillClass: "pill-yellow" };
  }
  return { tab: "Ok", tabClass: "tag-ok", pill: "Estável", pillClass: "pill-green" };
}

function renderPet() {
  const holder = $("pet-content");
  clear(holder);
  const pet = currentPet();
  if (!pet) {
    holder.append(el("div", { class: "empty-state" },
      el("h2", { text: "Nenhum pet cadastrado ainda" }),
      el("p", { text: "Cadastre seu primeiro pet para acompanhar a saúde dele e usar a triagem." }),
      el("button", { class: "cds-btn cds-btn--primary", type: "button", id: "empty-new-pet", text: "Cadastrar pet", onclick: () => openPetDialog(null) })));
    return;
  }
  const data = state.data.get(pet.pet_id) ?? { care: [], timeline: [] };
  put(holder,
    el("p", { class: "print-only print-header", text: `Zelo · Relatório de saúde de ${pet.name} (${pet.pet_id}) — gerado em ${formatDateTime(new Date().toISOString())}` }),
    ctaStrip(pet),
    el("div", { class: "content-body" },
      profileSection(pet, data),
      historySection(pet, data),
      careSection(pet, data),
      healthSection(pet, data)),
  );
}

function svgIcon(path) {
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("viewBox", "0 0 32 32");
  svg.setAttribute("aria-hidden", "true");
  const p = document.createElementNS(SVG_NS, "path");
  p.setAttribute("d", path);
  svg.append(p);
  return svg;
}

function ctaStrip(pet) {
  const article = pet.female ? "a" : "o";
  const cat = pet.species === "cat";
  return el("div", { class: `chat-cta-strip no-print${cat ? " chat-cta-strip--cat" : ""}` },
    el("div", { class: "chat-cta-text-area" },
      el("div", { class: "chat-cta-eyebrow", text: "Triagem inteligente disponível" }),
      el("h2", { class: "chat-cta-heading" }, `Algo incomoda ${article} `, el("strong", { text: pet.name }), "?"),
      el("p", { class: "chat-cta-sub", text: "Descreva os sintomas à Zelo. Em minutos, a assistente indica a prioridade e, se você autorizar, avisa a clínica — sem diagnosticar, sem prescrever." })),
    el("button", { class: "cta-btn", type: "button", id: "cta-triage", onclick: () => startTriage(pet.pet_id) },
      svgIcon("M17.7 3H6C4.3 3 3 4.3 3 6v21l6-6h20V6c0-1.7-1.3-3-3-3H17.7z"), `Iniciar Triagem com ${pet.name}`));
}

function sectionHeader(title, id, action) {
  return el("div", { class: "section-header-row" }, el("h2", { class: "section-heading", id, text: title }), action ?? null);
}

function historyLabel(id) {
  return state.catalog?.history_tags.find((t) => t.id === id)?.label ?? id.replaceAll("_", " ");
}

function breedLabel(pet) {
  if (!pet.breed) return "Raça não informada";
  if (pet.breed === "Mixed Breed") return "SRD (sem raça definida)";
  const list = pet.species === "cat" ? state.catalog?.cat_breeds : state.catalog?.dog_breeds;
  return list?.find((b) => b.id === pet.breed)?.label ?? pet.breed;
}

function profileSection(pet, data) {
  const status = petStatus(pet);
  const vital = (label, value, unit) => el("div", { class: "vital-cell" },
    el("div", { class: "vital-cell-label", text: label }),
    el("div", { class: "vital-cell-value" }, value, unit ? el("span", { class: "vital-cell-unit", text: ` ${unit}` }) : null));
  const age = pet.age_months == null ? ["—", null] : pet.age_months < 12 ? [String(pet.age_months), pet.age_months === 1 ? "mês" : "meses"] : [String(Math.floor(pet.age_months / 12)), Math.floor(pet.age_months / 12) === 1 ? "ano" : "anos"];
  const tags = [
    ...pet.history_tags.map((t) => el("span", { class: "data-tag data-tag-blue", text: historyLabel(t) })),
    pet.brachycephalic ? el("span", { class: "data-tag data-tag-yellow", text: "Focinho curto (braquicefálico)" }) : null,
    el("span", { class: `data-tag ${pet.sharing_enabled ? "data-tag-green" : "data-tag-gray"}`, text: pet.sharing_enabled ? "Compartilhamento com a clínica ativo" : "Compartilhamento com a clínica desativado" }),
  ];
  const overdue = data.care.filter((c) => c.status === "OVERDUE");
  const soon = data.care.filter((c) => c.status === "DUE_SOON");
  const lastReview = data.timeline.find((e) => e.type === "review");
  const notes = [];
  if (overdue.length) notes.push(`Cuidados vencidos: ${overdue.map((c) => c.name).join(", ")}. Agende com a clínica.`);
  if (soon.length) notes.push(`Vencem em breve: ${soon.map((c) => `${c.name} (${formatDay(c.next_due)})`).join(", ")}.`);
  if (lastReview) notes.push(`Último retorno da clínica (${formatDateTime(lastReview.occurred_at)}): ${lastReview.summary || lastReview.title}`);
  if (!notes.length) notes.push("Nenhuma pendência no momento. Mantenha o plano de cuidados em dia.");

  return el("section", { class: "dash-section", "data-section-id": "perfil", "aria-labelledby": "sec-perfil" },
    sectionHeader("Perfil do Animal", "sec-perfil", el("button", { class: "text-link no-print", type: "button", id: "edit-pet", text: "Editar dados →", onclick: () => openPetDialog(pet.pet_id) })),
    el("div", { class: "pet-profile-section" },
      el("div", { class: "pet-profile-photo-col" },
        petAvatar(pet, "pet-photo-circle"),
        el("span", { class: "pet-id-mono", text: pet.pet_id }),
        el("div", { class: `pet-status-pill ${status.pillClass}` }, el("span", { class: "status-dot" }), status.pill)),
      el("div", { class: "pet-profile-info-col" },
        el("div", { class: "section-label", text: "Ficha do Pet" }),
        el("h3", { class: "pet-name-h" }, el("strong", { text: pet.name })),
        el("p", { class: "pet-breed-line", text: `${breedLabel(pet)} — ${pet.female ? "Fêmea" : "Macho"} — ${speciesLabel(pet.species)}` }),
        el("div", { class: "vitals-grid" },
          vital("Idade", age[0], age[1]),
          vital("Peso", pet.weight_kg != null ? String(pet.weight_kg).replace(".", ",") : "—", pet.weight_kg != null ? "kg" : null),
          vital("Espécie", speciesLabel(pet.species)),
          vital("Nascimento", pet.birth_date ? pet.birth_date.split("-").reverse().join("/") : "—")),
        el("div", { class: "tags-row" }, tags),
        el("div", { class: "notes-block" }, el("strong", { text: "Observações: " }), notes.join(" ")))),
  );
}

function historySection(pet, data) {
  const { reviewed, shared } = triageState(data.timeline);
  const rows = data.timeline.map((event) => {
    const level = event.type === "triage" || event.type === "review" ? priorityOf(event) : null;
    let status;
    if (event.type === "triage") {
      const ref = String(event.ref_id);
      status = reviewed.has(ref) ? ["Revisada pela clínica", "status-confirmed"] : shared.has(ref) ? ["Aguardando revisão", "status-pending"] : ["Registrada no app", "status-resolved"];
    } else if (event.type === "review") {
      status = ["Confirmado pela clínica", "status-confirmed"];
    } else {
      status = [EVENT_LABEL[event.type] ?? event.type, "status-resolved"];
    }
    return el("tr", { "data-type": event.type },
      el("td", { class: "td-date", text: formatDateTime(event.occurred_at) }),
      el("td", { class: "td-desc" },
        el("div", { class: "desc-main", text: humanTitle(event.title) }),
        event.summary ? el("div", { class: "desc-sub", text: event.summary }) : null,
        el("div", { class: "desc-source", text: SOURCE_LABEL[event.source] ?? event.source })),
      el("td", {}, level ? el("span", { class: `priority-tag ${PRIORITY_CLASS[level]}`, text: PRIORITY_LABEL[level] }) : el("span", { class: "priority-tag pt-routine", text: "Registro" })),
      el("td", {}, el("span", { class: status[1], text: status[0] })));
  });
  const table = rows.length
    ? el("div", { class: "table-scroll" }, el("table", { class: "data-table", id: "history-table" },
        el("thead", {}, el("tr", {}, ["Data", "Ocorrência", "Prioridade", "Status"].map((h) => el("th", { scope: "col", text: h })))),
        el("tbody", {}, rows)))
    : el("p", { class: "empty-row", text: `Nenhum atendimento registrado para ${pet.name} ainda. As triagens e revisões da clínica aparecem aqui.` });
  return el("section", { class: "dash-section", "data-section-id": "historico", "aria-labelledby": "sec-historico" },
    sectionHeader("Histórico de Atendimento", "sec-historico", el("button", { class: "text-link no-print", type: "button", id: "export-pdf", text: "Exportar PDF →", onclick: () => exportPdf(pet) })),
    table);
}

const PRIORITY_CLASS = { baixa: "pt-low", media: "pt-medium", alta: "pt-high", emergencia: "pt-emergency" };

function humanTitle(title) {
  return title.replace(/(baixa|media|alta|emergencia)\s*$/, (value) => PRIORITY_LABEL[value].toLowerCase());
}

function careSection(pet, data) {
  const statusCell = (item) => {
    if (item.status === "OVERDUE") return ["care-status-late", `Atrasado há ${-item.days_until_due} ${-item.days_until_due === 1 ? "dia" : "dias"}`];
    if (item.status === "DUE_SOON") return ["care-status-soon", item.days_until_due === 0 ? "Vence hoje" : `Vence em ${item.days_until_due} ${item.days_until_due === 1 ? "dia" : "dias"}`];
    return ["care-status-ok", "Em dia"];
  };
  const rows = data.care.map((item) => {
    const [cls, text] = statusCell(item);
    return el("tr", { class: item.status === "OVERDUE" ? "care-overdue" : "", "data-care-id": String(item.id) },
      el("td", {}, el("strong", { text: item.name }), el("span", { class: "care-type", text: ` · ${CARE_TYPE_LABEL[item.type] ?? item.type}` })),
      el("td", { class: "care-date", text: item.last_done ? formatDay(item.last_done) : "—" }),
      el("td", { class: "care-date", text: formatDay(item.next_due) }),
      el("td", { class: cls, text }),
      state.manageCare ? el("td", { class: "care-actions no-print" },
        el("button", { class: "cds-btn cds-btn--ghost cds-btn--sm", type: "button", "data-action": "done", text: "Marcar como feito", onclick: () => markDone(pet, item) }),
        el("button", { class: "cds-btn cds-btn--danger-ghost cds-btn--sm", type: "button", "data-action": "remove", text: "Remover", onclick: () => removeCare(pet, item) })) : null);
  });
  const manage = el("button", { class: "text-link no-print", type: "button", id: "manage-care", "aria-pressed": String(state.manageCare),
    text: state.manageCare ? "Concluir ✓" : "Gerenciar →", onclick: () => { state.manageCare = !state.manageCare; renderPet(); scrollToSection("cuidados", false); } });
  const headers = ["Cuidado", "Última aplicação", "Próximo", "Status", ...(state.manageCare ? ["Ações"] : [])];
  return el("section", { class: "dash-section", "data-section-id": "cuidados", "aria-labelledby": "sec-cuidados" },
    sectionHeader("Plano de Cuidados", "sec-cuidados", manage),
    state.manageCare ? el("div", { class: "care-toolbar no-print" },
      el("p", { class: "hint", text: "Marque o que já foi feito (a próxima data segue o intervalo do tipo de cuidado) ou adicione novos itens." }),
      el("button", { class: "cds-btn cds-btn--primary cds-btn--sm", type: "button", id: "add-care", text: "+ Adicionar cuidado", onclick: openCareDialog })) : null,
    rows.length
      ? el("div", { class: "table-scroll" }, el("table", { class: "care-table", id: "care-table" },
          el("thead", {}, el("tr", {}, headers.map((h) => el("th", { scope: "col", text: h })))),
          el("tbody", {}, rows)))
      : el("p", { class: "empty-row", text: "Nenhum cuidado cadastrado. Use “Gerenciar” para adicionar vacinas, vermífugo, antipulgas e consultas." }));
}

function healthSection(pet, data) {
  const kv = (key, value, tone) => el("div", { class: "kv-row" }, el("span", { class: "kv-key", text: key }), el("span", { class: `kv-val${tone ? ` ${tone}` : ""}`, text: value }));
  const careRows = data.care.length
    ? data.care.map((c) => kv(c.name, c.status === "OVERDUE" ? "Vencido" : c.status === "DUE_SOON" ? `Vence ${formatDay(c.next_due)}` : "Em dia", c.status === "OVERDUE" ? "late" : c.status === "DUE_SOON" ? "warn" : "ok"))
    : [kv("Plano de cuidados", "Sem itens", null)];
  const lastTriage = data.timeline.find((e) => e.type === "triage");
  const lastReview = data.timeline.find((e) => e.type === "review");
  const triageLevel = lastTriage ? priorityOf(lastTriage) : null;
  const overdue = data.care.filter((c) => c.status === "OVERDUE").length;
  return el("section", { class: "dash-section", "data-section-id": "saude", "aria-labelledby": "sec-saude" },
    sectionHeader("Resumo de Saúde", "sec-saude"),
    el("div", { class: "two-col-grid" },
      el("div", { class: "col-block" }, el("div", { class: "col-block-title", text: "Vacinas & Preventivos" }), el("div", { class: "kv-list" }, careRows)),
      el("div", { class: "col-block" }, el("div", { class: "col-block-title", text: "Histórico Clínico" }), el("div", { class: "kv-list" },
        kv("Última triagem", lastTriage ? `${formatDateTime(lastTriage.occurred_at)} · ${PRIORITY_LABEL[triageLevel] ?? "—"}` : "Nenhuma", triageLevel && PRIORITY_LEVEL[triageLevel] >= 3 ? "late" : triageLevel ? "warn" : null),
        kv("Revisão da clínica", lastReview ? formatDateTime(lastReview.occurred_at) : "Nenhuma", lastReview ? "ok" : null),
        kv("Compartilhamento", pet.sharing_enabled ? "Ativo" : "Desativado", pet.sharing_enabled ? "ok" : null),
        kv("Cuidados vencidos", String(overdue), overdue ? "late" : "ok"),
        kv("Histórico de saúde", pet.history_tags.length ? pet.history_tags.map(historyLabel).join(", ") : "Nada informado", null),
        kv("Idade", formatAge(pet.age_months), null)))),
  );
}

// ------------------------------------------------------------------------------ aviso do topo
function renderNotification() {
  const notice = buildNotification();
  let dismissed = null;
  try { dismissed = sessionStorage.getItem("zelo.dashboard.notif"); } catch { /* sem armazenamento */ }
  const box = $("main-notif");
  if (!notice || dismissed === notice.title) {
    box.hidden = true;
    return;
  }
  $("notif-title").textContent = notice.title;
  $("notif-body").textContent = notice.body;
  box.dataset.tone = notice.tone;
  box.hidden = false;
}

function buildNotification() {
  for (const pet of state.pets) {
    const { timeline } = state.data.get(pet.pet_id) ?? { timeline: [] };
    const { reviewed, shared } = triageState(timeline);
    const pending = timeline.find((e) => e.type === "triage" && shared.has(String(e.ref_id)) && !reviewed.has(String(e.ref_id)));
    if (pending && PRIORITY_LEVEL[priorityOf(pending)] >= 3) {
      return { tone: "error", title: `Ação necessária — ${pet.name}`, body: `A triagem de ${formatDateTime(pending.occurred_at)} (prioridade ${PRIORITY_LABEL[priorityOf(pending)].toLowerCase()}) foi enviada à clínica e aguarda revisão. Se piorar, procure atendimento imediatamente.` };
    }
  }
  for (const pet of state.pets) {
    const { timeline } = state.data.get(pet.pet_id) ?? { timeline: [] };
    const review = timeline.find((e) => e.type === "review");
    if (review && Date.now() - new Date(review.occurred_at).getTime() < 7 * 86400000) {
      return { tone: "info", title: `Retorno da clínica — ${pet.name}`, body: `${humanTitle(review.title)}. ${review.summary ?? ""}`.trim() };
    }
  }
  for (const pet of state.pets) {
    const { care } = state.data.get(pet.pet_id) ?? { care: [] };
    const late = care.find((c) => c.status === "OVERDUE");
    if (late) {
      return { tone: "warning", title: `Cuidado vencido — ${pet.name}`, body: `“${late.name}” venceu há ${-late.days_until_due} ${-late.days_until_due === 1 ? "dia" : "dias"}. Agende com a clínica ou marque como feito no Plano de Cuidados.` };
    }
  }
  return null;
}

// ------------------------------------------------------------------------------- navegação
function scrollToSection(section, smooth = true) {
  document.querySelectorAll("[data-section]").forEach((b) => b.classList.toggle("active", b.dataset.section === section));
  const target = document.querySelector(`[data-section-id="${section}"]`);
  if (!target) return;
  target.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
  history.replaceState(null, "", `#${section}`);
}

function exportPdf(pet) {
  document.title = `Zelo - Relatorio de saude - ${pet.name}`; // vira o nome sugerido do arquivo ao salvar em PDF
  window.print();
  setTimeout(() => { document.title = "Zelo — Painel do Tutor"; }, 1000);
}

// ------------------------------------------------------------------------- plano de cuidados
async function markDone(pet, item) {
  if (state.busy) return;
  state.busy = true;
  try {
    const updated = await api.post(`/api/pets/${pet.pet_id}/care-plan/${item.id}/done`, {});
    await reloadPet(pet.pet_id);
    toast(`“${item.name}” marcado como feito. Próxima data: ${formatDay(updated.next_due)}.`);
  } catch (error) {
    toast(error.message, "error");
  } finally {
    state.busy = false;
  }
}

async function removeCare(pet, item) {
  if (state.busy) return;
  if (!window.confirm(`Remover “${item.name}” do plano de cuidados de ${pet.name}?`)) return;
  state.busy = true;
  try {
    await api.del(`/api/pets/${pet.pet_id}/care-plan/${item.id}`);
    await reloadPet(pet.pet_id);
    toast(`“${item.name}” removido do plano.`);
  } catch (error) {
    toast(error.message, "error");
  } finally {
    state.busy = false;
  }
}

function wireCareDialog() {
  const dialog = $("care-dialog");
  $("care-cancel").addEventListener("click", () => dialog.close());
  $("care-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (state.busy) return;
    const name = $("care-name").value.trim();
    const nextDue = $("care-next").value;
    const lastDone = $("care-last").value || null;
    if (!name || !nextDue) {
      showError("care-error", "Informe a descrição e a próxima data.");
      return;
    }
    if (lastDone && nextDue < lastDone) {
      showError("care-error", "A próxima data não pode ser anterior à última aplicação.");
      return;
    }
    state.busy = true;
    $("care-submit").disabled = true;
    try {
      const pet = currentPet();
      await api.post(`/api/pets/${pet.pet_id}/care-plan`, { type: $("care-type").value, name, last_done: lastDone, next_due: nextDue });
      dialog.close();
      await reloadPet(pet.pet_id);
      toast(`“${name}” adicionado ao plano de ${pet.name}.`);
    } catch (error) {
      showError("care-error", fieldErrors(error));
    } finally {
      state.busy = false;
      $("care-submit").disabled = false;
    }
  });
}

function openCareDialog() {
  $("care-form").reset();
  $("care-error").hidden = true;
  const today = new Date().toISOString().slice(0, 10);
  $("care-last").max = today;
  $("care-next").min = today;
  $("care-dialog-title").textContent = `Adicionar cuidado — ${currentPet().name}`;
  $("care-dialog").showModal();
  $("care-name").focus();
}

// ------------------------------------------------------------------------- cadastro / edição
function wirePetDialog() {
  const dialog = $("pet-dialog");
  $("pet-species").addEventListener("change", () => fillBreeds(null));
  $("pet-cancel").addEventListener("click", () => dialog.close());
  $("pet-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await submitPet();
  });
}

function fillBreeds(selected) {
  const species = $("pet-species").value;
  const breeds = species === "cat" ? state.catalog.cat_breeds : state.catalog.dog_breeds;
  const select = $("pet-breed");
  clear(select);
  select.append(el("option", { value: "", text: "Não sei / outra" }));
  breeds.forEach((b) => select.append(el("option", { value: b.id, text: b.label, selected: b.id === selected })));
  if (selected && !breeds.some((b) => b.id === selected)) {
    select.append(el("option", { value: selected, text: selected, selected: true }));
  }
}

function openPetDialog(petId) {
  if (!state.catalog) return;
  state.editingPetId = petId;
  const pet = petId ? state.pets.find((p) => p.pet_id === petId) : null;
  $("pet-form").reset();
  $("pet-error").hidden = true;
  $("pet-dialog-title").textContent = pet ? `Editar dados — ${pet.name}` : "Cadastrar um pet";
  $("pet-submit").textContent = pet ? "Salvar alterações" : "Cadastrar";
  $("pet-name").value = pet?.name ?? "";
  $("pet-species").value = pet?.species ?? "dog";
  $("pet-sex").value = pet && !pet.female ? "M" : "F";
  fillBreeds(pet?.breed ?? null);
  $("pet-birth").value = pet?.birth_date ?? "";
  $("pet-birth").max = new Date().toISOString().slice(0, 10);
  $("pet-weight").value = pet?.weight_kg ?? "";
  const holder = $("pet-history");
  clear(holder);
  for (const tag of state.catalog.history_tags) {
    holder.append(el("label", { class: "cds-check" }, el("input", { type: "checkbox", value: tag.id, checked: Boolean(pet?.history_tags.includes(tag.id)) }), tag.label));
  }
  $("pet-dialog").showModal();
  $("pet-name").focus();
}

async function submitPet() {
  if (state.busy) return;
  const name = $("pet-name").value.trim();
  if (!name) {
    showError("pet-error", "Informe o nome do pet.");
    return;
  }
  const weight = $("pet-weight").value;
  if (weight && (Number(weight) < 0.1 || Number(weight) > 120)) {
    showError("pet-error", "O peso precisa estar entre 0,1 e 120 kg.");
    return;
  }
  const payload = {
    name,
    species: $("pet-species").value,
    breed: $("pet-breed").value || null,
    female: $("pet-sex").value === "F",
    birth_date: $("pet-birth").value || null,
    weight_kg: weight ? Number(weight) : null,
    history_tags: [...document.querySelectorAll("#pet-history input:checked")].map((i) => i.value),
  };
  state.busy = true;
  $("pet-submit").disabled = true;
  try {
    const editing = state.editingPetId;
    const saved = editing ? await api.put(`/api/pets/${editing}`, payload) : await api.post("/api/pets", payload);
    $("pet-dialog").close();
    state.petId = saved.pet_id;
    prefs.set(LAST_PET_KEY, saved.pet_id);
    await reloadPet(saved.pet_id);
    toast(editing ? `Dados de ${saved.name} atualizados.` : `${saved.name} foi cadastrado.`);
  } catch (error) {
    showError("pet-error", fieldErrors(error));
  } finally {
    state.busy = false;
    $("pet-submit").disabled = false;
  }
}

function fieldErrors(error) {
  const details = error instanceof ApiError && error.errors ? Object.values(error.errors).join(" ") : "";
  return details || error.message || "Não foi possível salvar.";
}

function showError(id, message) {
  const node = $(id);
  node.textContent = message;
  node.hidden = !message;
}

init();
