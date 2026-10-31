import { api, ApiError, ensureCsrf } from "./api.js";
import { renderTriageCard } from "./triage-card.js";
import { clear, el, eventTitle, formatAge, formatDateTime, icon, PRIORITY_LABEL, priorityBadge, put, speciesLabel, toast } from "./ui.js";

const $ = (id) => document.getElementById(id);
const POLL_MS = 10_000;
/** Procedência de cada evento: o que o tutor digitou não deve parecer verificado pela clínica. */
const SOURCE_LABEL = {
  tutor: "informado pelo tutor", clinic: "registrado pela clínica", chat: "conversa com a Zelo",
  api: "triagem avulsa", vision: "análise de foto (experimental)",
};

const state = {
  queue: [],
  selectedId: null,
  filter: "all",
  seen: new Set(),
  firstLoad: true,
};

async function init() {
  $("brand-mark").append(icon("paw", 18));
  $("logout").addEventListener("click", async () => {
    await api.logout().catch(() => undefined);
    location.assign("/index.html");
  });
  document.querySelectorAll("[data-filter]").forEach((chip) =>
    chip.addEventListener("click", () => {
      state.filter = chip.dataset.filter;
      document.querySelectorAll("[data-filter]").forEach((c) => c.setAttribute("aria-pressed", String(c === chip)));
      renderQueue();
    }),
  );

  await ensureCsrf();
  try {
    const me = await api.get("/api/auth/me");
    if (me.role !== "CLINICA") throw new Error("perfil");
    $("user-name").textContent = me.name;
  } catch {
    location.replace("/index.html?acesso=negado");
    return;
  }

  renderEmpty();
  await refreshQueue();
  setInterval(() => {
    if (!document.hidden) refreshQueue();
  }, POLL_MS);
}

// ------------------------------------------------------------------------------------- fila
async function refreshQueue() {
  try {
    const queue = await api.get("/api/clinic/triages");
    announceNewcomers(queue);
    state.queue = queue;
    renderQueue();
    $("live").className = "badge badge--ok";
  } catch (error) {
    $("live").className = "badge badge--warn";
    if (error instanceof ApiError && error.status === 401) location.replace("/index.html");
  }
}

function announceNewcomers(queue) {
  const fresh = queue.filter((t) => !state.seen.has(t.triage_id));
  if (!state.firstLoad) {
    fresh.forEach((t) => toast(`Nova triagem compartilhada: ${t.pet_name} (${PRIORITY_LABEL[t.priority].toLowerCase()})`));
  }
  fresh.forEach((t) => {
    t.isNew = !state.firstLoad;
    state.seen.add(t.triage_id);
  });
  state.firstLoad = false;
}

function renderStats() {
  const holder = $("stats");
  clear(holder);
  const pending = state.queue.filter((t) => !t.reviewed);
  holder.append(el("span", { class: "badge", text: `${pending.length} aguardando revisão` }));
  for (const level of ["emergencia", "alta"]) {
    const count = pending.filter((t) => t.priority === level).length;
    if (count > 0) holder.append(priorityBadge(level), el("span", { class: "hint", text: `× ${count}` }));
  }
}

function visibleQueue() {
  return state.queue.filter((t) => state.filter === "all" || (state.filter === "pending" ? !t.reviewed : t.reviewed));
}

function renderQueue() {
  renderStats();
  const list = $("queue-list");
  // a fila é reconstruída a cada atualização: quem navega por teclado não pode perder o foco
  const focusedId = list.contains(document.activeElement) ? document.activeElement.dataset.triageId : null;
  clear(list);
  const items = visibleQueue();
  if (items.length === 0) {
    list.append(el("li", { class: "hint", text: state.queue.length === 0
      ? "Nenhuma triagem compartilhada ainda. Quando um tutor autorizar, ela aparece aqui."
      : "Nenhuma triagem neste filtro." }));
    return;
  }
  for (const item of items) {
    const symptoms = item.symptom_labels.length ? item.symptom_labels.slice(0, 3).join(", ") : "sem sintomas reconhecidos";
    list.append(
      el("li", {},
        el("button", {
          class: `q-item${item.isNew ? " is-new" : ""}`,
          type: "button",
          "data-priority": item.priority,
          "data-triage-id": item.triage_id,
          "aria-current": String(item.triage_id === state.selectedId),
          onclick: () => select(item.triage_id),
        },
          el("span", { class: "q-item__top" }, el("strong", { text: item.pet_name }), priorityBadge(item.priority)),
          el("small", { text: `${speciesLabel(item.species)} · ${formatAge(item.pet_age_months)} · tutor(a) ${item.tutor_name}` }),
          el("small", { text: symptoms }),
          el("span", { class: "q-item__top" },
            el("small", { text: formatDateTime(item.created_at) }),
            item.reviewed ? el("span", { class: "badge badge--ok", text: "Revisada" }) : el("span", { class: "badge badge--review", text: "Aguardando revisão" }))),
      ),
    );
  }
  if (focusedId) list.querySelector(`[data-triage-id="${focusedId}"]`)?.focus();
}

// ---------------------------------------------------------------------------------- detalhe
function renderEmpty() {
  const detail = $("detalhe");
  clear(detail);
  detail.append(
    el("div", { class: "detail__empty" },
      el("h2", { text: "Escolha uma triagem" }),
      el("p", { text: "O tutor envia o resumo enquanto ainda está a caminho. Você vê o relato, o histórico autorizado e confirma ou ajusta a prioridade sugerida pela IA." })),
  );
}

async function select(triageId) {
  state.selectedId = triageId;
  renderQueue();
  try {
    const detail = await api.get(`/api/clinic/triages/${triageId}`);
    if (state.selectedId !== triageId) return; // clique mais recente venceu: descarta a resposta atrasada
    renderDetail(detail);
  } catch (error) {
    if (state.selectedId !== triageId) return;
    toast(error.message, "error");
    renderEmpty();
  }
}

function renderDetail(detail) {
  const { triage, pet, summary } = detail;
  const holder = $("detalhe");
  clear(holder);

  put(holder,
    el("header", {},
      el("p", { class: "eyebrow", text: `${speciesLabel(pet.species)} · tutor(a) ${summary.tutor_name}` }),
      el("h2", { text: `${pet.name}, ${formatAge(pet.age_months)}` }),
      el("div", { class: "chips" },
        pet.breed ? el("span", { class: "chip chip--static", text: pet.breed === "Mixed Breed" ? "SRD" : pet.breed }) : null,
        pet.weight_kg ? el("span", { class: "chip chip--static", text: `${pet.weight_kg} kg` }) : null,
        pet.history_tags.map((t) => el("span", { class: "chip chip--static", text: t.replaceAll("_", " ") })))),
    el("section", {}, el("h3", { class: "section", text: "O que o tutor relatou" }), el("blockquote", { class: "quote", text: detail.input_text })),
    el("section", {}, el("h3", { class: "section", text: "Triagem preliminar da IA" }), renderTriageCard(triage, { showShare: false })),
    reviewsSection(detail),
    reviewForm(detail),
    timelineSection(detail),
  );
  holder.scrollTop = 0;
}

function reviewsSection(detail) {
  if (detail.reviews.length === 0) return null;
  return el("section", {},
    el("h3", { class: "section", text: "Revisões registradas" }),
    detail.reviews.map((r) =>
      el("div", { class: "review-entry" },
        el("strong", { text: `${r.decision === "CONFIRMADA" ? "Confirmada" : "Ajustada"} · ${PRIORITY_LABEL[r.final_priority]}` }),
        r.note ? el("p", { text: r.note }) : null,
        el("small", { text: `${r.reviewer} · ${formatDateTime(r.created_at)}` }))),
  );
}

function reviewForm(detail) {
  const suggested = detail.triage.priority;
  const form = el("form", { class: "card review", novalidate: true });
  const decisionRow = el("div", { class: "radio-row", role: "radiogroup", "aria-label": "Decisão" },
    radio("decision", "CONFIRMADA", `Confirmar prioridade (${PRIORITY_LABEL[suggested]})`, true),
    radio("decision", "AJUSTADA", "Ajustar prioridade", false));
  const prioritySelect = el("select", { class: "select", id: "final-priority", name: "final_priority" },
    Object.entries(PRIORITY_LABEL).map(([value, label]) => el("option", { value, text: label, selected: value === suggested })));
  prioritySelect.disabled = true;
  const note = el("textarea", { class: "textarea", id: "review-note", name: "note", maxlength: "1000", placeholder: "Orientação ao tutor, observações clínicas…" });
  const error = el("p", { class: "error-text", role: "alert", hidden: true });
  const submit = el("button", { class: "btn btn--primary", type: "submit", text: "Registrar revisão" });

  decisionRow.addEventListener("change", () => {
    const adjusting = form.elements.decision.value === "AJUSTADA";
    prioritySelect.disabled = !adjusting;
    if (!adjusting) prioritySelect.value = suggested;
  });

  form.append(
    el("h3", { class: "section", text: "Revisão profissional (humano no circuito)" }),
    decisionRow,
    el("div", { class: "field" }, el("label", { for: "final-priority", text: "Prioridade final" }), prioritySelect),
    el("div", { class: "field" }, el("label", { for: "review-note", text: "Observação" }), note),
    error, submit);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    error.hidden = true;
    const decision = form.elements.decision.value;
    if (decision === "AJUSTADA" && prioritySelect.value === suggested) {
      error.textContent = "Para ajustar, escolha uma prioridade diferente da sugerida pela IA.";
      error.hidden = false;
      return;
    }
    submit.disabled = true;
    try {
      await api.post("/api/clinic/reviews", {
        triage_id: detail.triage.triage_id,
        decision,
        final_priority: prioritySelect.value,
        note: note.value.trim() || null,
      });
      toast("Revisão registrada. O tutor vê o retorno na linha do tempo do pet.");
      await refreshQueue();
      await select(detail.triage.triage_id);
    } catch (e) {
      error.textContent = e.message;
      error.hidden = false;
      submit.disabled = false;
    }
  });
  return form;
}

function radio(name, value, label, checked) {
  return el("label", {}, el("input", { type: "radio", name, value, checked }), label);
}

function timelineSection(detail) {
  if (detail.timeline.length === 0) return null;
  return el("section", {},
    el("h3", { class: "section", text: "Histórico autorizado do pet" }),
    el("ol", { class: "timeline" },
      detail.timeline.map((event) =>
        el("li", { class: "timeline__item", "data-type": event.type },
          el("time", { datetime: event.occurred_at, text: formatDateTime(event.occurred_at) }),
          el("strong", { text: eventTitle(event.title) }),
          event.summary ? el("p", { text: event.summary }) : null,
          el("small", { class: "hint", text: SOURCE_LABEL[event.source] ?? event.source })))));
}

init();
