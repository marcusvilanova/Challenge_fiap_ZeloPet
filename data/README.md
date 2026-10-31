# Dados do Zelo

Os datasets completos **não** estão no repositório (tamanho e licenças). Aqui ficam o catálogo, amostras controladas e o esquema do contrato.

```
data/
├── samples/
│   ├── d2_clean_sample.csv     200 registros do D2 já normalizados (sintomas canônicos) — gerado por ia/triage/build_knowledge.py
│   └── d3/                     12 fotos reais do D3 (2 por classe), usadas na demonstração e nos testes do serviço de visão
├── schemas/
│   ├── triage-result.schema.json    JSON Schema da saída da triagem (validado por scripts/smoke_test.py)
│   └── triage-result.example.json
└── README.md
```

## Origem dos cinco datasets (Sprint 3)

| ID | Dataset | Onde obter | Papel na Sprint 4 |
|---|---|---|---|
| D1 | `Animal_Vet.xlsx` (gestação, 610 reg.) | arquivo fornecido pela equipe | **não usado** (catalogado; caso especializado) |
| D2 | `veterinary_clinical_data.csv` (10.000 reg., parte sintéticos) | arquivo fornecido pela equipe | **usado**: vocabulário de sintomas, normalização, perfil do pet |
| D3 | Doenças de pele em cães (4.315 imagens, 6 classes) | pasta `Dataset/3` fornecida pela equipe (nomes de arquivo no padrão Roboflow, com augmentations) | **usado**: treino do classificador de visão |
| D4 | `dataset.csv` de surtos (WAHIS/WOAH, 6.363 reg.) | arquivo fornecido pela equipe | **não usado** (contexto epidemiológico; unidade é o surto, não o pet) |
| D5 | Imagens de doenças em pets (1.736 arquivos, 22 classes) | pasta `Dataset/5` fornecida pela equipe | **não usado** (exige auditoria/split; ver `docs/catalogo-dados.md`) |

Para reproduzir, copie as pastas para `data/raw/` (ignorada pelo Git) ou aponte os caminhos diretamente:

```powershell
# D2 -> catálogo de conhecimento do backend + relatório de qualidade
ia\.venv\Scripts\python ia\triage\build_knowledge.py --d2 <caminho>\veterinary_clinical_data.csv

# D3 -> auditoria de vazamento, treino, validação cruzada agrupada e exportação ONNX (~10 min em CPU)
ia\.venv\Scripts\python ia\vision\training\train_vision.py --dataset <caminho>\Dataset\3 `
    --out ia\vision\service\model --report-dir docs\relatorios
```

(para treinar: `pip install -r ia/requirements-training.txt`)

## Privacidade

Nenhum dado pessoal real está versionado. Tutores, pets e eventos do banco local são **fictícios** (seed de demonstração). Fotos enviadas pelo chat **não são armazenadas**: guarda-se apenas o hash SHA-256 e o resultado.
