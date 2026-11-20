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
- Firebase está ligado a autenticação/perfil; não é servidor multiplayer em tempo real.

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
