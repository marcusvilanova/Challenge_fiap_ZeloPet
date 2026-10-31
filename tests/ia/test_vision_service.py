"""Testes do serviço de visão (FastAPI + ONNX) com amostras reais do D3."""

from __future__ import annotations

import io
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app import app

SAMPLES = Path(__file__).resolve().parents[2] / "data" / "samples" / "d3"
EXPECTED = {
    "demodicosis": "demodicosis", "dermatite": "Dermatitis", "fungica": "Fungal_infections",
    "saudavel": "Healthy", "hipersensibilidade": "Hypersensitivity", "tinha": "ringworm",
}


@pytest.fixture(scope="module")
def client() -> TestClient:
    with TestClient(app) as test_client:  # executa o lifespan (carrega o modelo)
        yield test_client


def _post(client: TestClient, data: bytes, name: str = "foto.jpg"):
    return client.post("/classify", files={"file": (name, data, "application/octet-stream")})


def test_health_reports_model_version(client: TestClient) -> None:
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["model_version"].startswith("zelo-skin-")


def test_classify_returns_sorted_top3_with_valid_probabilities(client: TestClient) -> None:
    response = _post(client, (SAMPLES / "saudavel_1.jpg").read_bytes())
    assert response.status_code == 200
    body = response.json()
    probs = [p["probability"] for p in body["predictions"]]
    assert len(probs) == 3
    assert probs == sorted(probs, reverse=True)
    assert 0.0 < sum(probs) <= 1.0001
    assert body["threshold"] == 0.5
    assert body["low_confidence"] == (probs[0] < 0.5)
    assert all(p["label_pt"] for p in body["predictions"])


def test_smoke_accuracy_on_samples(client: TestClient) -> None:
    """Teste de fumaça (não é métrica): a maioria das 12 amostras deve cair na classe certa."""
    hits = total = 0
    for path in sorted(SAMPLES.glob("*.jpg")):
        expected = EXPECTED[path.stem.rsplit("_", 1)[0]]
        top = _post(client, path.read_bytes()).json()["predictions"][0]["label"]
        hits += top == expected
        total += 1
    assert total == 12
    assert hits / total >= 0.6


def test_rejects_empty_non_image_and_oversized(client: TestClient) -> None:
    assert _post(client, b"").status_code == 400
    assert _post(client, b"isto nao e uma imagem").status_code == 422
    assert _post(client, b"0" * (5 * 1024 * 1024 + 10)).status_code == 413


def test_accepts_png_and_grayscale(client: TestClient) -> None:
    buffer = io.BytesIO()
    Image.new("L", (64, 48), color=128).save(buffer, format="PNG")
    assert _post(client, buffer.getvalue(), "cinza.png").status_code == 200
