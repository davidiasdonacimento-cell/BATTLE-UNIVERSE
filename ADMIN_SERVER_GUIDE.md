# BATTLE UNIVERSE — Central privada de configurações

## Objetivo
O aplicativo BATTLE UNIVERSE ADMIN é uma central privada do proprietário. Ele permite consultar o resumo, pesquisar contas, editar nickname e personagem, suspender/reativar contas, encerrar contas por exclusão lógica, consultar e ajustar diamantes, gerenciar códigos promocionais, alterar configurações globais e consultar auditoria. Não existe login administrativo com usuário e senha.

## Proteção do proprietário
O serviço usa a variável privada `OWNER_PAIRING_CODE`, com pelo menos 32 caracteres aleatórios. O código:
- fica somente nas variáveis privadas do Render;
- nunca é incluído no APK nem no repositório;
- é digitado na ativação inicial do aparelho;
- é comparado no servidor antes de emitir um token de proprietário;
- não é armazenado no aparelho; somente o token é guardado cifrado com Android Keystore.

Todas as rotas administrativas (resumo, contas, alterações, exclusão lógica, configurações e auditoria) exigem Bearer token com papel `owner`. A API rejeita chamadas sem token ou com token de jogador. A tela do aplicativo também pergunta “Quantos anos tem?” e aceita `1000`; essa pergunta é apenas uma barreira local de interface, não substitui a autorização forte do servidor. O token tem validade de 30 dias. Para revogar tokens existentes, rotacione `JWT_SECRET` (isso também invalida sessões de jogadores); para impedir novas ativações, altere ou remova `OWNER_PAIRING_CODE`.

## Variáveis privadas do Render
Mantenha:
- `DATABASE_URL`: conexão PostgreSQL.
- `JWT_SECRET`: segredo aleatório com pelo menos 32 caracteres.
- `OWNER_PAIRING_CODE`: código privado aleatório com pelo menos 32 caracteres.

Não configure `ADMIN_USERNAME` ou `ADMIN_PASSWORD`; o fluxo antigo de login administrativo foi removido da API.

## Endpoints do proprietário
- `POST /v1/owner/pair`: troca o código privado por token de proprietário.
- `GET /v1/owner/overview`: resumo de contas.
- `GET /v1/owner/accounts`: pesquisa/listagem paginada.
- `PATCH /v1/owner/accounts/{player_id}`: edita nickname/personagem ou suspende/reativa.
- `DELETE /v1/owner/accounts/{player_id}`: exclusão lógica; bloqueia o acesso e preserva registro mínimo para auditoria.
- `GET /v1/owner/settings` e `PUT /v1/owner/settings`: leitura e gravação de nome do jogo e nomes de personagens.
- `GET /v1/owner/accounts/{player_id}/wallet`: saldo de diamantes e últimas 30 movimentações.
- `POST /v1/owner/accounts/{player_id}/wallet`: adicionar ou retirar diamantes, com motivo, ID idempotente e auditoria.
- `GET /v1/owner/promos`: lista códigos, recompensas, status e contagem de resgates.
- `POST /v1/owner/promos`: cria código promocional com recompensa em diamantes e limite total (0 = ilimitado).
- `PATCH /v1/owner/promos/{promo_id}`: ativa ou desativa um código.
- `GET /v1/owner/audit`: últimas operações administrativas.

O endpoint `GET /v1/game/settings` continua disponível para clientes de jogo autenticados, para ler as configurações globais.

## Limitações conhecidas
A economia oficial é exclusivamente em diamantes. O painel permite ajustar saldos e gerenciar códigos promocionais. A API mantém histórico de operações da carteira. Outras categorias (modos, mapas, eventos e partidas) ainda precisam ser implementadas no modelo de configurações e integradas ao jogo antes de aparecerem como controles funcionais. A API não renomeia o pacote Android nem altera o binário já instalado automaticamente.
