# Catálogo de dados

Quais dados o projeto usa, de onde vêm, como foram tratados e quais riscos carregam. Como obter cada fonte e reproduzir: [`data/README.md`](../data/README.md).

## 1. Fontes de dados

| Fonte | Papel na Sprint 4 | Volume | Tratamento | Está no repositório? |
|---|---|---|---|---|
| **D2** — registros clínicos veterinários (`veterinary_clinical_data.csv`) | Vocabulário de sintomas, cobertura do catálogo, frequência de referência por espécie, perfil do pet | 10.000 registros brutos → 9.980 limpos (20 duplicatas exatas removidas) | Auditoria, normalização em minúsculas/espaços, mapeamento de 935 grafias para o catálogo canônico, similaridade (≥ 0,88) para erros de digitação | Só uma **amostra controlada** (`data/samples/d2_clean_sample.csv`) |
| **D3** — doenças de pele em cães (imagens) | Treino do classificador de visão | 4.315 imagens, 6 classes (dermatite, infecção fúngica, saudável, hipersensibilidade, demodicose, tinha) | Auditoria de duplicatas e de **vazamento** por imagem-fonte, validação cruzada agrupada, exportação ONNX | Só **12 fotos de amostra** (`data/samples/d3`) |
| **Catálogo de sintomas** (curadoria da equipe) | Léxico PT-BR, severidade por espécie, famílias, sinais de alerta | 72 sintomas, 536 padrões | Revisado em rodadas contra conjuntos de avaliação | Sim (`ia/triage/symptom_catalog.py`) |
| **Conjuntos de avaliação** (escritos pela equipe) | Medir o motor de triagem | 86 (ouro) + 56 (holdout) + 35 (cego) relatos com prioridade e sintomas esperados | Protocolo de primeira rodada sem ajuste | Sim (`ia/triage/eval/*.jsonl`) |
| **Dados de demonstração** (seed) | Rodar o app local | 2 tutores, 3 pets, 1 clínica, vacinas e eventos fictícios | Gerados na subida **se** `ZELO_SEED_DEMO=true` | Não há dados; só o gerador |
| **D1, D4, D5** | — | — | **Não usados** nesta entrega | Não |

## 2. Dados produzidos pelo app (em execução)

| Dado | Finalidade | Retenção/tratamento |
|---|---|---|
| Conta (e-mail, nome, hash BCrypt da senha) | Autenticação | No banco local; a senha nunca é guardada |
| Pet (nome, espécie, raça, nascimento, peso, tags de histórico) | Contexto da triagem | No banco; só o tutor dono acessa |
| Conversa e mensagens | Continuidade e rastreabilidade | No banco; só o tutor dono acessa |
| Triagem (relato, saída estruturada, versões de modelo/regras) | Rastreabilidade e revisão | No banco; a clínica só vê se **compartilhada com consentimento ativo** |
| Linha do tempo do pet | Histórico de eventos | Anotações livres do tutor **não** vão à clínica |
| Consentimento (escopo, versão do texto, concessão, revogação) | LGPD | Revogar oculta as triagens enviadas |
| Resultado da análise de foto + **hash SHA-256** da imagem | Apoio e auditoria | **A foto não é armazenada** |

O banco local (H2) fica em `backend/data/` e está no `.gitignore`.

## 3. Qualidade e riscos

| Fonte | Achado | Consequência no projeto |
|---|---|---|
| D2 | Parcialmente sintético; sem diagnóstico-alvo confiável; 28,4% das linhas repetem sintoma na mesma linha; `AnimalName` traz a espécie, não o nome; lift ≈ 1 para sintomas comuns | **Não** serve para treinar classificador de doenças. Usado para vocabulário e cobertura (81,2% das menções viram sintoma canônico) |
| D2 | 14,1% das menções (cauda longa) não mapeadas | Relato fora do léxico → média + revisão humana, e o LLM (opcional) complementa |
| D3 | A mesma foto-fonte do Roboflow aparece, com *augmentations*, em treino, validação e teste (ex.: 357 das 433 imagens de teste têm fonte já vista) | A acurácia do teste original (91,5%) é inflada; usamos validação cruzada **agrupada** (82,2% ± 1,3) |
| D3 | Só cães; classes desbalanceadas (hipersensibilidade tem ~7% das imagens) | O app recusa gatos e marca a saída como experimental |
| Conjuntos de avaliação | Escritos pela própria equipe | Medem consistência, não a variedade de relatos reais |

Procedência: o D2 e o D3 foram fornecidos pela equipe (pasta de dados da Sprint 3). Antes de redistribuir qualquer dado, confira a **licença de cada fonte original** — o repositório só leva amostras.
