# BATTLE UNIVERSE — Administração do servidor

## Segurança e provisionamento
O administrador não é criado pelo APK e não existe senha mestra embutida no aplicativo. Configure no serviço privado do Render:

- `DATABASE_URL`: URL PostgreSQL do banco persistente.
- `JWT_SECRET`: segredo aleatório com pelo menos 32 caracteres.
- `ADMIN_USERNAME`: usuário administrativo inicial (3–64 caracteres).
- `ADMIN_PASSWORD`: senha administrativa exclusiva com pelo menos 12 caracteres.
- `ADMIN_TOKEN_MINUTES`: validade do token administrativo (padrão 120).

Na primeira inicialização, a API cria o administrador se ele ainda não existir. A senha é armazenada como hash Argon2. Alterar `ADMIN_PASSWORD` no ambiente não redefine automaticamente um administrador já criado; isso exige fluxo explícito de rotação de credencial, ainda não disponibilizado.

## Endpoints administrativos
Todos, exceto o login, exigem Bearer token emitido por `POST /v1/admin/login`:

- `GET /v1/admin/overview`: totais de contas.
- `GET /v1/admin/accounts?search=nome&limit=50&offset=0`: pesquisa e listagem.
- `PATCH /v1/admin/accounts/{id}`: altera nickname, nome de exibição do personagem e estado active/suspended.
- `DELETE /v1/admin/accounts/{id}`: exclusão lógica; bloqueia login, invalida a senha e limpa o perfil, preservando o registro mínimo para auditoria.
- `GET /v1/admin/settings` e `PUT /v1/admin/settings`: nome global do jogo e mapa de nomes de personagens por ID.
- `GET /v1/admin/audit`: histórico de operações administrativas.

A alteração de nome do jogo e nomes de personagens fica persistida no banco, mas o APK do jogo só passará a exibir essas configurações depois que a próxima integração do cliente consultar o endpoint de configurações. O servidor não altera arquivos binários nem renomeia o pacote Android.

## Implantação
No Render, crie um Web Service apontando para este repositório e defina o Root Directory como `server`. Build command: `pip install -r requirements.txt`. Start command: `uvicorn main:app --host 0.0.0.0 --port $PORT`. Adicione as variáveis acima e conecte um PostgreSQL persistente. O endpoint de saúde é `/health`.

## Limitações desta etapa
O código usa `create_all` para criar as tabelas administrativas novas; alterações futuras de esquema devem usar migrações versionadas (Alembic). Configure backups do PostgreSQL antes de operações reais. A exclusão administrativa é lógica e auditada, não remoção física imediata. Proteja o painel com HTTPS e não compartilhe o APK administrativo com jogadores comuns.
