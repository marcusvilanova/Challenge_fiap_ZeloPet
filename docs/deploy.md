# Versão online (Vercel)

A Vercel não executa o backend Java. Por isso a versão online é **estática**: as mesmas telas (`index.html`, `chat.html`, `clinic.html`), com a API rodando **no próprio navegador**.

| O que | Como funciona na Vercel |
|---|---|
| Motor de triagem | Porte em JavaScript de `br.com.zelo.ai` e `br.com.zelo.chat` (`web/engine/`). Dá **o mesmo resultado do Java** nos conjuntos ouro, holdout e cego (`npm test`) |
| API | `web/local-api.js` substitui o `js/api.js` no build. Mesmas rotas e formatos de resposta |
| Dados | Ficam no `localStorage` do navegador (contas, pets e conversas de demonstração). Cada visitante tem os seus; nada vai para servidor |
| Login | Contas de exemplo entram com qualquer senha (é demonstração); contas criadas no cadastro guardam o hash da senha no navegador |
| Clínica | Funciona no **mesmo navegador**: entre como tutora, compartilhe, saia e entre como `vet@zelo.demo` |
| IA generativa | Opcional: função `api/llm.js` chama o **Google Gemini** com a chave guardada só nas variáveis da Vercel. Mesmas travas do Java: só soma sintomas, só reescreve baixa/média, `ResponseGuard` reprova dose/remédio/diagnóstico |
| Análise de foto | Indisponível online (o classificador ONNX roda no serviço Python). O chat avisa e segue funcionando |

## Publicar

1. Na Vercel, importe o repositório do GitHub (o `vercel.json` já define `buildCommand: node web/build.mjs` e `outputDirectory: web/dist`; não mude o "Root Directory").
2. (Opcional) Em **Settings → Environment Variables**, crie `GEMINI_API_KEY` com a sua chave e, se quiser, `GEMINI_MODEL` (padrão `gemini-2.5-flash`). Faça **Redeploy**.
3. Abra o endereço da Vercel e entre como **Tutora Ana**.

Sem `GEMINI_API_KEY` o selo do topo mostra "Motor local (sem LLM)" — é o caminho validado. Com a chave, mostra o modelo do Gemini e o rodapé do chat avisa que o texto vai para um provedor externo.

## Cuidados

- **Nunca** coloque a chave em arquivo do repositório; só no painel da Vercel. Troque a chave se ela já apareceu em algum lugar público.
- O caminho com o Gemini **não foi testado contra a API real** por quem escreveu o código. Teste antes de gravar; se algo falhar, o app cai no motor local sozinho.
- A função é pública: qualquer visitante pode gerar chamadas ao Gemini na sua conta. Para a demonstração, ligue a chave só no período do vídeo, ou defina um limite de uso no Google AI Studio.

## Rodar a versão estática no computador

```powershell
node web/build.mjs
ia\.venv\Scripts\python -m http.server 5173 --directory web\dist   # http://127.0.0.1:5173
npm test                                                          # paridade JS × Java
```
