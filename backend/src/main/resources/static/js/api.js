// Cliente da API: JSON em snake_case, CSRF por cabeçalho (cookie XSRF-TOKEN) e erros padronizados (RFC 7807).

export class ApiError extends Error {
  constructor(status, detail, errors) {
    super(detail);
    this.status = status;
    this.errors = errors || null;
  }
}

function csrfToken() {
  const match = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return match ? decodeURIComponent(match.split("=")[1]) : "";
}

async function request(method, url, { json, form } = {}) {
  const headers = { Accept: "application/json" };
  const token = csrfToken();
  if (token) headers["X-XSRF-TOKEN"] = token;

  let body;
  if (json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(json);
  } else if (form instanceof URLSearchParams) {
    headers["Content-Type"] = "application/x-www-form-urlencoded";
    body = form;
  } else if (form instanceof FormData) {
    body = form; // o navegador define o boundary do multipart
  }

  let response;
  try {
    response = await fetch(url, { method, headers, body, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "Sem conexão com o servidor. Verifique sua internet e tente de novo.");
  }
  if (response.status === 204) return null;

  const isJson = (response.headers.get("content-type") || "").includes("json");
  const payload = isJson ? await response.json().catch(() => null) : null;
  if (!response.ok) {
    throw new ApiError(response.status, payload?.detail || "Não foi possível concluir a operação.", payload?.errors);
  }
  return payload;
}

export const api = {
  get: (url) => request("GET", url),
  post: (url, json) => request("POST", url, { json }),
  del: (url) => request("DELETE", url),
  upload: (url, formData) => request("POST", url, { form: formData }),
  login: (email, password) => request("POST", "/api/auth/login", { form: new URLSearchParams({ email, password }) }),
  logout: () => request("POST", "/api/auth/logout"),
};

/** Garante que o cookie CSRF exista antes do primeiro POST (ele é emitido em qualquer GET). */
export async function ensureCsrf() {
  if (!csrfToken()) await fetch("/api/system/status", { credentials: "same-origin" }).catch(() => undefined);
}
