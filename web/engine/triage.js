// Motor de triagem determinístico em JavaScript — porte fiel de br.com.zelo.ai (Java) para rodar na Vercel,
// direto no navegador. Mesma base de conhecimento (triage_knowledge.json) e mesmas regras; a paridade com o
// Java é conferida por web/test/parity.test.mjs contra os conjuntos ouro, holdout e cego.

export const RULES_VERSION = "zelo-rules-1.0";

export const Priority = Object.freeze({
  BAIXA: { wire: "baixa", level: 1 },
  MEDIA: { wire: "media", level: 2 },
  ALTA: { wire: "alta", level: 3 },
  EMERGENCIA: { wire: "emergencia", level: 4 },
});
const BY_LEVEL = [null, Priority.BAIXA, Priority.MEDIA, Priority.ALTA, Priority.EMERGENCIA];
export const priorityFromLevel = (level) => BY_LEVEL[Math.max(1, Math.min(4, level))];
export const priorityFromWire = (wire) => Object.values(Priority).find((p) => p.wire === wire);

// ---------------------------------------------------------------------------------- texto
const DIACRITICS = /\p{M}+/gu;
const CLAUSE_BREAK = /[.,;:!?\n\r()[\]{}…]+/g;
const REPEATED = /([a-z])\1{2,}/g;
const NON_ALNUM = /[^a-z0-9| ]+/g;
const SPACES = /\s+/g;

/** Minúsculas, sem acentos, pontuação de fim de oração vira " | " (espelha TextNormalizer.java). */
export function normalize(text) {
  if (text == null) return "";
  let s = String(text).toLowerCase().normalize("NFD").replace(DIACRITICS, "");
  s = s.replace(REPEATED, "$1").replace(CLAUSE_BREAK, " | ").replaceAll("'", " ").replace(NON_ALNUM, " ");
  return s.replace(SPACES, " ").trim();
}

// ------------------------------------------------------------------------ base de conhecimento
export class KnowledgeBase {
  constructor(file) {
    this.version = file.version;
    this.d2 = file.d2;
    this.symptoms = file.symptoms.map((e) => ({
      id: e.id,
      label: e.label,
      category: e.category,
      baseSeverity: e.severity,
      severityCat: e.severityCat ?? null,
      severityDog: e.severityDog ?? null,
      family: e.family ?? null,
      patterns: e.patterns.map((p) => new RegExp(p, "g")),
      typoWords: e.typoWords ?? [],
      d2: e.d2 ?? null,
    }));
    this.byId = new Map(this.symptoms.map((s) => [s.id, s]));
    this.breeds = file.breeds ?? {};
    this.historyTags = file.historyTags ?? [];
  }

  symptom(id) {
    const def = this.byId.get(id);
    if (!def) throw new Error(`Sintoma desconhecido: ${id}`);
    return def;
  }

  knows(id) {
    return this.byId.has(id);
  }

  isBrachycephalic(species, breed) {
    if (!breed) return false;
    return (this.breeds[species] ?? []).some((b) => b.brachycephalic && b.id.toLowerCase() === breed.trim().toLowerCase());
  }

  recognizesWord(token) {
    return this.symptoms.some((s) => s.patterns.some((p) => test(p, token)));
  }

  typoVocabulary() {
    return new Set(this.symptoms.flatMap((s) => s.typoWords).filter((w) => w.length >= 6));
  }
}

export function severityFor(def, species) {
  const override = species === "cat" ? def.severityCat : def.severityDog;
  return override ?? def.baseSeverity;
}

export function referenceFrequency(def, species) {
  return def.d2?.prevalence?.[species] ?? 0;
}

/** Regex com flag g: sempre testar a partir do início (evita o lastIndex "grudado"). */
function test(regex, text) {
  regex.lastIndex = 0;
  return regex.test(text);
}

// ---------------------------------------------------------------------------- pet
const PUPPY_MONTHS = 6;
const LARGE_DOG_KG = 25;

export function petContext({ petId = null, name = "seu pet", species, ageMonths = null, breed = null,
  brachycephalic = false, weightKg = null, female = false, historyTags = [] }) {
  return { petId, name, species, ageMonths, breed, brachycephalic, weightKg, female, historyTags: new Set(historyTags) };
}

export const isPuppy = (pet) => pet.ageMonths != null && pet.ageMonths < PUPPY_MONTHS;
export function isSenior(pet) {
  if (pet.ageMonths == null) return false;
  const years = Math.floor(pet.ageMonths / 12);
  if (pet.species === "cat") return years >= 11;
  const large = pet.weightKg != null && pet.weightKg > LARGE_DOG_KG;
  return years >= (large ? 7 : 8);
}
export const pronoun = (pet) => (pet.female ? "ela" : "ele");
export const article = (pet) => (pet.female ? "a" : "o");

// ------------------------------------------------------------------- correção de digitação
const MIN_TYPO_LENGTH = 6;
const LONG_WORD = 9;

export class TypoCorrector {
  constructor(vocabulary, recognized = () => false) {
    this.vocabulary = [...vocabulary];
    this.vocabSet = new Set(vocabulary);
    this.recognized = recognized;
  }

  correct(normalized) {
    const tokens = normalized.split(" ");
    const corrections = [];
    tokens.forEach((token, i) => {
      if (token.length < MIN_TYPO_LENGTH || this.vocabSet.has(token) || this.recognized(token)) return;
      const best = this.closest(token);
      if (best != null) {
        corrections.push(`${token}→${best}`);
        tokens[i] = best;
      }
    });
    return { text: tokens.join(" "), corrections };
  }

  closest(token) {
    const maxDistance = token.length >= LONG_WORD ? 2 : 1;
    let best = null;
    let bestDistance = maxDistance + 1;
    for (const word of this.vocabulary) {
      if (word[0] !== token[0] || Math.abs(word.length - token.length) > maxDistance) continue;
      const d = damerau(token, word);
      if (d < bestDistance) {
        bestDistance = d;
        best = word;
      }
    }
    return best;
  }
}

export function damerau(a, b) {
  const d = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0));
  for (let i = 0; i <= a.length; i++) d[i][0] = i;
  for (let j = 0; j <= b.length; j++) d[0][j] = j;
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
    }
  }
  return d[a.length][b.length];
}

// ------------------------------------------------------------------ extração com negação
const NEGATION_WINDOW = 3;
const RED_FLAG_NEGATION_WINDOW = 2;
const RESOLUTION_WINDOW = 3;
const RECURRENCE_WINDOW = 4;
const NEGATORS = new Set(["nao", "sem", "nunca", "nem", "nenhum", "nenhuma", "jamais", "nada"]);
const STOPPED_VERBS = new Set(["parou", "para", "deixou", "deixa"]);
const RESOLVED_MARKERS = new Set(["passou", "sumiu", "cessou", "acabou", "parou", "melhorou"]);
const RECURRENCE = /\b(?:voltou|voltaram|volta|novamente|de novo|outra vez|piorou)\b/;
const BREAKERS = new Set(["mas", "porem", "entretanto", "contudo", "todavia", "e", "ou", "|"]);

export const isRedFlag = (m) => m.severity >= Priority.EMERGENCIA.level;

function mention(def, species, evidence, start, end) {
  return { id: def.id, label: def.label, category: def.category, severity: severityFor(def, species), evidence, negated: false, start, end };
}

function tokenSpans(text, from, to) {
  const spans = [];
  const region = text.slice(from, to);
  for (const m of region.matchAll(/\S+/g)) spans.push([from + m.index, from + m.index + m[0].length]);
  return spans;
}

export class SymptomExtractor {
  constructor(knowledge, typoCorrector) {
    this.knowledge = knowledge;
    this.typoCorrector = typoCorrector;
    this.catalogIndex = new Map(knowledge.symptoms.map((s, i) => [s.id, i]));
  }

  extract(rawText, species) {
    const typo = this.typoCorrector.correct(normalize(rawText));
    const text = typo.text;
    const hits = this.dropContained(this.findHits(text, species));
    const negationApplied = hits.map((h) => ({ ...h, negated: this.isNegated(text, h, hits) }));
    // a família é resolvida DEPOIS da negação: "vomitou, sem sangue no vômito" mantém o vômito presente
    const mentions = collapseById(this.resolveFamilies(negationApplied));
    return {
      normalizedText: text,
      mentions,
      typoCorrections: typo.corrections,
      active: () => mentions.filter((m) => !m.negated),
      negated: () => mentions.filter((m) => m.negated),
    };
  }

  findHits(text, species) {
    const hits = [];
    for (const def of this.knowledge.symptoms) {
      for (const pattern of def.patterns) {
        pattern.lastIndex = 0;
        for (const m of text.matchAll(pattern)) {
          hits.push(mention(def, species, m[0].trim(), m.index, m.index + m[0].length));
        }
      }
    }
    return hits;
  }

  dropContained(hits) {
    const ordered = [...hits].sort((a, b) => (b.end - b.start) - (a.end - a.start)
      || b.severity - a.severity
      || this.catalogIndex.get(a.id) - this.catalogIndex.get(b.id));
    const accepted = [];
    for (const hit of ordered) {
      const swallowed = accepted.some((a) => a.start <= hit.start && hit.end <= a.end && a.severity >= hit.severity);
      if (!swallowed) accepted.push(hit);
    }
    return accepted;
  }

  resolveFamilies(hits) {
    const strongest = new Map();
    for (const hit of hits) {
      const family = this.knowledge.symptom(hit.id).family;
      if (family != null && !hit.negated) {
        const current = strongest.get(family);
        if (!current || hit.severity > current.severity) strongest.set(family, hit);
      }
    }
    return hits.filter((h) => {
      const family = this.knowledge.symptom(h.id).family;
      const top = family == null ? null : strongest.get(family);
      if (!top) return true;
      return h.negated ? h.severity > top.severity : top.id === h.id;
    });
  }

  isNegated(text, hit, all) {
    return negatedByPrecedingTerm(text, hit, all) || resolvedByFollowingTerm(text, hit);
  }
}

function isNegator(token, previous, next) {
  if (STOPPED_VERBS.has(token)) return next === "de" && previous !== "nao"; // "não parou de espirrar" = continua
  if (token === "nao") return next !== "sei" && !STOPPED_VERBS.has(next);  // "não sei se foi…" = incerteza
  return NEGATORS.has(token);
}

function negatedByPrecedingTerm(text, hit, all) {
  const clauseStart = text.lastIndexOf("|", hit.start) + 1;
  if (clauseStart >= hit.start) return false;
  const tokens = tokenSpans(text, clauseStart, hit.start);
  const words = tokens.map(([s, e]) => text.slice(s, e));
  const window = isRedFlag(hit) ? RED_FLAG_NEGATION_WINDOW : NEGATION_WINDOW;
  for (let i = tokens.length - 1, seen = 0; i >= 0 && seen < window; i--, seen++) {
    const tokenStart = tokens[i][0];
    if (BREAKERS.has(words[i]) || all.some((o) => o !== hit && o.start <= tokenStart && tokenStart < o.end)) return false;
    if (isNegator(words[i], words[i - 1] ?? "", words[i + 1] ?? "")) return true;
  }
  return false;
}

function resolvedByFollowingTerm(text, hit) {
  const clauseEnd = text.indexOf("|", hit.end);
  const end = clauseEnd < 0 ? text.length : clauseEnd;
  const tokens = tokenSpans(text, hit.end, end);
  const word = (i) => (i >= 0 && i < tokens.length ? text.slice(tokens[i][0], tokens[i][1]) : "");
  for (let i = 0; i < tokens.length && i < RESOLUTION_WINDOW; i++) {
    const token = word(i);
    if (BREAKERS.has(token)) return false;
    if (!RESOLVED_MARKERS.has(token)) continue;
    const previous = word(i - 1);
    const next = word(i + 1);
    const lastWord = Math.min(tokens.length - 1, i + RECURRENCE_WINDOW);
    const afterMarker = text.slice(tokens[i][1], tokens[lastWord][1]);
    const continues = NEGATORS.has(previous) || previous === "ainda"
      || (token === "parou" && next === "de")
      || (token === "passou" && next === "mal")
      || RECURRENCE.test(afterMarker);
    return !continues;
  }
  return false;
}

function collapseById(mentions) {
  const merged = new Map();
  [...mentions].sort((a, b) => a.start - b.start).forEach((m) => {
    const a = merged.get(m.id);
    if (!a) merged.set(m.id, m);
    else if (a.negated && !m.negated) merged.set(m.id, m);
  });
  return [...merged.values()];
}

// ---------------------------------------------------------------------------- duração
const NUMBER_WORDS = { um: 1, uma: 1, dois: 2, duas: 2, tres: 3, quatro: 4, cinco: 5, seis: 6, sete: 7, oito: 8, nove: 9, dez: 10, doze: 12, quinze: 15, vinte: 20 };
const NUMBER = "(\\d{1,3}|um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez|doze|quinze|vinte)";
const QUANTIFIED = new RegExp(`\\b(mais de )?${NUMBER}(?: ou ${NUMBER})? (horas?|dias?|semanas?|mes|meses)\\b`, "g");
const AGE_CUE_LONG = /\b(?:tem|com|de|idade|aos)\s*$/;
const AGE_CUE_SHORT = /\b(?:idade|aos|(?:filhote|filhota|bebe|cachorrinh[oa]|gatinh[oa]|cachorro|cao|gato|gata|cadela)\s+com)\s*$/;
const AGE_AFTER = /^ (?:de )?(?:idade|vida|nascid[oa])/;
const CONTEXT_CHARS = 20;
const LANDMARKS = [
  [/\b(?:hoje|esta manha|essa manha|de manha|agora|ha pouco|faz pouco)\b/, 8],
  [/\b(?:madrugada|noite passada|ontem a noite)\b/, 14],
  [/\bontem\b/, 24],
  [/\banteontem\b/, 48],
  [/\b(?:varios|alguns) dias\b|\b(?:ha|faz) dias\b/, 72],
  [/\b(?:(?:ha|faz|por|durante) (?:varias |algumas |muitas )?|ultimas )semanas\b/, 336],
  [/\b(?:(?:ha|faz|por|durante) (?:varios |alguns |muitos )?|ultimos )meses\b/, 720],
];

/** Maior duração mencionada, em horas (a mais conservadora), ou null. */
export function parseHours(normalized) {
  let best = -1;
  for (const m of normalized.matchAll(QUANTIFIED)) {
    if (!isAge(normalized, m)) best = Math.max(best, quantifiedHours(m));
  }
  for (const [pattern, hours] of LANDMARKS) {
    if (pattern.test(normalized)) best = Math.max(best, hours);
  }
  return best < 0 ? null : best;
}

function isAge(text, m) {
  const start = m.index;
  const end = m.index + m[0].length;
  const before = text.slice(Math.max(0, start - CONTEXT_CHARS), start);
  const after = text.slice(end, Math.min(text.length, end + 14));
  if (after.startsWith(" que") || after.startsWith(" atras")) return false;
  if (AGE_AFTER.test(after)) return true;
  if (after.startsWith(" de ") || after.startsWith(" sem ")) return false;
  const longUnit = m[4].startsWith("semana") || m[4].startsWith("mes");
  return (longUnit ? AGE_CUE_LONG : AGE_CUE_SHORT).test(before);
}

function quantifiedHours(m) {
  const number = (t) => (/^\d+$/.test(t) ? parseInt(t, 10) : NUMBER_WORDS[t]);
  const first = number(m[2]);
  const amount = m[3] == null ? first : Math.max(first, number(m[3]));
  const unit = m[4];
  const unitHours = unit.startsWith("hora") ? 1 : unit.startsWith("dia") ? 24 : unit.startsWith("semana") ? 168 : 720;
  const hours = amount * unitHours;
  return m[1] != null ? hours + 1 : hours;
}

// -------------------------------------------------------------------- regras de prioridade
const set = (...v) => new Set(v);
const VOMIT = set("vomito", "vomito_repetido", "vomito_sangue");
const DIARRHEA = set("diarreia", "diarreia_sangue", "fezes_escuras");
const COMBINES_WITH_LETHARGY = set("vomito", "vomito_repetido", "diarreia", "diarreia_sangue", "febre", "perda_apetite", "dor",
  "dor_abdominal", "tosse", "respiracao_ofegante", "recusa_agua", "desidratacao", "tremores");
const PERSISTENCE_CANDIDATES = set("vomito", "vomito_repetido", "diarreia", "perda_apetite", "apatia", "febre", "fraqueza");
const BLOAT_COMPANIONS = set("salivacao_excessiva", "dor", "dor_abdominal", "apatia", "respiracao_ofegante", "vomito", "fraqueza",
  "mudanca_comportamento");
const CARDIAC_SIGNS = set("tosse", "intolerancia_exercicio", "respiracao_ofegante", "apatia", "sibilo");
const RENAL_SIGNS = set("sede_urina_excessiva", "vomito", "vomito_repetido", "perda_apetite", "apatia", "xixi_fora_lugar");
const BRACHY_SIGNS = set("respiracao_ofegante", "sibilo", "tosse");

/** Camada determinística de segurança: a prioridade é o MÁXIMO entre severidade e regras (espelha PriorityEngine.java). */
export function decide(active, pet, hours) {
  const ids = new Set(active.map((m) => m.id));
  const reasons = new Set();
  const factors = new Set();
  const rules = new Set();
  const redFlags = [];
  let level = 1;
  const has = (id) => ids.has(id);
  const anyOf = (s) => [...ids].some((id) => s.has(id));
  const longerThan = (h) => hours != null && hours > h;
  const raiseTo = (target, rule, reason) => {
    const changed = target.level > level;
    if (changed) level = target.level;
    if (target.level >= Priority.MEDIA.level) {
      reasons.add(reason);
      rules.add(rule);
    }
    return changed;
  };
  const bump = (rule, reason, factor) => {
    if (level < Priority.ALTA.level) {
      level++;
      reasons.add(reason);
      rules.add(rule);
      factors.add(factor);
    }
  };

  // base
  for (const m of active) {
    level = Math.max(level, m.severity);
    if (isRedFlag(m)) {
      redFlags.push(m.id);
      reasons.add(`Sinal de alerta relatado: ${m.label.toLowerCase()}`);
      rules.add("R1_SINAL_DE_ALERTA");
    }
  }
  if (redFlags.length === 0 && level >= Priority.ALTA.level) {
    const m = active.find((x) => x.severity >= Priority.ALTA.level);
    if (m) reasons.add(`Sinal que exige avaliação veterinária: ${m.label.toLowerCase()}`);
    rules.add("R0_SEVERIDADE_DO_SINTOMA");
  }
  // combinações
  if (has("apatia") && anyOf(COMBINES_WITH_LETHARGY)) {
    raiseTo(Priority.ALTA, "R2_ABATIMENTO_COMBINADO", "Abatimento junto de outros sinais indica que o organismo está sendo afetado");
  }
  if (anyOf(VOMIT) && anyOf(DIARRHEA)) {
    const fragile = isPuppy(pet) || isSenior(pet) || longerThan(24) || has("vomito_repetido");
    raiseTo(fragile ? Priority.ALTA : Priority.MEDIA, "R3_VOMITO_E_DIARREIA", "Vômito e diarreia juntos aumentam o risco de desidratação");
  }
  if (pet.species === "dog" && has("abdomen_distendido") && anyOf(BLOAT_COMPANIONS)) {
    redFlags.push("abdomen_distendido");
    raiseTo(Priority.EMERGENCIA, "R12_SUSPEITA_DE_TORCAO",
      "Barriga inchada junto de outros sinais pode indicar torção de estômago, que é emergência");
  }
  if (active.filter((m) => m.severity >= Priority.MEDIA.level).length >= 3) {
    raiseTo(Priority.ALTA, "R4_VARIOS_SINAIS", "Vários sinais ao mesmo tempo merecem avaliação profissional");
  }
  // persistência
  if (longerThan(48) && anyOf(PERSISTENCE_CANDIDATES)) {
    bump("R5_PERSISTENCIA", "Sinais que duram mais de 48 horas não devem ser apenas observados", "duração acima de 48 horas");
  }
  if (hours != null && hours >= 168 && active.length > 0) {
    raiseTo(Priority.MEDIA, "R5_PERSISTENCIA_SEMANA", "Sinais há uma semana ou mais pedem consulta");
  }
  if (pet.species === "cat" && has("perda_apetite") && hours != null && hours >= 24) {
    raiseTo(Priority.ALTA, "R6_GATO_SEM_COMER", "Gatos que ficam sem comer por 24 horas ou mais precisam de avaliação");
  }
  // contexto do pet
  const significant = active.some((m) => m.severity >= Priority.MEDIA.level);
  if (significant && isPuppy(pet)) bump("R7_FILHOTE", `${pet.name} é filhote, e filhotes pioram e se desidratam mais rápido`, "filhote");
  if (significant && isSenior(pet)) bump("R8_IDOSO", `${pet.name} já é idoso, o que pede mais cautela`, "idoso");
  if (pet.historyTags.has("cardiopatia") && anyOf(CARDIAC_SIGNS)) {
    bump("R9_HISTORICO_CARDIACO", "O histórico cardíaco torna esses sinais mais relevantes", "histórico cardíaco");
  }
  if ((pet.historyTags.has("doenca_renal") || pet.historyTags.has("doenca_cronica")) && anyOf(RENAL_SIGNS)) {
    bump("R9_HISTORICO_RENAL_CRONICO", "O histórico de saúde torna esses sinais mais relevantes",
      pet.historyTags.has("doenca_renal") ? "histórico renal" : "doença crônica");
  }
  if (isPuppy(pet) && pet.historyTags.has("nao_vacinado") && (anyOf(VOMIT) || anyOf(DIARRHEA))
    && raiseTo(Priority.ALTA, "R10_FILHOTE_SEM_VACINA", "Filhote sem vacinas em dia com vômito ou diarreia precisa de avaliação rápida")) {
    factors.add("sem vacinação em dia");
  }
  if (pet.brachycephalic && anyOf(BRACHY_SIGNS)) {
    bump("R11_BRAQUICEFALICO", "Raças de focinho curto têm mais risco respiratório", "raça braquicefálica");
  }
  return {
    priority: priorityFromLevel(active.length === 0 ? 1 : level),
    reasons: [...reasons], redFlagIds: redFlags, contextFactors: [...factors], rules: [...rules],
  };
}

// --------------------------------------------------------------------------- confiança
export function estimateConfidence(active, durationKnown, profileKnown, typoCorrections, redFlag) {
  if (active.length === 0) return 0.2;
  const vague = active.every((m) => m.id === "mal_estar_inespecifico");
  let score = 0.35 + 0.15 * Math.min(active.length, 3);
  score += durationKnown ? 0.08 : 0;
  score += profileKnown ? 0.05 : 0;
  score -= 0.05 * Math.min(typoCorrections, 2);
  if (vague) score = Math.min(score, 0.45);
  if (redFlag) score = Math.max(score, 0.9);
  return Math.round(Math.min(score, 0.97) * 100) / 100;
}

// ----------------------------------------------------------------------- orquestração
const REASSURING = /\b(?:esta|ta|estao|anda)\s+(?:bem|normal|otim[oa]|tranquil[oa]|animad[oa]|feliz)\b|\bcomendo bem\b|\bbrincando\b|\bnormal\b|\bsem problemas?\b|\btudo bem\b/;
/** "não está animado" não é tranquilizador: a frase negada sai antes do teste de tranquilidade. */
const NEGATED_PHRASE = /\b(?:nao|nunca|nem)\s+(?:(?:esta|estao|anda|ta|fica|tem|ficou)\s+)?\w+/g;
const REVIEW_CONFIDENCE_THRESHOLD = 0.6;

export function humanReviewRequired(a) {
  return a.priority.level >= Priority.ALTA.level || a.confidence < REVIEW_CONFIDENCE_THRESHOLD || a.vague || a.unrecognized;
}

/** O LLM só soma: se a segunda análise ficar abaixo da primeira, a prioridade da primeira prevalece. */
export function withFloor(analysis, floor) {
  if (analysis.priority.level >= floor.priority.level) return analysis;
  const reasons = [...floor.reasons, ...analysis.reasons.filter((r) => !floor.reasons.includes(r))];
  return { ...analysis, priority: floor.priority, reasons, rules: [...floor.rules, "R14_PISO_DETERMINISTICO"],
    unrecognized: analysis.unrecognized || floor.unrecognized };
}

export class TriageEngine {
  constructor(knowledge) {
    this.knowledge = knowledge;
    const corrector = new TypoCorrector(knowledge.typoVocabulary(), (t) => knowledge.recognizesWord(t));
    this.extractor = new SymptomExtractor(knowledge, corrector);
  }

  /**
   * @param priorActive   sintomas já coletados nos turnos anteriores
   * @param priorDuration duração já informada (ou null)
   * @param extraSymptoms sintomas sugeridos pelo LLM: entram como presentes e só podem AUMENTAR a prioridade
   */
  analyze(text, pet, priorActive = [], priorDuration = null, extraSymptoms = []) {
    const extraction = this.extractor.extract(text, pet.species);
    const combined = [...extraction.mentions];
    extraSymptoms.filter((e) => !combined.some((m) => m.id === e.id)).forEach((e) => combined.push(e));
    const active = mergeMentions(priorActive, combined);
    const parsed = parseHours(extraction.normalizedText);
    const duration = parsed == null ? priorDuration : priorDuration == null ? parsed : Math.max(priorDuration, parsed);
    const decision = decide(active, pet, duration);
    const negated = extraction.negated();
    const withoutNegations = extraction.normalizedText.replace(NEGATED_PHRASE, " ");
    const unrecognized = active.length === 0 && negated.length === 0 && extraction.normalizedText.trim() !== ""
      && !REASSURING.test(withoutNegations);
    if (unrecognized) {
      return {
        normalizedText: extraction.normalizedText, active: [], negated: [], priority: Priority.MEDIA,
        reasons: ["Não consegui identificar sinais específicos no relato; por precaução, a orientação é avaliar com a clínica"],
        redFlagIds: [], contextFactors: [], rules: ["R13_RELATO_NAO_RECONHECIDO"], durationHours: duration,
        confidence: 0.2, vague: false, unrecognized: true, typoCorrections: extraction.typoCorrections,
      };
    }
    const vague = active.length > 0 && active.every((m) => m.id === "mal_estar_inespecifico");
    const confidence = estimateConfidence(active, duration != null, pet.ageMonths != null,
      extraction.typoCorrections.length, decision.redFlagIds.length > 0);
    return {
      normalizedText: extraction.normalizedText, active, negated, priority: decision.priority, reasons: decision.reasons,
      redFlagIds: decision.redFlagIds, contextFactors: decision.contextFactors, rules: decision.rules,
      durationHours: duration, confidence, vague, unrecognized: false, typoCorrections: extraction.typoCorrections,
    };
  }

  /** Sintoma do catálogo como menção "identificada pela IA" (sugestão do LLM). */
  aiMention(id, species) {
    const def = this.knowledge.symptom(id);
    return mention(def, species, "(identificado pela IA)", 0, 0);
  }
}

/** Une o que já se sabia com o que veio agora; uma negação nova ("não vomita mais") remove o sintoma. */
function mergeMentions(prior, current) {
  const merged = new Map();
  prior.forEach((m) => merged.set(m.id, m));
  for (const m of current) {
    if (m.negated) merged.delete(m.id);
    else merged.set(m.id, m);
  }
  return [...merged.values()];
}
