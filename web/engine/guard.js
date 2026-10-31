// Barreira antes de mostrar texto gerado pelo LLM (porte de ResponseGuard.java e PiiScrubber.java).
// Lista de bloqueio heurística, não prova de segurança: a prioridade sempre vem do motor determinístico.
import { normalize, Priority } from "./triage.js";

const MAX_LENGTH = 1400;
const DOSAGE = /\b(?:\d+(?:[.,]\d+)?|meio|meia|um|uma|dois|duas|tres|quatro|cinco|dez)\s?(?:mg|ml|mcg|ug|ui|g|gramas?|miligramas?|mililitros?|gotas?|comprimidos?|capsulas?|ampolas?|colher(?:es)?|saches?|doses?)\b|\ba cada \d+ horas\b/;
const DISEASE = new RegExp("\\b(?:cinomose|parvovirose|parvo|leptospirose|giardia|giardiase|erliquiose|babesiose|cushing"
  + "|diabetes|insuficiencia renal|cancer|tumor maligno|raiva|fiv|felv|pancreatite|gastrite|verminose|otite|dermatite"
  + "|alergia|infeccao|intoxicacao|envenenamento|torcao|cistite|piometra|atopia|sarna|tinha|micose|hepatite|artrose|displasia"
  + "|cardiopatia|insuficiencia cardiaca|pneumonia|bronquite|tosse dos canis|traqueobronquite|calazar|leishmaniose)\\b", "g");
const DIAGNOSIS = /\bo diagnostico (?:e|eh|certo|correto|provavel)\b|\bdiagnosticad[oa]\b|\bconfirm\w* (?:o )?diagnostico\b|\bcom certeza (?:e|eh|tem)\b|\bcertamente (?:e|eh|tem)\b/;
const DRUGS = ["dipirona", "novalgina", "paracetamol", "tylenol", "ibuprofeno", "advil", "tramadol", "amoxicilina", "prednisolona",
  "prednisona", "meloxicam", "cetoprofeno", "dexametasona", "ivermectina", "buscopan", "dramin", "diazepam", "cefalexina",
  "metronidazol", "omeprazol", "enrofloxacino", "carprofeno", "firocoxib", "gabapentina", "acepromazina", "aspirina",
  "naproxeno", "diclofenaco", "nimesulida", "loratadina", "benadryl", "hidroxizina", "ondansetrona", "metoclopramida",
  "plasil", "penicilina", "ciprofloxacino", "dorflex", "neosaldina", "clonazepam", "rivotril", "carvao ativado",
  "antibiotico", "anti-inflamatorio", "antiinflamatorio", "vermifugo caseiro"];
const DISMISSIVE = /\bnao (?:e|eh|precisa|ha|tem|existe|vejo)\b(?:\s+\w+){0,3}\s+(?:urgen\w*|emergenc\w*|veterinari\w*|clinica|consulta|pressa|motivo|preocupa\w*)|\bpode esperar\b|\bsem pressa\b|\bfique tranquil[oa]\b|\bnao se preocupe\b|\bnao e (?:grave|nada)\b|\bnao precisa (?:ir|levar|procurar|de)\b/;
const URGENCY = /urgen|emergenc|agora|imediat|pronto.?atend|sem esperar/;
const PROFESSIONAL = /veterinari|clinica/;
const MARKDOWN_MARKS = /\*\*|__|^#{1,6}\s+/gm;
const compact = (s) => s.replace(/[^a-z0-9]/g, "");

export function sanitize(reply) {
  return reply == null ? "" : String(reply).replace(MARKDOWN_MARKS, "").trim();
}

/** @returns {{accepted: boolean, reason: string}} */
export function check(reply, priority, draft) {
  const reject = (reason) => ({ accepted: false, reason });
  if (!reply || !reply.trim()) return reject("vazia");
  if (reply.length > MAX_LENGTH) return reject("longa demais");
  if (reply.includes("```")) return reject("bloco de código inesperado");
  const text = normalize(reply);
  const draftText = normalize(draft);
  if (DOSAGE.test(text)) return reject("menciona dose");
  const diseases = [...text.matchAll(DISEASE)].map((m) => m[0]);
  if (DIAGNOSIS.test(text) || diseases.some((d) => !draftText.includes(d))) return reject("afirma ou sugere diagnóstico");
  const drug = DRUGS.find((d) => compact(text).includes(compact(normalize(d))) && !compact(draftText).includes(compact(normalize(d))));
  if (drug) return reject(`menciona medicamento: ${drug}`);
  if (priority === Priority.EMERGENCIA && !URGENCY.test(text)) return reject("perdeu o sinal de urgência");
  if (priority.level >= Priority.MEDIA.level) {
    if (!PROFESSIONAL.test(text)) return reject("não encaminha a um profissional");
    if (DISMISSIVE.test(text)) return reject("minimiza a situação");
  }
  return { accepted: true, reason: "ok" };
}

const MASK = "[dado removido]";
export function scrubPii(text) {
  if (text == null) return "";
  return String(text)
    .replace(/[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g, MASK)
    .replace(/\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b/g, MASK)
    .replace(/(?<!\d)(?:\+?55\s?)?\(?\d{2}\)?[\s.-]?9?\d{4}[\s.-]?\d{4}(?!\d)/g, MASK)
    .replace(/\b\d{5}-?\d{3}\b/g, MASK);
}
