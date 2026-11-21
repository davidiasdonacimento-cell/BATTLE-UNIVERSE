# BATTLE UNIVERSE — estado e decisões confirmadas

Documento de continuidade obrigatório. Antes de qualquer alteração, revisar este arquivo, o histórico de requisitos e a implementação real. Não criar versões artificiais por etapa.

## Requisitos permanentes
- Android nativo Java + C++ / OpenGL ES 3.0; sem Unity, Godot ou WebView.
- Lobby de battle royale mobile, não aparência de site.
- Personagens masculinos e femininos com aparência humana, modelos 3D completos, rig, animações e skins equipáveis.
- Lobby com personagem central, cenário 3D, câmera dinâmica, menus laterais e botão JOGAR.
- Telas pré-partida: personagens, cofre/inventário, loja, Royale, passe, missões, eventos, perfil, amigos/equipe, configurações, modos, coleção/equipamentos, correio, conquistas/classificação, notícias, calendário e resgate de códigos.
- Loja com preços e compras funcionais; Royale com diamantes virtuais, probabilidades exibidas e somando 100%, histórico e prêmios no inventário.
- Escolha ONLINE/OFFLINE; 30 participantes; offline jogador + 29 bots; online aguarda 30 segundos por humanos e completa com bots quando servidor real estiver pronto.
- Fluxo da partida: sala de espera, contagem, aeronave caminhável, mapa tático, salto, queda livre, paraquedas, combate, resultado e recompensas.
- Modos solo, dupla, esquadrão e treinamento; bots NORMAL; fogo amigo desligado.
- Armas, equipamentos, loot, construção, zona segura, reviver aliados, missões, estatísticas, progressão, clima e ciclo dia/noite.
- Pacote adicional com conteúdo real, download dentro do jogo, validação e carregamento; nunca inflar com dados vazios ou duplicados.
- Monetização futura apenas transparente e opcional, respeitando as regras das lojas. Não prometer renda.

## Arquitetura confirmada
- MainActivity cria GameSurfaceView e HudOverlay em FrameLayout.
- GameSurfaceView encaminha controles e JNI.
- game_engine.cpp renderiza mundo/personagens em OpenGL ES 3.0.
- HudOverlay desenha telas Android sobre a engine.
- Identidade e perfis online migrando para API própria FastAPI + PostgreSQL; Firebase foi descontinuado nesta arquitetura.

## Estado anterior comprovado
- 29 bots locais, 30 participantes no modo offline.
- Modos Battle Royale, dupla, esquadrão e treinamento.
- IA local básica de patrulha, perseguição e combate.
- Aeronave 3D com corredor, rampa, movimentação, embarque de 7 segundos e saída de segurança aos 34 segundos.
- Salto, queda livre, paraquedas, armas, loot, construção, zona segura, estados derrubado/rastejar/reviver.
- Loja, inventário, missões, passe, perfil, seleção visual e roupas básicas.
- Royale de 100 diamantes com probabilidades 40/30/20/9/1; compensação por duplicados.
- Online permanece bloqueado sem servidor multiplayer real.
- Modelo anterior era Bloob geométrico, não personagem humano realista.
- Build GitHub Actions do commit 54d5d359 passou; APK debug de aproximadamente 9,15 MB e pacote de conteúdo de aproximadamente 429 MB. Sem teste físico comprovado no Redmi 12.

## Alterações publicadas nesta sequência
- Navegação do lobby usa painel vertical compacto e mapeamento explícito de páginas para não confundir as telas com os estados internos de jogo/roleta.
- Seleção visual mantém quatro operadores: Caio, Maya, Dante e Luna; perfis masculino/feminino.
- Engine contém uma primeira silhueta humanoide procedural. Ela ainda não é malha realista profissional, não possui rig importado e não equivale a animações profissionais/mocap.
- Lobby agora expõe as 14 áreas centrais planejadas: personagens, cofre/inventário, loja, Royale/passe, missões, eventos, perfil, amigos/equipe, configurações, modos, coleção/equipamentos, correio, conquistas e mapas. A tela inicial permanece como entrada adicional.
- Novas telas de eventos e correio oferecem resgate local de recompensas únicas; conquistas liberam recompensas conforme XP; amigos permite salvar um nome de contato apenas no aparelho. Essas ações não sincronizam entre usuários e não são funcionalidades online.
- A navegação foi compactada para caber no layout paisagem 1280x720; é necessário validar visualmente no Redmi 12.

## Conta BATTLE UNIVERSE — arquitetura oficial sem Firebase
- Firebase está removido da arquitetura-alvo e não deve ser adicionado como dependência nova.
- Identidade, cadastro, login, sessões, perfis e sincronização online serão atendidos pela API própria FastAPI + PostgreSQL.
- O cliente Android manterá cache local e fila offline; a API será a fonte de verdade dos dados online, com operações idempotentes e validação no servidor.
- Senhas devem ser armazenadas somente como hash seguro no backend (Argon2 ou algoritmo equivalente), nunca em SharedPreferences ou logs. Tokens devem ser protegidos no armazenamento privado do Android.
- Recuperação de conta, troca de senha e validação de sessão precisam ser implementadas pela API própria, sem depender de provedores Firebase.
- Status: arquitetura definida; remoção de SDKs, arquivos de configuração, regras, chamadas e migração dos dados existentes depende da auditoria técnica e dos testes de compatibilidade. Não declarar a remoção do código concluída antes dessa verificação.
 
## Mundo 3D — expansão de marcos por mapa (código publicado, build pendente)
- A engine OpenGL ES recebeu conjuntos geométricos próprios para os oito destinos: marina e cabanas costeiras (Ilha Tropical); skyline, fachadas luminosas e via elevada (Neon City); mesas, ravinas e arcos de pedra (Desert Storm); espigões de gelo e estação polar (Frozen Front); tanques, tubulações e pórticos (Industrial Zone); rio, pontes e colinas (Green Valley); plataformas de lançamento e hangares (Orbital Base); muros colapsados, torres partidas e campos de destroços (Abandoned Sector).
- Os marcos são desenhados com primitivas 3D nativas da engine; não são imagens planas nem apenas troca de paleta.
- Limitação desta alteração: a base territorial, parte das estradas e a distribuição principal de edifícios ainda são compartilhadas entre os oito mapas. Os novos marcos são principalmente visuais e ainda não têm colisão/interação própria, loot exclusivo, interiores exclusivos ou navegação específica. Portanto, os oito mapas ainda não devem ser descritos como oito mundos totalmente independentes.
- A compilação do commit desta expansão e o teste visual no Redmi 12 ainda precisam ser confirmados.

## Arsenal definitivo — correção publicada em 2026-11-21 (build pendente)
- O arsenal permitido passa a conter somente **Desert Eagle (Desert), Mini Uzi, XM8 e UMP**. Pulso Plasma, Raio Arc, Nova e Carapina foram retiradas da engine.
- A engine C++ usa quatro índices coerentes (0–3) para modelo desenhado, nome, munição, dano, alcance, cadência, coleta e troca de arma.
- O loot do BR foi redistribuído para oferecer apenas essas quatro armas e suas munições.
- O Treinamento disponibiliza as quatro armas, com munição infinita, e inicia com a Mini Uzi equipada; as quatro também aparecem como coletas próximas ao ponto inicial.
- Os modelos atuais continuam sendo formas 3D procedurais desenhadas pela engine, não modelos externos de alta resolução.
- Esta alteração está publicada no código; ainda depende de build CI e teste no aparelho para confirmar a exibição real das quatro armas.

## Pendências explícitas
- Criar/importar modelos humanos de alta qualidade (GLB), rig profissional e biblioteca de animações; validar skins modulares reais.
- Fazer teste visual e funcional no Redmi 12; confirmar orientação, escala, FPS, deformações e controles.
- Completar telas secundárias e integrar ações reais em cada uma.
- Servidor multiplayer autoritativo, matchmaking, salas, sincronização, reconexão e anti-cheat.
- Sala de espera separada antes do avião; hoje a contagem/embarque ocorrem no avião.
- Mapas com geometria/interiores/assets exclusivos de alta fidelidade.
- Revisar segurança, privacidade, pagamentos e políticas antes de monetizar.

## Regra de comprovação
- Alteração publicada = código no GitHub.
- Build verde = compilação confirmada.
- APK baixável = artefato confirmado.
- Teste no aparelho = funcionamento verificado no dispositivo.
- Nunca confundir planejado, implementado, compilado e testado.


## Novos modos solicitados — regras confirmadas em 2026-11-20

### Gladiadores
- Duelo individual: vence o primeiro participante a alcançar 5 eliminações.
- Ao atingir 5 eliminações, encerrar o duelo, exibir placar/resultado e preparar uma nova partida com contagem reiniciada.
- Cada participante escolhe seu equipamento antes do duelo; adversários controlados por IA também devem usar escolhas válidas.
- A implementação precisa de estado próprio de duelo, placar até 5, reinício de rodada e seleção de equipamento. Não reutilizar o contador de BR como se fosse o modo completo.

### Contra Squad (CS)
- Dois times; a partida termina quando um time vence 4 rounds.
- Um round termina quando todos os integrantes de um dos times forem eliminados.
- Início da economia: cada participante começa com pistola e saldo inicial definido pelo balanceamento.
- Recompensas em moeda por round/desempenho; loja de equipamentos entre rounds; compras descontam do saldo e o inventário comprado vale para a partida.
- Selecionar/alternar mapa entre partidas, não no meio de uma partida em andamento.
- Exige lógica de times, eliminação completa, placar de rounds, fase de compra, carteira e inventário por partida. Ainda não considerar implementado pela tela de seleção.

### Battle Royale (BR)
- Expandir o mapa real da engine, não apenas o desenho do minimapa: maior área navegável, setores distintos, casas com interiores acessíveis, cidades/vilas, estradas, vegetação e pontos de interesse.
- Distribuir itens/coletas pelo mundo com posições reais, colisão e interação; manter inventário e persistência coerentes durante a partida.
- Melhorar qualidade visual com geometria, materiais, iluminação, sombras e efeitos compatíveis com OpenGL ES 3.0 e com o desempenho do Redmi 12.
- Preservar o limite atual de 30 participantes offline (jogador + 29 bots) até que testes confirmem capacidade maior.

### Regras de implementação e comprovação
- Os modos novos devem coexistir com os modos atuais (BR, Dupla, Esquadrão e Treinamento), sem alterar os IDs 0–3 usados pela JNI/native engine.
- Antes de ativar qualquer novo modo na interface, implementar e testar o respectivo fluxo na engine; não apresentar seleção visual como modo funcional.
- Multiplayer humano exige servidor autoritativo e matchmaking; bots locais não contam como jogadores online.
- Testar compilação e, separadamente, testar no Redmi 12. Não afirmar que mapas, interiores, IA, economia ou modos estão prontos sem evidência.


## Geografia própria dos oito mapas — código publicado, build pendente
- `drawMapTerrain()` agora cria formas de relevo específicas por mapa, em vez de depender apenas do disco de terreno circular e de marcos decorativos:
  1. Ilha Tropical: espinhaço central, morros costeiros separados e enseadas.
  2. Neon City: quadras elevadas em terraços e canal urbano rebaixado.
  3. Desert Storm: dunas/mesas e corredor de vale seco.
  4. Frozen Front: cordilheiras glaciais e corredor de fenda.
  5. Industrial Zone: taludes de escavação, bacias e platôs fabris.
  6. Green Valley: colinas onduladas e vale fluvial.
  7. Orbital Base: crateras, anéis de impacto e plataforma central.
  8. Abandoned Sector: crateras de impacto e cordões de entulho.
- `terrainPositionBlocked()` impede a movimentação de entrar nas áreas íngremes/ocupadas correspondentes às formas principais, além das colisões de construções já existentes.
- Limite técnico importante: o terreno ainda é composto por primitivas low-poly sobre uma superfície-base plana; não é um heightmap contínuo com inclinações caminháveis. As áreas de relevo alto são tratadas como obstáculos, não como encostas que o personagem escala. Rios/canais são representação visual e ainda precisam de regras próprias de travessia/água. Não declarar os mapas como totalmente finalizados.
- Compilação Android e teste visual após este commit ainda pendentes; o sucesso do workflow anterior não valida esta alteração.


## Relevo contínuo de mapas — malhas 3D e altura do movimento (build pendente)
- Cada um dos oito mapas agora tem uma malha de terreno triangulada própria, gerada por grade 64×64 com alturas e normais calculadas por vértice, enviada à GPU como VBO.
- Perfis de relevo: morros tropicais; terraços urbanos; dunas; cristas glaciais; taludes industriais; colinas e vale; crateras lunares; crateras e ruínas.
- A malha recebe coloração específica por mapa e faz transição de costa para abaixo do nível do oceano, evitando prolongar uma plataforma quadrada plana sobre a água.
- O movimento no solo atualiza a altura vertical do personagem conforme a função de terreno do mapa. A colisão existente continua bloqueando as áreas íngremes definidas.
- Limites: esta versão usa terreno procedural low-poly e aproximações matemáticas; não é um sistema completo de navegação em encostas, física de água, interiores ou destruição. Pontos de loot e alguns objetos antigos ainda precisam ser ajustados individualmente à altura do terreno.
- A compilação após os commits de relevo e teste visual no Redmi 12 ainda precisam ser confirmados. Não instalar até o workflow terminar e o APK correto ser validado.


## Configurações do motor — implementação publicada em 2026-11-21 (build pendente)
- A tela de configurações foi reorganizada para expor controles que já possuem integração real, sem manter botões de áudio que apenas alteravam preferências sem mixer conectado.
- Sensibilidade geral e sensibilidade ao mirar agora são valores independentes de 25% a 200%, persistidos em SharedPreferences e reaplicados quando a superfície OpenGL é recriada.
- A engine C++ recebe os dois valores por JNI e seleciona a sensibilidade de câmera livre ou de mira conforme o estado real de mira.
- A sensibilidade antiga de três níveis é migrada para um valor inicial compatível quando o usuário ainda não tem a nova preferência salva.
- Qualidade gráfica continua ligada a `nativeSetQuality`; inversão vertical, minimapa e háptica mantêm suas preferências existentes. A inversão vertical já é aplicada no deslocamento vertical enviado ao motor.
- Áudio/mixagem não é apresentado como configurável nesta tela enquanto não existir um mixer real integrado. Sensibilidade independente por ponto vermelho/2x/4x/sniper/olhadinha, layout avançado do HUD, volumes de áudio, coleta automática e demais ajustes continuam pendentes; não declarar o menu de configurações completo.
- Código publicado nos commits `56df6551231b16f9dc931ac530878a2402547f93`, `a16cf9e77f2a6c62dc527e973cbcc66a63d0f6de` e `390da6b529d633c2b1957f9fa79a3eafad599b43`. A compilação deste conjunto ainda precisa ser confirmada pelo GitHub Actions; não há teste físico no Redmi 12.


## Núcleo compartilhado de perfil/economia — código publicado em 2026-11-21 (build pendente)
- Novo `PlayerProfileStore` centraliza leitura do perfil, XP, nível, carteira de diamantes e carteira de moedas; migra saves existentes preservando XP/diamantes e inicializa a carteira de moedas sem sobrescrever um saldo já inicializado.
- Compras da loja debitam diamantes e registram a propriedade do item no mesmo commit local; saldo insuficiente, item já adquirido e falha de gravação têm respostas distintas.
- Resgates de missões, passe, recompensa diária, código 100K, evento, correio e conquistas passam pelo mesmo serviço, com marcadores de resgate e histórico local de até 60 movimentações.
- Giro Royale registra débito, prêmio, item/compensação por repetição, contador e histórico numa única gravação; o resultado só é mostrado após confirmação do commit.
- Recompensas de partida incluem moedas virtuais e identificador de sessão para evitar reaplicação da mesma recompensa local.
- Lobby superior, perfil, loja, inventário e painel de progresso consultam a mesma carteira; inventário apresenta as duas movimentações mais recentes. O painel de progresso abre a central de missões.
- `AccountSyncManager` deverá sincronizar moedas, ledger, configurações de sensibilidade, missões e marcos Royale pela API própria FastAPI; o ledger deverá detectar transações repetidas após restauração em outro aparelho.
- Limites: a carteira continua local/offline até existir servidor autoritativo; não é saldo online seguro. A loja atual ainda usa diamantes para os itens catalogados; catálogo de compras com moedas permanece pendente. O ledger guarda as 60 movimentações mais recentes. Ainda faltam testes automatizados de concorrência/migração, compilação Android e teste no Redmi 12.
- Arquivos: `PlayerProfileStore.java`, `HudOverlay.java`, `AccountSyncManager.java`. Não distribuir APK antes do workflow confirmar a compilação deste conjunto.


## Carteira dupla e cosméticos de perfil — código publicado em 2026-11-21 (build pendente)
- A carteira local agora tem moedas virtuais e diamantes separados. Perfis existentes recebem 1.000 moedas iniciais uma única vez, sem sobrescrever saldo já inicializado.
- Na loja, os três cosméticos de perfil (moldura neon, emblema Bloob e título Campeão) usam moedas; os itens de roupa continuam com preços em diamantes. As compras verificam saldo, registram propriedade e histórico na mesma gravação.
- Moldura, emblema e título adquiridos passam a aparecer no cabeçalho do perfil. Moedas também são concedidas por recompensas locais de partida, missões, evento, correio, passe e conquistas.
- O inventário apresenta as duas últimas movimentações da carteira. A sincronização pela API própria deverá incluir o saldo de moedas e o histórico.
- Validação realizada até agora: a classe `PlayerProfileStore.java` compilou isoladamente com `javac` usando uma interface `SharedPreferences` de teste. Isso valida sintaxe dessa classe, mas NÃO substitui a compilação Gradle/Android. GitHub Actions ainda precisa confirmar o APK completo; não houve teste físico no Redmi 12.


## Estatísticas de carreira e histórico de partidas — código publicado em 2026-11-21 (build pendente)
- O perfil registra partidas concluídas, vitórias, eliminações, dano causado e recursos coletados; cada partida usa um ID de sessão para evitar contagem duplicada.
- O histórico local mantém as 30 partidas mais recentes e deverá ser incluído na sincronização da conta pela API própria.
- Perfil e painel do lobby exibem partidas, vitórias e eliminações a partir da mesma fonte compartilhada.
- O registro de estatísticas é idempotente e pode ser repetido após uma falha sem somar novamente a mesma partida.
- Limite: estatísticas atuais vêm do modo local existente; ainda não representam partidas online, temporadas ranqueadas ou placares de servidor. Build Android completa e teste físico permanecem pendentes.


## Servidor próprio e aplicativo administrativo — implementação publicada em 2026-11-21

- A API `server/main.py` usa FastAPI + PostgreSQL, JWT com papéis separados (`player` e `admin`) e hash Argon2 para credenciais.
- Administrador inicial é provisionado por variáveis privadas `ADMIN_USERNAME` e `ADMIN_PASSWORD`; o APK não contém senha mestra.
- Endpoints administrativos: visão geral, pesquisa/listagem de contas, alteração de nickname, nome de exibição do personagem por conta, suspensão/reativação, exclusão lógica, nome global do jogo, nomes de personagens por ID e auditoria.
- Exclusão de conta é lógica: bloqueia login, troca o hash por credencial aleatória e limpa o perfil, mantendo registro mínimo e histórico de auditoria. Backups do PostgreSQL devem ser configurados antes de uso real.
- Novo módulo Android nativo `:admin`, pacote `com.battleuniverse.admin`, fornece login HTTPS, painel, pesquisa, edição, suspensão, encerramento, configurações globais e auditoria. É um APK separado do jogo; não usa WebView.
- Workflow GitHub Actions agora compila e verifica os dois APKs e publica artefatos separados.
- A URL HTTPS do servidor é informada na tela de login do ADMIN. O servidor ainda precisa ser implantado e receber PostgreSQL e variáveis privadas no Render antes de qualquer operação funcionar.
- O cliente do jogo ainda precisa consultar as configurações globais do servidor para aplicar nome do jogo/personagens no lobby e precisa ser integrado/testado com a API própria. A alteração administrativa do nome exibido não altera applicationId, nome do pacote nem assinatura do APK.
- Estado: código publicado; build em execução; sem confirmação de APK compilado, implantação de servidor ou teste no Redmi 12.
- Próxima construção de conteúdo: modelos 3D originais de barbas, skins clássicas exclusivas BATTLE UNIVERSE e equipamentos modulares com geometria, materiais, encaixes, colisões quando aplicável e integração no inventário/engine. Não copiar modelos, marcas ou skins proprietárias de outros jogos.


## Construção de barbas, skins e equipamentos — 2026-11-21

- A engine OpenGL ES 3.0 agora possui quatro slots de acessórios cosméticos do operador: barba (3 variantes), capacete (3), mochila (3) e colete (3), cada categoria com opção de remover. Os componentes são geometria 3D procedural encaixada em coordenadas locais do corpo e acompanham a rotação/pose do personagem.
- A página "ACESSÓRIOS 3D" foi adicionada à oficina de roupas; seleção é persistida em SharedPreferences e sincronizada pelo AccountSyncManager com o perfil PostgreSQL usando equipped_beard, equipped_helmet, equipped_backpack e equipped_vest.
- Foram adicionadas três skins originais BATTLE UNIVERSE: VULCÃO, NEBULAR e CERRADO, com paletas, placas, ombreiras e faixas geométricas próprias na engine. A loja usa preço fixo em diamantes virtuais e a seleção fica persistida/sincronizada.
- A API permite agora que cliente autenticado consulte /v1/game/settings. O login/restauração busca nome global do jogo, nomes de personagens e nome de exibição individual; HudOverlay aplica esses valores ao lobby.
- Limite importante: estes acessórios e skins são malhas/primitivas procedurais de OpenGL ES, não arquivos de personagem GLB/FBX com rig profissional. A etapa futura ainda precisa produzir/importar modelos de alta fidelidade, UVs, texturas, pesos/rig e animações de equipamento; esta entrega não deve ser descrita como modelos escaneados ou assets de estúdio.
- Integração administrativa e de conta está no código, mas não pode ser declarada operacional até o servidor e PostgreSQL serem implantados e testados.
- Bloqueio de hospedagem identificado: a conta Render já tem um único PostgreSQL gratuito ativo, pertencente ao projeto Quantum Hub, com expiração indicada em 2026-12-14. Não reutilizar banco de outro projeto. O Render recusou criar um segundo banco free. Para manter API no Render sem misturar dados, escolher um banco gratuito externo (ex.: Neon) ou autorizar plano pago com responsável pela conta.
- Estado desta construção: commits publicados; workflow Android em execução; APKs e teste no aparelho ainda não confirmados.


## Atualização THE VISION + Season 02 embutida + Royale — 2026-11-21
**Estado: código publicado; compilação e validação do APK pendentes. Não há teste físico confirmado.**

- O workflow gera o pacote Season 02 e copia o ZIP e o manifesto para os assets gerados do módulo Android antes do Gradle. O APK do jogo deve conter `assets/season02.zip` e `assets/season02-manifest.json`.
- `ContentPackInstaller` passou a ler os assets do APK, conferir tamanho e SHA-256 do ZIP, extrair em staging, validar hashes de cada recurso e ativar os dados de forma atômica com preservação/rollback. A instalação inicial não consulta o servidor nem baixa o ZIP.
- A primeira entrada apresenta a identidade original THE VISION, mascote procedural animado, partículas, anéis e etapas de configuração. O progresso é ligado à cópia, extração e validação reais; não deve exibir uma falsa conclusão.
- O workflow agora deve conferir presença dos assets, tamanho declarado, SHA-256, integridade do ZIP interno e tamanho do APK acima de 400 MB.
- Royale recebeu roleta animada com custo local de 100 diamantes, chances visíveis de 40/30/20/9/1 (100% no total), cinco cosméticos, teste temporário do visual antes do giro, compensação de 100 diamantes para repetidos e histórico persistido.
- O giro e a carteira continuam locais/offline. Não são compras online nem saldo autoritativo de servidor. Não habilitar monetização real com este mecanismo.
- O pacote ZIP da release antiga não foi apagado; permanece como rollback até o APK embutido ser compilado e validado. A existência do código no GitHub não comprova que o APK final foi produzido ou instalado.
- Após build: confirmar APK contém os dois assets e comparar hash; validar ZIP interno e hashes de arquivos; medir tamanho real; verificar instalação/armazenamento; só então considerar retirar a distribuição separada antiga.


## Carteira online e código 100K — código publicado em 2026-11-21 (deploy/build pendentes)
- O código promocional 100K agora é validado pela API FastAPI e concede 100.000 diamantes à carteira PostgreSQL; cada conta pode resgatá-lo uma vez, com unicidade no banco e retorno do saldo confirmado pelo servidor.
- O HUD chama o endpoint online e atualiza o saldo mostrado no cabeçalho com a carteira retornada. O resgate não concede diamantes localmente como fallback.
- A API migra uma única vez os saldos antigos de moedas/diamantes do perfil para player_wallets, removendo os campos antigos do perfil. A migração também registra o antigo resgate local 100K encontrado no ledger para evitar duplicação.
- coins e diamonds foram removidos da lista de campos que o cliente pode gravar no perfil; a carteira é consultada separadamente pelo endpoint /v1/accounts/me/wallet.
- A recompensa diária já usa o servidor e a roleta online já debita/credita na carteira PostgreSQL.
- Limite importante: recompensas de partidas, missões, passe, evento, correio, conquistas e compras que ainda usam PlayerProfileStore permanecem locais. O modo de partidas atual não possui servidor autoritativo capaz de validar resultados; não aceitar valores enviados pelo cliente como prova de partida online. Esses fluxos ainda precisam ser migrados com validação de servidor.
- Estado: alterações publicadas no GitHub; deploy Render, testes automatizados, build Android e teste no aparelho ainda não confirmados. Não declarar saldo online corrigido até verificar o deploy e testar /wallet e /redeem com uma conta.


## MODMENU2 e correção da sincronização — 2026-11-21 (build pendente)
- Causa encontrada na sincronização: o debounce e o botão `syncNow()` chamavam `saveProfile()` diretamente no thread principal do Android, embora `saveProfile()` faça HTTP síncrono. Isso provocava `NetworkOnMainThreadException`, capturada como “Falha ao sincronizar. Dados locais preservados”.
- Corrigido: todos os salvamentos disparados pelo botão e pelo listener de SharedPreferences agora são enfileirados no executor de rede. Falhas transitórias de rede recebem nova tentativa após 15 segundos e mostram o tipo/motivo da exceção, sem apagar SharedPreferences.
- Na restauração da conta, contadores de progresso são mesclados pelo maior valor, flags de desbloqueio/recompensa usam união lógica e históricos locais/remotos são unidos sem duplicatas (com limites de linhas), evitando substituir progresso local por um perfil remoto menor.
- Adicionado `MODMENU2` nas Configurações. O God Mode é restrito ao modo Treinamento offline; não altera partidas online. Não há aimbot nem edição de carteira pelo cliente. Moedas e diamantes permanecem controlados pela API autenticada.
- Estado: commits no GitHub; workflow de validação DEBUG está em execução. APK ainda não foi compilado nem testado no aparelho.


## Pacote Season 02 salvo no celular e reutilizado — 2026-11-21
- `ContentPackInstaller` agora salva o ZIP externo validado em `Downloads/Battle Universe/battle-universe-season-02-pack.zip` no Android 10+ por MediaStore.
- Em nova instalação/atualização, procura esse ZIP no armazenamento compartilhado, copia para a área privada de trabalho e valida o SHA-256 antes de extrair. Não baixa novamente quando o arquivo existente está íntegro.
- O ZIP é mantido após a instalação. Se estiver ausente, incompleto ou com hash incorreto, o instalador baixa uma nova cópia e substitui a salva.
- Android 7–9 usa armazenamento externo específico do app como fallback.
- Código publicado nos commits `1903fef`, `7c7ce21`, `af92026` e `6e51c0c`. A cópia privada temporária é removida após instalação bem-sucedida, mantendo o ZIP em Downloads; a checagem de espaço considera ZIP duplicado temporário e até 3 GiB de extração. Build Android e teste físico ainda não confirmados; o comportamento no aparelho permanece pendente de validação.

## Gladiadores offline + proteção MODMENU2 — implementação na branch em 2026-11-21
- A engine nativa recebe o modo 4, preservando sem migração os IDs existentes 0–3.
- Gladiadores é um duelo local 1v1: um único oponente IA, placar independente dos dois lados, vitória de quem alcançar primeiro 5 eliminações, reentrada após ponto, reinício de vida/posição e tela final com placar.
- Antes da partida, o jogador escolhe uma das quatro armas já permitidas; a IA recebe automaticamente uma escolha válida. O duelo começa direto na arena, sem aeronave, loot, kits, construção ou zona BR.
- Gladiadores é offline. Não concede moedas/diamantes por resultado local e não finge ser multiplayer online.
- MODMENU2 permanece acessível nas Configurações. God Mode continua restrito à sessão Treinamento offline; trocar de modo ou selecionar Online desativa o estado ativo. Nenhuma função de vantagem online ou edição de carteira foi adicionada.
- Estado de comprovação: código enviado somente à branch feature/arena-modes-core; build CI, APK e teste físico ainda pendentes. Não mesclado na main.
