# Servidor próprio de contas — BATTLE UNIVERSE

O backend usa FastAPI + PostgreSQL. O APK comunica com a API por HTTPS.

## Persistência do login entre atualizações

- A conta e o hash Argon2 da senha ficam no PostgreSQL.
- A senha original nunca é guardada no APK nem enviada para o armazenamento local.
- Após cadastro/login, a API devolve um token de sessão JWT válido por até 30 dias.
- O APK guarda token, nickname e validade num blob AES-GCM criptografado com uma chave criada e protegida pelo Android Keystore.
- Atualizar o APK por cima da instalação existente preserva os dados privados e a chave Keystore, desde que package name e certificado de assinatura permaneçam iguais.
- Desinstalar o jogo, limpar os dados do aplicativo, trocar de dispositivo ou perder a chave de assinatura pode remover a sessão local. A conta continua no PostgreSQL; basta entrar novamente com nickname e senha.
- A sessão não é eterna: após 30 dias será necessário autenticar novamente.

## Deploy da API

1. Crie PostgreSQL gerenciado e copie a connection string com SSL.
2. No Render, crie um Blueprint deste repositório e confirme `render.yaml`.
3. Defina `DATABASE_URL` no serviço; o Render gera `JWT_SECRET`.
4. Aguarde o deploy e confirme `/health` e `/docs`.
5. Copie a URL HTTPS pública do serviço.

## Conectar o APK

No GitHub, abra Settings → Secrets and variables → Actions → New repository secret.
Crie `ACCOUNT_API_BASE_URL` com a URL HTTPS pública do serviço, sem barra final.
A próxima compilação incluirá esse endereço em BuildConfig.

## Manter atualizações instaláveis sem perder a sessão

O Android só aceita atualização sobre o APK existente se o package name e o certificado de assinatura forem os mesmos. Configure os quatro secrets de assinatura usados pelo workflow:
- `BATTLE_UNIVERSE_KEYSTORE_BASE64`
- `BATTLE_UNIVERSE_KEYSTORE_PASSWORD`
- `BATTLE_UNIVERSE_KEY_ALIAS`
- `BATTLE_UNIVERSE_KEY_PASSWORD`

A chave de assinatura precisa ser a mesma que assinou o APK que está instalado no aparelho. Se os APKs anteriores foram assinados por chaves temporárias diferentes do GitHub Actions, não será possível atualizá-los por cima; será necessário instalar uma build assinada pela chave estável uma vez. Não desinstale o APK atual até confirmar a assinatura compatível.

## Carteira online e códigos promocionais

- GET /v1/accounts/me/wallet retorna a carteira autoritativa player_wallets.
- POST /v1/game/rewards/redeem recebe { "code": "CODIGO" } e valida o código ativo cadastrado pelo ADMIN. O servidor credita a quantidade de diamantes configurada no PostgreSQL uma única vez por conta, respeitando o limite global de resgates; repetir o pedido não concede novamente. O código legado 100K permanece disponível se estiver ativo no cadastro do servidor.
- O saldo antigo de moedas/diamantes salvo no perfil é migrado uma única vez para a carteira na primeira consulta. O ledger antigo é usado para preservar o estado de resgate anterior do código 100K.
- coins e diamonds não podem mais ser enviados pelo cliente em PATCH /v1/accounts/me/profile. A carteira é lida e alterada por endpoints próprios.
- POST /v1/game/rewards/daily concede a recompensa diária pelo servidor. POST /v1/royale/spin debita e registra giros no servidor.

## Limites atuais da economia

- Recompensas de partida, missões, passe, evento, correio, conquistas e compras que ainda chamam PlayerProfileStore são locais e não aumentam a carteira online.
- O modo de partida atual ainda não tem um serviço de partidas autoritativo. Não é seguro conceder diamantes online com base apenas em kills/vitória informados pelo APK; esses dados podem ser falsificados.
- A migração da economia restante exige endpoints e critérios de validação do servidor de partidas, além de idempotência e auditoria.

## Segurança

- Nicknames exclusivos no PostgreSQL, normalizados em minúsculas.
- Senhas protegidas com Argon2; nunca em texto puro.
- Token JWT com validade máxima de 30 dias, guardado no Android Keystore.
- O perfil aceita apenas configurações e cosméticos não econômicos.
- Diamantes e moedas não são aceitos como valores enviados pelo cliente para o perfil.
- Ainda não existe recuperação de senha nem revogação remota individual de sessão.
