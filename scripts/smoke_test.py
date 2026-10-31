"""Teste de fumaça ponta a ponta contra um backend em execução.

Faz login como tutora e como clínica, conduz uma conversa real, valida a saída da triagem contra
data/schemas/triage-result.schema.json e confere o fluxo de compartilhamento -> revisão.

Uso (com o backend em http://localhost:8080 e o seed de demonstração ativo):
    ia\\.venv\\Scripts\\python scripts\\smoke_test.py [--base http://localhost:8080]
A senha do seed vem de ZELO_DEMO_PASSWORD (padrão do application.yml).
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

SCHEMA = Path(__file__).resolve().parents[1] / "data" / "schemas" / "triage-result.schema.json"
DEFAULT_PASSWORD = "zelo-demo-2026"  # padrão do seed de demonstração (application.yml)


class Client:
    """Cliente mínimo com cookie de sessão e CSRF (cookie XSRF-TOKEN devolvido no cabeçalho)."""

    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _xsrf(self) -> str:
        return next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), "")

    def call(self, method: str, path: str, body: object | None = None, form: dict | None = None) -> tuple[int, object]:
        headers = {"Accept": "application/json", "X-XSRF-TOKEN": self._xsrf()}
        data = None
        if form is not None:
            data = urllib.parse.urlencode(form).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        elif body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=30) as response:
                raw = response.read()
                return response.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as error:
            raw = error.read()
            return error.code, (json.loads(raw) if raw else None)

    def login(self, email: str, password: str) -> dict:
        self.call("GET", "/api/system/status")  # semeia o cookie CSRF
        status, user = self.call("POST", "/api/auth/login", form={"email": email, "password": password})
        check(status == 200, f"login de {email}: HTTP {status}")
        return user


def check(condition: bool, message: str) -> None:
    print(("  OK   " if condition else "  FALHA ") + message)
    if not condition:
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://localhost:8080")
    base = parser.parse_args().base
    password = os.environ.get("ZELO_DEMO_PASSWORD", DEFAULT_PASSWORD)
    validator = Draft202012Validator(json.loads(SCHEMA.read_text(encoding="utf-8")), format_checker=FormatChecker())

    print("1. Tutora")
    tutor = Client(base)
    check(tutor.login("ana@zelo.demo", password)["role"] == "TUTOR", "perfil TUTOR")
    pets = tutor.call("GET", "/api/pets")[1]
    mel = next(p["pet_id"] for p in pets if p["name"] == "Mel")
    check(tutor.call("GET", "/api/pets/PET-9999")[0] == 404, "pet inexistente/alheio devolve 404")

    print("2. Conversa e triagem estruturada")
    session = tutor.call("POST", "/api/chat/sessions", {"pet_id": mel, "fresh": True})[1]["session_id"]
    reply = tutor.call("POST", "/api/chat/messages", {"pet_id": mel, "session_id": session,
                                                       "text": "Ela está muito abatida e vomitou três vezes desde ontem"})[1]
    triage = reply["reply"]["triage"]
    errors = sorted(validator.iter_errors(triage), key=lambda e: list(e.path))
    check(not errors, "saída da triagem válida no JSON Schema" + ("" if not errors else f": {errors[0].message}"))
    check(triage["priority"] == "alta" and triage["human_review_required"], "prioridade alta com revisão humana")
    check(triage["disclaimer"].startswith("Esta orientação não substitui"), "aviso de segurança presente")

    print("3. Emergência e compartilhamento")
    emergency = tutor.call("POST", "/api/triage", {"pet_id": mel, "text": "Ela teve uma convulsão agora há pouco"})[1]
    check(not list(validator.iter_errors(emergency)), "triagem avulsa válida no JSON Schema")
    check(emergency["priority"] == "emergencia", "convulsão = emergência")
    shared = tutor.call("POST", "/api/chat/messages", {"pet_id": mel, "session_id": session, "text": "Sim, compartilhar com a clínica"})[1]
    check(shared.get("shared_triage_id") == triage["triage_id"], "triagem compartilhada com consentimento")

    print("4. Clínica")
    clinic = Client(base)
    check(clinic.login("vet@zelo.demo", password)["role"] == "CLINICA", "perfil CLINICA")
    check(clinic.call("GET", "/api/pets")[0] == 403, "clínica não acessa rotas do tutor (403)")
    queue = clinic.call("GET", "/api/clinic/triages")[1]
    check(any(t["triage_id"] == triage["triage_id"] for t in queue), "triagem aparece na fila da clínica")
    check(all(t["triage_id"] != emergency["triage_id"] for t in queue), "triagem NÃO compartilhada fica fora da fila")
    status, _ = clinic.call("POST", "/api/clinic/reviews", {"triage_id": triage["triage_id"], "decision": "CONFIRMADA",
                                                            "final_priority": "alta", "note": "Smoke test"})
    check(status == 201, "revisão profissional registrada")

    print("5. Segurança básica")
    check(tutor.call("GET", "/api/clinic/triages")[0] == 403, "tutor não acessa rotas da clínica (403)")
    check(Client(base).call("GET", "/api/pets")[0] == 401, "anônimo recebe 401")
    print("\nTudo certo: o fluxo tutor -> IA -> clínica está funcionando.")


if __name__ == "__main__":
    main()
