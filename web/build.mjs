// Monta a versão estática para a Vercel em web/dist: as mesmas telas do backend, com a API rodando no navegador.
// Uso: node web/build.mjs   (a Vercel roda isto como buildCommand)
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const dist = join(root, "web", "dist");
const statics = join(root, "backend", "src", "main", "resources", "static");

rmSync(dist, { recursive: true, force: true });
mkdirSync(dist, { recursive: true });
cpSync(statics, dist, { recursive: true });
cpSync(join(root, "web", "local-api.js"), join(dist, "js", "api.js"));
cpSync(join(root, "web", "engine"), join(dist, "js", "engine"), { recursive: true });
mkdirSync(join(dist, "triage"), { recursive: true });
cpSync(join(root, "backend", "src", "main", "resources", "triage", "triage_knowledge.json"), join(dist, "triage", "triage_knowledge.json"));

if (existsSync(join(root, "apresentacao.html"))) {
  cpSync(join(root, "apresentacao.html"), join(dist, "apresentacao.html"));
}

if (existsSync(join(root, "qa", "video-guia-apresentacao.html"))) {
  cpSync(join(root, "qa", "video-guia-apresentacao.html"), join(dist, "video.html"));
}

// dashboard.html e as fotos dos pets de demonstração já vêm de backend/src/main/resources/static (cópia acima).

function patch(file, from, to) {
  const path = join(dist, file);
  const html = readFileSync(path, "utf8");
  if (!html.includes(from)) throw new Error(`Trecho não encontrado em ${file}: ${from}`);
  writeFileSync(path, html.replace(from, to));
}

patch("index.html", "A senha da demonstração está no README do projeto.",
  "Versão online de demonstração: os dados ficam só neste navegador e as contas de exemplo entram com qualquer senha.");
patch("chat.html", "provedor externo (Anthropic)", "provedor externo (Google Gemini)");

console.log(`Versão estática gerada em ${dist}`);
