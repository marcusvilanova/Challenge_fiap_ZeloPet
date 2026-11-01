"""Gera qa/selenium/zelo.side (projeto do Selenium IDE) a partir de uma descrição legível dos casos.

Uso: python qa/selenium/build_side.py   → abre o zelo.side no Selenium IDE (Chrome/Firefox) ou roda com
selenium-side-runner (ver qa/README.md).
"""

import json
import uuid
from pathlib import Path

BASE_URL = "https://zelopet.vercel.app"
OUT = Path(__file__).with_name("zelo.side")
WAIT_MS = "15000"


def cmd(command, target="", value="", comment=""):
    return {"id": str(uuid.uuid4()), "comment": comment, "command": command, "target": target, "targets": [], "value": value}


def last_bot_contains(text):
    """Espera a última mensagem do bot conter o texto (falha após o tempo limite)."""
    return cmd("waitForElementPresent", f"xpath=(//div[contains(@class,'msg--bot')])[last()][contains(.,\"{text}\")]", WAIT_MS,
               f"Resposta contém: {text}")


def login(email, comment):
    return [
        cmd("open", "/index.html", comment="Tela de entrada"),
        cmd("waitForElementVisible", "id=login-email", WAIT_MS),
        cmd("type", "id=login-email", email),
        cmd("type", "id=login-password", "Teste-1234", "Contas de demonstração aceitam qualquer senha"),
        cmd("click", "id=login-submit", comment=comment),
    ]


def enter_chat_as_ana():
    return login("ana@zelo.demo", "Entrar como tutora") + [
        cmd("waitForElementEditable", "id=message", WAIT_MS),
        cmd("click", "id=new-chat", comment="Conversa nova (estado limpo)"),
        cmd("waitForElementPresent", "xpath=(//div[contains(@class,'msg--bot')])[last()][contains(.,'Oi! Eu sou a Zelo')]", WAIT_MS),
    ]


def say(text):
    return [
        cmd("type", "id=message", text, f"Tutor escreve: {text}"),
        cmd("sendKeys", "id=message", "${KEY_ENTER}"),
        cmd("waitForElementEditable", "id=message", WAIT_MS, "Espera a resposta do bot"),
    ]


def quick(label):
    return [
        cmd("click", f"xpath=//div[@id='quick']/button[normalize-space()='{label}']", comment=f"Resposta rápida: {label}"),
        cmd("waitForElementEditable", "id=message", WAIT_MS),
    ]


def logout():
    return [cmd("click", "id=logout", comment="Sair"), cmd("waitForElementVisible", "id=login-email", WAIT_MS)]


TESTS = {
    "TA-01 Login da tutora e saudação personalizada (CT-01)":
        login("ana@zelo.demo", "Entrar como tutora") + [
            cmd("waitForElementEditable", "id=message", WAIT_MS),
            cmd("assertTitle", "Zelo — conversa com a assistente"),
            cmd("waitForElementPresent", "xpath=//h1[@id='chat-title'][normalize-space()='Conversa sobre Mel']", WAIT_MS, "Espera o pet carregar"),
            cmd("assertElementPresent", "xpath=//span[@id='user-name'][normalize-space()='Ana Souza']", comment="Nome da tutora no topo (presente também no layout de celular)"),
            cmd("assertElementPresent", "xpath=//div[contains(@class,'msg--bot')][contains(.,'assistente de cuidado da Mel')]"),
        ] + logout(),
    "TA-02 Login com e-mail não cadastrado é recusado (CT-02)":
        login("naoexiste@zelo.demo", "Tentar entrar com e-mail inexistente") + [
            cmd("waitForElementVisible", "id=login-error", WAIT_MS),
            cmd("assertText", "id=login-error", "E-mail ou senha inválidos."),
            cmd("assertTitle", "Zelo — cuidado contínuo antes da urgência"),
        ],
    "TA-03 Sinal de alerta gera emergência imediata (CT-07)":
        enter_chat_as_ana() + say("Ela teve uma convulsão agora há pouco") + [
            last_bot_contains("Prioridade emergência"),
            last_bot_contains("Buscar atendimento veterinário de urgência agora"),
            last_bot_contains("Sinal de alerta relatado: convulsão"),
            last_bot_contains("90%"),
        ] + logout(),
    "TA-04 Triagem média com perguntas de acompanhamento (CT-06)":
        enter_chat_as_ana() + say("Mel está com tosse") + [
            last_bot_contains("Desde quando ela está assim?"),
        ] + quick("Há 1 ou 2 dias") + [
            last_bot_contains("respiração da Mel"),
        ] + quick("Respira normalmente") + [
            last_bot_contains("Prioridade média"),
            last_bot_contains("Agendar consulta nas próximas 24–72 horas"),
            last_bot_contains("≈ 2 dias"),
            last_bot_contains("Quer que eu envie este resumo para a clínica?"),
        ] + logout(),
    "TA-05 Pedido de remédio é recusado (CT-12)":
        enter_chat_as_ana() + say("Posso dar dipirona para ela?") + [
            last_bot_contains("Não posso indicar remédios nem doses"),
            cmd("assertElementNotPresent", "xpath=(//div[contains(@class,'msg--bot')])[last()][contains(.,' mg')]", comment="Nenhuma dose citada"),
        ] + logout(),
    "TA-06 Compartilhamento com consentimento chega à fila da clínica (CT-14)":
        enter_chat_as_ana() + say("Ela teve uma convulsão agora há pouco") + quick("Sim, compartilhar com a clínica") + [
            last_bot_contains("Pronto. Enviei o resumo da Mel para a clínica"),
            cmd("waitForElementPresent", "xpath=//*[contains(@class,'msg--bot')]//*[contains(.,'Compartilhada com a clínica')]", WAIT_MS),
        ] + logout() + login("vet@zelo.demo", "Entrar como clínica") + [
            cmd("waitForElementPresent", "css=.q-item", WAIT_MS),
            cmd("assertTitle", "Zelo — painel da clínica"),
            cmd("assertElementPresent", "xpath=//button[contains(@class,'q-item')][contains(.,'Mel')][contains(.,'Emergência')][contains(.,'Convulsão')]"),
        ] + logout(),
}


def main():
    tests = [{"id": str(uuid.uuid4()), "name": name, "commands": commands} for name, commands in TESTS.items()]
    project = {
        "id": str(uuid.uuid4()),
        "version": "2.0",
        "name": "Zelo — testes automatizados (Sprint 4)",
        "url": BASE_URL,
        "tests": tests,
        "suites": [{"id": str(uuid.uuid4()), "name": "Zelo — fluxo do tutor e da clínica", "persistSession": False,
                    "parallel": False, "timeout": 300, "tests": [t["id"] for t in tests]}],
        "urls": [BASE_URL],
        "plugins": [],
    }
    OUT.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{OUT} gerado com {len(tests)} casos")


if __name__ == "__main__":
    main()
