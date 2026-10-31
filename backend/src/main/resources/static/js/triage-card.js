import { el, icon, meter, priorityBadge, PRIORITY_LABEL } from "./ui.js";

const ACTION_ICON = { emergencia: "alert", alta: "alert", media: "clock", baixa: "check" };

function bulletList(items) {
  return el("ul", {}, items.map((text) => el("li", { text })));
}

function chipList(labels, titles = []) {
  return el(
    "div",
    { class: "chips" },
    labels.map((label, i) => el("span", { class: "chip chip--static", title: titles[i] || null, text: label })),
  );
}

function referenceNote(detail) {
  if (!detail.reference_frequency) return null;
  return `Em ${Math.round(detail.reference_frequency * 100)}% dos registros do dataset D2 (contexto, não risco de doença)`;
}

/**
 * Cartão da triagem preliminar: prioridade (medidor + rótulo), ação, motivos, sintomas, fatores considerados,
 * confiança, versão do motor e a saída JSON estruturada (contrato Sprint 3).
 * @param {object} triage resposta de /api/chat/messages ou /api/triage
 * @param {{ showShare?: boolean }} [options]
 * @returns {HTMLElement}
 */
export function renderTriageCard(triage, options = {}) {
  const { showShare = true } = options;
  const card = el("article", {
    class: "triage-card",
    "data-priority": triage.priority,
    "data-triage-id": triage.triage_id,
    "aria-label": `Triagem preliminar, prioridade ${PRIORITY_LABEL[triage.priority]}`,
  });

  const head = el(
    "div",
    { class: "triage-card__head" },
    meter(triage.priority),
    el(
      "div",
      {},
      el("p", { class: "eyebrow", text: `Triagem preliminar · ${triage.triage_id}` }),
      el("h3", { class: "triage-card__title", text: `Prioridade ${PRIORITY_LABEL[triage.priority].toLowerCase()}` }),
    ),
    el("span", { class: "triage-card__spacer" }),
    triage.human_review_required ? el("span", { class: "badge badge--review", text: "Revisão humana" }) : null,
  );

  const action = el(
    "p",
    { class: "triage-card__action" },
    icon(ACTION_ICON[triage.priority] || "check", 20),
    el("span", { text: triage.next_action_label }),
  );

  const dl = el("dl");
  if (triage.reasons?.length) {
    dl.append(el("div", {}, el("dt", { text: "Por quê" }), el("dd", {}, bulletList(triage.reasons))));
  }
  if (triage.symptom_details?.length) {
    dl.append(
      el(
        "div",
        {},
        el("dt", { text: "Sintomas identificados" }),
        el("dd", {}, chipList(triage.symptom_details.map((s) => s.label), triage.symptom_details.map(referenceNote))),
      ),
    );
  }
  if (triage.context_factors?.length) {
    dl.append(el("div", {}, el("dt", { text: "Personalização" }), el("dd", {}, chipList(triage.context_factors))));
  }
  if (triage.duration_hours) {
    const days = triage.duration_hours >= 48 ? `${Math.round(triage.duration_hours / 24)} dias` : `${triage.duration_hours} h`;
    dl.append(el("div", {}, el("dt", { text: "Duração informada" }), el("dd", { text: `≈ ${days}` })));
  }

  const pct = Math.round(triage.confidence * 100);
  const confidence = el(
    "div",
    { class: "confidence" },
    el("span", { text: "Confiança da interpretação" }),
    el(
      "span",
      { class: "confidence__bar", role: "img", "aria-label": `${pct}%` },
      el("span", { class: "confidence__fill" }),
    ),
    el("strong", { text: `${pct}%` }),
  );
  confidence.querySelector(".confidence__fill").style.width = `${pct}%`; // CSSOM é permitido pela CSP

  const json = el("pre", { class: "json-view", tabindex: "0", "aria-label": "JSON estruturado da triagem" });
  json.textContent = JSON.stringify(triage, null, 2);

  const engine = triage.engine || {};
  const foot = el(
    "div",
    { class: "triage-card__foot" },
    el("span", {
      text: `${triage.model_version} · regras ${engine.rules} · base ${engine.knowledge} · IA generativa: ${
        engine.llm === "local-fallback" ? "motor local (sem LLM)" : engine.llm
      }`,
    }),
    el("details", {}, el("summary", { text: "Ver saída JSON estruturada" }), json),
    el("span", { text: triage.disclaimer }),
    showShare && triage.shared_with_clinic
      ? el("span", { class: "badge badge--ok", "data-share-badge": "", text: "Compartilhada com a clínica" })
      : null,
  );

  card.append(head, action, dl, confidence, foot);
  return card;
}

/** Marca um cartão já renderizado como compartilhado (quando o tutor autoriza depois). */
export function markCardShared(card) {
  if (card.querySelector("[data-share-badge]")) return;
  card.querySelector(".triage-card__foot").append(
    el("span", { class: "badge badge--ok", "data-share-badge": "", text: "Compartilhada com a clínica" }),
  );
}

/**
 * Cartão do resultado EXPERIMENTAL da análise de foto (somente cães).
 * @param {object} vision resposta de /api/vision/analyze
 * @returns {HTMLElement}
 */
export function renderVisionCard(vision) {
  const card = el("article", {
    class: "triage-card",
    "data-priority": vision.priority,
    "aria-label": "Resultado experimental da análise de foto",
  });
  card.append(
    el(
      "div",
      { class: "triage-card__head" },
      el(
        "div",
        {},
        el("p", { class: "eyebrow", text: "Análise de foto · experimental" }),
        el("h3", { class: "triage-card__title", text: vision.top_label_pt }),
      ),
      el("span", { class: "triage-card__spacer" }),
      priorityBadge(vision.priority),
    ),
  );

  const bars = el("div", { class: "bars", role: "list", "aria-label": "Probabilidades por classe" });
  vision.predictions.forEach((p, index) => {
    const pct = Math.round(p.probability * 100);
    const fill = el("div", { class: "bar__fill" });
    fill.style.width = `${pct}%`;
    bars.append(
      el(
        "div",
        { class: `bar${index === 0 ? " bar--top" : ""}`, role: "listitem" },
        el("span", { text: p.label_pt }),
        el("span", { class: "bar__pct", text: `${pct}%` }),
        el("div", { class: "bar__track" }, fill),
      ),
    );
  });
  card.append(bars);

  if (vision.low_confidence) {
    card.append(el("p", { class: "triage-card__action" }, icon("alert", 20), el("span", { text: "Confiança baixa: considere uma nova foto, com boa luz." })));
  }
  card.append(
    el(
      "div",
      { class: "triage-card__foot" },
      el("span", { text: `${vision.model_version} · só o hash da imagem é guardado; a foto não é armazenada` }),
      el("span", { text: vision.disclaimer }),
    ),
  );
  return card;
}
