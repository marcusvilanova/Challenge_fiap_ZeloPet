"""Treino do classificador dermatológico (D3 - doenças de pele em cães).

Estratégia: transfer learning com *backbone congelado* (ImageNet) + classificador logístico
multinomial treinado sobre as features. É a opção mais defensável para ~3 mil imagens em CPU:
barata, reprodutível e sem risco de overfitting do backbone.

Etapas
  1. Auditoria do dataset: hash MD5, duplicatas e vazamento entre train/valid/test
     (os nomes "*.rf.<hash>" indicam augmentations do Roboflow: o mesmo "source" pode
     aparecer em splits diferentes).
  2. Extração de features para cada backbone candidato (cache em .npz).
  3. Escolha de backbone e C *somente pela validação* (macro-F1).
  4. Avaliação única no teste: completo e "limpo" (sem fontes vistas no treino).
  5. Exportação para ONNX (backbone + normalização + classificador + softmax) e model_card.json.

Uso:
  python train_vision.py --dataset <pasta D3> --out ../service/model --report-dir <docs/relatorios>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.preprocessing import StandardScaler
from torch import nn
from torchvision import models

LOG = logging.getLogger("zelo.vision.train")

SPLITS = ("train", "valid", "test")
IMAGE_SIZE = 224
IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)
SEED = 42
LABELS_PT = {
    "demodicosis": "Demodicose (sarna demodécica)",
    "Dermatitis": "Dermatite",
    "Fungal_infections": "Infecção fúngica",
    "Healthy": "Pele saudável",
    "Hypersensitivity": "Hipersensibilidade (reação alérgica)",
    "ringworm": "Tinha (dermatofitose)",
}
CONFIDENCE_THRESHOLD = 0.5


@dataclass(frozen=True)
class Sample:
    path: Path
    split: str
    label: str
    md5: str
    source: str  # nome-base antes de ".rf." (imagem original do Roboflow)


# --------------------------------------------------------------------------- auditoria
def collect_samples(dataset: Path) -> list[Sample]:
    samples: list[Sample] = []
    for split in SPLITS:
        for class_dir in sorted(p for p in (dataset / split).iterdir() if p.is_dir()):
            for img in sorted(class_dir.glob("*.jpg")):
                md5 = hashlib.md5(img.read_bytes()).hexdigest()  # noqa: S324 (hash de integridade)
                source = img.name.split(".rf.")[0]
                samples.append(Sample(img, split, class_dir.name, md5, source))
    return samples


def audit(samples: list[Sample]) -> dict:
    by_split: dict[str, list[Sample]] = defaultdict(list)
    for s in samples:
        by_split[s.split].append(s)

    report: dict = {"splits": {}, "overlaps": {}}
    for split, items in by_split.items():
        report["splits"][split] = {
            "images": len(items),
            "unique_md5": len({s.md5 for s in items}),
            "unique_sources": len({s.source for s in items}),
            "classes": dict(sorted(Counter(s.label for s in items).items())),
        }
    for a, b in (("train", "valid"), ("train", "test"), ("valid", "test")):
        md5_a, md5_b = {s.md5 for s in by_split[a]}, {s.md5 for s in by_split[b]}
        src_a, src_b = {s.source for s in by_split[a]}, {s.source for s in by_split[b]}
        report["overlaps"][f"{a}~{b}"] = {
            "identical_files": len(md5_a & md5_b),
            "shared_sources": len(src_a & src_b),
            "images_in_b_with_shared_source": sum(1 for s in by_split[b] if s.source in src_a),
        }
    return report


# ------------------------------------------------------------------------- features
def build_feature_extractor(name: str) -> tuple[nn.Module, int]:
    if name == "mobilenet_v3_large":
        net = models.mobilenet_v3_large(weights=models.MobileNet_V3_Large_Weights.IMAGENET1K_V2)
        body = nn.Sequential(net.features, net.avgpool, nn.Flatten())
        dim = 960
    elif name == "efficientnet_b0":
        net = models.efficientnet_b0(weights=models.EfficientNet_B0_Weights.IMAGENET1K_V1)
        body = nn.Sequential(net.features, net.avgpool, nn.Flatten())
        dim = 1280
    elif name == "resnet50":
        net = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V2)
        net.fc = nn.Identity()
        body, dim = net, 2048
    else:
        raise ValueError(f"backbone desconhecido: {name}")
    return body.eval(), dim


def load_tensor(path: Path) -> torch.Tensor:
    with Image.open(path) as img:
        arr = np.asarray(img.convert("RGB").resize((IMAGE_SIZE, IMAGE_SIZE), Image.BILINEAR),
                         dtype=np.float32) / 255.0
    arr = (arr - np.array(IMAGENET_MEAN, dtype=np.float32)) / np.array(IMAGENET_STD, dtype=np.float32)
    return torch.from_numpy(arr.transpose(2, 0, 1))


@torch.no_grad()
def extract(backbone: nn.Module, samples: list[Sample], batch: int = 32) -> np.ndarray:
    feats: list[np.ndarray] = []
    for i in range(0, len(samples), batch):
        x = torch.stack([load_tensor(s.path) for s in samples[i:i + batch]])
        feats.append(backbone(x).numpy())
        if (i // batch) % 20 == 0:
            LOG.info("  %d/%d", min(i + batch, len(samples)), len(samples))
    return np.concatenate(feats)


def cached_features(name: str, samples: list[Sample], cache_dir: Path) -> np.ndarray:
    cache = cache_dir / f"features_{name}.npz"
    keys = np.array([f"{s.split}/{s.label}/{s.path.name}" for s in samples])
    if cache.exists():
        data = np.load(cache, allow_pickle=False)
        if np.array_equal(data["keys"], keys):
            LOG.info("features em cache: %s", cache.name)
            return data["x"]
    LOG.info("extraindo features: %s (%d imagens)", name, len(samples))
    backbone, _ = build_feature_extractor(name)
    start = time.time()
    x = extract(backbone, samples)
    LOG.info("  %s: %.1f s", name, time.time() - start)
    cache_dir.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(cache, x=x, keys=keys)
    return x


# ------------------------------------------------------------------------ modelagem
def fit_classifier(x_train: np.ndarray, y_train: np.ndarray, c: float) -> tuple[StandardScaler, LogisticRegression]:
    scaler = StandardScaler().fit(x_train)
    clf = LogisticRegression(C=c, max_iter=3000, class_weight="balanced", random_state=SEED)
    clf.fit(scaler.transform(x_train), y_train)
    return scaler, clf


def evaluate(scaler: StandardScaler, clf: LogisticRegression, x: np.ndarray, y: np.ndarray,
             classes: list[str]) -> dict:
    probs = clf.predict_proba(scaler.transform(x))
    pred = probs.argmax(1)
    top = probs.max(1)
    accepted = top >= CONFIDENCE_THRESHOLD
    return {
        "n": int(len(y)),
        "accuracy": round(float(accuracy_score(y, pred)), 4),
        "macro_f1": round(float(f1_score(y, pred, average="macro")), 4),
        "per_class": classification_report(y, pred, target_names=classes, output_dict=True,
                                           zero_division=0),
        "confusion_matrix": confusion_matrix(y, pred, labels=range(len(classes))).tolist(),
        "selective": {
            "threshold": CONFIDENCE_THRESHOLD,
            "coverage": round(float(accepted.mean()), 4),
            "accuracy_when_accepted": round(float(accuracy_score(y[accepted], pred[accepted])), 4)
            if accepted.any() else None,
        },
    }


def connected_groups(samples: list[Sample]) -> np.ndarray:
    """Agrupa imagens que compartilham a imagem-fonte do Roboflow OU o mesmo conteúdo (MD5)."""
    parent = list(range(len(samples)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    first_seen: dict[str, int] = {}
    for i, s in enumerate(samples):
        for key in (f"src:{s.source}", f"md5:{s.md5}"):
            if key in first_seen:
                parent[find(i)] = find(first_seen[key])
            else:
                first_seen[key] = i
    roots = {r: n for n, r in enumerate(sorted({find(i) for i in range(len(samples))}))}
    return np.array([roots[find(i)] for i in range(len(samples))])


def grouped_cross_validation(x: np.ndarray, y: np.ndarray, groups: np.ndarray, c: float, folds: int = 5) -> dict:
    """Validação cruzada sem vazamento: nenhuma imagem-fonte aparece em treino e teste ao mesmo tempo."""
    from sklearn.model_selection import StratifiedGroupKFold

    accs, f1s = [], []
    splitter = StratifiedGroupKFold(n_splits=folds, shuffle=True, random_state=SEED)
    for train_idx, test_idx in splitter.split(x, y, groups):
        scaler, clf = fit_classifier(x[train_idx], y[train_idx], c)
        pred = clf.predict(scaler.transform(x[test_idx]))
        accs.append(accuracy_score(y[test_idx], pred))
        f1s.append(f1_score(y[test_idx], pred, average="macro"))
    return {"folds": folds, "groups": int(len(set(groups.tolist()))),
            "accuracy_mean": round(float(np.mean(accs)), 4), "accuracy_std": round(float(np.std(accs)), 4),
            "macro_f1_mean": round(float(np.mean(f1s)), 4), "macro_f1_std": round(float(np.std(f1s)), 4)}


class ExportedModel(nn.Module):
    """backbone -> padronização -> regressão logística -> softmax (tudo em um grafo ONNX)."""

    def __init__(self, backbone: nn.Module, scaler: StandardScaler, clf: LogisticRegression) -> None:
        super().__init__()
        self.backbone = backbone
        self.register_buffer("mean", torch.tensor(scaler.mean_, dtype=torch.float32))
        self.register_buffer("scale", torch.tensor(scaler.scale_, dtype=torch.float32))
        self.register_buffer("weight", torch.tensor(clf.coef_, dtype=torch.float32))
        self.register_buffer("bias", torch.tensor(clf.intercept_, dtype=torch.float32))

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        feats = (self.backbone(image) - self.mean) / self.scale
        return torch.softmax(feats @ self.weight.T + self.bias, dim=1)


def export_onnx(name: str, scaler: StandardScaler, clf: LogisticRegression, out: Path,
                sample_tensor: torch.Tensor) -> float:
    import onnxruntime as ort

    backbone, _ = build_feature_extractor(name)
    model = ExportedModel(backbone, scaler, clf).eval()
    out.mkdir(parents=True, exist_ok=True)
    onnx_path = out / "skin_classifier.onnx"
    dummy = sample_tensor.unsqueeze(0)
    torch.onnx.export(model, dummy, str(onnx_path), input_names=["image"], output_names=["probs"],
                      dynamic_axes={"image": {0: "batch"}, "probs": {0: "batch"}}, opset_version=17)
    with torch.no_grad():
        expected = model(dummy).numpy()
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    got = session.run(None, {"image": dummy.numpy()})[0]
    return float(np.abs(expected - got).max())


# ------------------------------------------------------------------------- relatório
def render_markdown(card: dict, audit_report: dict, candidates: list[dict]) -> str:
    classes = card["classes"]
    lines = ["# D3 — auditoria, treino e avaliação do classificador de pele", "",
             f"_Gerado em {card['trained_at']} por `ia/vision/training/train_vision.py`._", "",
             "## 1. Auditoria do dataset", "",
             "| Split | Imagens | MD5 únicos | Fontes únicas | Classes |", "|---|---|---|---|---|"]
    for split in SPLITS:
        s = audit_report["splits"][split]
        dist = ", ".join(f"{k}: {v}" for k, v in s["classes"].items())
        lines.append(f"| {split} | {s['images']} | {s['unique_md5']} | {s['unique_sources']} | {dist} |")
    lines += ["", "**Vazamento entre splits** (mesmo arquivo ou mesma imagem-fonte do Roboflow):", "",
              "| Par | Arquivos idênticos | Fontes compartilhadas | Imagens do 2º split com fonte já vista |",
              "|---|---|---|---|"]
    for pair, o in audit_report["overlaps"].items():
        lines.append(f"| {pair} | {o['identical_files']} | {o['shared_sources']} | "
                     f"{o['images_in_b_with_shared_source']} |")
    lines += ["", "## 2. Seleção de backbone (somente validação)", "",
              "| Backbone | Dim. features | C | Acurácia (valid) | Macro-F1 (valid) |", "|---|---|---|---|---|"]
    for c in candidates:
        mark = " ✅" if c["name"] == card["backbone"] else ""
        lines.append(f"| {c['name']}{mark} | {c['dim']} | {c['best_c']} | {c['val_accuracy']} | {c['val_macro_f1']} |")

    for title, key in (("3. Teste completo", "test"), ("4. Teste limpo (sem fontes vistas no treino/validação)", "test_clean")):
        m = card["metrics"][key]
        lines += ["", f"## {title}", "", f"- Imagens: **{m['n']}**  ·  Acurácia: **{m['accuracy']}**  ·  Macro-F1: **{m['macro_f1']}**",
                  f"- Com limiar de confiança {m['selective']['threshold']}: cobertura {m['selective']['coverage']}, "
                  f"acurácia nas aceitas {m['selective']['accuracy_when_accepted']}", "",
                  "| Classe | Precisão | Recall | F1 | Suporte |", "|---|---|---|---|---|"]
        for cls in classes:
            r = m["per_class"].get(cls)
            if r:
                lines.append(f"| {cls} | {r['precision']:.2f} | {r['recall']:.2f} | {r['f1-score']:.2f} | {int(r['support'])} |")
        lines += ["", "Matriz de confusão (linhas = real, colunas = previsto):", "",
                  "| real \\ previsto | " + " | ".join(classes) + " |", "|---|" + "---|" * len(classes)]
        for cls, row in zip(classes, m["confusion_matrix"]):
            lines.append(f"| {cls} | " + " | ".join(str(v) for v in row) + " |")
    cv = card["metrics"]["grouped_cv"]
    lines += ["", "## 5. Validação cruzada agrupada (sem vazamento de imagem-fonte)", "",
              f"{cv['folds']} folds estratificados sobre as {sum(audit_report['splits'][s]['images'] for s in SPLITS)} imagens, "
              f"agrupadas em {cv['groups']} grupos (mesma fonte Roboflow ou mesmo MD5 ficam sempre no mesmo fold).", "",
              f"- Acurácia: **{cv['accuracy_mean']:.3f} ± {cv['accuracy_std']:.3f}**",
              f"- Macro-F1: **{cv['macro_f1_mean']:.3f} ± {cv['macro_f1_std']:.3f}**", "",
              "## 6. Leitura honesta dos resultados", "",
              "- O classificador é **experimental** e treinado só com cães; nunca é diagnóstico.",
              "- O split original do dataset tem vazamento (augmentations da mesma foto em treino e teste): o teste completo é otimista.",
              "- A referência confiável é a **validação cruzada agrupada** (seção 5); o teste limpo (n pequeno) serve de conferência.",
              "- A literatura citada na Sprint 3 (93–95%) usa outros datasets/arquiteturas; não é meta garantida aqui.", ""]
    return "\n".join(lines)


# ------------------------------------------------------------------------------ main
def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--report-dir", type=Path, required=True)
    parser.add_argument("--cache-dir", type=Path, default=Path(__file__).parent / "cache")
    parser.add_argument("--backbones", default="mobilenet_v3_large,efficientnet_b0,resnet50")
    parser.add_argument("--threads", type=int, default=6)
    parser.add_argument("--tolerance", type=float, default=0.01,
                        help="escolhe o menor backbone a até esta diferença do melhor macro-F1 de validação")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    torch.set_num_threads(args.threads)
    np.random.seed(SEED)
    torch.manual_seed(SEED)

    samples = collect_samples(args.dataset)
    LOG.info("%d imagens coletadas", len(samples))
    audit_report = audit(samples)
    classes = sorted({s.label for s in samples})
    y = np.array([classes.index(s.label) for s in samples])
    split_idx = {sp: np.array([i for i, s in enumerate(samples) if s.split == sp]) for sp in SPLITS}

    candidates: list[dict] = []
    for name in args.backbones.split(","):  # ordem = do menor para o maior modelo
        x = cached_features(name, samples, args.cache_dir)
        _, dim = build_feature_extractor(name)
        grid = []
        for c in (0.001, 0.01, 0.1, 1.0):
            scaler, clf = fit_classifier(x[split_idx["train"]], y[split_idx["train"]], c)
            val = evaluate(scaler, clf, x[split_idx["valid"]], y[split_idx["valid"]], classes)
            grid.append((val["macro_f1"], c, val["accuracy"]))
            LOG.info("%s C=%s val acc=%.4f macroF1=%.4f", name, c, val["accuracy"], val["macro_f1"])
        f1, c, acc = max(grid)
        candidates.append({"name": name, "dim": dim, "best_c": c, "val_accuracy": acc, "val_macro_f1": f1, "x": x})

    # Regra de seleção (pela VALIDAÇÃO, nunca pelo teste): o menor modelo cujo macro-F1 fique a
    # até --tolerance do melhor. Modelo menor = inferência mais rápida em CPU e arquivo leve no repositório.
    top_f1 = max(cand["val_macro_f1"] for cand in candidates)
    best = next(cand for cand in candidates if cand["val_macro_f1"] >= top_f1 - args.tolerance)
    name, c, x = best["name"], best["best_c"], best["x"]
    LOG.info("selecionado: %s (C=%s) — menor modelo a %.3f do melhor macro-F1 de validação (%.4f)",
             name, c, args.tolerance, top_f1)

    # refit em train (a escolha foi feita na validação; o teste permanece intocado)
    scaler, clf = fit_classifier(x[split_idx["train"]], y[split_idx["train"]], c)

    seen_sources = {s.source for s in samples if s.split in ("train", "valid")}
    test_idx = split_idx["test"]
    clean_idx = np.array([i for i in test_idx if samples[i].source not in seen_sources])
    metrics = {"test": evaluate(scaler, clf, x[test_idx], y[test_idx], classes),
               "valid": evaluate(scaler, clf, x[split_idx["valid"]], y[split_idx["valid"]], classes)}
    metrics["test_clean"] = (evaluate(scaler, clf, x[clean_idx], y[clean_idx], classes)
                             if len(clean_idx) else {**metrics["test"], "n": 0})
    metrics["grouped_cv"] = grouped_cross_validation(x, y, connected_groups(samples), c)
    LOG.info("CV agrupada (%d grupos): acc %.4f±%.4f | macroF1 %.4f±%.4f", metrics["grouped_cv"]["groups"],
             metrics["grouped_cv"]["accuracy_mean"], metrics["grouped_cv"]["accuracy_std"],
             metrics["grouped_cv"]["macro_f1_mean"], metrics["grouped_cv"]["macro_f1_std"])
    LOG.info("TESTE acc=%.4f f1=%.4f | TESTE LIMPO n=%d acc=%.4f f1=%.4f",
             metrics["test"]["accuracy"], metrics["test"]["macro_f1"], metrics["test_clean"]["n"],
             metrics["test_clean"]["accuracy"], metrics["test_clean"]["macro_f1"])

    diff = export_onnx(name, scaler, clf, args.out, load_tensor(samples[0].path))
    LOG.info("ONNX exportado; divergência máx. torch vs onnxruntime: %.2e", diff)

    card = {
        "model_version": f"zelo-skin-{name}-lr-1.0",
        "task": "classificação dermatológica experimental (cães)",
        "backbone": name, "classes": classes, "labels_pt": LABELS_PT,
        "input": {"size": IMAGE_SIZE, "mean": IMAGENET_MEAN, "std": IMAGENET_STD, "layout": "NCHW float32 RGB"},
        "confidence_threshold": CONFIDENCE_THRESHOLD,
        "classifier": {"type": "LogisticRegression(multinomial, class_weight=balanced)", "C": c},
        "trained_at": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "dataset": {"name": "D3 - Doenças de pele em cães", "images": len(samples),
                    "split_sizes": {k: int(len(v)) for k, v in split_idx.items()}},
        "onnx_max_abs_diff": diff,
        "metrics": {k: {kk: vv for kk, vv in v.items()} for k, v in metrics.items()},
        "disclaimer": "Classificação experimental. Não é diagnóstico; sempre procure um médico-veterinário.",
    }
    (args.out / "model_card.json").write_text(json.dumps(card, ensure_ascii=False, indent=2), encoding="utf-8")
    args.report_dir.mkdir(parents=True, exist_ok=True)
    (args.report_dir / "d3_auditoria_e_treino.md").write_text(
        render_markdown(card, audit_report, candidates), encoding="utf-8")
    (args.report_dir / "d3_auditoria.json").write_text(json.dumps(audit_report, indent=2), encoding="utf-8")
    LOG.info("concluído")


if __name__ == "__main__":
    main()
