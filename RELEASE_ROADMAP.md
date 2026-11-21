# BATTLE UNIVERSE — Cronograma integrado para lançamento completo

**Decisão do responsável pelo projeto (2026-11-21):** o objetivo é lançar o BATTLE UNIVERSE com todo o escopo obrigatório pronto na primeira versão pública. Não haverá lançamento público reduzido com funcionalidades prometidas para depois. As fases abaixo são marcos internos de construção, integração e validação; não são lançamentos públicos separados.

**Estado deste cronograma: PLANEJADO.** Ele organiza a execução futura e não comprova implementação, compilação, deploy ou teste. Nenhuma implementação é autorizada apenas por este documento.

## 1. Documentos de referência e precedência

1. `PROJECT_STATUS.md`: requisitos permanentes e registro do estado real dos itens 1 a 11. Deve continuar preservado.
2. `MASTER_GAME_COMPLETION_PLAN.md`: arquitetura, regras de engenharia, critérios de conclusão e histórico técnico.
3. `FEATURE_EXPANSION_PLAN.md`: todas as funcionalidades adicionais escolhidas e critérios específicos, incluindo o resumo de partida com participantes/IA lado a lado.
4. Este documento: ordem de dependências, marcos internos e porta única de lançamento.

Se uma descrição antiga divergir da decisão mais recente, aplicar estas decisões sem apagar o histórico:
- Diamantes: única moeda do jogo e único saldo autoritativo no PostgreSQL. Ouro/moedas foram removidos completamente. O APK mantém somente cache de leitura; nenhuma operação local pode alterar a carteira online.
- Lançamento: todas as funcionalidades obrigatórias planejadas para a primeira versão pública devem estar implementadas, integradas e aprovadas antes da publicação. Builds internas de desenvolvimento e teste são permitidas e não contam como lançamentos públicos.
- Bots/IA: devem ser identificados como IA no lobby, placares e resultados; nunca apresentados como pessoas reais.
- Online: só é considerado funcional após servidor autoritativo implantado e validado com clientes reais.

## 2. Regras que valem para todas as fases

- Preservar os itens 1–11 e tudo que já está no Plano Mestre e no registro do projeto: jogo Android nativo Java + C++ + OpenGL ES 3.0 + JNI; lobby 3D; modos; limite de 30 participantes offline (1 jogador + 29 bots); oito mapas; conta; sincronização; THE VISION; Season 02; armazenamento e persistência; administração; MODMENU2 restrito ao Treinamento offline; identidade e assinatura estável do APK.
- Não substituir o jogo por HTML/WebView, protótipo, Unity ou Godot.
- Não remover recursos, IDs, dados, saves, assets ou compatibilidade existentes sem auditoria, migração versionada, backup e testes de regressão.
- Não criar telas ou botões decorativos para simular sistemas inexistentes.
- O HUD apresenta dados; regras de combate, inventário, física, dano e resultados pertencem ao motor/simulador ou servidor responsável.
- O cliente não é autoridade para partidas online, ranking, compras, saldo ou recompensas competitivas.
- Todas as transações, partidas, resgates e operações críticas precisam de IDs idempotentes e ledger/auditoria.
- Cada item usa estados separados: PLANEJADO, EM DESENVOLVIMENTO, IMPLEMENTADO, COMPILADO, TESTADO. Código publicado não significa build concluída; build concluída não significa teste no aparelho.
- Não estimar datas de calendário antes da auditoria de código, volume de assets, dependências e capacidade real. Ao final da Fase 0, produzir estimativa por tarefa e riscos, sem prometer data artificial.

## 3. Cronograma por fases internas

### Fase 0 — Auditoria, congelamento de escopo e arquitetura
**Objetivo:** descobrir exatamente o que existe, o que está incompleto e o que precisa ser construído antes de modificar qualquer sistema.

Tarefas:
- Ler integralmente os itens 1–11 e todos os documentos de planejamento.
- Inventariar módulos Java, C++, JNI, IDs nativos, modos, telas, banco, API, autenticação, saves, assets, pacotes e workflows.
- Mapear todas as fontes de dados de perfil, carteira, inventário, XP, partidas, estatísticas e recompensas.
- Mapear chamadas e dependências entre engine, HUD, API própria FastAPI, PostgreSQL e instalador de conteúdo.
- Identificar funções duplicadas, telas sem integração, recursos falsamente apresentados como online e lacunas de testes.
- Criar matriz requisito → arquivo/módulo → dependência → teste → evidência.
- Fazer backup lógico e registrar hashes/versões dos dados e artefatos críticos.
- Definir contratos versionados para Player, Inventory, Wallet, Match, MatchEvent, MatchResult, RankSeason, Clan, Tournament, ContentManifest e Settings.
- Identificar conflitos entre o escopo novo e código antigo sem apagar requisitos.

**Porta de saída:** inventário técnico revisado; matriz de rastreabilidade completa; riscos e dependências documentados; plano de migração aprovado. Nenhuma feature nova começa antes desta porta.

### Fase 1 — Fundação de dados, persistência e economia
**Objetivo:** garantir que os sistemas posteriores usem os mesmos contratos e não percam dados.

Tarefas:
- Consolidar perfil, identidade, inventário, estatísticas, configurações, progresso e histórico.
- Implementar exclusivamente uma carteira de diamantes, sem ouro/moedas e sem divisão offline/online.
- Migrar saldos legados uma única vez, preservar recibos/ledger e impedir concessões duplicadas.
- Criar ledger imutável/idempotente e fila de transações offline com reconciliação segura.
- Definir limites e validação de recompensas offline; recompensas competitivas online dependem de resultado validado pelo servidor.
- Integrar conta, recuperação, sincronização, conflito entre dispositivos, funcionamento sem rede e restauração.
- Definir armazenamento persistente do progresso e dos arquivos de conteúdo, com estratégia segura de backup/reinstalação; segredos e tokens não podem ir para pastas públicas.
- Validar compras locais, inventário, códigos, Royale, missões e resgates com operações atômicas.
- Garantir que o MODMENU2 continue limitado ao Treinamento offline e nunca possa editar a carteira ou ranking.

**Porta de saída:** testes de migração, saldo único, idempotência, concorrência, reconciliação offline/online, restauração e regressão aprovados; build Android/servidor correspondente.

### Fase 2 — Núcleo jogável offline e combate
**Objetivo:** completar a experiência de partida local antes de depender de multiplayer.

Tarefas:
- Completar ciclo de partida, BR offline, Dupla/Esquadrão conforme regras planejadas, Treinamento, Contra Squad e Gladiadores.
- Preservar 30 participantes offline (jogador + 29 bots) até validação de desempenho.
- Integrar IA/bots com estados, navegação, combate, loot e identificação visual clara.
- Completar movimentação, câmera, colisões, dano, cura, armas, munição, recarga, troca e estatísticas.
- Mochila: capacidade, slots/peso, categorias, empilhamento, descarte, coleta, troca e persistência na partida.
- Acessórios de armas por slots/compatibilidade e efeitos balanceados.
- Granadas e equipamentos táticos com regras de colisão, tempo, área e feedback.
- Paraquedas com salto, controle, abertura, queda e pouso.
- Reviver aliados nos modos compatíveis, com estados, interrupções e regras.
- Veículos dirigíveis: entrada/saída, assentos, controles mobile, física, colisão, dano e spawn por mapa.
- Airdrops/caixas: surgimento, aviso, loot, abertura, disputa e limites.
- Registrar eventos de jogo de forma estruturada para alimentar estatísticas e tela de resultados.

**Porta de saída:** testes funcionais de cada modo e subsistema; partidas completas sem bloqueadores; regressão de bots, dano, loot, inventário e saves; build e teste em aparelho.

### Fase 3 — Mapas e mundo explorável
**Objetivo:** fazer os oito mapas planejados serem ambientes reais, distintos e jogáveis.

Tarefas:
- Preservar nomes, identidade e geografia dos oito mapas existentes.
- Completar terreno, colisões, navegação, pontos de interesse, estradas, vegetação, água/travessias e pontos de spawn.
- Construir cidades exploráveis, casas e interiores acessíveis com colisões e loot.
- Criar áreas secretas com acessos e recompensas equilibrados.
- Integrar veículos, airdrops e equipamentos aos mapas compatíveis.
- Implementar clima dinâmico, transições, efeitos e impacto controlado na visibilidade.
- Implementar ciclo dia/noite com iluminação e desempenho adequados.
- Implementar eventos aleatórios com regras, avisos, locais, duração e recompensas.
- Produzir assets originais com formatos, escalas, materiais, UV, rig quando necessário, LOD, compressão, colisões e licenças verificadas.
- Preservar Season 02, manifesto, hashes, instalação atômica, rollback e cópia persistente no armazenamento compartilhado conforme requisitos existentes.

**Porta de saída:** cada mapa validado em navegação, interiores, colisões, loot, desempenho e conteúdo; sem áreas anunciadas como jogáveis que sejam apenas decoração.

### Fase 4 — Personagens, cosméticos e apresentação
**Objetivo:** integrar todos os elementos visuais e animações ao mesmo inventário e personagem.

Tarefas:
- Completar personagens humanos, rigs, animações e compatibilidade de peças.
- Preservar as skins e slots já existentes, incluindo barba, capacete, mochila, colete e coleções.
- Adicionar personagens lendários, habilidades exclusivas, roupas, skins evolutivas, emotes, danças, pets visuais e animações de entrada.
- Criar prévias reais no lobby e oficina, com os mesmos IDs/peças usados na partida.
- Integrar animações de vitória, derrota e finalização com opções para reduzir efeitos.
- Garantir que raridade/cosméticos não causem vantagem competitiva indevida.
- Validar clipping, encaixes, LOD, materiais, animação, memória e performance no aparelho de referência.

**Porta de saída:** cosméticos equipados persistem e aparecem corretamente no lobby e na partida; animações não quebram estados de movimento/combate; testes de desempenho aprovados.

### Fase 5 — Progressão, economia de jogo e resultados
**Objetivo:** fechar o ciclo de recompensa, evolução e transparência após cada partida.

Tarefas:
- XP, níveis, missões diárias/semanais, conquistas, medalhas, passe de temporada, eventos especiais, loja rotativa, códigos e histórico de recompensas.
- Patentes com divisões, pontos, partidas de colocação, regras de ganho/perda, desempate, abandono/AFK e proteção contra abuso.
- Temporadas ranqueadas com calendário, regras de reinício, recompensas e histórico.
- Recompensas por dano válido, eliminações, assistências, colocação, vitória, sobrevivência e objetivos, com limites e anti-farming.
- MVP com critérios transparentes, cálculo reproduzível e desempate; não apenas por eliminações.
- Resumo de partida completo e responsivo:
  - todos os participantes lado a lado, separados por equipe;
  - jogador, aliados, adversários e IAs identificados corretamente;
  - avatar, nickname/nome da IA, equipe, patente e MVP;
  - estatísticas individuais e de equipe: eliminações, assistências, dano causado/recebido, precisão, tiros/acertos disponíveis, distância, sobrevivência, recursos, revives e objetivos;
  - comparação entre equipes;
  - MVP com justificativa;
  - recompensas por origem;
  - patente anterior/atual, pontos ganhos/perdidos, cálculo discriminado e progresso até a próxima divisão;
  - ações para continuar, repetir, convidar, abrir perfil e histórico quando disponíveis.
- Estatísticas indisponíveis não podem ser inventadas. O resultado precisa ter Match ID e ser gravado uma única vez.
- Partidas offline podem gerar progresso local limitado; ranking e recompensas competitivas online só podem vir de partidas validadas pelo servidor.

**Porta de saída:** consistência entre eventos, estatísticas, MVP, pontos, recompensas e histórico; testes de idempotência e saldo; nenhuma duplicação após reinício ou sincronização.

### Fase 6 — Serviços online autoritativos e comunidade
**Objetivo:** construir a infraestrutura real necessária para todas as funcionalidades online antes de anunciá-las como ativas.

Tarefas:
- Serviço separado de partidas em tempo real: autenticação, salas, matchmaking, regiões, latência, estado replicado, sequência de eventos, validação de comandos e servidor autoritativo.
- Sincronizar jogadores, equipes, aeronave, salto, movimento, armas, dano, inventário, loot, zona, eventos, desconexões e resultados.
- Reconexão, abandono, encerramento determinístico, limites e proteção contra replay, spam, manipulação e duplicação.
- Validar partidas com dois ou mais clientes reais; bots não contam como clientes humanos.
- Ranking mundial/regional, perfis públicos e estatísticas competitivas.
- Amigos, solicitações, bloqueios, convites, presença e equipes.
- Clãs/guildas, cargos, permissões, emblemas, níveis e histórico.
- Chat de texto com moderação, bloqueio, denúncia, filtros e antispam.
- Chat por voz com permissão explícita, mute, volume, canais, indicadores e moderação.
- Espectador ao vivo com autorização e proteção contra vazamento de informação; manter replay como sistema separado.
- Sistema de denúncias, análise/moderação e auditoria.
- Backups, observabilidade, limites de API, privacidade, segurança de banco e recuperação de falhas.
- Manter recursos online bloqueados até passar todos os testes de integração e operação.

**Porta de saída:** serviço implantado e monitorado; teste multi-cliente real; falhas de rede/reconexão e anti-abuso aprovados; social e moderação testados; ranking e resultados derivados apenas do servidor.

### Fase 7 — Campeonatos, torneios e conteúdo sazonal
**Objetivo:** ativar os sistemas de competição e retenção que dependem da base online completa.

Tarefas:
- Campeonatos oficiais com regras, elegibilidade, inscrições, classificatórias, chaves, calendário, resultados e histórico de campeões.
- Torneios entre clãs e competições individuais/equipe com desempates.
- Premiações virtuais auditáveis e entregues uma única vez.
- Ferramentas administrativas para criar, moderar, encerrar e auditar eventos.
- Passe, loja rotativa, eventos, missões e recompensas sincronizados com calendário e servidor.
- Ranking de clãs, tabelas de classificação e histórico de temporadas.
- Garantir que nenhum campeonato seja chamado oficial antes de operação real e resultados auditáveis.

**Porta de saída:** torneio completo simulado e validado em ambiente de teste; operação administrativa, resultados, auditoria e prêmios idempotentes; teste de carga e segurança.

### Fase 8 — Configurações, acessibilidade, otimização e confiabilidade
**Objetivo:** garantir que todo o escopo funcione em aparelhos suportados e que opções sejam reais.

Tarefas:
- Gráficos baixo/médio/alto, escala/resolução, sombras e efeitos realmente aplicados.
- FPS selecionável com limites estáveis e térmicos adequados.
- HUD personalizável com posição, tamanho, transparência, predefinições e persistência.
- Sensibilidades individuais, controles, câmera, áudio/mixer, coleta automática, idioma, acessibilidade, vibração, notificações e privacidade.
- Tutorial interativo para controles, armas, mochila, paraquedas, veículos, modos e resultados.
- Replay com compatibilidade de versão, limites de armazenamento e gestão de arquivos.
- Modo espectador separado do replay.
- Otimização CPU/GPU/RAM, texturas, LOD, carregamento, armazenamento, rede, bateria, temperatura e estabilidade.
- Testes de toque/multitouch, diferentes telas, permissões, áudio, rede fraca, offline, atualização, armazenamento e recuperação.
- Segurança: dados mínimos, proteção de conta, logs sem segredos, backups, rate limiting, moderação e política de privacidade.

**Porta de saída:** perfil de desempenho medido e aprovado no Redmi 12/Android 15 e em aparelhos adicionais; sem crash/blocker; opções verificadas no subsistema real; saves e atualizações sem regressão.

### Fase 9 — Integração total e campanha de testes
**Objetivo:** testar o jogo como um produto único, não apenas módulos isolados.

Tarefas:
- Testes unitários, integração, contrato, migração, UI, rede, segurança, carga, concorrência e regressão.
- Build Android debug e release reproduzíveis, bibliotecas corretas, assinatura estável e verificação do APK.
- Testar conteúdo Season 02, hashes, integridade, extração, persistência, reinstalação e rollback.
- Testar fluxo completo: instalar → criar/entrar na conta → lobby → selecionar modo → jogar → resultado → recompensa → patente → histórico → fechar → abrir → restaurar.
- Testar offline/online, sincronização entre instalações, saldo único, fila de transações e ausência de duplicação/perda.
- Testar BR, Dupla, Esquadrão, Treinamento, Contra Squad, Gladiadores e todos os modos planejados.
- Testar online com pelo menos dois dispositivos/instâncias reais e serviços implantados.
- Medir FPS, RAM, aquecimento, bateria, armazenamento, carregamento e estabilidade.
- Corrigir bugs bloqueadores e regressões; manter lista de problemas conhecidos e riscos.

**Porta de saída:** todos os critérios obrigatórios aprovados; builds identificadas; relatórios e evidências anexados; nenhum requisito obrigatório marcado como concluído sem prova.

### Fase 10 — Candidato final, Kensho e lançamento único
**Objetivo:** entregar uma versão pública completa e verificável.

Tarefas:
- Criar build candidata com número/versão/commit identificados.
- Preparar roteiro do Kensho cobrindo gameplay, progressão, mapas, sistemas online, tela de resultados, estabilidade e instalação/atualização.
- O Kensho registra defeitos, sugestões, itens não testáveis e evidências; vídeo deve mostrar a versão/build real e não anunciar funcionalidades futuras como prontas.
- Corrigir problemas encontrados e repetir testes afetados.
- Revisar privacidade, permissões, segurança, distribuição, assinatura e recuperação.
- Gerar APK/AAB final conforme canal de distribuição, verificar assinatura e hash, testar atualização in-place e instalação limpa.
- Publicar a primeira versão pública somente depois de todas as portas anteriores aprovadas.
- Publicar notas de versão honestas, requisitos de aparelho, serviços online ativos e limitações conhecidas.

**Porta final:** aceite técnico, teste físico, avaliação do Kensho, assinatura e pacote final verificados; todas as funcionalidades obrigatórias do escopo do primeiro lançamento estão presentes e funcionais.

## 4. Dependências críticas entre sistemas

- Inventário/contrato de itens → mochila → loot/airdrops → acessórios → loja e cosméticos.
- Motor de combate/eventos → estatísticas → MVP/resultados → XP/recompensas → patentes/temporadas.
- Servidor autoritativo → partidas online → resultados confiáveis → ranking → torneios oficiais.
- Carteira única/ledger → compras/resgates → recompensas → passe/eventos → histórico e sincronização.
- Personagem/rig/animações → habilidades/cosméticos → lobby/partida → resultados visuais.
- Mapas/colisões/navegação → veículos/paraquedas/airdrops → eventos dinâmicos.
- Identidade/conta/moderação → amigos/clãs/chat/voz/denúncias/espectador.
- Configurações reais + perfil de desempenho → otimização → validação em aparelhos.

Não implementar um sistema dependente antes de estabilizar seu contrato e sistema-base.

## 5. Matriz de preservação obrigatória

A auditoria da Fase 0 deve confirmar explicitamente todos estes grupos antes de fechar o escopo:
- Requisitos permanentes dos itens 1–11 e todos os pedidos do histórico relevante.
- Android nativo Java/C++/OpenGL ES 3.0/JNI; lobby e navegação 3D.
- Modos atuais e planejados, IDs 0–3 preservados, BR offline com 30 participantes.
- Oito mapas e suas identidades próprias.
- Conta, perfil, API, administração e sincronização.
- THE VISION, Season 02, pacote externo/persistência e integridade de conteúdo.
- Assinatura de release estável para atualização in-place; não trocar chave.
- MODMENU2 com God Mode apenas no Treinamento offline.
- Uma única carteira de diamantes; ouro/moedas comuns não são permitidos.
- Todos os recursos selecionados em FEATURE_EXPANSION_PLAN.md.
- Tela de fim de partida com IAs e equipes lado a lado, estatísticas e MVP.
- Plano de teste e avaliação do Kensho.
- Segurança, privacidade, anti-abuso, acessibilidade, otimização e documentação.

## 6. Definition of Done — definição de concluído

Uma funcionalidade só pode receber estado TESTADO quando:
1. Está implementada no módulo correto, não apenas desenhada na tela.
2. Está integrada aos sistemas dependentes e usa contratos versionados.
3. Tem testes automatizados relevantes aprovados.
4. Passou pela build Android/servidor correspondente.
5. Foi exercitada no aparelho/dispositivo/ambiente real apropriado.
6. Não quebra dados antigos, saves, assinatura, conteúdo ou funcionalidades anteriores.
7. Possui evidência: commit, build/run, logs/testes e relatório de validação.
8. Documentos de status foram atualizados com limites e problemas restantes.

## 7. Condição de lançamento

A decisão “tudo pronto no primeiro lançamento” significa:
- As fases são internas e sequenciais, mas não serão versões públicas incompletas.
- Recursos online, campeonatos, chat, voz, ranking e social fazem parte do escopo final e precisam estar realmente operacionais antes da publicação.
- Se um requisito obrigatório falhar, o lançamento público é adiado; não se finge que está pronto nem se remove silenciosamente do escopo.
- Podem existir builds internas para desenvolvimento e para Kensho testar, mas elas devem ser identificadas como candidatas de teste.
- Não prometer “zero bugs”; exigir testes rigorosos, corrigir bloqueadores e documentar riscos residuais.

## 8. Próxima ação autorizada após organização

Quando o responsável autorizar iniciar a execução, começar pela Fase 0: ler por completo os documentos e o estado atual do repositório, gerar a matriz de rastreabilidade, identificar contradições e apresentar relatório de auditoria antes de editar código. Até essa autorização, manter este cronograma como planejamento.


## 9. Requisitos específicos recuperados na conferência dos documentos-base

Esta seção torna explícitos requisitos que já estavam no estado do projeto e que precisam continuar cobertos pelo cronograma. Não substitui os documentos de origem.

### Lobby, telas e fluxo da partida
- Manter lobby de battle royale mobile com personagem humano central em cenário 3D, câmera dinâmica, menus laterais e botão JOGAR; não transformar a experiência em site.
- Preservar as áreas: personagens, cofre/inventário, loja, Royale, passe, missões, eventos, perfil, amigos/equipe, configurações, modos, coleção/equipamentos, correio, conquistas/classificação, notícias, calendário e resgate de códigos.
- O fluxo BR inclui sala de espera, contagem, aeronave caminhável, mapa tático, salto, queda livre, paraquedas, combate, resultado e recompensas.
- A aeronave já registrada no estado do projeto possui corredor, rampa, movimentação, embarque de 7 segundos e saída de segurança aos 34 segundos. A auditoria deve confirmar o comportamento atual e preservar esses tempos/regras, salvo decisão expressa posterior.
- Fogo amigo permanece desligado; dificuldade dos bots permanece NORMAL como requisito atual.
- Em partidas online, aguardar 30 segundos por participantes humanos e só então completar vagas com bots quando o serviço real de partidas estiver implantado. Nunca contar bots como jogadores humanos.

### Modos e regras específicas
- Preservar solo, dupla, esquadrão e treinamento, além dos modos adicionais já definidos.
- Gladiadores: duelo individual; primeiro a alcançar 5 eliminações vence; seleção de equipamento antes do duelo; placar e reinício de partida próprios.
- Contra Squad: duas equipes; vence quem alcançar 4 rounds; round termina quando uma equipe é eliminada; fase de compra entre rounds; pistola e saldo inicial; compras e inventário válidos durante a partida.
- Não alterar IDs JNI/nativos existentes (incluindo 0–3) sem migração versionada e testes.

### Arsenal e Royale
- O estado atual de `PROJECT_STATUS.md` define o arsenal permitido como exatamente quatro armas: Desert Eagle (Desert), Mini Uzi, XM8 e UMP. Não reintroduzir armas removidas nem mudar índices sem auditoria e migração.
- O Royale existente tem custo de 100 diamantes, probabilidades 40/30/20/9/1 (total de 100%), cinco cosméticos, compensação por repetição e histórico. Preservar transparência das chances, validação da soma, compensação e histórico durante a migração para a carteira única.

### Conta, autenticação e sincronização — conflito que a Fase 0 precisa resolver
- Decisão arquitetural: Firebase está descontinuado e proibido na arquitetura-alvo. Identidade, autenticação, perfis, sincronização e carteira online usarão exclusivamente a API própria FastAPI + PostgreSQL; o servidor de partidas continuará separado e autoritativo.
- Na Fase 0, verificar o código atual, os endpoints realmente implantados, os fluxos de login/sincronização e a fonte de verdade de cada dado. Localizar e remover SDKs, arquivos de configuração, regras, chamadas e dependências legadas; migrar dados necessários com compatibilidade e sem perda de contas/progresso. Não declarar remoção concluída até testes de cadastro, login, gravação, logout, restauração em outra instalação e reconciliação offline.
- A carteira PostgreSQL é registrada como fonte de verdade online para saldo; o armazenamento local é cache/fila offline. O objetivo final continua sendo uma única carteira de diamantes, com reconciliação idempotente e sem saldo duplicado.
- A API administrativa, os papéis player/admin, auditoria, exclusão lógica e configurações globais precisam ser auditados e testados; alterações de nome exibido não podem mudar applicationId, pacote ou assinatura.

### Conteúdo, identidade e distribuição
- Preservar THE VISION como experiência de primeira entrada com etapas ligadas à cópia, extração e validação reais do conteúdo.
- Preservar Season 02 como conteúdo real, versionado e validado por manifesto/hash; manter a estratégia de pacote externo e cópia persistente em Downloads/Battle Universe, confirmando o comportamento em reinstalação.
- Preservar assinatura estável do APK para atualização in-place. Nunca recriar ou substituir a chave de assinatura existente.
- Monetização futura deve ser transparente, opcional e compatível com regras da plataforma; não prometer renda.

### Itens de auditoria adicionais obrigatórios na Fase 0
- Conferir os requisitos permanentes e o histórico completo dos itens 1–11, não apenas este resumo.
- Confirmar cada tela, modo, ID, arma, regra de aeronave, bot, mapa, conta, sincronização, Royale, THE VISION, Season 02 e assinatura contra o código e os testes atuais.
- Registrar explicitamente o que existe apenas como tela, o que é lógica local, o que depende de backend e o que já foi testado em aparelho.
- Não resolver conflitos de documentos por suposição: registrar a decisão e obter confirmação quando a escolha alterar comportamento ou dados existentes.
