// Helpers de interface. Regra de ouro: nenhum dado do usuário passa por innerHTML — só textContent/atributos.

const SVG_NS = "http://www.w3.org/2000/svg";

const ICONS = {
  paw: [
    ["ellipse", { cx: 12, cy: 15.5, rx: 4.6, ry: 3.8 }],
    ["ellipse", { cx: 5.6, cy: 11, rx: 2.1, ry: 2.8, transform: "rotate(-18 5.6 11)" }],
    ["ellipse", { cx: 9.6, cy: 6.5, rx: 2.1, ry: 2.9 }],
    ["ellipse", { cx: 14.4, cy: 6.5, rx: 2.1, ry: 2.9 }],
    ["ellipse", { cx: 18.4, cy: 11, rx: 2.1, ry: 2.8, transform: "rotate(18 18.4 11)" }],
  ],
  send: [["path", { d: "M3.4 11.2 20 4l-5.6 16-3.2-6.6-7.8-2.2Z", fill: "none", stroke: "currentColor", "stroke-width": 1.9, "stroke-linejoin": "round" }]],
  camera: [
    ["path", { d: "M4 8.5h3l1.5-2h7l1.5 2h3v10H4z", fill: "none", stroke: "currentColor", "stroke-width": 1.8, "stroke-linejoin": "round" }],
    ["circle", { cx: 12, cy: 13.4, r: 3.2, fill: "none", stroke: "currentColor", "stroke-width": 1.8 }],
  ],
  alert: [
    ["path", { d: "M12 4 21.5 20h-19z", fill: "none", stroke: "currentColor", "stroke-width": 1.9, "stroke-linejoin": "round" }],
    ["path", { d: "M12 10v4.4M12 17.2v.1", fill: "none", stroke: "currentColor", "stroke-width": 2, "stroke-linecap": "round" }],
  ],
  check: [["path", { d: "m5 12.5 4.5 4.5L19 7.5", fill: "none", stroke: "currentColor", "stroke-width": 2.2, "stroke-linecap": "round", "stroke-linejoin": "round" }]],
  clock: [
    ["circle", { cx: 12, cy: 12, r: 8.2, fill: "none", stroke: "currentColor", "stroke-width": 1.8 }],
    ["path", { d: "M12 7.5V12l3 2", fill: "none", stroke: "currentColor", "stroke-width": 1.8, "stroke-linecap": "round" }],
  ],
  shield: [["path", { d: "M12 3.5 19 6v5.5c0 4.2-2.8 7.2-7 9-4.2-1.8-7-4.8-7-9V6z", fill: "none", stroke: "currentColor", "stroke-width": 1.8, "stroke-linejoin": "round" }]],
  plus: [["path", { d: "M12 5v14M5 12h14", fill: "none", stroke: "currentColor", "stroke-width": 2, "stroke-linecap": "round" }]],
  logout: [["path", { d: "M10 5H5v14h5M14 8l4 4-4 4M18 12H9", fill: "none", stroke: "currentColor", "stroke-width": 1.8, "stroke-linecap": "round", "stroke-linejoin": "round" }]],
  heart: [["path", { d: "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.4A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z", fill: "none", stroke: "currentColor", "stroke-width": 1.8, "stroke-linejoin": "round" }]],
};

export function icon(name, size = 20) {
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("focusable", "false");
  if (name === "paw") svg.setAttribute("fill", "currentColor");
  for (const [tag, attrs] of ICONS[name] || []) {
    const node = document.createElementNS(SVG_NS, tag);
    for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, String(v));
    svg.append(node);
  }
  return svg;
}

/** Cria um elemento com classes, atributos e filhos (strings viram texto, nunca HTML). */
export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "text") node.textContent = value;
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

/** Como Element.append, mas ignora null/undefined/false (o append nativo escreveria o texto "null"). */
export function put(parent, ...children) {
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    parent.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return parent;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

export const PRIORITY_LABEL = { baixa: "Baixa", media: "Média", alta: "Alta", emergencia: "Emergência" };
export const PRIORITY_LEVEL = { baixa: 1, media: 2, alta: 3, emergencia: 4 };

/** Títulos de eventos trazem o valor técnico da prioridade ("emergencia"); na tela vira o rótulo com acento. */
export function eventTitle(title) {
  return title.replace(/prioridade (baixa|media|alta|emergencia)\b/g, (_, value) => `prioridade ${PRIORITY_LABEL[value].toLowerCase()}`);
}

export function priorityBadge(priority) {
  return el("span", { class: "prio", "data-priority": priority, text: PRIORITY_LABEL[priority] || priority });
}

export function meter(priority) {
  const level = PRIORITY_LEVEL[priority] || 1;
  return el(
    "div",
    { class: "meter", "data-priority": priority, role: "img", "aria-label": `Prioridade ${PRIORITY_LABEL[priority]}, nível ${level} de 4` },
    [1, 2, 3, 4].map(() => el("span", { class: "meter__seg" })),
  );
}

export function toast(message, kind = "info") {
  let region = document.getElementById("toasts");
  if (!region) {
    region = el("div", { id: "toasts", class: "toast-region", role: "status", "aria-live": "polite" });
    document.body.append(region);
  }
  const node = el("div", { class: `toast${kind === "error" ? " toast--error" : ""}`, text: message });
  region.append(node);
  setTimeout(() => node.remove(), kind === "error" ? 7000 : 4200);
}

const dateFormat = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
const dayFormat = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", year: "numeric" });

export function formatDateTime(iso) {
  return dateFormat.format(new Date(iso));
}

export function formatDay(isoDate) {
  return dayFormat.format(new Date(`${isoDate}T12:00:00`));
}

export function formatAge(months) {
  if (months === null || months === undefined) return "idade não informada";
  if (months < 12) return `${months} ${months === 1 ? "mês" : "meses"}`;
  const years = Math.floor(months / 12);
  const rest = months % 12;
  return rest === 0 ? `${years} ${years === 1 ? "ano" : "anos"}` : `${years}a ${rest}m`;
}

export function speciesLabel(species) {
  return species === "cat" ? "Gato" : "Cachorro";
}

export function initials(name) {
  return name.trim().slice(0, 1).toUpperCase();
}

/** Guarda preferências leves; localStorage pode estar bloqueado, então nunca é requisito. */
export const prefs = {
  get(key) {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* sem armazenamento: segue sem lembrar */
    }
  },
};
