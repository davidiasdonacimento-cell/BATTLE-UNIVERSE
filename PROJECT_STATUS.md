# BATTLE UNIVERSE — estado e decisões confirmadas

Este é o documento de continuidade do projeto. Não criar versões artificiais por etapa. Recursos só são considerados prontos quando houver prova no código, build e teste.

## Decisões do jogador
- Android nativo em Java + C++ / OpenGL ES 3.0; sem Unity, Godot ou WebView.
- Lobby de jogo mobile Battle Royale, não aparência de site.
- Personagem humanoide 3D no lobby, skins equipáveis e cenário.
- Menu com JOGAR, PERSONAGENS, LOJA, ROYALE, INVENTÁRIO, MISSÕES, EVENTOS, PERFIL e AJUSTES.
- Escolha ONLINE ou OFFLINE.
- 30 participantes totais contando o jogador; modos solo, dupla e esquadrão.
- ONLINE planejado: esperar 30 segundos por humanos e completar vagas restantes com bots.
- OFFLINE: 1 jogador + 29 bots IA.
- IA em dificuldade NORMAL.
- Fogo amigo DESATIVADO.
- Fluxo: espera/contagem, interior de aeronave com movimento, mapa tático, salto, queda livre, paraquedas, batalha e resultado.
- Royale com diamantes virtuais, prêmios aleatórios e probabilidades visíveis; loja com cosméticos.
- Não inflar pacote com arquivos vazios/repetidos; assets precisam ser utilizáveis.

## Arquitetura real
- MainActivity cria GameSurfaceView e HudOverlay em FrameLayout.
- GameSurfaceView transmite controles e chamadas JNI.
- game_engine.cpp desenha mundo/personagens em OpenGL ES 3.0.
- HudOverlay desenha telas/HUD Android por cima da engine.
- Firebase neste repositório é para autenticação e salvamento de perfil; não é servidor multiplayer em tempo real.

## Existia antes destas alterações
- 29 bots locais: total de 30 com o jogador.
- Modos Battle Royale, dupla, esquadrão e treinamento.
- IA local simples de patrulha, perseguição e combate; não IA avançada completa.
- Aeronave 3D com corredor, rampa traseira, movimentação no interior, contagem de embarque de 7 segundos e saída forçada de segurança aos 34 segundos.
- Salto, queda e paraquedas.
- Armas locais, loot, construção, zona segura, estados derrubado/rastejar/reviver.
- Seleção de personagens, roupas/peças, loja virtual, inventário, missões, passe Royale e perfil.
- Modelo 3D humanoide estilizado construído com primitivas OpenGL; ainda não é malha realista com rig profissional.

## Alterações incluídas neste commit
- Lobby agora renderiza uma cena OpenGL 3D por trás do HUD; painéis do fundo ficaram translúcidos.
- Seletor ONLINE/OFFLINE. OFFLINE inicia o jogo local; ONLINE é identificado como pendente e não inicia partida falsa.
- Giro Royale custa 100 diamantes virtuais. Chances: 50 diamantes 40%; 100 diamantes 30%; camisa Neon 20%; botas Frost 9%; conjunto Orbital 1%. Soma = 100%. Repetidos geram compensação virtual.
- Mapa tático usa dados de posição do jogador, aeronave, rota e zona publicados pela engine; toque no mapa marca um waypoint que aparece no mundo 3D após o pouso.
- Constante FRIENDLY_FIRE_ENABLED = false e filtros explícitos contra alvos da mesma equipe.
- HUD da aeronave exibe 30 participantes (jogador + 29 bots) no modo local.

## Ainda pendente — não declarar concluído
- Servidor de partidas online com humanos, salas, matchmaking, sincronização, reconexão, validação autoritativa e anti-cheat.
- Fila online de 30 segundos e preenchimento de vagas com bots. ONLINE fica bloqueado até existir esse backend.
- Sala de espera separada em ilha antes da aeronave; hoje a contagem e o embarque ocorrem dentro do avião.
- Modelo humano realista com rig profissional, biblioteca completa de animações e customização avançada.
- Veículos completos, mascotes, eventos, comunicação e outros sistemas ainda não integrados.
- Mapas com geometria, interiores e assets exclusivos de alta fidelidade.
- Teste no Redmi 12 após esta alteração.

## Comprovação
- Código no main comprova publicação das alterações.
- GitHub Actions verde comprova compilação do commit.
- Artefato comprova que o APK foi gerado.
- Só teste físico confirma funcionamento no aparelho.
- Não declarar online, servidor ou teste físico como funcionais antes de comprovar.
