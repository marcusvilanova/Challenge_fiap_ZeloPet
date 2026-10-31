# Checklist de entrega — Sprint 4 (Disruptive Architectures: IoT, IoB & Generative IA)

Prazo informado no enunciado: **04/11/2026**. O que o grupo precisa fazer, na ordem. Itens marcados com **[GRUPO]** só a equipe pode fazer (publicar, gravar, assinar com os nomes).

## 1. Conteúdo exigido

| Exigência | Como foi atendida | Pronto? |
|---|---|---|
| Implementação da solução de IA da Sprint 3, integrada ao app | `backend/` (Java) + front-end estático + `ia/` | Sim |
| Testes e validação | `docs/validacao-e-testes.md`, `docs/relatorios/` | Sim |
| Evidências (telas, fluxos, relatórios) | `docs/evidencias/` | Sim |
| README com uso, tecnologias e resultados parciais | `README.md` | Sim — **[GRUPO]** inserir os links |
| Vídeo pitch de ~5 min (YouTube, não listado) | roteiro em `docs/roteiro-video-pitch.md` | **[GRUPO]** gravar e publicar |
| Repositório no GitHub | pasta `zelo-challenge/` pronta para o primeiro commit | **[GRUPO]** criar e publicar |
| .zip com os links | `scripts/build-zip.ps1` gera `entrega-sprint4-zelo.zip` | **[GRUPO]** conferir e enviar |

## 2. Passo a passo

1. **[GRUPO] Revisar e ensaiar.** Rode `.\scripts\run-local.ps1`, siga o "Roteiro de 2 minutos" do README e o ensaio do vídeo. Se for mostrar o LLM ligado, teste antes com a chave (o caminho real não foi executado contra a API).
2. **[GRUPO] Gravar o vídeo** com `docs/roteiro-video-pitch.md` e publicar no YouTube como **Não listado**. Copie o link.
3. **[GRUPO] Criar o repositório** no GitHub e publicar esta pasta. Antes do primeiro commit:
   - confira que **não há** `backend/data`, `ia/.venv`, `backend/target`, `ia/vision/training/cache` (o `.gitignore` já cobre);
   - confira que **não há** chave de API em nenhum arquivo (`git grep -i "sk-ant"` deve voltar vazio);
   - o `README.md` já cita a senha **de demonstração** (dados fictícios); não use essa senha em nenhum ambiente real.
   ```powershell
   git init; git add .; git status   # revise a lista antes de commitar
   git commit -m "feat: Zelo - chatbot de triagem (Sprint 4)"
   ```
4. **[GRUPO] Preencher os links** no `README.md` (topo) — vídeo e repositório — e commitar.
5. **Gerar o .zip:** `.\scripts\build-zip.ps1 -Video "<link do YouTube>" -Repo "<link do GitHub>"`. O script grava o `LEIA-ME.txt` com os links e a lista do grupo, e inclui o código, a documentação e as evidências (sem `.venv`, `target`, banco local e cache de treino).
6. **[GRUPO] Conferir o .zip:** abra o `LEIA-ME.txt` e teste os dois links em uma janela anônima (o vídeo não listado abre com o link; o repositório precisa estar acessível aos professores — se for privado, convide-os).
7. **[GRUPO] Enviar** no portal da FIAP até o prazo.

## 3. Mapa de pontuação (do enunciado)

| Critério | Peso | Onde o grupo mostra |
|---|---|---|
| Solução de IA (técnica) | 60 | chatbot funcionando, regras + LLM opcional + visão, validação honesta, testes |
| Vídeo pitch | 20 | roteiro, demonstração real, números corretos, limitações |
| Repositório e documentação | 20 | README, `docs/`, estrutura organizada, evidências |

## 4. Verificação final (rode antes de enviar)

```powershell
cd backend; .\mvnw.cmd verify                 # 195 testes; relatório em target\site\jacoco
cd ..; ia\.venv\Scripts\python -m pytest tests\ia   # 33 testes
.\scripts\run-local.ps1                       # app no ar; depois: ia\.venv\Scripts\python scripts\smoke_test.py
```

## 5. Integrantes

Bruno Ferreira (RM 563489) · Gabriel Robertoni Padilha (RM 566293) · Hebert Lopes do Santos (RM 563192) · Marcus Vinícius Vila Nova da Silva (RM 558771) · Nicolas Monteiro Ramiro (RM 562380)
