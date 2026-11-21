# BATTLE UNIVERSE BR — Atualização 1.3.0 / OPERAÇÃO BLOOB

## Escopo desta entrega

Atualização do projeto Android nativo (Java + C++ / OpenGL ES 3.0). A interface continua desenhada em Canvas Android e o cenário é renderizado no motor 3D nativo. Não foi introduzido HTML, CSS, WebView ou Unity.

## Aeronave de transporte — correção do embarque

1. Cabine de transporte ampliada para 32 x 12 unidades do cenário.
2. Piso interno contínuo.
3. Teto reforçado e vigas estruturais visíveis.
4. Laterais com grandes janelas.
5. Corredor central livre.
6. Fileiras de assentos organizadas em quinze linhas.
7. Vinte e nove passageiros IA colocados em posições separadas, sem a antiga sobreposição de colunas.
8. Personagem do jogador renderizado separadamente e acompanhado pela câmera.
9. Console e divisória do cockpit.
10. Iluminação interna distribuída pelo teto.
11. Trilhos laterais no piso.
12. Porta traseira de carga com estado fechado/aberto.
13. Rampa traseira que aparece após a contagem inicial.
14. Movimento livre do jogador durante o voo usando o analógico.
15. Câmera em terceira pessoa acompanha a posição do jogador dentro da cabine.
16. Limites de movimento para impedir que o personagem atravesse as laterais.
17. Direção inicial da câmera alinhada ao comprimento da aeronave.
18. Salto manual liberado depois da contagem e ao chegar à rampa.
19. Saída automática de segurança após o limite de permanência na aeronave.
20. Mensagens de HUD diferentes para embarque, caminhada até a rampa e saída liberada.
21. Silhueta externa da aeronave aumentada, com fuselagem e asas maiores.

## Lobby, Royale e interface nativa

22. Fundo do lobby redesenhado com cenário atmosférico vetorial nativo.
23. Navegação do lobby ampliada para dez áreas, incluindo a Central de Missões.
24. Acesso direto ao Menu Royale no cartão principal.
25. Loja ampliada para nove itens cosméticos de perfil.
26. Inventário redesenhado em grade para os nove itens.
27. Compras verificam o saldo e salvam a propriedade no perfil local.
28. Passe Royale com seis marcos de recompensa nos níveis 2, 4, 6, 8, 10 e 12.
29. Cada marco Royale pode ser resgatado uma única vez e entrega diamantes virtuais.
30. Bônus diário continua disponível uma vez por dia: 100 XP e 50 diamantes virtuais.
31. Central de três missões diárias com progresso, barra e resgate individual.
32. Missão de partida concluída registrada ao sair depois do pouso.
33. Missão de eliminações usa o contador nativo de eliminações.
34. Missão de coleta usa o contador nativo de cristais.
35. Progresso das missões reinicia por dia e as recompensas ficam marcadas como resgatadas.
36. Configuração para inverter o eixo vertical da câmera, aplicada ao controle.
37. Opção para ocultar ou mostrar o minimapa na partida.
38. Opção para vibração tátil nos toques da interface.
39. Configurações de qualidade gráfica e sensibilidade continuam salvas no dispositivo.
40. HUD do avião mostra instruções de movimentação e o estado da rampa.

## Build e testes

- Versão Android: 1.3.0 (versionCode 16).
- O GitHub Actions executa os testes unitários disponíveis e compila o APK debug.
- O workflow valida o ZIP do APK, DEX e bibliotecas nativas ARM64/ARMv7 antes de publicar o artefato.
- O pacote adicional da Temporada 02 é separado do APK-base e contém recursos reais. O tamanho do APK não é inflado com arquivos vazios ou duplicados.
- A compilação não equivale a um teste físico no celular; a navegação na cabine, o alinhamento da rampa e os controles precisam ser confirmados no Redmi 12 após a instalação.

## Limitações mantidas e transparentes

- Partidas continuam locais/offline contra IA; não foi adicionado multiplayer online.
- O Royale, as missões e as compras são sistemas locais e usam moedas virtuais, sem pagamento real.
- O progresso na nuvem via Google/Firebase depende da configuração OAuth, SHA-1 e chave de assinatura estável; esta atualização não substitui essa configuração.
- A aeronave foi reconstruída com geometria nativa do motor. Os modelos externos OBJ e as texturas do pacote adicional não passam automaticamente a ser carregados pelo renderizador.
