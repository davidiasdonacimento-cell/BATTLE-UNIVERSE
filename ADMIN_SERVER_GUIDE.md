# BATTLE UNIVERSE — Central privada de configurações

## Objetivo
O aplicativo BATTLE UNIVERSE ADMIN serve exclusivamente para alterar configurações globais do jogo. Não oferece pesquisa, edição, suspensão ou exclusão de contas. Não existe login com usuário e senha administrativos.

## Proteção do proprietário
O serviço usa a variável privada `OWNER_PAIRING_CODE`, com pelo menos 32 caracteres aleatórios. O código:
- fica somente nas variáveis privadas do Render;
- nunca é incluído no APK nem no repositório;
- é digitado na ativação inicial do aparelho;
- é comparado no servidor antes de emitir um token de proprietário;
- não é armazenado no aparelho; somente o token é guardado cifrado com Android Keystore.

As rotas de leitura e gravação de configurações exigem Bearer token com papel `owner`. A API rejeita chamadas sem token ou com token de jogador. O token tem validade de 30 dias. Para revogar tokens existentes, rotacione `JWT_SECRET` (isso também invalida sessões de jogadores); para impedir novas ativações, altere ou remova `OWNER_PAIRING_CODE`.

## Variáveis privadas do Render
Mantenha:
- `DATABASE_URL`: conexão PostgreSQL.
- `JWT_SECRET`: segredo aleatório com pelo menos 32 caracteres.
- `OWNER_PAIRING_CODE`: código privado aleatório com pelo menos 32 caracteres.

Não configure `ADMIN_USERNAME` ou `ADMIN_PASSWORD`; o fluxo antigo de login administrativo foi removido da API.

## Endpoints do proprietário
- `POST /v1/owner/pair`: troca o código privado por token de proprietário.
- `GET /v1/owner/settings`: lê nome do jogo e nomes de personagens.
- `PUT /v1/owner/settings`: valida e salva as configurações no PostgreSQL.

O endpoint `GET /v1/game/settings` continua disponível para clientes de jogo autenticados, para ler as configurações globais.

## Limitações conhecidas
O painel atual altera somente nome global do jogo e nomes de personagens por ID. Outras categorias (modos, mapas, economia, eventos e partidas) ainda precisam ser implementadas no modelo de configurações e integradas ao jogo antes de aparecerem como controles funcionais. A API não renomeia o pacote Android nem altera o binário já instalado automaticamente.
