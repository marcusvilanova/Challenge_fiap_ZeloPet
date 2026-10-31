// Conversa do Zelo em JavaScript — porte de br.com.zelo.chat (IntentClassifier, ReplyComposer, ConversationFlow).
import { article, normalize, Priority, pronoun, withFloor } from "./triage.js";

// ------------------------------------------------------------------------------ intenções
// Consentimento (LGPD) nunca é inferido de linguagem solta: "sim" só vale como frase curta; pedido de
// compartilhar só vale como comando explícito, sem negação e sem ser pergunta.
const NEGATOR = /\b(?:nao|nunca|jamais|nem|sem|dispenso|depois|deixa)\b/;
const SHARE_CONTEXT = /compartilh\w*|\bclinica\b|autoriz\w*|\benviar?\b/;
const NO_PLAIN = /^(?:nao|agora nao|depois|talvez depois|dispenso|deixa|nao quero|nao obrigad[oa]|nao precisa)(?: \|)?(?: obrigad[oa])?$/;
const YES_STRICT = /^(?:sim|claro|pode|pode sim|quero|quero sim|aceito|autorizo|ok|okay|com certeza|certo|beleza)(?: \|)?(?: por favor)?(?: compartilhar(?: com a clinica)?)?$/;
const SHARE_STRICT = /^(?:(?:eu )?(?:quero|gostaria de|pode|vou|por favor)\s+)*(?:compartilhar|enviar|mandar)\b(?:\s+\w+){0,6}\s+(?:para|pra|com)\s+(?:a\s+)?clinica(?:\s+\w+){0,2}$/;
const MEDICATION = new RegExp(
  "\\b(?:posso|pode|podemos|devo|deveria)\\s+(?:\\w+\\s+){0,3}(?:dar|usar|colocar|passar|tomar|medicar|aplicar)\\b"
  + "|\\bqual\\s+(?:\\w+\\s+){0,2}(?:remedio|medicamento|dose|dosagem|antibiotico)\\b"
  + "|\\bque\\s+(?:remedio|medicamento)\\b|\\bdosagem\\b|\\bdose de\\b|\\bquantas? gotas\\b"
  + "|\\bquantos? (?:ml|comprimidos?)\\b|\\bme (?:indica|recomenda|receita)\\b"
  + "|\\bremedio (?:para|pra) (?:ele|ela|o|a)\\b");
const OTHERS = [
  ["FINISH", /^(?:so isso|e so|e isso|pronto|finalizar|finaliza|terminar|era so isso|nada mais|mais nada|so por enquanto)\b/],
  ["NEW_REPORT", /^(?:relatar|descrever|novo relato|quero relatar|vou relatar|queria relatar)\b/],
  ["THANKS", /\b(?:obrigad[oa]|valeu|agradec\w*|brigad[oa])\b/],
  ["FAREWELL", /^(?:tchau|ate (?:logo|mais|amanha)|flw|falou)\b/],
  ["HELP", /\b(?:ajuda|o que (?:voce )?(?:faz|pode)|como funciona|quem (?:e|eh) voce|para que serve|menu)\b/],
  ["CARE_PLAN", /\b(?:vacina\w*|vermifug\w*|antipulga\w*|proximos? cuidados?|plano de cuidados?|calendario|carteirinha|ciclo zelo)\b/],
  ["GREETING", /^(?:oi+|ola|opa|e ai|bom dia|boa tarde|boa noite|hey|hello|salve)\b/],
];

export function classifyIntent(normalized) {
  if (normalized.trim() === "") return "OTHER";
  const negated = NEGATOR.test(normalized);
  if (NO_PLAIN.test(normalized) || (negated && SHARE_CONTEXT.test(normalized))) return "NO";
  if (MEDICATION.test(normalized)) return "MEDICATION_REQUEST";
  if (!negated && SHARE_STRICT.test(normalized)) return "SHARE";
  if (YES_STRICT.test(normalized)) return "YES";
  for (const [intent, pattern] of OTHERS) if (pattern.test(normalized)) return intent;
  return "OTHER";
}

// ------------------------------------------------------------------------------- respostas
const fmt = (template, ...args) => { let i = 0; return template.replace(/%s/g, () => args[i++] ?? ""); };
const of = (pet) => `${pet.female ? "da " : "do "}${pet.name}`;
const inThe = (pet) => (pet.female ? `na ${pet.name}` : `no ${pet.name}`);
const ending = (pet) => (pet.female ? "a" : "o");

const WATCH_FOR = {
  digestivo: "Procure a clínica antes se houver sangue no vômito ou nas fezes, vômitos que não param, barriga inchada ou se %s ficar muito abatid%s.",
  respiratorio: "Procure atendimento na hora se %s respirar com esforço, de boca aberta, ou se a língua e as gengivas ficarem arroxeadas.",
  pele: "Evite que %s lamba ou coce a região e procure a clínica se a lesão aumentar, sangrar ou tiver mau cheiro.",
  urinario: "Observe se %s consegue urinar e anote a frequência. Se não sair xixi, é urgência.",
  neurologico: "Se houver convulsão, perda de equilíbrio ou desorientação, vá a um pronto-atendimento.",
  ocular: "Evite que %s coce o olho; se ficar fechado, opaco ou muito dolorido, procure atendimento hoje.",
  locomotor: "Limite o esforço, evite escadas e procure atendimento se %s não apoiar a pata ou se houver inchaço.",
};
const FIRST_AID = {
  convulsao: "Afaste objetos que possam machucar, não coloque a mão na boca, mantenha o ambiente calmo e anote a duração.",
  dificuldade_respirar: "Mantenha %s calm%s, em local fresco e arejado, e evite qualquer esforço.",
  ingestao_toxica: "Leve a embalagem, planta ou produto ingerido e não provoque vômito sem orientação veterinária.",
  nao_urina: "Não espere: a obstrução urinária pode ser fatal em poucas horas.",
  dificuldade_urinar: "Não espere: em gatos, dificuldade para urinar pode virar obstrução e ser fatal em poucas horas.",
  golpe_calor: "Leve a um local fresco e molhe o corpo com água em temperatura ambiente (não gelada) durante o transporte.",
  trauma: "Movimente %s o mínimo possível e, se houver sangramento, pressione com um pano limpo.",
  sangramento_intenso: "Pressione o local com um pano limpo e mantenha a pressão durante o transporte.",
  picada_peconhento: "Mantenha %s calm%s e, se possível, fotografe ou anote o animal. Não faça torniquete nem corte o local.",
  abdomen_distendido: "Não ofereça comida nem água.",
  abdomen_agudo: "Não ofereça comida nem água.",
};

export const MEDICATION_REFUSAL_MARK = "Não posso indicar remédios";

export const composer = {
  greeting: (pet, highlight) => {
    const base = `Oi! Eu sou a Zelo, assistente de cuidado ${of(pet)}. Posso entender o que ${pronoun(pet)} está sentindo e orientar o próximo passo, ou mostrar os cuidados que estão chegando.`;
    return highlight == null ? base : `${base}\n\n${highlight}`;
  },
  help: (pet) => `Eu faço uma triagem preliminar: entendo o que você observa ${inThe(pet)}, indico a prioridade e o próximo passo, e posso avisar a clínica se você autorizar.\n\nNão faço diagnóstico nem indico remédios: isso é com o médico-veterinário. Também mostro as vacinas e os cuidados que estão chegando.`,
  thanks: (pet) => `Por nada! Se ${article(pet)} ${pet.name} piorar ou surgir algo novo, é só me contar.`,
  farewell: (pet) => `Até logo! Cuide bem ${of(pet)}. Se algo mudar, volte aqui.`,
  askForReport: (pet) => `Claro. O que você notou ${inThe(pet)}? Pode escrever do seu jeito, por exemplo: "está vomitando e quieto desde ontem".`,
  medicationRefusal: () => "Não posso indicar remédios nem doses. Vários medicamentos humanos, como dipirona, paracetamol e ibuprofeno, são perigosos para cães e gatos, e a dose certa depende do peso e do quadro. Quem define isso com segurança é o médico-veterinário.",
  carePlan: (pet, lines) => (lines.length === 0
    ? `Ainda não há cuidados cadastrados ${of(pet)}. A clínica pode registrá-los na próxima consulta.`
    : `Estes são os próximos cuidados ${of(pet)}:\n${lines.map((l) => `• ${l}`).join("\n")}`),
  askDuration: (pet, a) => `Entendi: ${a.active.slice(0, 5).map((m) => m.label.toLowerCase()).join(", ")}. Desde quando ${pronoun(pet)} está assim?`,
  askIntake: (pet) => `${pronoun(pet)[0].toUpperCase()}${pronoun(pet).slice(1)} está comendo e bebendo água normalmente?`,
  askBreathing: (pet) => `Como está a respiração ${of(pet)}? Ela está tranquila ou ${pronoun(pet)} respira com esforço?`,
  clarify: (pet) => `Não consegui entender isso como um sinal de saúde ${of(pet)}. Pode contar com outras palavras o que ${pronoun(pet)} está sentindo?\n\nSe houver falta de ar, convulsão, desmaio, sangramento forte, suspeita de intoxicação ou se ${pronoun(pet)} não conseguir urinar, procure um pronto-atendimento veterinário agora, sem esperar.`,
  noTriageToShare: (pet) => `Ainda não tenho uma triagem para compartilhar. Conte o que ${pronoun(pet)} está sentindo e eu preparo o resumo.`,
  shareGranted: (pet) => `Pronto. Enviei o resumo ${of(pet)} para a clínica. Um profissional vai revisar e você pode revogar esse compartilhamento quando quiser. Se ${pronoun(pet)} piorar, não espere resposta: procure atendimento.`,
  shareDeclined: () => "Tudo bem, não compartilhei nada com a clínica. A triagem fica salva só no seu histórico, e você pode enviá-la depois.",

  triage(a, pet, highlight, askToShare) {
    let text = headline(a, pet);
    const aid = firstAid(a, pet);
    if (aid) text += `\n\n${aid}`;
    if (a.reasons.length > 0) text += `\n\nPor quê: ${a.reasons[0]}.`;
    if (a.priority.level < Priority.EMERGENCIA.level) text += `\n\n${watchFor(a, pet)}`;
    if (highlight != null && a.priority.level <= Priority.MEDIA.level) text += `\n\n${highlight}`;
    text += "\n\nIsto é uma triagem preliminar e não substitui a avaliação de um médico-veterinário.";
    if (askToShare) {
      text += a.priority === Priority.EMERGENCIA
        ? " Posso avisar a clínica agora enquanto você se desloca?"
        : " Quer que eu envie este resumo para a clínica?";
    }
    return text;
  },
};

function headline(a, pet) {
  switch (a.priority) {
    case Priority.EMERGENCIA:
      return `Atenção: isso pode ser uma emergência. Leve ${article(pet)} ${pet.name} agora a um pronto-atendimento veterinário. Ligue antes para avisarem a equipe e não ofereça comida, água ou remédios sem orientação.`;
    case Priority.ALTA:
      return "Pelo que você descreveu, o ideal é falar com a clínica ainda hoje. Não vale esperar para ver se melhora sozinho.";
    case Priority.MEDIA:
      return "Vale agendar uma consulta nas próximas 24 a 72 horas. Se piorar antes, antecipe.";
    default:
      return a.active.length > 0
        ? `Pelo que você descreveu, parece algo leve. Observe por 24 a 48 horas e registre como ${pronoun(pet)} evolui.`
        : "Não identifiquei sinais de alerta no que você contou. Continue observando e me avise se algo mudar.";
  }
}

function firstAid(a, pet) {
  for (const id of a.redFlagIds) if (FIRST_AID[id]) return fmt(FIRST_AID[id], pronoun(pet), ending(pet));
  return a.priority === Priority.EMERGENCIA ? `Mantenha ${pronoun(pet)} calm${ending(pet)} e aquecid${ending(pet)} até chegar.` : "";
}

function watchFor(a, pet) {
  const category = a.active.map((m) => m.category).find((c) => WATCH_FOR[c]);
  if (!category) return `Se ${pronoun(pet)} piorar, parar de comer ou beber, ou surgir qualquer sinal novo, me conte ou procure a clínica.`;
  return fmt(WATCH_FOR[category], pronoun(pet), ending(pet));
}

// -------------------------------------------------------------------- estado da conversa
export const initialState = () => ({ stage: "IDLE", symptoms: [], durationHours: null, asked: [], turns: 0, report: null, pendingText: null, lastTriageId: null });
const collecting = (s, symptoms, duration, report, slot) => ({ ...s, stage: "COLLECTING", symptoms, durationHours: duration, asked: slot ? [...s.asked, slot] : s.asked, turns: s.turns + 1, report, pendingText: null });
const finished = (s, stage) => ({ ...s, stage, symptoms: [], durationHours: null, asked: [], turns: s.turns + 1, report: null, pendingText: null });
const idle = (s) => ({ ...s, stage: "IDLE", turns: s.turns + 1 });
const doneKeepingTriage = (s) => ({ ...s, stage: "DONE", symptoms: [], durationHours: null, asked: [], turns: s.turns + 1, report: null, pendingText: null });

// ---------------------------------------------------------------------- máquina de estados
const MAX_FOLLOW_UPS = 2;
const MIN_WORDS_FOR_PENDING = 4;
const LOW_CONFIDENCE = 0.6;
const INTAKE_RELEVANT = new Set(["vomito", "vomito_repetido", "diarreia", "apatia", "febre", "fraqueza", "nausea", "dor_abdominal"]);
const MENU = ["Relatar um sintoma", "Próximos cuidados", "O que você faz?"];
const simple = (kind, text, quickReplies, nextState) => ({ kind, text, quickReplies, triage: null, reportText: null, share: "NONE", nextState, draft: false });

/** Determinística e sem E/S (o LLM opcional entra pelo `assist`, que só soma sintomas). */
export class ConversationFlow {
  constructor(engine) {
    this.engine = engine;
  }

  /** @param assist async (text, analysis) => lista de menções extras (ou null para desligar o LLM) */
  async next(state, userText, pet, care, assist = null) {
    const normalized = normalize(userText);
    const intent = classifyIntent(normalized);
    if (state.stage === "AWAITING_SHARE" && (intent === "YES" || intent === "SHARE")) return this.shareTurn(state, pet, "GRANT");
    if (state.stage === "AWAITING_SHARE" && intent === "NO") return this.shareTurn(state, pet, "DECLINE");

    // saudações, agradecimentos, atalhos e "sim/não" nunca saem para o LLM
    const clinicalCandidate = intent === "OTHER" || intent === "MEDICATION_REQUEST";
    const analysis = await this.analyze(userText, pet, state, clinicalCandidate ? assist : null);
    if (analysis.active.length > 0 || analysis.negated.length > 0) return this.symptomTurn(state, userText, pet, care, analysis, intent);
    return this.nonClinicalTurn(state, userText, normalized, pet, care, intent);
  }

  async analyze(text, pet, state, assist) {
    const prior = this.rehydrate(state, pet);
    const analysis = this.engine.analyze(text, pet, prior, state.durationHours);
    const weak = analysis.unrecognized || analysis.vague || analysis.confidence < LOW_CONFIDENCE;
    if (assist && weak) {
      const extra = await assist(text, analysis);
      if (extra.length > 0) return withFloor(this.engine.analyze(text, pet, prior, state.durationHours, extra), analysis);
    }
    return analysis;
  }

  symptomTurn(state, text, pet, care, analysis, intent) {
    const report = state.report == null ? text : `${state.report}\n${text}`;
    const stop = analysis.priority === Priority.EMERGENCIA || intent === "FINISH" || analysis.active.length === 0;
    const slot = stop ? null : this.nextSlot(analysis, state);
    if (slot) {
      const next = collecting(state, analysis.active.map((m) => ({ id: m.id, evidence: m.evidence })), analysis.durationHours, report, slot);
      if (slot === "duration") return simple("QUESTION", composer.askDuration(pet, analysis), ["Desde hoje", "Há 1 ou 2 dias", "Há mais de 3 dias"], next);
      if (slot === "intake") return simple("QUESTION", composer.askIntake(pet), ["Está comendo e bebendo normal", "Não está comendo", "Não está bebendo água"], next);
      return simple("QUESTION", composer.askBreathing(pet), ["Respira normalmente", "Respira com dificuldade"], next);
    }
    const askToShare = analysis.priority.level >= Priority.MEDIA.level;
    let reply = composer.triage(analysis, pet, care.highlight, askToShare);
    if (intent === "MEDICATION_REQUEST") reply = `${composer.medicationRefusal()}\n\n${reply}`;
    const quick = askToShare ? ["Sim, compartilhar com a clínica", "Agora não"] : ["Relatar outro sintoma", "Próximos cuidados"];
    return { kind: "TRIAGE", text: reply, quickReplies: quick, triage: analysis, reportText: report, share: "NONE",
      nextState: finished(state, askToShare ? "AWAITING_SHARE" : "DONE"), draft: true };
  }

  nextSlot(analysis, state) {
    if (state.asked.length >= MAX_FOLLOW_UPS || analysis.priority.level >= Priority.ALTA.level) return null;
    const ids = new Set(analysis.active.map((m) => m.id));
    if (analysis.durationHours == null && !state.asked.includes("duration")) return "duration";
    const intakeRelevant = [...ids].some((id) => INTAKE_RELEVANT.has(id));
    if (intakeRelevant && !ids.has("perda_apetite") && !ids.has("recusa_agua") && !state.asked.includes("intake")) return "intake";
    const respiratory = analysis.active.some((m) => m.category === "respiratorio");
    if (respiratory && !ids.has("dificuldade_respirar") && !state.asked.includes("breathing")) return "breathing";
    return null;
  }

  nonClinicalTurn(state, text, normalized, pet, care, intent) {
    const next = idle(state);
    switch (intent) {
      case "GREETING": return simple("SMALLTALK", composer.greeting(pet, care.highlight), MENU, next);
      case "THANKS": return simple("SMALLTALK", composer.thanks(pet), ["Relatar um sintoma"], next);
      case "FAREWELL": return simple("SMALLTALK", composer.farewell(pet), [], next);
      case "HELP": return simple("SMALLTALK", composer.help(pet), MENU, next);
      case "CARE_PLAN": return simple("CARE_PLAN", composer.carePlan(pet, care.lines), ["Relatar um sintoma"], next);
      case "NEW_REPORT": return simple("SMALLTALK", composer.askForReport(pet), [], next);
      case "MEDICATION_REQUEST":
        return simple("SMALLTALK", `${composer.medicationRefusal()}\n\nSe ${pronoun(pet)} estiver com algum sinal, me conte e eu oriento o próximo passo.`, ["Relatar um sintoma"], next);
      case "SHARE": return this.shareWithoutQuestion(state, pet, next);
      default: {
        const worthKeeping = normalized.split(" ").length >= MIN_WORDS_FOR_PENDING;
        const clarifyState = worthKeeping ? { ...next, pendingText: text } : next;
        const quick = worthKeeping ? ["Descrever sintomas", "Enviar para a clínica avaliar", "Próximos cuidados"] : ["Descrever sintomas", "Próximos cuidados", "O que você faz?"];
        return simple("CLARIFY", composer.clarify(pet), quick, clarifyState);
      }
    }
  }

  shareWithoutQuestion(state, pet, next) {
    // o relato ainda não triado (pendente) vem antes: é ele que o tutor acabou de pedir para a clínica avaliar
    if (state.pendingText != null) {
      const pending = this.engine.analyze(state.pendingText, pet, [], null);
      const reply = `${composer.triage(pending, pet, null, false)}\n\n${composer.shareGranted(pet)}`;
      return { kind: "TRIAGE", text: reply, quickReplies: ["Relatar outro sintoma", "Próximos cuidados"], triage: pending,
        reportText: state.pendingText, share: "GRANT", nextState: finished(next, "DONE"), draft: true };
    }
    if (state.lastTriageId != null) return this.shareTurn(state, pet, "GRANT");
    return simple("SHARE", composer.noTriageToShare(pet), ["Relatar um sintoma"], next);
  }

  shareTurn(state, pet, action) {
    return { kind: "SHARE", text: action === "GRANT" ? composer.shareGranted(pet) : composer.shareDeclined(),
      quickReplies: ["Relatar outro sintoma", "Próximos cuidados"], triage: null, reportText: null, share: action,
      nextState: doneKeepingTriage(state), draft: false };
  }

  rehydrate(state, pet) {
    return state.symptoms.filter((s) => this.engine.knowledge.knows(s.id)).map((s) => {
      const m = this.engine.aiMention(s.id, pet.species);
      return { ...m, evidence: s.evidence };
    });
  }
}
