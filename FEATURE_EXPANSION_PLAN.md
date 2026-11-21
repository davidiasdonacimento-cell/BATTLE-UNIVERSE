# BATTLE UNIVERSE — Plano complementar de funcionalidades e critérios de teste

**Estado de tudo neste documento: PLANEJADO.** Este documento registra as escolhas adicionais solicitadas em 2026-11-21. Não afirma que qualquer item foi implementado, compilado ou testado. Não substitui o Plano Mestre nem os requisitos permanentes dos itens 1 a 11 em `PROJECT_STATUS.md`.

## 1. Regras de preservação

- Preservar integralmente o Plano Mestre, os requisitos dos itens 1 a 11, os modos existentes, os oito mapas planejados, limite de 30 participantes offline (jogador + 29 bots), identidade THE VISION, Season 02, conta, sincronização, conteúdo, administração, MODMENU2 e assinatura estável do APK.
- Continuar usando Android nativo Java + C++ + OpenGL ES 3.0 + JNI. Não substituir por protótipo WebView, Unity ou Godot.
- Não remover, renumerar ou quebrar IDs nativos existentes sem migração versionada e testes.
- Não declarar online funcional sem serviço de partidas autoritativo, matchmaking, deploy e testes com pelo menos dois clientes reais.
- Separar claramente PLANEJADO, EM DESENVOLVIMENTO, IMPLEMENTADO, COMPILADO e TESTADO. Cada item só avança com evidência.
- Esta expansão não autoriza iniciar implementação ainda. Primeiro concluir auditoria de arquitetura, dependências, conflitos e critérios de aceite.

## 2. Economia e progressão competitiva

### Carteira única de diamantes
- Existe **um único tipo de diamante e um único saldo**, sem carteiras separadas para offline e online.
- Diamantes recebidos em fluxos offline e online devem convergir para a mesma carteira da conta, sem duplicação e sem perda de saldo.
- Quando offline, transações locais devem ser registradas em fila idempotente e reconciliadas com o servidor quando houver conexão; conflitos, limites e prevenção de fraude precisam ser definidos antes de liberar o fluxo.
- O servidor é a fonte de verdade para saldo online, compras e recompensas competitivas. O cliente não pode inventar resultados ou alterar saldo.
- Moedas comuns/ouro foram removidos completamente; a única moeda é diamante.
- Preservar saldos legados por migração única auditável, sem conceder duas vezes valores já migrados.

### Patentes, pontos e temporadas
- Patentes competitivas com divisões e progressão por pontos.
- Pontos ganhos ou perdidos devem aparecer após cada partida, com explicação dos fatores que compõem o resultado.
- Exibir patente/divisão anterior, pontos anteriores, pontos ganhos/perdidos, saldo final, progresso até a próxima divisão e proteção/limites aplicáveis.
- Considerar colocação, vitória, eliminações, dano válido, assistência, objetivos, sobrevivência e desempenho da equipe, com pesos e limites configuráveis por temporada.
- Prever partidas de colocação, proteção contra abuso, penalidades por abandono/AFK e regras de desempate.
- Temporadas ranqueadas com calendário, reinício/rebaixamento documentado, recompensas e histórico de temporadas.
- Ranking mundial e filtros por região, temporada, patente e clã; não exibir bots como jogadores humanos.
- Sistema de MVP com critérios transparentes e desempate; MVP não deve depender apenas de eliminações.

### Recompensas por desempenho
- Recompensas separadas e detalhadas para dano válido, eliminações, assistências, colocação, vitória, objetivos e participação.
- Mostrar a origem de cada XP e diamante no resumo da partida e no histórico de recompensas.
- Definir limites por partida, validação de dano, proteção contra auto-dano/farming, alvos repetidos, partidas inválidas e duplicação de resultados.
- Partidas offline contra bots podem ter recompensas locais sujeitas a limites e reconciliação; não podem gerar pontos de ranking online sem validação autoritativa.
- Recompensas de partidas online só são concedidas após validação do servidor de partidas.

## 3. Resumo completo ao terminar cada partida

Ao encerrar a partida, abrir uma tela de resultados completa, legível em celular, com:

- Colocação final, modo, mapa, duração, resultado da equipe e ID/hora da partida.
- **Todos os participantes apresentados lado a lado por equipe**, com cartões/colunas comparáveis. Incluir o jogador, aliados, adversários e bots/IA; identificar claramente bots como IA e nunca como pessoas reais.
- Avatar/modelo, nickname ou nome de bot, equipe, patente (quando aplicável) e indicação de MVP.
- Estatísticas individuais: eliminações, assistências, dano causado, dano recebido, precisão, tiros/acertos quando disponíveis, distância percorrida, tempo de sobrevivência, recursos coletados, revives e objetivos concluídos.
- Estatísticas da equipe e comparação entre equipes.
- Lista de eventos relevantes da partida, quando houver dados confiáveis.
- Destaque visual do MVP e explicação dos critérios que determinaram o prêmio.
- Recompensas detalhadas: XP, diamantes (saldo único), itens, missões concluídas e progresso do passe.
- Painel de patente: pontos ganhos/perdidos e cálculo discriminado, patente/divisão anterior e atual, barra de progresso e pontos restantes para subir.
- Ações: continuar, jogar novamente, convidar equipe, ver perfil e abrir histórico, conforme o modo e os serviços disponíveis.
- Se algum dado não existir, mostrar indisponível ou omitir; nunca inventar estatísticas.
- Resultados precisam ser persistidos uma única vez, com ID idempotente, e sincronizados sem duplicar recompensas.

## 4. Gameplay, equipamentos e combate

- Veículos dirigíveis com entrada/saída, controles mobile, física, colisões, dano, combustível/limites se aplicáveis, assentos e regras por mapa.
- Veículos distribuídos pelo mapa como elementos reais de gameplay; não confundir com simples decoração.
- Mochila com capacidade limitada, peso/slots definidos, categorias, empilhamento, descarte, troca, coleta automática configurável e interface de gerenciamento.
- Paraquedas avançado: salto, direção, velocidade, abertura/fechamento conforme regras, pouso, colisão e sincronização em partidas online.
- Airdrops e caixas de suprimentos com pontos/tempos de surgimento, sinalização, loot, disputa, abertura, limites e validação no servidor.
- Acessórios de armas: compatibilidade por arma, slots, atributos, inventário, coleta, equipar/remover e balanceamento.
- Granadas e equipamentos táticos com tipos, trajetória, área de efeito, temporização, colisão, efeitos, limites e feedback; balancear e testar sem permitir abuso.
- Reviver aliados com janela, duração, interrupção, estados, animação, regras por modo e sincronização; não aplicar a modos incompatíveis.
- Finalizações e animações de vitória/derrota, com opção de reduzir efeitos e sem conceder vantagem injusta.
- Estatísticas por arma e balanceamento contínuo; registrar apenas métricas efetivamente medidas pela engine/servidor.

## 5. Personagens e personalização

- Habilidades exclusivas com descrição, cooldown, duração, limites, contrajogo e balanceamento por modo.
- Personagens lendários como conteúdo desbloqueável; raridade visual não deve significar vantagem paga desbalanceada.
- Emotes e danças utilizáveis no lobby e nos contextos permitidos da partida.
- Pets com funções estritamente visuais, salvo aprovação futura de regras competitivas equilibradas.
- Animações de entrada para lobby/partida e transições.
- Personalização de roupas por peças compatíveis, com prévia, inventário, propriedade e persistência.
- Skins evolutivas com níveis, requisitos e efeitos visuais; evolução deve ser auditável e não alterar atributos competitivos sem balanceamento explícito.
- Preservar os cosméticos, slots e coleções já planejados/registrados; não sobrescrever IDs de itens existentes.

## 6. Mapas, mundo e eventos

- Clima dinâmico com estados, transições, impacto visual/sonoro e limites de desempenho.
- Ciclo de dia e noite com iluminação, visibilidade e configurações acessíveis.
- Cidades exploráveis com ruas, pontos de interesse, navegação, colisões e loot funcional.
- Casas e edifícios com interiores realmente acessíveis, geometria, colisões, portas/entradas, iluminação e navegação.
- Áreas secretas com acesso intencional, recompensas equilibradas e indicação por pistas do jogo.
- Eventos aleatórios com regras, frequência, localização, aviso, encerramento e recompensas.
- Integrar veículos, clima, eventos, interiores e áreas secretas nos mapas compatíveis sem reduzir o limite técnico atual de 30 participantes offline até validação.
- Preservar a identidade e geografia específica dos oito mapas existentes; nenhuma área decorativa deve ser anunciada como explorável sem interação real.

## 7. Recursos sociais e comunidade

- Clãs/guildas com criação, nome, emblema, cargos, permissões, entrada/saída, convites e histórico.
- Torneios entre clãs com inscrição, calendário, chaves, regras, resultados e tabela.
- Lista de amigos com solicitações, aceitar/recusar, remover, bloquear, presença real e privacidade.
- Convites de equipe e fluxo de aceitar/recusar com expiração.
- Perfil público com estatísticas, patente, histórico selecionado, cosméticos e opções de privacidade.
- Chat de texto com moderação, bloqueio, denúncia, filtros e proteção contra spam.
- Chat por voz com permissões Android, controles de mudo, volume, equipe/canal, indicadores e moderação; não ativar microfone sem ação/permissão clara do usuário.
- Espectador de partidas com autorização, atraso/limites quando necessário e proteção contra vazamento de informação competitiva.
- Não simular presença, amigos, clãs ou participantes online antes da implantação dos serviços reais.

## 8. Conteúdo, missões e recompensas

- Passe de temporada com trilha gratuita e premium, níveis, XP, calendário, recompensas, resgates e histórico.
- Missões diárias e semanais com objetivos mensuráveis, renovação e resgate idempotente.
- Loja rotativa com catálogo, preços exclusivamente em diamantes, disponibilidade, prévia, confirmação e histórico.
- Conquistas e medalhas com critérios verificáveis, progresso e data de obtenção.
- Eventos especiais com calendário, regras, objetivos e recompensas.
- Códigos promocionais com validade, limite por conta, auditoria e validação no servidor quando online.
- Histórico de recompensas com origem, data, partida/evento, diamantes/item e estado de sincronização.
- Evitar duplicação de recompensas entre passe, missão, evento, correio, partida e carteira.

## 9. Qualidade, apresentação e acessibilidade

- Gráficos configuráveis com níveis que correspondam a opções realmente aplicadas no motor.
- FPS selecionável com limites compatíveis com aparelho, estabilidade térmica e consumo.
- HUD personalizável: posição, tamanho, transparência, predefinições, persistência e restauração.
- Replay de partidas com gravação/reprodução dos dados necessários, compatibilidade de versão, limites de armazenamento e opção de apagar.
- Modo espectador integrado ao serviço de partidas; diferenciar de replay local.
- Tutorial interativo com objetivos, prática de controles, armas, mochila, paraquedas, veículos e leitura do resumo.
- Sistema de denúncias para jogador, chat e comportamento, com motivo, evidência disponível, protocolo e análise/moderação.
- Otimização para celulares: orçamento de CPU/GPU/RAM, texturas/LOD, carregamento, armazenamento, rede, aquecimento, bateria e estabilidade.
- Configurações e acessibilidade devem continuar funcionando no Redmi 12/Android 15 e em aparelhos de diferentes capacidades; opções indisponíveis devem ser explicadas, não simuladas.

## 10. Campeonatos oficiais

- Competições organizadas por temporada, com regras publicadas, inscrições, classificatórias, chaves, partidas, resultados e histórico.
- Torneios entre clãs e competições individuais/equipe, com critérios de elegibilidade e desempate.
- Tabelas de classificação, calendário, status das partidas e histórico de campeões.
- Premiações virtuais com origem, condições, auditoria e entrega idempotente.
- Ferramentas administrativas para criar/encerrar torneios, validar resultados e moderar participantes.
- Campeonatos só podem ser chamados oficiais quando houver serviço online, regras, administração e resultados auditáveis funcionando.

## 11. Plano de teste futuro do Kensho

Quando existir uma build candidata estável, preparar roteiro e versão identificada para teste. O Kensho deverá verificar:

1. Instalação, atualização sobre versão anterior, login e restauração de conta.
2. Lobby, personagens, roupas, habilidades, pets, emotes e animações.
3. Mochila, acessórios, armas, granadas, veículos, paraquedas, airdrops e reviver.
4. Mapas, cidades, interiores, áreas secretas, clima, dia/noite e eventos.
5. Bots identificados como IA, comportamento, combate e limite de 30 participantes offline.
6. Tela de resultados com equipes lado a lado, todas as estatísticas disponíveis, MVP e justificativa.
7. Recompensas, carteira única de diamantes, histórico, missões e passe.
8. Patentes, divisões, pontos ganhos/perdidos e explicação para subir de patente.
9. Ranking, temporadas, clãs, amigos, convites, chat, voz, espectador e torneios, somente se os serviços reais estiverem ativos.
10. Gráficos, FPS, HUD, replay, tutorial, denúncias e acessibilidade.
11. Desempenho: FPS medido, travamentos, aquecimento, bateria, RAM, armazenamento, rede fraca e reconexão.
12. Testes de regressão: saves, atualização do APK, conteúdo Season 02, assinatura, persistência e ausência de duplicação.

O relatório de teste deve separar defeitos confirmados, sugestões, itens não testáveis e recursos ainda não implementados. Gravação de vídeo deve mostrar a versão/build usada e não apresentar recursos planejados como funcionais.

## 12. Dependências e ordem de execução futura

1. Auditar código, banco, IDs, JNI, dados existentes e conflitos com o Plano Mestre.
2. Fechar contratos de partida, estatísticas, inventário, economia, ranking, temporada e resultados.
3. Construir e validar servidor autoritativo de partidas antes de habilitar recursos competitivos online.
4. Consolidar carteira única de diamantes e ledger idempotente com migração preservando saldos.
5. Construir inventário/mochila e integração de equipamentos com engine.
6. Integrar veículos, paraquedas, airdrops, granadas e reviver ao motor e aos modos compatíveis.
7. Construir sistema de pontuação ranqueada, temporadas, MVP e resumo de partida.
8. Integrar personagens, habilidades, cosméticos e animações.
9. Expandir mapas, interiores, clima, dia/noite e eventos.
10. Construir serviços sociais, clãs, chat, voz e espectador com segurança/moderação.
11. Integrar passe, missões, loja, conquistas, eventos e campeonatos.
12. Otimizar, testar regressões, compilar, instalar e validar no Redmi 12 e em outros aparelhos.
13. Só então preparar build candidata e roteiro de avaliação do Kensho.

## 13. Critérios de aceite obrigatórios

- Cada funcionalidade precisa ter implementação real, integração, testes automatizados, build e teste em aparelho compatível.
- Recursos online precisam de servidor ativo, autenticação, autorização, moderação/anti-abuso e teste com clientes reais.
- Recompensas e ranking devem ser reproduzíveis a partir de eventos/resultados validados; não confiar em números enviados pelo cliente.
- O resumo de partida deve ser consistente com os dados armazenados e não duplicar resultados/recompensas.
- A carteira única de diamantes é autoritativa na API; o cache do APK não concede nem altera saldo. Não existe carteira de ouro/moedas.
- O jogo deve manter compatibilidade com os requisitos permanentes e não regredir funcionalidades já existentes.
- Bugs conhecidos, limitações e testes pendentes devem ser registrados; nunca prometer ausência total de bugs.
