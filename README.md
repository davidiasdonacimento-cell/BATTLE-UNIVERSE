# BATTLE UNIVERSE

Jogo Android nativo em desenvolvimento ativo. Java gerencia a interface, controles e instalação de conteúdo; C++ com OpenGL ES 3.0 executa o motor de renderização e a lógica da sessão. Não usa Godot, Unity, WebView, HTML, CSS ou JavaScript.

## Versão 0.3.0-alpha

### Sistemas implementados nesta etapa
- Lobby nativo com navegação para início, personagens, modos, mapas, inventário e configurações.
- Controle de personagem em terceira pessoa; correção do sinal do joystick para que arrastar para cima mova para frente.
- HUD de partida com minimapa visual, painel de status, joystick e controles para pulso, construção, corrida, pulo e interação.
- Personagem humanoide estilizado com segmentos articulados e balanço de braços/pernas durante o movimento.
- IA offline com objetivos de coleta, acompanhamento de equipe em Dupla/Esquadrão e separação entre NPCs.
- Construção local com paredes, plataformas e rampas, custo de madeira, limite de peças e colisão com paredes.
- Emissor de energia fictício para treino contra alvos flutuantes; munição, integridade dos alvos e pontuação.
- Preferências locais de personagem, mapa, modo e qualidade.
- Instalador de conteúdo com tela animada, download automático, verificação SHA-256 do pacote, verificação dos hashes internos, extração segura, substituição atômica e reinício após instalação.
- Pacote da Temporada 01 com modelos OBJ, texturas 1024px, três configurações de mapa, missões, shaders ES 3.0 e áudio ambiente.

### Controles na partida
- Joystick esquerdo: movimento.
- Área direita: girar a câmera.
- PULSO: usar carga do emissor fictício em alvos de treinamento.
- CONSTRUIR: alterna entre parede, plataforma e rampa.
- CORRER: manter pressionado para correr.
- PULAR: salto.
- USAR: coletar recurso próximo.

### Compilação
O workflow em `.github/workflows/android.yml`:
1. Configura Python, Java 17, Android SDK, NDK e CMake.
2. Gera o pacote de dados e publica-o como release de pré-lançamento.
3. Executa os testes Android disponíveis e compila o APK debug.
4. Verifica `classes.dex`, bibliotecas ARM64/ARMv7 e a integridade ZIP do APK.
5. Publica APK e pacote de dados como artefatos.

## Limitações conhecidas
Esta é uma etapa jogável em evolução, não o jogo AAA completo. Os modelos ainda são estilizados e não vêm de captura de movimento ou rigs profissionais; há três cenários iniciais em vez dos oito mapas planejados; os NPCs seguem rotas e objetivos locais, mas não oferecem comportamento tático avançado; o modo online, veículos, sistema completo de destruição, áudio espacial e loja/passe integrados ainda não estão implementados. O pacote instalado valida e guarda os recursos, enquanto a integração de todos os modelos/texturas/áudio com o renderizador será feita em etapas separadas.

## APK
Abra Actions, selecione a execução mais recente de **BATTLE UNIVERSE Android** e baixe o artefato `BATTLE-UNIVERSE-debug-apk`. Extraia o ZIP para encontrar `app-debug.apk`. É uma compilação debug para testes; a execução no aparelho deve ser validada separadamente.
