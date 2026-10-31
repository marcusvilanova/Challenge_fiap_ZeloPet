# Deploy público (Vercel na frente + Render atrás)

A Vercel não executa Java. Por isso: **a Vercel serve o front-end e repassa `/api/*` ao backend hospedado no Render**. Para o navegador é um só endereço (`zelopet.vercel.app`), então sessão e CSRF funcionam normalmente.

> Estes arquivos (Dockerfiles, `render.yaml`, `vercel.json`) **não foram executados num deploy real** por quem os escreveu: o primeiro deploy pode pedir ajustes. Para gravar o vídeo, o app local (`scripts/run-local.ps1`) continua sendo o caminho já testado.

## 1. Render (backend + visão)
1. Em render.com, **New → Blueprint** e escolha este repositório. Ele lê o `render.yaml` e cria `zelopet-backend` e `zelopet-vision`.
2. Preencha **no painel** (nunca no repositório):
   - `ZELO_DEMO_PASSWORD`: uma senha **nova** para as contas de demonstração (não use a do README);
   - `ZELO_VISION_URL`: `https://zelopet-vision.onrender.com` (o endereço real do serviço de visão; opcional).
3. Anote o endereço do backend (ex.: `https://zelopet-backend.onrender.com`) e teste `/actuator/health`.

O plano gratuito **dorme** após alguns minutos parado e leva ~1 min para acordar: abra o app antes de gravar. Os dados ficam em disco efêmero (o seed recria as contas a cada subida).

## 2. Vercel (front-end)
1. No `vercel.json`, troque `SEU-BACKEND.onrender.com` pelo endereço do backend e faça commit/push; a Vercel publica sozinha.
2. Abra `https://zelopet.vercel.app`, entre com `ana@zelo.demo` e a senha definida no Render.

## 3. Cuidados
- App público = cadastro aberto a qualquer pessoa. Não ligue chave de IA generativa sem ter noção do custo.
- `ZELO_LLM_PROVIDER=local` (motor de regras) é o caminho validado.
- Nunca coloque chaves em arquivos do repositório; só no painel do Render.
