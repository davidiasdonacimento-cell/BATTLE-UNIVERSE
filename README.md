# BATTLE UNIVERSE

Jogo Android nativo em evolução, com Java para a camada Android e C++/OpenGL ES 3.0 para o motor de jogo. Não usa Godot, Unity, WebView, HTML, CSS ou JavaScript.

## Versão 0.5.0-alpha — inserção no avião e batalha local

### Correções e sistemas desta etapa
- Controles touchscreen usam o mesmo sistema de coordenadas da HUD, compatível com telas panorâmicas.
- Convenção de orientação do personagem ajustada para o eixo frontal local -Z.
- Aliados em Dupla/Esquadrão patrulham setores próprios e não seguem o jogador.
- NPCs hostis patrulham/coletam recursos, aproximam-se em curta distância, têm integridade e podem ser desativados.
- HUD nativa de jogo: minimapa com posição do jogador, joystick, disparo, troca de arma, construção, corrida, salto e interação.
- Três armas de energia totalmente fictícias para o jogo: Pulso Plasma, Raio Arc e Nova. Munição, alternância, disparos visíveis, alvos de treino e dano aos bots.
- Construção de paredes, plataformas e rampas, com madeira, limite, detecção de sobreposição e colisão de paredes.
- Partida começa dentro de um avião de transporte aberto, com 12 adversários IA visíveis, contagem regressiva de 7 segundos e salto manual em terceira pessoa.
- Modo Battle Royale local com 12 adversários IA espalhados pelo mapa, vida, eliminações, ataques fictícios de energia e dano fora da zona segura; treinamento fica separado.
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
O instalador na primeira abertura baixa automaticamente um pacote de conteúdo expandido de aproximadamente 300 MB (tamanho exato mostrado na tela). O arquivo inclui:
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
