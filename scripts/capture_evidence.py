"""Captura as telas de evidência (docs/evidencias/*.png) dirigindo o app real num navegador.

Pré-requisitos (fora do requirements do projeto, só para gerar as imagens):
    python -m venv .venv-evidencias ; .venv-evidencias\\Scripts\\pip install playwright
    (usa o Google Chrome instalado: channel="chrome")
Com o backend em http://localhost:8080 e o seed de demonstração ativo (scripts/run-local.ps1):
    .venv-evidencias\\Scripts\\python scripts\\capture_evidence.py [--base http://localhost:8080]
A senha do seed vem de ZELO_DEMO_PASSWORD (padrão do application.yml). Só funciona contra o app local de demonstração.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

from playwright.sync_api import Page, expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "evidencias"
SAMPLE_PHOTO = ROOT / "data" / "samples" / "d3" / "fungica_2.jpg"  # amostra do D3; o desempenho real está em docs/relatorios/d3_*
DEFAULT_PASSWORD = "zelo-demo-2026"  # padrão do seed de demonstração (application.yml)
DESKTOP = {"width": 1440, "height": 900}
PHONE = {"width": 390, "height": 844}


def login(page: Page, base: str, email: str, password: str, landing: str) -> None:
    page.goto(f"{base}/index.html")
    page.fill("#login-email", email)
    page.fill("#login-password", password)
    page.click("#login-submit")
    page.wait_for_url(f"**/{landing}")
    page.wait_for_load_state("networkidle")


def settle(page: Page, timeout: int = 30000) -> None:
    """Espera o bot terminar de responder: o campo reabilita e o indicador de digitação some.
    (A CSP do app proíbe eval, então não usamos wait_for_function com texto.)"""
    expect(page.locator("#message")).to_be_enabled(timeout=timeout)
    expect(page.locator("#typing")).to_have_count(0, timeout=timeout)
    page.wait_for_timeout(250)


def say(page: Page, text: str) -> None:
    """Envia pelo campo de texto e espera a resposta."""
    page.fill("#message", text)
    page.press("#message", "Enter")
    settle(page)


def quick(page: Page, label: str) -> None:
    page.locator("#quick").get_by_role("button", name=label).click()
    settle(page)


def pick_pet(page: Page, name: str) -> None:
    page.locator(".pet-tab", has_text=name).first.click()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(300)


def shot(page: Page, name: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(OUT / name))
    print(f"  ok  {name}")


def tutor_flow(browser, base: str, password: str) -> None:
    context = browser.new_context(viewport=DESKTOP, locale="pt-BR")
    page = context.new_page()
    page.goto(f"{base}/index.html")
    shot(page, "01-login.png")
    login(page, base, "ana@zelo.demo", password, "chat.html")
    pick_pet(page, "Mel")
    page.click("#new-chat")
    page.wait_for_timeout(500)
    shot(page, "02-chat-boas-vindas.png")

    # prioridade média: duas perguntas de acompanhamento (duração e respiração) e depois a triagem
    say(page, "Mel está com tosse")
    quick(page, "Há 1 ou 2 dias")
    quick(page, "Respira normalmente")
    shot(page, "03-triagem-media-com-explicacao.png")
    quick(page, "Agora não")

    # emergência: sinal de alerta encerra as perguntas e pede o consentimento antes de compartilhar
    page.click("#new-chat")
    page.wait_for_timeout(500)
    say(page, "Ela teve uma convulsão agora há pouco")
    shot(page, "04-triagem-emergencia.png")
    quick(page, "Sim, compartilhar com a clínica")
    shot(page, "05-compartilhado-com-consentimento.png")

    # limite de segurança: não indica remédio
    page.click("#new-chat")
    page.wait_for_timeout(500)
    say(page, "Posso dar dipirona para ela?")
    shot(page, "06-recusa-de-medicamento.png")

    # visão (somente cães): foto real do conjunto D3, resultado experimental
    page.click("#new-chat")
    page.wait_for_timeout(500)
    page.set_input_files("#photo", str(SAMPLE_PHOTO))
    settle(page, timeout=60000)
    shot(page, "07-analise-de-foto-experimental.png")

    # gato com sinal de risco específico da espécie (obstrução urinária)
    pick_pet(page, "Thor")
    page.click("#new-chat")
    page.wait_for_timeout(500)
    say(page, "Ele tenta fazer xixi e não sai nada, está miando muito")
    shot(page, "08-triagem-gato-obstrucao-urinaria.png")
    quick(page, "Sim, compartilhar com a clínica")  # mais uma emergência na fila da clínica
    context.close()

    # outro tutor (Bruno) compartilha uma triagem de prioridade alta: a fila mostra a ordenação por prioridade
    other = browser.new_context(viewport=DESKTOP, locale="pt-BR")
    bruno = other.new_page()
    login(bruno, base, "bruno@zelo.demo", password, "chat.html")
    bruno.click("#new-chat")
    bruno.wait_for_timeout(500)
    say(bruno, "Ele está muito abatido e vomitou duas vezes desde ontem")
    quick(bruno, "Sim, compartilhar com a clínica")
    other.close()

    # celular (a tela é pensada para o tutor no caminho da clínica)
    mobile = browser.new_context(viewport=PHONE, device_scale_factor=2, is_mobile=True, has_touch=True, locale="pt-BR")
    phone = mobile.new_page()
    login(phone, base, "ana@zelo.demo", password, "chat.html")
    pick_pet(phone, "Mel")
    shot(phone, "11-celular-chat.png")
    mobile.close()


def clinic_flow(browser, base: str, password: str) -> None:
    context = browser.new_context(viewport=DESKTOP, locale="pt-BR")
    page = context.new_page()
    login(page, base, "vet@zelo.demo", password, "clinic.html")
    page.wait_for_selector(".q-item")
    shot(page, "09-clinica-fila-por-prioridade.png")
    page.locator(".q-item", has_text="Convulsão").first.click()
    page.wait_for_selector("#review-note")
    page.fill("#review-note", "Trazer a Mel agora. Se possível, filmar a próxima crise.")
    shot(page, "10-clinica-detalhe-e-revisao.png")
    page.get_by_role("button", name="Registrar revisão").click()
    page.wait_for_selector(".review-entry")
    page.wait_for_timeout(400)
    shot(page, "10b-clinica-revisao-registrada.png")
    context.close()


def dashboard_flow(browser, base: str, password: str) -> None:
    """Painel do Tutor (IBM Carbon), depois da revisão da clínica: o retorno aparece no aviso e no histórico."""
    context = browser.new_context(viewport=DESKTOP, locale="pt-BR")
    page = context.new_page()
    login(page, base, "ana@zelo.demo", password, "chat.html")
    page.goto(f"{base}/dashboard")
    page.wait_for_selector("#cta-triage")
    page.locator('[data-pet-id="PET-0001"]').click()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(400)
    shot(page, "12-painel-do-tutor.png")

    page.click('[data-section="historico"]')
    page.wait_for_timeout(900)
    shot(page, "13-painel-historico-de-atendimento.png")

    page.click("#manage-care")
    page.wait_for_selector("#add-care")
    page.click('[data-section="cuidados"]')
    page.wait_for_timeout(900)
    shot(page, "14-painel-gerenciar-plano-de-cuidados.png")

    page.click('[data-section="perfil"]')
    page.wait_for_timeout(600)
    page.click("#edit-pet")
    page.wait_for_selector("#pet-dialog[open]")
    page.wait_for_timeout(300)
    shot(page, "15-painel-editar-dados-do-pet.png")
    context.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080")
    base = parser.parse_args().base.rstrip("/")
    password = os.environ.get("ZELO_DEMO_PASSWORD", DEFAULT_PASSWORD)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="chrome", headless=True)
        tutor_flow(browser, base, password)
        clinic_flow(browser, base, password)
        dashboard_flow(browser, base, password)
        browser.close()
    print(f"Evidências em {OUT}")


if __name__ == "__main__":
    main()
