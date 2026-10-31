"""Serviço de visão do Zelo: classificação EXPERIMENTAL de doenças de pele em cães (dataset D3).

Inferência com ONNX Runtime (sem PyTorch). O backend Java chama POST /classify; se este serviço estiver
fora do ar, o chatbot segue funcionando e avisa o tutor (degradação graciosa).

Executar (a partir de ia/vision/service):
    uvicorn app:app --host 127.0.0.1 --port 8001
"""

from __future__ import annotations

import io
import json
import logging
import os
import warnings
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel

LOG = logging.getLogger("zelo.vision")

MODEL_DIR = Path(os.environ.get("ZELO_VISION_MODEL_DIR", Path(__file__).parent / "model"))
MAX_BYTES = 5 * 1024 * 1024
MAX_PIXELS = 12_000_000  # ~12 MP: o modelo redimensiona para 224x224, fotos maiores só gastam memória
TOP_K = 3

Image.MAX_IMAGE_PIXELS = MAX_PIXELS
warnings.simplefilter("error", Image.DecompressionBombWarning)  # acima do limite vira erro, não aviso


class Prediction(BaseModel):
    label: str
    label_pt: str
    probability: float


class Classification(BaseModel):
    model_version: str
    predictions: list[Prediction]
    low_confidence: bool
    threshold: float


class Health(BaseModel):
    status: str
    model_version: str


class Model:
    """Carrega o grafo ONNX e o model_card uma única vez."""

    def __init__(self, model_dir: Path) -> None:
        card = json.loads((model_dir / "model_card.json").read_text(encoding="utf-8"))
        self.version: str = card["model_version"]
        self.classes: list[str] = card["classes"]
        self.labels_pt: dict[str, str] = card["labels_pt"]
        self.threshold: float = card["confidence_threshold"]
        self.size: int = card["input"]["size"]
        self.mean = np.array(card["input"]["mean"], dtype=np.float32)
        self.std = np.array(card["input"]["std"], dtype=np.float32)
        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        self.session = ort.InferenceSession(
            str(model_dir / "skin_classifier.onnx"), options, providers=["CPUExecutionProvider"])

    def preprocess(self, image: Image.Image) -> np.ndarray:
        resized = image.convert("RGB").resize((self.size, self.size), Image.BILINEAR)
        array = np.asarray(resized, dtype=np.float32) / 255.0
        array = (array - self.mean) / self.std
        return array.transpose(2, 0, 1)[None, ...]

    def classify(self, image: Image.Image) -> Classification:
        probs = self.session.run(None, {"image": self.preprocess(image)})[0][0]
        order = np.argsort(probs)[::-1][:TOP_K]
        predictions = [
            Prediction(label=self.classes[i], label_pt=self.labels_pt.get(self.classes[i], self.classes[i]),
                       probability=round(float(probs[i]), 4))
            for i in order
        ]
        return Classification(model_version=self.version, predictions=predictions,
                              low_confidence=predictions[0].probability < self.threshold, threshold=self.threshold)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    app.state.model = Model(MODEL_DIR)
    LOG.info("modelo carregado: %s", app.state.model.version)
    yield


app = FastAPI(title="Zelo — serviço de visão (experimental)", version="1.0.0", lifespan=lifespan)


@app.get("/health", response_model=Health)
def health() -> Health:
    return Health(status="ok", model_version=app.state.model.version)


@app.post("/classify", response_model=Classification)
def classify(file: UploadFile = File(...)) -> Classification:
    """Handler síncrono de propósito: o FastAPI o executa em thread pool e a inferência não bloqueia /health."""
    data = file.file.read(MAX_BYTES + 1)
    if not data:
        raise HTTPException(status_code=400, detail="arquivo vazio")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="imagem acima de 5 MB")
    try:
        image = Image.open(io.BytesIO(data))
        if image.width * image.height > MAX_PIXELS:  # confere ANTES de decodificar
            raise HTTPException(status_code=413, detail="imagem com resolução acima do limite")
        image.load()
    except HTTPException:
        raise
    except (UnidentifiedImageError, OSError, ValueError, SyntaxError, EOFError, Image.DecompressionBombError,
            Image.DecompressionBombWarning):
        raise HTTPException(status_code=422, detail="não foi possível ler a imagem") from None
    return app.state.model.classify(image)
