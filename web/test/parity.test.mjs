// Paridade do motor JS com o Java: os mesmos conjuntos (ouro, holdout, cego) que o Java aprova em 100%
// precisam dar a mesma prioridade aqui. Rodar: node --test web/test
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { KnowledgeBase, petContext, TriageEngine } from "../engine/triage.js";
import { classifyIntent, ConversationFlow, initialState } from "../engine/chat.js";
import { normalize } from "../engine/triage.js";
import { check } from "../engine/guard.js";
import { Priority } from "../engine/triage.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const knowledge = new KnowledgeBase(JSON.parse(readFileSync(join(root, "backend/src/main/resources/triage/triage_knowledge.json"), "utf8")));
const engine = new TriageEngine(knowledge);

function pet(p) {
  if (typeof p === "string") return petContext({ name: "Mel", species: p, ageMonths: 48, weightKg: 15, female: true });
  return petContext({ name: "Mel", species: p.species, ageMonths: p.age_months ?? 48, weightKg: p.weight_kg ?? 15,
    brachycephalic: Boolean(p.brachycephalic), female: true, historyTags: p.history ?? [] });
}

for (const file of ["gold_cases.jsonl", "holdout_cases.jsonl", "blind_cases.jsonl"]) {
  test(`paridade de prioridade com o Java: ${file}`, () => {
    const cases = readFileSync(join(root, "ia/triage/eval", file), "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));
    const misses = cases.filter((c) => engine.analyze(c.text, pet(c.pet)).priority.wire !== c.priority)
      .map((c) => `${c.id}: esperado ${c.priority}, veio ${engine.analyze(c.text, pet(c.pet)).priority.wire} — ${c.text}`);
    assert.deepEqual(misses, []);
  });
}

test("regressões da revisão de código continuam corrigidas", () => {
  const dog = pet("dog");
  assert.equal(engine.analyze("a convulsão não parou", dog).priority, Priority.EMERGENCIA);
  assert.ok(engine.analyze("ele vomitou, não tem sangue no vômito", dog).active.some((m) => m.id === "vomito"));
  assert.notEqual(engine.analyze("ele não está animado", dog).priority, Priority.BAIXA);
  assert.equal(engine.analyze("ele está com 2 dias sem comer", pet("cat")).priority, Priority.ALTA);
  assert.ok(!engine.analyze("vomitou sem sangue", dog).active.some((m) => m.id === "vomito_sangue"));
});

test("consentimento estrito", () => {
  const intent = (t) => classifyIntent(normalize(t));
  assert.equal(intent("Não quero compartilhar com a clínica"), "NO");
  assert.equal(intent("Sim, compartilhar com a clínica"), "YES");
  assert.equal(intent("quero compartilhar com a clínica"), "SHARE");
  assert.notEqual(intent("Pode ser grave? Ele está vomitando sangue"), "YES");
});

test("conversa: tosse → duas perguntas → triagem média que pede consentimento", async () => {
  const flow = new ConversationFlow(engine);
  const mel = pet("dog");
  const care = { highlight: null, lines: [] };
  let turn = await flow.next(initialState(), "ela está com tosse", mel, care);
  assert.equal(turn.kind, "QUESTION");
  turn = await flow.next(turn.nextState, "Há 1 ou 2 dias", mel, care);
  assert.equal(turn.kind, "QUESTION");
  turn = await flow.next(turn.nextState, "Respira normalmente", mel, care);
  assert.equal(turn.kind, "TRIAGE");
  assert.equal(turn.triage.priority, Priority.MEDIA);
  assert.equal(turn.nextState.stage, "AWAITING_SHARE");
  const refusal = await flow.next(turn.nextState, "não quero compartilhar com a clínica", mel, care);
  assert.equal(refusal.share, "DECLINE");
});

test("o LLM não consegue rebaixar um relato não reconhecido", async () => {
  const flow = new ConversationFlow(engine);
  const assist = async () => [engine.aiMention("espirros", "dog")];
  const analysis = await flow.analyze("meu cachorro tem um negócio no zóio", pet("dog"), initialState(), assist);
  assert.ok(analysis.active.some((m) => m.id === "espirros"));
  assert.equal(analysis.priority, Priority.MEDIA);
  assert.ok(analysis.rules.includes("R14_PISO_DETERMINISTICO"));
});

test("guard reprova dose, remédio e minimização", () => {
  assert.equal(check("Dê 10 mg de dipirona e procure a clínica veterinária.", Priority.MEDIA, "rascunho").accepted, false);
  assert.equal(check("Isso não é urgente e não precisa de veterinário.", Priority.MEDIA, "rascunho").accepted, false);
  assert.equal(check("Vale marcar uma consulta com a clínica veterinária nos próximos dias.", Priority.MEDIA, "rascunho").accepted, true);
});
