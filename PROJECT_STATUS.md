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

## Conta BATTLE UNIVERSE com nome e senha — implementação publicada
- Perfil possui fluxo de criar conta e entrar usando nome de usuário (3–18 caracteres: a-z, 0-9 e _) e senha (8–72 caracteres); não exige login Google.
- Firebase Authentication é usado como backend de identidade com um identificador interno derivado do nome de usuário; a senha não é gravada em SharedPreferences nem enviada ao Firestore.
- Firebase UID identifica o documento privado em Firestore: players/{uid}. O arquivo firestore.rules restringe leitura e gravação ao próprio UID autenticado.
- O salvamento em nuvem inclui nickname, XP, diamantes virtuais, modo/mapa/personagem, configurações, roupas/equipamentos, eventos, correio e conquistas selecionadas.
- A sessão Firebase é restaurada pelo SDK; o progresso local continua como cache offline.
- Limitação importante: o identificador interno não é um e-mail real. Não existe recuperação de senha por e-mail neste fluxo. O jogador precisa guardar a senha. O Firebase/Firestore precisa estar configurado no projeto e as regras firestore.rules precisam estar publicadas no console Firebase.
- A conta só recupera dados de APKs que usem o mesmo projeto Firebase, o mesmo applicationId e o mesmo esquema de dados. Build compilado não comprova sincronização real; testar criação, login, gravação, logout e restauração em instalação separada.
 
## Mundo 3D — expansão de marcos por mapa (código publicado, build pendente)
- A engine OpenGL ES recebeu conjuntos geométricos próprios para os oito destinos: marina e cabanas costeiras (Ilha Tropical); skyline, fachadas luminosas e via elevada (Neon City); mesas, ravinas e arcos de pedra (Desert Storm); espigões de gelo e estação polar (Frozen Front); tanques, tubulações e pórticos (Industrial Zone); rio, pontes e colinas (Green Valley); plataformas de lançamento e hangares (Orbital Base); muros colapsados, torres partidas e campos de destroços (Abandoned Sector).
- Os marcos são desenhados com primitivas 3D nativas da engine; não são imagens planas nem apenas troca de paleta.
- Limitação desta alteração: a base territorial, parte das estradas e a distribuição principal de edifícios ainda são compartilhadas entre os oito mapas. Os novos marcos são principalmente visuais e ainda não têm colisão/interação própria, loot exclusivo, interiores exclusivos ou navegação específica. Portanto, os oito mapas ainda não devem ser descritos como oito mundos totalmente independentes.
- A compilação do commit desta expansão e o teste visual no Redmi 12 ainda precisam ser confirmados.

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
