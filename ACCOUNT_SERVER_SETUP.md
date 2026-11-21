# Servidor próprio de contas — BATTLE UNIVERSE

O backend novo fica em `server/` e usa FastAPI + PostgreSQL. Ele substitui Firebase Authentication/Firestore para identidade e sincronização dos campos de perfil permitidos.

## Hospedagem prevista

- API: Render Web Service usando o Dockerfile em `server/Dockerfile`.
- Banco: PostgreSQL gerenciado (Neon ou outro provedor PostgreSQL).
- URL esperada do serviço Render: `https://battle-universe-accounts.onrender.com` (só ficará ativa depois de criar e implantar o serviço).
- O blueprint `render.yaml` cria o serviço e solicita a variável DATABASE_URL. O JWT_SECRET é gerado pelo Render.

## Configuração do banco

Crie um banco PostgreSQL gerenciado e copie a connection string com SSL. No Render, abra o serviço `battle-universe-accounts`, vá a Environment e defina `DATABASE_URL` com essa connection string. Não publique essa URL no GitHub ou no chat.

## Deploy

1. No Render, crie um Blueprint e conecte este repositório GitHub.
2. Confirme o blueprint `render.yaml`.
3. Informe DATABASE_URL quando solicitado.
4. Aguarde o deploy e abra `/health`. O resultado esperado é JSON com `status: ok`.
5. Abra `/docs` para conferir os endpoints.

## Estado da integração Android

A API está no repositório, mas ainda precisa ser conectada ao cliente Android. Até essa integração, o APK existente continua usando a implementação anterior e NÃO está conectado ao novo servidor. Não remova o projeto Firebase nem seus arquivos de configuração antigos até validar cadastro, login, restauração de sessão e sincronização no APK novo.

## Segurança e limites atuais

- Nicknames únicos e normalizados em minúsculas no PostgreSQL.
- Senhas com hash Argon2; nunca armazenadas em texto puro.
- JWT com expiração; o cliente deve guardar o token localmente e não a senha.
- O endpoint de perfil só aceita campos permitidos de configuração e cosméticos.
- Moedas, diamantes, XP, inventário, recompensas e resultados não são aceitos do cliente como autoridade. Precisam de endpoints de economia/partida validados no servidor.
- Use HTTPS e mantenha DATABASE_URL/JWT_SECRET somente nas variáveis de ambiente do provedor.
- Ainda não há recuperação de senha por e-mail nem revogação remota de tokens.
