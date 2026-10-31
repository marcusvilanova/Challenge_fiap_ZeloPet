# D3 — auditoria, treino e avaliação do classificador de pele

_Gerado em 2026-10-31 14:09 UTC por `ia/vision/training/train_vision.py`._

## 1. Auditoria do dataset

| Split | Imagens | MD5 únicos | Fontes únicas | Classes |
|---|---|---|---|---|
| train | 3022 | 2974 | 1536 | Dermatitis: 546, Fungal_infections: 375, Healthy: 492, Hypersensitivity: 230, demodicosis: 588, ringworm: 791 |
| valid | 860 | 856 | 694 | Dermatitis: 175, Fungal_infections: 97, Healthy: 139, Hypersensitivity: 63, demodicosis: 174, ringworm: 212 |
| test | 433 | 433 | 394 | Dermatitis: 66, Fungal_infections: 54, Healthy: 69, Hypersensitivity: 29, demodicosis: 100, ringworm: 115 |

**Vazamento entre splits** (mesmo arquivo ou mesma imagem-fonte do Roboflow):

| Par | Arquivos idênticos | Fontes compartilhadas | Imagens do 2º split com fonte já vista |
|---|---|---|---|
| train~valid | 21 | 569 | 709 |
| train~test | 7 | 325 | 357 |
| valid~test | 2 | 135 | 147 |

## 2. Seleção de backbone (somente validação)

| Backbone | Dim. features | C | Acurácia (valid) | Macro-F1 (valid) |
|---|---|---|---|---|
| mobilenet_v3_large ✅ | 960 | 1.0 | 0.9279 | 0.9187 |
| efficientnet_b0 | 1280 | 0.1 | 0.9314 | 0.9223 |
| resnet50 | 2048 | 0.1 | 0.9372 | 0.9231 |

## 3. Teste completo

- Imagens: **433**  ·  Acurácia: **0.9145**  ·  Macro-F1: **0.8906**
- Com limiar de confiança 0.5: cobertura 0.9885, acurácia nas aceitas 0.9252

| Classe | Precisão | Recall | F1 | Suporte |
|---|---|---|---|---|
| Dermatitis | 0.89 | 0.88 | 0.89 | 66 |
| Fungal_infections | 0.85 | 0.81 | 0.83 | 54 |
| Healthy | 0.90 | 0.93 | 0.91 | 69 |
| Hypersensitivity | 0.75 | 0.83 | 0.79 | 29 |
| demodicosis | 0.97 | 0.98 | 0.98 | 100 |
| ringworm | 0.96 | 0.94 | 0.95 | 115 |

Matriz de confusão (linhas = real, colunas = previsto):

| real \ previsto | Dermatitis | Fungal_infections | Healthy | Hypersensitivity | demodicosis | ringworm |
|---|---|---|---|---|---|---|
| Dermatitis | 58 | 2 | 2 | 2 | 0 | 2 |
| Fungal_infections | 1 | 44 | 3 | 4 | 2 | 0 |
| Healthy | 1 | 4 | 64 | 0 | 0 | 0 |
| Hypersensitivity | 1 | 0 | 2 | 24 | 0 | 2 |
| demodicosis | 1 | 1 | 0 | 0 | 98 | 0 |
| ringworm | 3 | 1 | 0 | 2 | 1 | 108 |

## 4. Teste limpo (sem fontes vistas no treino/validação)

- Imagens: **49**  ·  Acurácia: **0.8571**  ·  Macro-F1: **0.7852**
- Com limiar de confiança 0.5: cobertura 0.9796, acurácia nas aceitas 0.875

| Classe | Precisão | Recall | F1 | Suporte |
|---|---|---|---|---|
| Dermatitis | 0.92 | 0.75 | 0.83 | 16 |
| Fungal_infections | 0.50 | 0.50 | 0.50 | 2 |
| Healthy | 0.83 | 0.91 | 0.87 | 11 |
| Hypersensitivity | 1.00 | 0.50 | 0.67 | 2 |
| demodicosis | 0.90 | 1.00 | 0.95 | 9 |
| ringworm | 0.82 | 1.00 | 0.90 | 9 |

Matriz de confusão (linhas = real, colunas = previsto):

| real \ previsto | Dermatitis | Fungal_infections | Healthy | Hypersensitivity | demodicosis | ringworm |
|---|---|---|---|---|---|---|
| Dermatitis | 12 | 1 | 1 | 0 | 0 | 2 |
| Fungal_infections | 0 | 1 | 0 | 0 | 1 | 0 |
| Healthy | 1 | 0 | 10 | 0 | 0 | 0 |
| Hypersensitivity | 0 | 0 | 1 | 1 | 0 | 0 |
| demodicosis | 0 | 0 | 0 | 0 | 9 | 0 |
| ringworm | 0 | 0 | 0 | 0 | 0 | 9 |

## 5. Validação cruzada agrupada (sem vazamento de imagem-fonte)

5 folds estratificados sobre as 4315 imagens, agrupadas em 1705 grupos (mesma fonte Roboflow ou mesmo MD5 ficam sempre no mesmo fold).

- Acurácia: **0.822 ± 0.013**
- Macro-F1: **0.770 ± 0.016**

## 6. Leitura honesta dos resultados

- O classificador é **experimental** e treinado só com cães; nunca é diagnóstico.
- O split original do dataset tem vazamento (augmentations da mesma foto em treino e teste): o teste completo é otimista.
- A referência confiável é a **validação cruzada agrupada** (seção 5); o teste limpo (n pequeno) serve de conferência.
- A literatura citada na Sprint 3 (93–95%) usa outros datasets/arquiteturas; não é meta garantida aqui.
