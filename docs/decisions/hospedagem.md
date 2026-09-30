# Hospedagem, acesso, auditoria e administração

orelha.app pelo Cloudflare Tunnel + Access, o que só roda no PC do acervo, auditoria e a aba do administrador. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

**Hospedagem (decidido em 2026-09-16).** Cloudflare Tunnel + Access a partir do PC do dono: o extrator
não cabe em plano gratuito, o resto é leve. O backend serve o Angular compilado na mesma origem
(`spring.web.resources.static-locations` → `frontend/dist/frontend/browser`, `SpaForwardController` faz
o fallback das rotas), então o túnel aponta para `localhost:8081` só. **No ar desde 2026-09-17 em `https://orelha.app`**: domínio no Cloudflare Registrar, túnel `orelha`
(id em `%USERPROFILE%\.cloudflared\config.yml`, serviço do Windows), Zero Trust Free com a aplicação
"Orelha" e a política "usuarios" (Allow por e-mail, One-time PIN; id do túnel e nome da equipe ficam fora do
repositório, em `CLAUDE.local.md` e na memória). Liberar
alguém = adicionar o e-mail na política. Roteiro e limites (100 MB por upload no plano Free) em
`deploy/README.md`. **Importar pasta é só no PC do acervo** (2026-09-17): `RemoteImportGuard` responde 403
em `/api/tracks/import*` e `/import-path*` quando a requisição traz os cabeçalhos que o túnel e o Access
injetam (`Cf-Connecting-Ip`, `Cf-Access-Authenticated-User-Email`; `RemoteAccess`); `GET /api/access` diz
à UI se o acesso é remoto e o botão "+ importar pasta" fica desabilitado com a explicação no `title`.
Upload de uma faixa continua liberado remotamente. **Quem está logado e o que fez** (2026-09-17): a barra
mostra o e-mail do Access no canto direito ("local" sem túnel; `shared/access.ts` é o único `GET /api/access`
por carga); `AuditFilter` grava em `audit_event` (V12) ENTER (`GET /api/access`), OPEN_TRACK
(`GET /api/tracks/{id}/timeline`) e ACTION (POST/PUT/DELETE em /api) com ator, IP, status e duração, em
transação própria (`AuditService`), nunca derrubando a requisição; leituras de apoio e polling não entram.
Aba **administrador** (`/admin`, `admin/`) só para `orelha.admin.emails` (padrão dfcsantos@gmail.com) e para
acesso local: resumo por usuário e eventos descritos em português (`GET /api/admin/audit[/users]`, 403 para
os demais). `AdminProperties.isAdmin` decide; `/api/access` devolve `admin`. **Excluir faixa é só do administrador**
(2026-09-17): `DELETE /api/tracks/{id}` responde 403 aos demais e 409 com run RUNNING; `TrackService.remove`
trava os runs (`lockByTrackId`, FOR UPDATE — o SKIP LOCKED do worker pula o que está saindo), apaga runs e
faixa pelo JPA (o resto vai por cascata, V5) e devolve os arquivos do host, que `TrackRemoval.delete` apaga
**depois do commit**, sem nunca falhar a operação: áudio da biblioteca (não o cadastrado por path fora dela),
`data/stems/<sha>/` (e o Parquet, até o extrator 0.7.0). Stems e features são por SHA do áudio, então ficam se outra faixa tiver os
mesmos bytes (`upload` e `POST /api/tracks` não deduplicam por SHA; só o import faz). Botão ✕ na lista do
acervo só com `access.admin()`, com `confirm()`; erros das ações da lista aparecem acima do acervo
(`actionError`), não dentro do formulário de upload — foi por isso que o ↻ postou em `/api/tracks//analyze`
por dois dias sem ninguém ver (corrigido em 2026-09-17). Tarefa agendada "Orelha" (`deploy/install-task.ps1` → `start-orelha.ps1`) sobe Docker,
compose e backend no logon; o backend de produção é dela, não de sessões de desenvolvimento. **Reiniciar é
`Stop-ScheduledTask Orelha` + `Start-ScheduledTask Orelha`**: o Stop libera a tarefa mas **não alcança o java
filho** (ela bloqueia no `run.ps1`), então desde 2026-09-24 o `start-orelha.ps1` encerra quem estiver
escutando na porta antes de subir — e só se for `java`, para nunca encostar no que ocupa a 8080. Sem isso o
backend novo morria com "Port 8081 was already in use" e o antigo seguia no ar, o que só aparece no
`deploy/logs/backend.log`. Plano B
com o PC desligado: Oracle Cloud Always Free para banco, backend e stems, extrator em casa.

**Revisão de segurança (2026-09-30).** O cadastro de faixa por caminho do servidor (`POST /api/tracks`) saiu:
qualquer e-mail liberado no Access podia registrar um arquivo da máquina e baixá-lo por `/audio`. `PUT` e
`DELETE` de artista e álbum passaram a exigir administrador; as cinco cópias de "se não é admin, 403" viraram
`AdminProperties.require(request, ação)`. `POST` de artista e álbum continua aberto (o formulário de upload os cria
remotamente). As edições musicais (tonalidade, partes, letra, referência, MusicBrainz) seguem abertas a quem o
Access deixa entrar — é decisão de produto, não descuido.
