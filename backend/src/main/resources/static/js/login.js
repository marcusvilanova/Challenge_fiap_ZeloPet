import { api, ApiError, ensureCsrf } from "./api.js";
import { icon } from "./ui.js";

const HOME_BY_ROLE = { TUTOR: "/chat.html", CLINICA: "/clinic.html" };

const $ = (id) => document.getElementById(id);

document.getElementById("brand-mark").append(icon("paw", 22));

if (new URLSearchParams(location.search).get("acesso") === "negado") {
  $("denied").hidden = false;
}

// ---------------------------------------------------------------- abas (padrão WAI-ARIA)
const tabs = [$("tab-login"), $("tab-register")];
const panels = { "tab-login": $("panel-login"), "tab-register": $("panel-register") };

function selectTab(selected) {
  for (const tab of tabs) {
    const active = tab === selected;
    tab.setAttribute("aria-selected", String(active));
    tab.tabIndex = active ? 0 : -1;
    panels[tab.id].hidden = !active;
  }
  selected.focus();
}

tabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectTab(tab));
  tab.addEventListener("keydown", (event) => {
    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      selectTab(tabs[(index + 1) % tabs.length]);
      event.preventDefault();
    }
  });
});

// ------------------------------------------------------------------- contas de demonstração
// Senha só das contas FICTÍCIAS de demonstração (a mesma do README / ZELO_DEMO_PASSWORD padrão).
const DEMO_PASSWORD = document.querySelector("[data-demo-password]")?.dataset.demoPassword ?? "";

function fillDemo(email) {
  $("login-email").value = email;
  $("login-password").value = DEMO_PASSWORD;
  showError("login-error", "");
  $("login-submit").focus(); // um Enter (ou clique) e entra
}

document.querySelectorAll("[data-demo]").forEach((chip) => {
  chip.addEventListener("click", () => fillDemo(chip.dataset.demo));
});

// Atalho vindo do painel ("Visão Clínica"): já deixa a conta de demonstração escolhida preenchida.
const demoFromUrl = new URLSearchParams(location.search).get("demo");
if (demoFromUrl && document.querySelector(`[data-demo="${CSS.escape(demoFromUrl)}"]`)) {
  fillDemo(demoFromUrl);
}

// ------------------------------------------------------------------------------- envio
function showError(id, message) {
  const node = $(id);
  node.textContent = message;
  node.hidden = !message;
}

function goHome(role) {
  location.assign(HOME_BY_ROLE[role] || "/index.html");
}

async function submitWithState(button, label, work) {
  button.disabled = true;
  button.textContent = "Aguarde…";
  try {
    await work();
  } finally {
    button.disabled = false;
    button.textContent = label;
  }
}

$("form-login").addEventListener("submit", async (event) => {
  event.preventDefault();
  showError("login-error", "");
  const email = $("login-email").value.trim();
  const password = $("login-password").value;
  if (!email || !password) {
    showError("login-error", "Informe e-mail e senha.");
    return;
  }
  await submitWithState($("login-submit"), "Entrar", async () => {
    try {
      const user = await api.login(email, password);
      goHome(user.role);
    } catch (error) {
      showError("login-error", error instanceof ApiError ? error.message : "Falha inesperada. Tente de novo.");
    }
  });
});

$("form-register").addEventListener("submit", async (event) => {
  event.preventDefault();
  showError("register-error", "");
  const name = $("reg-name").value.trim();
  const email = $("reg-email").value.trim();
  const password = $("reg-password").value;
  if (!name || !email || password.length < 8) {
    showError("register-error", "Preencha nome, e-mail e uma senha com ao menos 8 caracteres.");
    return;
  }
  await submitWithState($("register-submit"), "Criar conta", async () => {
    try {
      await api.post("/api/auth/register", { name, email, password });
      const user = await api.login(email, password);
      goHome(user.role);
    } catch (error) {
      const fieldErrors = error instanceof ApiError && error.errors ? Object.values(error.errors).join(" ") : "";
      showError("register-error", fieldErrors || (error instanceof ApiError ? error.message : "Falha inesperada."));
    }
  });
});

// Quem já está autenticado vai direto para a sua área.
ensureCsrf().then(async () => {
  try {
    const me = await api.get("/api/auth/me");
    goHome(me.role);
  } catch {
    /* não autenticado: permanece na página de entrada */
  }
});
