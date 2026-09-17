# Hospedagem: Orelha em qualquer navegador (Cloudflare Tunnel + Access)

Decidido em 2026-09-16. O extrator (demucs, BTC, Whisper) não roda em plano gratuito nenhum, então o
Orelha continua no PC do dono e é exposto por um túnel de saída, com login na borda. Gratuito; a única
condição é o PC ligado. Quando quiser que fique no ar com o PC desligado, o plano B é Oracle Cloud
Always Free (ARM, 24 GB) para banco, backend e stems, com o extrator em casa.

## O que já está no código

- O backend serve o Angular compilado na mesma origem da API (`spring.web.resources.static-locations`
  aponta para `frontend/dist/frontend/browser`; `SpaForwardController` manda as rotas da SPA para o
  `index.html`). Um endereço só: `http://localhost:8081`.
- Segredos ficam no `.env` da raiz, carregado por `backend/run.ps1`.

## Passo a passo (uma vez)

1. **Domínio no Cloudflare** (plano Free). O túnel precisa de um hostname num domínio cujo DNS esteja
   no Cloudflare. Se não tiver domínio, o Cloudflare Registrar vende a preço de custo (~US$ 10/ano).
2. **Instalar o agente**: `winget install Cloudflare.cloudflared`.
3. **Criar o túnel** (abre o navegador para autorizar):
   ```powershell
   cloudflared tunnel login
   cloudflared tunnel create orelha
   cloudflared tunnel route dns orelha orelha.app
   ```
   Anote o id impresso pelo `create`.
4. **Configurar**: copie `deploy/cloudflared/config.yml.example` para `%USERPROFILE%\.cloudflared\config.yml`
   e preencha `<TUNNEL_ID>` e o hostname.
5. **Testar**: `cloudflared tunnel run orelha` e abra `https://orelha.app`. Deve mostrar o
   acervo (com o backend rodando na 8081 e o frontend compilado, ver abaixo).
6. **Login na borda (obrigatório antes de divulgar o endereço)**: no painel Cloudflare → Zero Trust →
   Access → Applications → Add → Self-hosted; domínio `orelha.app`; política *Allow* com
   *Emails* = os e-mails dos poucos usuários; identity provider *One-time PIN* (código por e-mail, sem
   senha). Gratuito até 50 usuários. Sem isso a API fica aberta ao mundo, e o acervo é áudio com
   direitos autorais.
7. **Subir com o Windows**: `cloudflared service install` registra o agente como serviço. Para o
   backend, um Agendador de Tarefas "ao iniciar sessão" rodando `C:\Users\dfcsa\orelha\backend\run.ps1 -Port 8081`,
   e `docker compose up -d` na raiz para Postgres e extrator (o Docker Desktop precisa iniciar com o
   Windows).

## Rotina

- Frontend: depois de mudar código Angular, `cd frontend && npx ng build` (produção). O backend lê a
  pasta em tempo real; não precisa reiniciar. O `ng serve` na 4200 continua sendo o modo de desenvolvimento.
- Backend: `.\backend\run.ps1 -Port 8081` (carrega o `.env`).

## Endurecimento recomendado

- Só o túnel deve chegar ao backend: acrescente `SERVER_ADDRESS=127.0.0.1` ao `.env` para o Spring não
  escutar na rede local. (Tire quando for usar o "Orelha no bolso" pela LAN.)
- O Postgres do compose expõe a 5432 só em localhost; não abrir.

## Limites conhecidos

- **Upload pelo túnel: 100 MB por requisição** no plano Free do Cloudflare. Faixas em MP3 passam; álbuns
  em FLAC ou lotes grandes de "importar pasta" não. Para importar muita coisa, use `localhost:8081` no
  próprio PC ou `POST /api/tracks/import-path` (pasta já no disco).
- Stems de ~20 MB por faixa saem pelo túnel sem problema; o player faz `Range`, que o Cloudflare respeita.
- O PC precisa estar ligado e com o Docker Desktop no ar; a fila de análise só anda em casa.
