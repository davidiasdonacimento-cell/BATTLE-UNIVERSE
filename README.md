# BATTLE UNIVERSE

Jogo Android nativo em evolução, com Java para a camada Android e C++/OpenGL ES 3.0 para o motor de jogo. Não usa Godot, Unity, WebView, HTML, CSS ou JavaScript.

## Estado da atualização 1.1.0

- IA dos bots ajustada para priorizar combate ao jogador após o pouso, manter distância de combate, mirar e exibir disparos de energia.
- Sequência de transporte alterada para mover o avião desde o início e usar uma câmera elevada dentro da cabine.
- Perfil local com nickname editável, XP/nível, saldo de diamantes e resgate 100K; estado salvo em SharedPreferences.
- Loja virtual local com três cosméticos de perfil, saldo e compras salvos no aparelho.
- Menu Royale local com passe calculado por XP e bônus diário resgatável uma vez por dia.
- O resgate 100K é limitado ao perfil local; apagar ou editar os dados do aplicativo pode reiniciar esse estado.
- O instalador foi preparado para arquivos futuros de até 2 GiB e preserva downloads parciais quando a conexão cai.
- **O pacote efetivamente publicado continua sendo o arquivo da Temporada 02, com 428.996.916 bytes (aprox. 409 MiB). O pacote de 2 GB ainda não foi gerado nem publicado.**
- A compilação automática não substitui o teste físico no Android.

## Versão 1.1.0 — primeira integração do Bloob

### Entrega inicial no código
- Personagem Bloob renderizado em 3D nativo: corpo cilíndrico, cabeça arredondada, olhos grandes, pupilas e botas claras.
- Animações procedurais em tempo real: parado, caminhada, corrida, mira, andar mirando, reação visual ao disparo, salto e descida de paraquedas.
- Quatro skins visuais: padrão azul, tática, neon e dourada.
- Partida offline configurada para 30 participantes: 1 jogador e 29 bots IA.
- Bots distribuídos por diferentes posições do mapa; a distância de renderização varia conforme a qualidade gráfica para reduzir o trabalho na GPU.
- Equipamento visual de energia fictício, sem armas reais.

### Estado desta etapa
A execução do GitHub Actions 44534495237 terminou com sucesso, incluindo testes unitários disponíveis, compilação Android e verificações do APK/DEX/bibliotecas ARM. Isso confirma a compilação, não o teste físico no aparelho. O pacote adicional de 2 GB não foi gerado: o arquivo atualmente publicado permanece com 428.996.916 bytes (aprox. 409 MiB).

## Versão anterior 0.5.0-alpha — inserção no avião e batalha local

### Correções e sistemas desta etapa
- Controles touchscreen usam o mesmo sistema de coordenadas da HUD, compatível com telas panorâmicas.
- Convenção de orientação do personagem ajustada para o eixo frontal local -Z.
- Aliados em Dupla/Esquadrão patrulham setores próprios e não seguem o jogador.
- IA hostil atualizada para priorizar o jogador após o pouso, aproximar-se em formação espaçada, mirar e disparar pulsos de energia visíveis; ainda requer teste físico de equilíbrio e comportamento.
- HUD nativa de jogo: minimapa com posição do jogador, joystick, disparo, troca de arma, construção, corrida, salto e interação.
- Três armas de energia totalmente fictícias para o jogo: Pulso Plasma, Raio Arc e Nova. Munição, alternância, disparos visíveis, alvos de treino e dano aos bots.
- Construção de paredes, plataformas e rampas, com madeira, limite, detecção de sobreposição e colisão de paredes.
- Sequência de avião atualizada: deslocamento desde o primeiro quadro, visão elevada dentro da cabine com 30 passageiros organizados em seis fileiras e salto disponível após a contagem.
- Modo Battle Royale local com 29 adversários IA espalhados pelo mapa, vida, eliminações, ataques fictícios de energia e dano fora da zona segura; treinamento fica separado.
- Mira de ombro com zoom de câmera, retículo central e disparo alinhado à direção da câmera.
- Movimento relativo à câmera corrigido; a combinação simultânea de analógico, câmera e botões mantém o encaminhamento multitouch ativo.
- Sensibilidade da câmera selecionável (baixa, média e alta), salva no aparelho.
- Arena ampliada para cerca de 230 x 230 unidades jogáveis, com estradas, 16 estruturas e cinturão de vegetação.
- Configurações e preferências continuam locais no aparelho.

### Controles
- Joystick esquerdo: movimentação.
- Arraste na metade direita: câmera.
- MIRA: manter pressionado para aproximar a câmera e alinhar a mira.
- DISPARAR: arma de energia equipada.
- ARMA: alterna entre Pulso Plasma, Raio Arc e Nova.
- CONSTRUIR: alterna parede, plataforma e rampa; cada peça custa madeira.
- CORRER: manter pressionado.
- SALTAR: salto normal ou salto do avião.
- USAR: coleta recursos próximos.

## Conteúdo da Temporada 02
O instalador na primeira abertura baixa automaticamente um pacote de conteúdo expandido de aproximadamente 409 MiB (tamanho exato mostrado na tela). O arquivo inclui:
- Modelo OBJ do avião de transporte, operador, emissor de energia, peças de construção e objetos de cenário.
- Cinco conjuntos de materiais PBR com texturas 4K de albedo, normal e roughness e mapas AO 2K.
- Oito arquivos de configuração de mapa.
- Roster, missões, metadados das armas fictícias, shaders OpenGL ES 3.0 e quatro ambientes de áudio.

O instalador valida o SHA-256 do ZIP e os hashes individuais dos arquivos, verifica caminhos antes da extração, limita o tamanho extraído, confere espaço livre e instala em uma pasta temporária antes de ativar. Se a conexão falhar, o usuário pode tentar novamente ou abrir o jogo-base.

## Build e testes
Workflow: `.github/workflows/android.yml`.
- Configura Python, JDK 17, Android SDK, NDK 27 e CMake.
- Gera o pacote de conteúdo e publica a release `data-pack-v2`.
- Executa os testes Android disponíveis e compila APK debug.
- Verifica `classes.dex`, libs ARM64/ARMv7 e integridade ZIP do APK.
- Publica APK e pacote 4K como artefatos.

## Limitações conhecidas
Esta versão ainda não é um jogo AAA completo. A partida é local contra adversários controlados por IA; ainda não existe multiplayer online com pessoas reais, matchmaking ou servidor de partidas. Os personagens e cenários continuam estilizados e o renderizador ainda não carrega todos os modelos OBJ e materiais 4K do pacote como recursos visuais em tempo real. O pacote de dados só deve crescer quando novos recursos estiverem integrados e forem utilizados pelo jogo. A execução física no aparelho precisa ser testada depois da instalação.

## Links
- Repositório: https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE
- Ações e APK: https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/actions
- Dados da Temporada 02: https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/tag/data-pack-v2


## Servidor de contas próprio e BATTLE UNIVERSE ADMIN

A arquitetura atual usa FastAPI + PostgreSQL para contas por nickname/senha, com senhas protegidas por Argon2 e sessões JWT. O aplicativo de administração é um APK Android nativo separado, no módulo `admin`.

O painel permite pesquisar contas, alterar nickname e nome de exibição do personagem, suspender/reativar, encerrar conta, atualizar o nome global do jogo e nomes de personagens, além de consultar auditoria. O acesso administrativo é provisionado no servidor por variáveis privadas; não existe senha administrativa embutida no APK.

Leia `ADMIN_SERVER_GUIDE.md` antes de implantar. O backend precisa de PostgreSQL e variáveis privadas no Render. O cliente do jogo ainda precisa consumir as configurações globais e passar por teste de integração. A compilação dos dois APKs é acompanhada no GitHub Actions.
