# BATTLE UNIVERSE

Jogo Android nativo em desenvolvimento. Java gerencia a interface e os controles Android; C++ com OpenGL ES 3.0 faz a renderização 3D. Não usa Godot, Unity, WebView ou HTML.

## Versão 0.2.0 — reconstrução do lobby

- O aplicativo abre no lobby principal, não diretamente na partida.
- Menu nativo: Início, Personagens, Modos, Mapas, Inventário e Configurações.
- Seleção de operador, modo e mapa.
- Preferências salvas localmente no aparelho.
- Botão Jogar entra no treinamento; Voltar retorna ao lobby.
- Motor 3D C++/OpenGL ES 3.0 e controles touchscreen na partida.
- GitHub Actions compila o APK de depuração.

## Limites desta etapa
Esta versão reconstrói a entrada e a navegação. Não representa ainda todos os recursos do planejamento. Modelos humanos de alta fidelidade, animações avançadas, veículos, construção, inventário de partida e mapas completos continuam em desenvolvimento e precisam de testes em aparelhos reais.

## APK
Abra a aba Actions, entre na execução Android APK mais recente e baixe o artefato BATTLE-UNIVERSE-debug-apk. Extraia o ZIP para encontrar o APK. Esta compilação debug é para testes.
