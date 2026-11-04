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
  $("show-dashboard").addEventListener("click", () => renderDashboard());
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

  await refreshQueue();
  renderDashboard();
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
    if (state.selectedId === null) renderDashboard(); // painel sempre com os números atuais
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
  detail.dataset.view = "empty";
  detail.append(
    el("div", { class: "detail__empty" },
      el("h2", { text: "Escolha uma triagem" }),
      el("p", { text: "O tutor envia o resumo enquanto ainda está a caminho. Você vê o relato, o histórico autorizado e confirma ou ajusta a prioridade sugerida pela IA." })),
  );
}

// ------------------------------------------------------------------------ painel de atendimento
const LEVELS = ["emergencia", "alta", "media", "baixa"];
const dayKey = (iso) => new Date(iso).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" });

function ago(iso) {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (minutes < 60) return `há ${minutes} min`;
  const hours = Math.round(minutes / 60);
  return hours < 24 ? `há ${hours} h` : `há ${Math.round(hours / 24)} d`;
}

function bar(fraction, priority) {
  const fill = el("span", { class: "dash-bar__fill", "data-priority": priority ?? "" });
  fill.style.width = `${Math.round(Math.max(0, Math.min(1, fraction)) * 100)}%`; // CSSOM: permitido pela CSP (sem style="")
  return el("span", { class: "dash-bar", "aria-hidden": "true" }, fill);
}

function kpi(label, value, hint, tone) {
  return el("div", { class: `dash-kpi${tone ? ` dash-kpi--${tone}` : ""}` },
    el("span", { class: "dash-kpi__label", text: label }),
    el("strong", { class: "dash-kpi__value", text: String(value) }),
    hint ? el("span", { class: "dash-kpi__hint", text: hint }) : null);
}

function card(title, ...children) {
  return el("section", { class: "dash-card" }, el("h3", { class: "dash-card__title", text: title }), ...children);
}

function renderDashboard() {
  const holder = $("detalhe");
  const keepScroll = holder.dataset.view === "dashboard" ? holder.scrollTop : 0;
  if (state.selectedId !== null) {
    state.selectedId = null;
    renderQueue();
  }
  clear(holder);
  holder.dataset.view = "dashboard";
  const q = state.queue;
  if (q.length === 0) {
    renderEmpty();
    return;
  }
  const pending = q.filter((t) => !t.reviewed);
  const reviewed = q.length - pending.length;
  const urgentPending = pending.filter((t) => t.priority === "emergencia").length;
  const oldestPending = pending.reduce((a, b) => (a && a.created_at < b.created_at ? a : b), null);

  const byLevel = LEVELS.map((level) => {
    const all = q.filter((t) => t.priority === level);
    return { level, total: all.length, pending: all.filter((t) => !t.reviewed).length };
  });
  const maxLevel = Math.max(1, ...byLevel.map((l) => l.total));

  const days = [...Array(7)].map((_, i) => { const d = new Date(); d.setDate(d.getDate() - (6 - i)); return d; });
  const perDay = days.map((d) => ({ day: d, count: q.filter((t) => dayKey(t.created_at) === dayKey(d.toISOString())).length }));
  const maxDay = Math.max(1, ...perDay.map((d) => d.count));
  const weekday = new Intl.DateTimeFormat("pt-BR", { weekday: "short" });

  const symptomCount = new Map();
  q.forEach((t) => t.symptom_labels.forEach((label) => symptomCount.set(label, (symptomCount.get(label) ?? 0) + 1)));
  const topSymptoms = [...symptomCount.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5);
  const maxSymptom = Math.max(1, ...topSymptoms.map(([, n]) => n));

  const dogs = q.filter((t) => t.species === "dog").length;
  const tutors = new Set(q.map((t) => t.tutor_name)).size;
  const row = (label, fraction, value, priority) => el("li", { class: "dash-row" },
    typeof label === "string" ? el("span", { class: "dash-row__label dash-row__label--text", text: label }) : el("span", { class: "dash-row__label" }, label),
    bar(fraction, priority),
    el("span", { class: "dash-row__value", text: value }));

  put(holder,
    el("header", { class: "dash-head" },
      el("p", { class: "eyebrow", text: "Visão geral da clínica" }),
      el("h2", { text: "Painel de atendimento" }),
      el("p", { class: "hint", text: "Triagens que os tutores enviaram com consentimento. Atualiza sozinho a cada 10 segundos; clique numa triagem para ver a conversa e revisar." })),
    el("div", { class: "dash-kpis" },
      kpi("Triagens recebidas", q.length, `${tutors} ${tutors === 1 ? "tutor" : "tutores"}`),
      kpi("Aguardando revisão", pending.length, oldestPending ? `mais antiga ${ago(oldestPending.created_at)}` : "fila em dia", pending.length ? "warn" : "ok"),
      kpi("Emergências pendentes", urgentPending, urgentPending ? "atender primeiro" : "nenhuma agora", urgentPending ? "danger" : "ok"),
      kpi("Revisadas", reviewed, `${Math.round((reviewed / q.length) * 100)}% do total`, "ok")),
    el("div", { class: "dash-grid" },
      card("Próximos atendimentos",
        pending.length
          ? el("ol", { class: "dash-next" }, pending.slice(0, 5).map((t) =>
              el("li", {}, el("button", { class: "dash-next__item", type: "button", "data-priority": t.priority, onclick: () => select(t.triage_id) },
                el("span", { class: "dash-next__top" }, el("strong", { text: t.pet_name }), priorityBadge(t.priority)),
                el("small", { text: `${t.symptom_labels.slice(0, 2).join(", ") || "sem sintomas reconhecidos"} · ${ago(t.created_at)}` })))))
          : el("p", { class: "hint", text: "Nenhuma triagem aguardando revisão." })),
      card("Por prioridade",
        el("ul", { class: "dash-rows" }, byLevel.map((l) =>
          row(priorityBadge(l.level), l.total / maxLevel, `${l.total}${l.pending ? ` · ${l.pending} pend.` : ""}`, l.level)))),
      card("Triagens nos últimos 7 dias",
        el("div", { class: "dash-cols", role: "img", "aria-label": perDay.map((d) => `${dayKey(d.day.toISOString())}: ${d.count}`).join("; ") },
          perDay.map((d) => {
            const column = el("span", { class: "dash-col__bar" });
            column.style.height = `${d.count === 0 ? 0 : Math.max(6, Math.round((d.count / maxDay) * 100))}%`;
            return el("div", { class: "dash-col" },
              el("span", { class: "dash-col__value", text: String(d.count) }),
              el("span", { class: "dash-col__track" }, column),
              el("span", { class: "dash-col__label", text: weekday.format(d.day).replace(".", "") }));
          }))),
      card("Sintomas mais relatados",
        el("ul", { class: "dash-rows" }, topSymptoms.map(([label, n]) => row(label, n / maxSymptom, String(n))))),
      card("Perfil dos pacientes",
        el("ul", { class: "dash-rows" },
          row("Cães", dogs / q.length, String(dogs)),
          row("Gatos", (q.length - dogs) / q.length, String(q.length - dogs))),
        el("p", { class: "hint", text: "A prioridade é sugerida pela IA; a decisão clínica é sempre da equipe veterinária." }))),
  );
  holder.scrollTop = keepScroll;
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
    renderDashboard();
  }
}

function renderDetail(detail) {
  const { triage, pet, summary } = detail;
  const holder = $("detalhe");
  clear(holder);
  holder.dataset.view = "detail";

  put(holder,
    el("header", {},
      el("p", { class: "eyebrow", text: `${speciesLabel(pet.species)} · tutor(a) ${summary.tutor_name}` }),
      el("h2", { text: `${pet.name}, ${formatAge(pet.age_months)}` }),
      el("div", { class: "chips" },
        pet.breed ? el("span", { class: "chip chip--static", text: pet.breed === "Mixed Breed" ? "SRD" : pet.breed }) : null,
        pet.weight_kg ? el("span", { class: "chip chip--static", text: `${pet.weight_kg} kg` }) : null,
        pet.history_tags.map((t) => el("span", { class: "chip chip--static", text: t.replaceAll("_", " ") })))),
    el("button", { class: "btn btn--ghost btn--small detail__back", type: "button", text: "← Painel de atendimento", onclick: () => renderDashboard() }),
    el("section", {}, el("h3", { class: "section", text: "O que o tutor relatou" }), el("blockquote", { class: "quote", text: detail.input_text })),
    conversationSection(detail),
    el("section", {}, el("h3", { class: "section", text: "Triagem preliminar da IA" }), renderTriageCard(triage, { showShare: false })),
    reviewsSection(detail),
    reviewForm(detail),
    timelineSection(detail),
  );
  holder.scrollTop = 0;
}

/** Conversa tutor ↔ Zelo que levou à triagem (só até a resposta com a triagem). */
function conversationSection(detail) {
  const lines = detail.conversation ?? [];
  if (lines.length === 0) return null;
  const tutorName = detail.summary.tutor_name.split(" ")[0];
  return el("section", {},
    el("details", { class: "convo", open: true },
      el("summary", { text: `Conversa com a Zelo (${lines.length} mensagens)` }),
      el("ol", { class: "convo__list" },
        lines.map((m) => el("li", { class: `convo__msg convo__msg--${m.author === "USER" ? "user" : "bot"}` },
          el("span", { class: "convo__who", text: `${m.author === "USER" ? tutorName : "Zelo"} · ${formatDateTime(m.created_at)}` }),
          el("p", { class: "convo__text", text: m.text }))))));
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
