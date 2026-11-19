# BATTLE UNIVERSE

Jogo Android nativo em desenvolvimento. O projeto usa Java para a camada Android e C++ com OpenGL ES 3.0 para renderização 3D. Não usa Godot, Unity, WebView nem HTML.

## Versão atual: 0.1.0-alpha

Esta primeira entrega é uma base jogável e compilável, não a versão final do jogo.

### O que já está implementado
- Aplicativo Android nativo em orientação horizontal.
- Renderização 3D com OpenGL ES 3.0 e C++ (NDK).
- Cenário tropical original com oceano, praia, gramado, construções e palmeiras.
- Personagem em terceira pessoa.
- Controles touchscreen: movimento, câmera, salto e interação.
- Bots de treinamento com movimento autônomo de patrulha.
- Cinco cristais coletáveis e objetivo de treinamento.
- Zona de segurança circular que diminui durante a sessão.
- HUD desenhado nativamente no Android.
- GitHub Actions compila o APK de depuração e publica o arquivo como artefato.

### Controles
- Analógico esquerdo: andar.
- Arrastar o lado direito da tela: girar a câmera.
- Botão PULAR: saltar.
- Botão COLETAR: recolher um cristal próximo.

### Como obter o APK
1. Abra a aba Actions deste repositório.
2. Entre na execução chamada Android APK mais recente.
3. Na seção Artifacts, baixe BATTLE-UNIVERSE-debug-apk.
4. Extraia o ZIP e instale o arquivo APK em um dispositivo Android compatível.

O APK de depuração é destinado a testes. Ainda não é uma versão assinada para distribuição pública.

### Compilação local
Requisitos: JDK 17, Gradle 8.9, Android SDK API 35, Android NDK 27.0.12077973 e CMake 3.22.1.

Execute na raiz do projeto:

    gradle --no-daemon :app:assembleDebug

O APK será gerado em:

    app/build/outputs/apk/debug/app-debug.apk

### Próximas etapas do planejamento
- Sistema de equipamentos e mecânicas de combate fictícias, após fechar o planejamento específico.
- Lobby 3D com formação de até quatro personagens, personalização, gestos e emotes.
- Modos Solo, Dupla, Esquadrão e Treinamento com IA.
- Mapas Neon City e Desert Storm.
- Inventário, coleta de recursos, construção, veículos e edifícios interativos.
- Portas, janelas, objetos, água, natação e mergulho.
- Clima, ciclo dia/noite, áudio espacial, idiomas e progressão.
- Tela de entrada cinematográfica e resultados completos.
- Otimização, testes em aparelhos Android reais e novas opções gráficas.

Esses itens são objetivos do projeto, não recursos já concluídos nesta versão. O tamanho das partidas, qualidade gráfica e taxa de quadros serão definidos por testes reais em aparelhos.

## Estrutura
- app/src/main/java: atividade Android, superfície OpenGL e HUD.
- app/src/main/cpp: motor 3D em C++.
- .github/workflows: automação de compilação do APK.
