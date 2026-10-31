# Serviço de visão (FastAPI + ONNX). Contexto de build: a pasta ia/.
FROM python:3.12-slim
RUN useradd --system --create-home zelo
WORKDIR /app
COPY requirements-service.txt .
RUN pip install --no-cache-dir -r requirements-service.txt
COPY vision/service/ ./
USER zelo
EXPOSE 8001
CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8001}"]
