# BATTLE UNIVERSE — Plano Mestre de Construção Integrada

**Documento de execução e prevenção de retrabalho.** Este plano complementa os requisitos permanentes dos itens 1 a 11 em `PROJECT_STATUS.md`; não os substitui nem autoriza remover funcionalidades existentes.

## 1. Objetivo e regra de conclusão

Construir um jogo Android nativo completo, com lobby 3D, personagens humanos, modos offline e online, conteúdo, progressão e serviços integrados. A arquitetura deve ser definida antes de expandir cada sistema.

Nenhuma funcionalidade será considerada concluída apenas porque existe uma tela, botão, dado local ou classe. Cada entrega deve passar por:
1. implementação no sistema responsável;
2. integração com os sistemas dependentes;
3. testes automatizados e validação de dados;
4. compilação Android/servidor;
5. teste funcional em aparelho real;
6. atualização deste documento e de `PROJECT_STATUS.md` com evidências.

Estados oficiais: **planejado**, **em desenvolvimento**, **implementado**, **compilado**, **testado**. Não confundir esses estados.

## 2. Arquitetura-alvo e limites de responsabilidade

### Android
- Java: ciclo de vida, telas, navegação, conta, armazenamento local, downloads, áudio, permissões e serviços Android.
- C++/OpenGL ES 3.0: renderização 3D, cena, câmera, animações, física simplificada, colisões e simulação local.
- JNI: interface pequena, tipada e versionada entre Java e C++; não duplicar regras de jogo nas duas camadas.
- HUD: apresentar estado e enviar comandos; não deve ser a fonte de verdade de saldo, inventário, dano, recompensa ou resultado.
- Conteúdo: carregador com manifesto, versão, hashes, dependências, tamanho, espaço livre, rollback e migração.

### Serviços online (ainda precisam ser construídos)
- A API própria FastAPI + PostgreSQL é responsável por identidade, perfis e administração. O servidor autoritativo de partidas em tempo real permanece um serviço separado; o cliente nunca decide resultados online.
- Criar serviço de partidas separado: autenticação de sessão, salas, matchmaking, servidor autoritativo, estado replicado, validação de comandos, reconexão, encerramento e resultados.
- O cliente nunca decide sozinho resultados online, saldo de conta, compras, recompensas ou sorteios.
- Definir protocolo e versão antes de conectar o cliente. Mensagens devem ter identificador, versão, sessão, sequência, validação de tamanho e tratamento de repetição/atraso.
- Dados persistentes e estado temporário de partida devem ficar separados. Falha de rede não pode corromper perfil ou inventário.

### Contratos entre sistemas
- Perfil: UID estável, nickname, nível, XP, estatísticas, preferências e identificadores de cosméticos.
- Inventário: IDs estáveis de itens, quantidade, propriedade, origem, estado equipado e versão.
- Economia: carteira com tipos de moeda separados, ledger imutável de transações, idempotência e validação no servidor quando online.
- Partida: ID, modo, mapa, regras, roster, equipes, estado, eventos, resultado e recompensas.
- Configurações: valores tipados, limites, versão de esquema e aplicação real nos subsistemas.
- Conteúdo: IDs e caminhos de assets estáveis; nunca depender da ordem de arquivos em diretório.
- Todas as operações críticas precisam de identificador idempotente para evitar duplicação ao repetir requisições.

## 3. Sistemas completos que precisam existir e conversar entre si

### A. Lobby e navegação
- Cena 3D própria, iluminação, ambiente, câmera, personagem central de corpo inteiro, animações de idle e gestos, rotação/zoom controlados.
- Navegação por páginas com estados consistentes, voltar, foco, transições, carregamento, erros e confirmação de ações.
- Perfil, moedas, diamantes, nível, XP, notificações, equipe, amigos, eventos e botão Jogar conectados às fontes de dados corretas.
- Layout adaptável à resolução e proporção do Redmi 12; zonas seguras, toque e multitouch.
- Nenhum cartão de demonstração pode ser apresentado como jogador real ou recurso online.

### B. Personagens, cosméticos e animação
- Personagens humanos masculinos e femininos como modelos 3D reais, rig esquelético, pesos de skinning e biblioteca de animações.
- Partes modulares compatíveis: cabeça/cabelo, torso, pernas, calçados, acessórios, mochila e conjuntos.
- Verificação de compatibilidade entre peças, materiais, silhueta, clipping, pontos de encaixe e LODs.
- Prévia no lobby, inventário e partida usa o mesmo identificador e combinação visual.
- Animações com estados e transições: idle, caminhada, corrida, salto, queda, pouso, interação, vitória e derrota.
- Cosméticos não concedem vantagem competitiva, salvo regra de jogo explicitamente balanceada.

### C. Modos e ciclo de partida
- BR, Dupla, Esquadrão, Treinamento, BR Ranqueado, Contra Squad e Gladiadores devem ter regras próprias e testes específicos.
- BR offline: 1 jogador + 29 bots, sem fingir que bots são pessoas online.
- Dupla/Esquadrão: composição e comportamento de aliados coerentes.
- Contra Squad: duas equipes, economia por rodada, fase de compra, inventário da rodada e vitória por quatro rounds.
- Gladiadores: duelo, placar próprio e vitória no limite definido de eliminações.
- Treinamento: área e objetivos próprios, sem contaminar estatísticas competitivas.
- Sala pré-partida, contagem, aeronave, mapa tático, salto, queda, pouso, partida, resultado e recompensas são estados explícitos e recuperáveis.
- Regras de zona, loot, inventário, colisões, construção, reviver, dano fictício, bots e condições de vitória devem pertencer ao motor/simulador, não à HUD.
- Preservar IDs nativos atuais dos modos 0–3 até migração versionada e testes de compatibilidade.

### D. Multiplayer online
- Matchmaking por modo, região, latência e tamanho de equipe.
- Sala com prazo de espera de 30 segundos; bots só preenchem vagas restantes após o prazo e devem ser identificados corretamente.
- Servidor autoritativo valida movimento plausível, ações, cooldowns, inventário, resultados e recompensas.
- Sincronizar roster, equipes, aeronave, salto, posição, estados, loot, zona, eventos, resultado e desconexões.
- Reconexão com janela definida, retomada de sessão, abandono e encerramento determinístico.
- Proteção contra replay, duplicação de recompensa, manipulação de cliente e spam.
- Não liberar botão ONLINE como funcional antes de existir servidor implantado, monitorado e testado com pelo menos dois clientes reais.

### E. Economia, loja e recompensas
- Carteiras separadas para moedas e diamantes virtuais; saldo sempre derivado do ledger.
- Loja com catálogo, preço, moeda, disponibilidade, prévia, confirmação, compra, recibo e entrega ao inventário.
- Operações de compra e recompensa idempotentes; não debitar duas vezes nem entregar duas vezes.
- Códigos promocionais com validade, limite por conta, auditoria e resposta clara.
- Royale/sorteios: probabilidades exibidas, soma validada em 100%, histórico e compensação de duplicados; qualquer uso de moeda paga exige revisão de regras da plataforma e transparência.
- Recompensas diárias, missões, eventos, passe, conquistas e correio com condições verificáveis e prevenção de resgate repetido.
- Offline usa carteira local claramente identificada; sincronização online precisa resolver conflitos sem criar saldo.

### F. Perfil, social e progressão
- Conta, nickname, avatar, moldura, títulos, estatísticas, histórico, XP, nível, patentes e temporadas.
- Amigos, solicitações, bloqueio, convites, equipe, presença, chat seguro e denúncias somente quando o serviço online existir.
- Correio, notificações, missões, eventos, calendário, conquistas e passe com expiração e regras versionadas.
- Recuperação de conta, troca de credenciais, encerramento de sessão e proteção de dados.
- Não apresentar contatos salvos localmente como amigos conectados.

### G. Configurações completas
- Sensibilidade independente: geral, ponto vermelho, mira 2x, 4x, sniper e olhadinha; sliders numéricos, faixa documentada, reset e persistência.
- Controles: layout HUD, posição, tamanho, transparência, botões, corrida, mira, disparo, agachar/deitar e predefinições.
- Gráficos: qualidade, resolução/escala, sombras, efeitos, limite de FPS e modo economia; opções respeitam capacidade real do aparelho.
- Áudio: música, efeitos, interface, voz e volumes separados; alternâncias realmente conectadas ao mixer.
- Coleta automática: categorias, prioridades e limites, conectados ao sistema de loot.
- Câmera: inversão, sensibilidade, distância e assistência configurável.
- Idioma, acessibilidade, vibração, notificações, privacidade, conta e restauração padrão.
- Toda configuração deve ser aplicada no subsistema correspondente, persistida, validada e restaurada após reinício.

### H. Mundo, mapas e conteúdo
- Mapas verdadeiramente distintos em geometria, navegação, interiores, pontos de interesse, colisões, loot e identidade visual.
- Terreno contínuo caminhável, água/travessias, edifícios com interiores, estradas, vegetação, objetos interativos e pontos de spawn.
- Pipeline de assets com formatos, escalas, pivôs, materiais, compressão, LOD, colisão, licenças e revisão visual.
- Pacotes adicionais separados do APK, divididos em arquivos compatíveis com limites de distribuição; conteúdo real e utilizado, sem inflar com arquivos vazios ou duplicados.
- Manifesto assinado/verificável, hashes, versões, dependências, retomada, espaço livre, instalação atômica, rollback e limpeza.
- Nunca ativar conteúdo no jogo antes de validar versão e integridade.

### I. Áudio, feedback e UX
- Música de lobby, ambientes por mapa, passos, UI, efeitos e mixagem por categorias.
- Legendas/feedback visual para eventos sonoros importantes.
- Feedback de carregamento, sucesso, erro, ausência de rede, conteúdo ausente e operação pendente.
- Tutoriais iniciais, dicas contextuais, ajuda e onboarding sem bloquear o jogador.

### J. Segurança, privacidade e confiabilidade
- Segredos e chaves somente em configuração segura do servidor/CI; nunca embutir credenciais privadas no APK.
- Regras de banco com menor privilégio, validação de entrada, limites de requisição e logs sem senhas/tokens.
- Dados pessoais mínimos, política de retenção, exclusão/exportação conforme aplicável e consentimentos necessários.
- Verificação de compras e recompensas no servidor; cliente offline nunca pode reivindicar autoridade online.
- Tratamento de falha, migração de banco, backup, observabilidade e plano de recuperação.
- Revisar políticas da loja de aplicativos antes de pagamentos, chat, conteúdo gerado por usuário ou distribuição.

## 4. Qualidade, testes e critérios de aceite

### Testes automatizados
- Unitários para economia, XP, missões, inventário, configurações, matchmaking e regras de modos.
- Testes de contrato para JNI, protocolo de rede, esquema de perfil e manifesto de conteúdo.
- Testes de regressão para migração de saves e atualização do APK.
- Testes de integridade de assets e pacotes.
- Testes de servidor para latência, perda de pacote, desconexão, reconexão, duplicação e concorrência.
- Testes de UI para navegação, toque, orientação e acessibilidade.

### Testes reais
- Redmi 12 / Android 15: instalar, abrir, baixar conteúdo, entrar offline, jogar, pausar/retomar, mudar de tela, alterar configurações, fechar e reabrir.
- Medir FPS, uso de RAM, armazenamento, aquecimento, bateria, tempo de carregamento e estabilidade.
- Dois ou mais aparelhos/instâncias para validar partidas online; bots não substituem esse teste.
- Validar em tela pequena e grande, toque multitouch, áudio, permissões, rede fraca e ausência de rede.
- Corrigir regressões antes de acrescentar novas funcionalidades dependentes.

### Critérios de liberação
- Build Android debug e release reproduzíveis; assinatura estável para atualização in-place.
- APK e bibliotecas ARM verificadas; testes automatizados aprovados.
- Pacotes de conteúdo íntegros, versionados e baixáveis.
- Servidor implantado e monitorado para habilitar online.
- Relatório de teste físico no Redmi 12.
- Lista explícita de limitações restantes; nunca usar “100%” enquanto algum requisito obrigatório não tiver evidência.

## 5. Ordem integrada de execução (para evitar retrabalho)

1. **Congelar contratos e inventário técnico:** auditar classes, IDs, JNI, saves, assets, dependências e testes; registrar migrações.
2. **Fundação de dados e serviços:** modelos de perfil, inventário, economia, partida, eventos e versões; interfaces entre módulos.
3. **Pipeline de assets e renderização:** carregador de modelos, materiais, animações, LOD, memória e fallback.
4. **Lobby e personagem reais:** cena, câmera, operador humano, equipamentos e navegação ligados aos dados.
5. **Configurações e UX:** cada ajuste ligado ao motor, controles, áudio, HUD e persistência.
6. **Sistemas locais completos:** loja, inventário, progressão, missões, eventos, Royale, modos offline e resultados.
7. **Conteúdo e mapas:** mapas com navegação, interiores, colisões, loot e assets exclusivos.
8. **Serviços online:** conta, social, matchmaking, servidor autoritativo, salas e partidas; habilitar online somente após testes.
9. **Segurança e operação:** regras, observabilidade, backups, recuperação, privacidade e anti-abuso.
10. **Integração e validação final:** testes automatizados, build, instalação, teste no Redmi 12, correções e relatório final.

## 6. Restrições que não podem ser quebradas
- Manter Android nativo Java + C++ + OpenGL ES 3.0 + JNI.
- Não introduzir Unity, Godot, WebView ou substituir a interface existente sem necessidade e autorização.
- Não remover nem renumerar estados/IDs usados pela JNI sem migração e testes.
- Não afirmar que modelo, modo, mapa, multiplayer, download ou configuração funciona sem evidência.
- Não criar botões decorativos, jogadores falsos ou arquivos de conteúdo artificiais.
- Não fazer alterações concorrentes que cancelem builds; agrupar mudanças coerentes e validar cada integração.
- Consultar o estado atual do repositório antes de cada conjunto de alterações.


## Registro de execução — fundação compartilhada iniciada em 2026-11-21
A fase 2 (fundação de dados e serviços) está **em desenvolvimento**, não concluída. Código já publicado:
- `PlayerProfileStore`: contrato local compartilhado de perfil/carteiras, migração de esquema, compras atômicas, resgates idempotentes e ledger limitado.
- `HudOverlay`: lobby/perfil/loja/inventário conectados à mesma fonte; saldo de moedas e diamantes; cosméticos de perfil com preço fixo em moedas; histórico de carteira e atalho funcional para missões.
- `AccountSyncManager`: persistência/restauração local e sincronização de moedas, ledger, sensibilidades, missões e marcos do passe pela API própria FastAPI + PostgreSQL.
- A base ainda é local/offline e não substitui validação autoritativa no servidor. Não habilitar multiplayer, compras online ou saldos seguros até construir o serviço autoritativo.
- Critérios restantes antes de promover esta fase: build Gradle/NDK, testes automatizados de migração/idempotência/conflitos, verificação de sincronização em duas instalações e teste físico no Redmi 12.


### Fundação compartilhada — atualização de implementação
A fase 2 agora inclui, além do contrato de perfil/carteiras:
- estatísticas persistentes de partidas e histórico local limitado;
- ID de sessão e proteção contra contagem repetida;
- sincronização dos contadores, histórico e carteiras pela API própria FastAPI + PostgreSQL;
- apresentação de estatísticas no lobby e perfil.
A fase permanece em desenvolvimento até build Android, testes automatizados, teste de sincronização entre instalações e validação física.


## Administração própria — extensão integrada em 2026-11-21
- O módulo Android `:admin` é um aplicativo nativo separado do jogo e acessa apenas a API administrativa por HTTPS.
- O backend separa tokens de jogador e administrador por claim de papel; todos os endpoints de administração exigem administrador ativo.
- Provisionamento inicial do administrador ocorre apenas por variáveis privadas do ambiente de hospedagem. Nenhum segredo administrativo pode ser empacotado no APK ou commitado no Git.
- Operações de conta e alterações de configuração são auditadas. Exclusão é lógica, com bloqueio imediato de login e preservação mínima de auditoria.
- As configurações de nome do jogo e nomes de personagens ficam persistidas no PostgreSQL. O cliente do jogo ainda precisa consumir essas configurações antes de serem visíveis no lobby.
- Antes de produção: migrar o esquema para Alembic, adicionar limitação de tentativas/login administrativo, configurar backups/restauração e testar endpoints em banco PostgreSQL real; validar APK admin e jogo em dispositivos Android.


## Cosméticos modulares — extensão de 2026-11-21
- Slots de barba, capacete, mochila e colete têm IDs independentes e opção none. A engine desenha os anexos no espaço local do operador; SharedPreferences persiste seleção local e API de perfil sincroniza seleção.
- VULCÃO, NEBULAR e CERRADO são três coleções de skin originais com variações de materiais e detalhes geométricos.
- A tela de oficina permite selecionar/remover cada peça e o guarda-roupa mostra as sete skins completas.
- Critério para fase de alta fidelidade: trocar os anexos procedurais por meshes otimizadas e UV/texturas/rig compatíveis, validar encaixe em todos os operadores e testar animação de corrida, salto, agachamento, mira e queda.
- Endpoint autenticado `GET /v1/game/settings` distribui configurações globais; cliente salva a última configuração válida localmente para funcionamento offline.


## Correção da carteira online e do código promocional — 2026-11-21
- A carteira PostgreSQL passa a ser a fonte de verdade para saldo online; coins e diamonds foram removidos do contrato de gravação do perfil.
- A primeira leitura de carteira migra uma vez os saldos legados do perfil para player_wallets e preserva no banco o resgate antigo 100K identificado pelo ledger.
- O código 100K é validado no FastAPI, concedido no PostgreSQL e limitado a um resgate por conta.
- A recompensa diária e a roleta já usam operações no servidor.
- Ainda não há servidor autoritativo de partidas. Resultados e recompensas de partidas, missões, passe, eventos, correio, conquistas e compras que continuam no armazenamento local não devem ser tratados como saldo online.
- Esta etapa só pode ser considerada operacional após deploy da API, testes automatizados, compilação Android e teste com conta real no aparelho.


## MODMENU2 e sincronização segura — 2026-11-21
- MODMENU2 é um painel de desenvolvimento, acessível em Configurações. God Mode altera a engine nativa somente quando o modo selecionado é Treinamento e a sessão é offline.
- Aimbot e adulteração client-side da carteira online não fazem parte do projeto; saldos virtuais devem ser alterados apenas por endpoints administrativos autenticados.
- A falha “Falha ao sincronizar. Dados locais preservados” vinha de I/O HTTP síncrono no thread principal Android. O debounce e `syncNow()` agora enviam o salvamento ao executor de rede; falhas transitórias são repetidas sem apagar os dados locais.
- A aplicação do perfil remoto mescla contadores, flags de desbloqueio e históricos com dados locais para reduzir risco de perda de progresso.
- Validação pendente: CI Android DEBUG, APK, deploy e teste de sincronização com conta real.


## Armazenamento persistente do pacote Season 02 — 2026-11-21
- O instalador Android salva o ZIP verificado em `Downloads/Battle Universe/battle-universe-season-02-pack.zip` no Android 10+ usando MediaStore, sem pedir permissão ampla de armazenamento.
- Antes de iniciar outro download, procura o arquivo salvo, confere tamanho e SHA-256 com o manifesto remoto e reutiliza o ZIP válido para extrair novamente.
- O ZIP não é apagado depois da instalação; permanece disponível no armazenamento compartilhado para futuras reinstalações/atualizações. A cópia de trabalho privada continua sendo usada para extração segura e instalação atômica e é apagada após o sucesso; a cópia compartilhada permanece. O cálculo de espaço livre considera o ZIP privado, o ZIP salvo e até 3 GiB de conteúdo extraído.
- Se o arquivo público não existir ou falhar na integridade, o instalador baixa uma nova cópia e substitui o arquivo salvo.
- Em Android 7–9, a cópia fica no diretório externo específico do aplicativo, sem permissão ampla.
- Estado: código publicado nos commits `1903fef`, `7c7ce21`, `af92026` e `6e51c0c`; compilação Android e teste físico ainda pendentes. Não declarar a função validada no aparelho até concluir esses testes.


## Analisador do arquivo Season 02 por nome — 2026-11-21
- Na inicialização/instalação, procurar o ZIP Season 02 pelo nome canônico e por variantes numéricas comuns criadas ao baixar novamente (`-1`, `(1)` etc.), em Downloads raiz ou subpastas.
- Não confiar apenas no nome: conferir tamanho esperado e SHA-256 do manifesto remoto antes de reutilizar. Se inválido/ausente, baixar novamente por Wi-Fi; nunca ativar dados sem validação.
- Exibir estados reais de análise, busca e validação; se os dados já estiverem instalados e válidos, abrir o jogo sem reinstalar o pacote.
- Implementação registrada na branch `feature/season02-download-file-analyzer`; CI e teste físico pendentes.
