package com.battleuniverse.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import java.text.NumberFormat;
import java.util.Locale;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.HapticFeedbackConstants;

public final class HudOverlay extends View {
    private static final int HOME=0, CHARACTERS=1, MODES_PAGE=2, MAPS_PAGE=3,
        INVENTORY=4, SETTINGS=5, PROFILE=6, SHOP=7, ROYALE=8, MISSIONS=9, GAME=10;
    private static final String[] NAV={"INÍCIO","PERSONAGENS","MODOS","MAPAS",
        "INVENTÁRIO","AJUSTES","PERFIL","LOJA","ROYALE","MISSÕES"};
    private static final String[] SHOP_NAMES={"MOLDURA NEON","EMBLEMA BLOOB","TÍTULO CAMPEÃO",
        "MOLDURA FROST","INSÍGNIA TROPA","TÍTULO ELITE","FAIXA VETERANO","SELO NOVA","BANNER VITÓRIA"};
    private static final String[] SHOP_KEYS={"shop_frame_neon","shop_emblem_bloob","shop_title_champion",
        "shop_frame_frost","shop_badge_squad","shop_title_elite","shop_ribbon_veteran","shop_seal_nova","shop_banner_victory"};
    private static final long[] SHOP_PRICES={500L,1000L,1500L,700L,900L,1800L,1200L,1600L,2200L};
    private static final int[] SHOP_COLORS={0xFF8D42D7,0xFF08A9D8,0xFFE0A62B,0xFF66DDEB,
        0xFF40B989,0xFFFFB454,0xFF4D79C7,0xFFB55BDD,0xFFE45B65};
    private static final int[] ROYALE_LEVELS={2,4,6,8,10,12};
    private static final long[] ROYALE_REWARDS={40,60,80,100,120,150};
    private static final String[] MODE_NAMES={"BATTLE ROYALE","DUPLA","ESQUADRÃO","TREINAMENTO"};
    private static final String[] MAP_NAMES={"ILHA TROPICAL","NEON CITY","DESERT STORM"};
    private static final String[] CHARACTER_NAMES={"BLOOB","BLOOB TÁTICO","BLOOB NEON","BLOOB DOURADO"};
    private static final String[] QUALITY_NAMES={"ECONÔMICO","EQUILIBRADO","ALTO"};
    private final GameSurfaceView game;
    private final SharedPreferences prefs;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r=new RectF();
    private int page=HOME, mode, map, character, quality=1, lookSensitivity=1;
    private boolean music=true, effects=true, forwarding;
    private boolean invertCameraY, showMinimap=true, hapticsEnabled=true;
    private float scale=1, offsetX, offsetY;
    private String toast="";
    private boolean googleConfigured, googleSignedIn;
    private String googleEmail="", googleMessage="Entre com Google para sincronizar.";
    private Runnable googleAccountAction;
    private long toastUntil, lastTap;

    public HudOverlay(Context context, GameSurfaceView game, SharedPreferences prefs) {
        super(context);
        this.game=game; this.prefs=prefs;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setClickable(true); setFocusable(true);
        mode=prefs.getInt("mode",0); map=prefs.getInt("map",0);
        character=prefs.getInt("character",0); quality=prefs.getInt("quality",1);
        music=prefs.getBoolean("music",true); effects=prefs.getBoolean("effects",true);
        lookSensitivity=prefs.getInt("look_sensitivity",1);
        invertCameraY=prefs.getBoolean("invert_camera_y",false);
        showMinimap=prefs.getBoolean("show_minimap",true);
        hapticsEnabled=prefs.getBoolean("haptics",true);
        ensureDailyMissionWindow();
        game.setInvertCameraY(invertCameraY);
        game.setCharacter(character); game.setMap(map);
        game.setMatchMode(mode); game.setQuality(quality); game.setLookSensitivity(lookSensitivity); game.setGameScreen(false);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        scale=Math.min(w/1280f,h/720f);
        offsetX=(w-1280f*scale)/2f; offsetY=(h-720f*scale)/2f;
        c.save(); c.translate(offsetX,offsetY); c.scale(scale,scale);
        if(page==GAME) drawGame(c); else drawLobby(c);
        c.restore();
        if(toastUntil>System.currentTimeMillis()){drawToast(c,w,h);postInvalidateDelayed(120);}
    }

    private void drawLobby(Canvas c) {
        p.setShader(new LinearGradient(0,0,900,0,new int[]{0xF2071421,0xC8071421,0x00071421},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,930,720,p); p.setShader(null);
        p.setColor(0x81071421); c.drawRect(0,0,1280,96,p);
        p.setColor(0x5579DDEB); c.drawRect(0,94,1280,96,p);
        drawBrand(c); drawProfile(c); drawSidebar(c); drawPage(c); drawTeam(c);
    }

    private void drawBrand(Canvas c) {
        text(c,"BATTLE",34,47,30,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"UNIVERSE",151,47,30,0xFF49D9E8,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"MUNDO  •  SOBREVIVÊNCIA  •  AVENTURA",36,68,10,0xFF9AB7C9,Typeface.DEFAULT_BOLD);
        pill(c,272,25,128,29,"LOBBY PRINCIPAL",0x332FD2E5,0xFF9BEAF4);
    }
    private void drawProfile(Canvas c) {
        long xp=prefs.getLong("xp",0L);
        long diamonds=prefs.getLong("diamonds",0L);
        int level=(int)Math.min(30L,1L+xp/500L);
        String nickname=prefs.getString("nickname","JOGADOR");
        panel(c,1000,17,246,60,0xC5152938,0x557AA0B8,15);
        circle(c,1032,47,18,0xFF1F7584,
            prefs.getBoolean("shop_frame_neon",false)?0xFFB978FF:0xFF65DCE7);
        text(c,nickname.length()>11?nickname.substring(0,11):nickname,1060,42,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"NÍVEL "+String.format(Locale.US,"%02d",level)+"  •  "+xp+" XP",1060,60,9,0xFF9BB6C6,Typeface.DEFAULT);
        text(c,"◆ "+formatDiamonds(diamonds),1176,51,11,0xFFFFC76B,Typeface.DEFAULT_BOLD);
    }
    private String formatDiamonds(long value) {
        return NumberFormat.getIntegerInstance(new Locale("pt","BR")).format(value);
    }
    private void drawSidebar(Canvas c) {
        // In-game horizontal navigation, not an admin-style web sidebar.
        panel(c,24,105,1224,50,0xB90A1B2B,0x336CA4B7,16);
        for(int i=0;i<NAV.length;i++) {
            float x=27+i*122f;boolean active=page==i;
            panel(c,x,111,118,38,active?0xDD136276:0x22243B4D,
                    active?0xFF35D6E5:0x225A8297,9);
            drawNavIcon(c,i,x+14,130,active);
            text(c,NAV[i],x+25,134,7,active?Color.WHITE:0xFFB8CBD7,
                Typeface.create(active?"sans-serif-black":"sans-serif-medium",Typeface.NORMAL));
        }
    }
    private void drawNavIcon(Canvas c,int i,float x,float y,boolean active){
        p.setColor(active?0xFFE9FCFF:0xFF8AA8B8);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);
        if(i==0){c.drawRoundRect(x-8,y-8,x+8,y+8,3,3,p);c.drawLine(x-5,y,x,y-5,p);c.drawLine(x,y-5,x+5,y,p);}
        else if(i==1){c.drawCircle(x,y-4,5,p);c.drawRoundRect(x-8,y+2,x+8,y+10,5,5,p);}
        else if(i==2){c.drawCircle(x,y,8,p);c.drawLine(x-6,y-6,x+6,y+6,p);c.drawLine(x+6,y-6,x-6,y+6,p);}
        else if(i==3){c.drawRoundRect(x-8,y-7,x+8,y+7,2,2,p);c.drawCircle(x,y,3,p);}
        else if(i==4){c.drawRoundRect(x-8,y-8,x+8,y+8,3,3,p);c.drawLine(x-4,y-3,x+4,y-3,p);c.drawLine(x-4,y+2,x+4,y+2,p);}
        else{c.drawCircle(x,y,8,p);c.drawCircle(x,y,3,p);}
        p.setStyle(Paint.Style.FILL);
    }
    private void drawPage(Canvas c){
        if(page==HOME)drawHome(c); else if(page==CHARACTERS)drawCharacters(c);
        else if(page==MODES_PAGE)drawModes(c); else if(page==MAPS_PAGE)drawMaps(c);
        else if(page==INVENTORY)drawInventory(c); else if(page==SETTINGS)drawSettings(c);
        else if(page==PROFILE)drawProfilePage(c); else if(page==SHOP)drawShopPage(c);
        else if(page==ROYALE)drawRoyalePage(c);
        else if(page==MISSIONS)drawMissionsPage(c);
    }
    private void drawHome(Canvas c){
        eyebrow(c,272,174,"BATTLE UNIVERSE BR 1.2.2  /  OPERAÇÃO BLOOB");
        text(c,"SUA PRÓXIMA",272,224,37,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"JORNADA COMEÇA AQUI.",272,267,37,0xFF55DDE7,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Monte sua equipe, escolha o destino e entre na batalha.",274,296,14,0xFFB3C8D4,Typeface.DEFAULT);
        panel(c,270,328,275,112,0xC9183040,0x557AA9BC,18);
        panel(c,558,328,292,112,0xC9183040,0x557AA9BC,18);
        eyebrow(c,290,357,"MODO DE JOGO");
        text(c,MODE_NAMES[mode],290,390,21,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,mode==0?"Você + 29 bots IA • 30 participantes":mode==1?"Você + 1 aliado • 28 bots IA":mode==2?"Você + 3 aliados • 26 bots IA":"Treinamento livre • alvos IA",290,417,11,0xFF9DB6C5,Typeface.DEFAULT);
        chevron(c,515,385);
        eyebrow(c,578,357,"MAPA SELECIONADO");
        text(c,MAP_NAMES[map],578,390,19,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,map==0?"Praias • Florestas • Vilas":map==1?"Cidade moderna • Neon":"Dunas • Cânions • Ruínas",578,417,11,0xFF9DB6C5,Typeface.DEFAULT);
        chevron(c,817,385);
        gradientButton(c,270,468,342,78,"JOGAR AGORA","ENTRAR NA PARTIDA",0xFFFFB45E,0xFFE97543);
        text(c,"▶",292,516,21,0xFFFFF4E7,Typeface.DEFAULT_BOLD);
        panel(c,628,468,222,78,0xB9183040,0x557AA9BC,17);
        text(c,"● PARTIDA LOCAL",650,498,11,0xFF76E7CF,Typeface.DEFAULT_BOLD);
        text(c,"BATTLE ROYALE",650,522,15,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"30 participantes offline • 1 jogador + 29 bots IA.",274,579,10,0xFF9CB5C2,Typeface.DEFAULT);
        panel(c,270,608,580,58,0x70152B3A,0x225B8296,14);
        stat(c,292,642,"30","PARTICIPANTES");stat(c,468,642,"29","BOTS IA");stat(c,665,642,"OFF","MODO OFFLINE");
    }
    private void drawCharacters(Canvas c){
        eyebrow(c,270,175,"PERSONALIZAÇÃO");title(c,"SUA EQUIPE",270,224);
        subtitle(c,"Escolha a skin do Bloob. As quatro variações aparecem no cenário 3D.",270,252);
        String[] roles={"PADRÃO","TÁTICO","NEON","DOURADO"};
        int[] colors={0xFF08A9D8,0xFF344C5C,0xFF8D42D7,0xFFE0A62B};
        for(int i=0;i<4;i++){
            float x=270+i*146;boolean active=character==i;
            panel(c,x,295,132,210,active?0xD51B5B69:0xA51A3040,active?0xFF48D7E1:0x335D8495,16);
            circle(c,x+66,365,36,colors[i],active?0xFF68E9EF:0xFF8AA5B4);
            text(c,new String[]{"B","T","N","G"}[i],x+59,372,19,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,CHARACTER_NAMES[i],x+18,432,14,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,roles[i],x+14,453,9,0xFF9DB6C5,Typeface.DEFAULT);
            pill(c,x+(active?27:35),470,active?78:62,23,active?"SELECIONADO":"ESCOLHER",
                    active?0xFF176A75:0x553B6172,active?Color.WHITE:0xFFBBD0D9);
        }
        panel(c,270,535,570,92,0xA6172C3C,0x335D8495,17);
        eyebrow(c,292,563,"VISUALIZAÇÃO 3D");
        text(c,"O operador selecionado aparece no lobby ao fundo.",292,590,15,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Cosméticos visuais não oferecem vantagem competitiva.",292,611,11,0xFF8FAAB8,Typeface.DEFAULT);
    }
    private void drawModes(Canvas c){
        eyebrow(c,270,175,"SELECIONE A EXPERIÊNCIA");title(c,"MODOS DE JOGO",270,224);
        subtitle(c,"Partida offline com até 30 participantes: você e 29 bots IA.",270,252);
        String[] desc={"Você contra 29 bots IA","Você + 1 aliado • 28 bots IA","Você + 3 aliados • 26 bots IA","Sem pressão • alvos e exploração"};
        String[] counts={"30 PARTICIPANTES","30 PARTICIPANTES","30 PARTICIPANTES","TREINAMENTO"};
        for(int i=0;i<4;i++){
            float x=270+(i%2)*292,y=290+(i/2)*150;boolean active=mode==i;
            panel(c,x,y,270,126,active?0xD51B5B69:0xAA193040,active?0xFF48D7E1:0x335D8495,17);
            pill(c,x+17,y+16,92,23,counts[i],0x553A6B7C,0xFFB9E8EF);
            text(c,MODE_NAMES[i],x+18,y+70,20,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,desc[i],x+18,y+96,11,0xFFAAC1CE,Typeface.DEFAULT);
            if(active)circle(c,x+242,y+25,7,0xFF5CE4DA,0xFFB6FFF5);
        }
    }
    private void drawMaps(Canvas c){
        eyebrow(c,270,175,"DESTINOS DISPONÍVEIS");title(c,"ESCOLHA O MAPA",270,224);
        subtitle(c,"Cada região terá clima, terreno e pontos de interesse próprios.",270,252);
        int[] colors={0xFF1B7B72,0xFF554C91,0xFFB77B43};
        String[] detail={"Praias, floresta e vilas","Cidade moderna e distritos neon","Dunas, cânions e instalações"};
        for(int i=0;i<3;i++){
            float x=270+i*190;boolean active=map==i;
            panel(c,x,292,174,258,active?0xDB1B5B69:0xB0193040,active?0xFF48D7E1:0x335D8495,18);
            panel(c,x+12,305,150,112,colors[i],0x66FFFFFF,12);
            mapArtwork(c,i,x+12,305,150,112);
            text(c,MAP_NAMES[i],x+15,451,14,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,detail[i],x+15,477,10,0xFFABC0CC,Typeface.DEFAULT);
            pill(c,x+15,506,active?105:93,25,active?"SELECIONADO":"SELECIONAR",
                    active?0xFF176A75:0x553B6172,active?Color.WHITE:0xFFBBD0D9);
        }
    }
    private void drawInventory(Canvas c){
        eyebrow(c,270,175,"COLEÇÃO DO OPERADOR");title(c,"INVENTÁRIO",270,224);
        subtitle(c,"Itens cosméticos locais desbloqueados na loja e no passe.",270,252);
        panel(c,270,270,570,58,0xAE193040,0x335D8495,15);
        text(c,"◆ "+formatDiamonds(prefs.getLong("diamonds",0L))+" DIAMANTES",292,306,17,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        for(int i=0;i<SHOP_NAMES.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=344+row*100f;
            boolean owned=prefs.getBoolean(SHOP_KEYS[i],false);
            panel(c,x,y,174,88,owned?0xB51A4650:0x771A3040,
                owned?0xFF4BD4C9:0x335D8495,14);
            circle(c,x+28,y+34,17,SHOP_COLORS[i],0xFFB5D6E1);
            text(c,SHOP_NAMES[i],x+53,y+31,9,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,owned?"DESBLOQUEADO":"BLOQUEADO",x+53,y+51,8,
                owned?0xFF83DCD5:0xFF91A9B7,Typeface.DEFAULT_BOLD);
            text(c,owned?"NA COLEÇÃO":"ADQUIRA NA LOJA",x+53,y+67,7,0xFF9EB6C4,Typeface.DEFAULT);
        }
    }

    private void drawSettings(Canvas c){
        eyebrow(c,270,175,"CONTROLES E PREFERÊNCIAS");title(c,"CONFIGURAÇÕES",270,224);
        subtitle(c,"Ajustes locais para a tela e o controle do operador.",270,252);
        panel(c,270,282,570,84,0xAE193040,0x335D8495,16);
        eyebrow(c,292,309,"QUALIDADE GRÁFICA");
        text(c,QUALITY_NAMES[quality],292,344,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        for(int i=0;i<3;i++)pill(c,520+i*100,302,92,38,QUALITY_NAMES[i],
            quality==i?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,378,276,60,0xAE193040,0x335D8495,14);
        text(c,"MÚSICA",290,414,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,430,392,100,32,music?"ATIVADA":"DESATIVADA",music?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,562,378,278,60,0xAE193040,0x335D8495,14);
        text(c,"EFEITOS",582,414,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,718,392,104,32,effects?"ATIVADOS":"DESATIVADOS",effects?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,450,570,76,0xAE193040,0x335D8495,15);
        eyebrow(c,292,477,"SENSIBILIDADE DA CÂMERA");
        String[] levels={"BAIXA","MÉDIA","ALTA"};
        for(int i=0;i<3;i++)pill(c,520+i*100,466,92,38,levels[i],
            lookSensitivity==i?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,538,570,108,0xAE193040,0x335D8495,15);
        text(c,"CÂMERA INVERTIDA",292,565,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,690,549,130,30,invertCameraY?"ATIVADA":"DESATIVADA",
            invertCameraY?0xFF176A75:0x553B6172,Color.WHITE);
        text(c,"MINIMAPA",292,598,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,690,582,130,30,showMinimap?"VISÍVEL":"OCULTO",
            showMinimap?0xFF176A75:0x553B6172,Color.WHITE);
        text(c,"VIBRAÇÃO NOS TOQUES",292,631,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,690,615,130,26,hapticsEnabled?"ATIVADA":"DESATIVADA",
            hapticsEnabled?0xFF176A75:0x553B6172,Color.WHITE);
    }

    private void drawProfilePage(Canvas c) {
        eyebrow(c,270,175,googleSignedIn?"CONTA GOOGLE CONECTADA":"PERFIL E CONTA GOOGLE");
        title(c,"PERFIL DO JOGADOR",270,224);
        subtitle(c,"Entre com Google para recuperar seu progresso em outros APKs.",270,252);
        long xp=prefs.getLong("xp",0L),diamonds=prefs.getLong("diamonds",0L);
        int level=(int)Math.min(30L,1L+xp/500L);
        panel(c,270,286,570,196,0xAE193040,0x557AA9BC,18);
        circle(c,326,365,38,0xFF1F7584,
            prefs.getBoolean("shop_frame_neon",false)?0xFFB978FF:0xFF65DCE7);
        text(c,prefs.getString("nickname","JOGADOR"),382,346,23,Color.WHITE,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"NÍVEL "+level+"  •  "+xp+" XP",382,374,13,0xFF9FC0CF,Typeface.DEFAULT_BOLD);
        text(c,"◆ "+formatDiamonds(diamonds)+" DIAMANTES",382,411,17,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        text(c,prefs.getBoolean("code_100k_redeemed",false)
            ?"CÓDIGO 100K JÁ RESGATADO NESTE PERFIL"
            :"CÓDIGO SECRETO 100K DISPONÍVEL",292,458,10,0xFF83DCD5,Typeface.DEFAULT_BOLD);
        panel(c,270,486,570,58,googleSignedIn?0xCC176A75:0xA91A3040,
            googleSignedIn?0xFF58DDE6:0x335D8495,15);
        text(c,googleSignedIn?"CONTA GOOGLE CONECTADA":
            (googleConfigured?"ENTRAR COM GOOGLE":"LOGIN GOOGLE A CONFIGURAR"),
            292,509,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        String accountLine=googleSignedIn&&!googleEmail.isEmpty()?googleEmail:googleMessage;
        if(accountLine.length()>68)accountLine=accountLine.substring(0,65)+"...";
        text(c,accountLine,292,530,9,0xFFBBD0D9,Typeface.DEFAULT);
        panel(c,270,558,270,70,0xCC176A75,0xFF58DDE6,16);
        text(c,"RESGATAR CÓDIGO",294,586,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Digite seu código secreto",294,607,10,0xFFD2F6F8,Typeface.DEFAULT);
        panel(c,558,558,282,70,0xA91A3040,0x335D8495,16);
        text(c,"EDITAR NICKNAME",582,586,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Nome do seu perfil",582,607,10,0xFF9EB6C4,Typeface.DEFAULT);
        text(c,"Com Google, seu save pode ser recuperado após reinstalar o jogo.",272,660,10,0xFF8FAAB8,Typeface.DEFAULT);
    }
    private void drawShopPage(Canvas c) {
        eyebrow(c,270,175,"LOJA VIRTUAL • SEM PAGAMENTO REAL");
        title(c,"LOJA DE COSMÉTICOS",270,224);
        subtitle(c,"Nove itens colecionáveis. Compras feitas só com diamantes virtuais.",270,252);
        panel(c,270,266,570,48,0xA91A3040,0x335D8495,14);
        text(c,"SALDO  ◆ "+formatDiamonds(prefs.getLong("diamonds",0L)),292,297,16,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        for(int i=0;i<SHOP_NAMES.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=326+row*108f;
            boolean owned=prefs.getBoolean(SHOP_KEYS[i],false);
            panel(c,x,y,174,96,owned?0xB51A4650:0xA91A3040,
                owned?0xFF4BD4C9:0x335D8495,14);
            circle(c,x+29,y+32,19,SHOP_COLORS[i],0xFFB5D6E1);
            text(c,SHOP_NAMES[i],x+56,y+27,8,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,owned?"JÁ ADQUIRIDO":"◆ "+SHOP_PRICES[i]+" DIAMANTES",x+56,y+47,7,0xFFFFD27A,Typeface.DEFAULT_BOLD);
            pill(c,x+56,y+58,104,26,owned?"NA COLEÇÃO":"ADQUIRIR",
                owned?0xFF176A75:0xFF345D79,Color.WHITE);
        }
        text(c,"Colecionáveis de perfil • não alteram dano, vida ou vantagem de jogo.",272,664,9,0xFF8FAAB8,Typeface.DEFAULT);
    }

    private void drawRoyalePage(Canvas c) {
        eyebrow(c,270,175,"TEMPORADA 02 • PROGRESSÃO OFFLINE");
        title(c,"MENU ROYALE",270,224);
        subtitle(c,"Trilha de recompensas, níveis, bônus diário e itens desbloqueáveis.",270,252);
        long xp=prefs.getLong("xp",0L);
        int level=(int)Math.min(30L,1L+xp/500L),progress=(int)(xp%500L);
        panel(c,270,276,570,128,0xAE193040,0x557AA9BC,17);
        eyebrow(c,292,305,"PASSE DE TEMPORADA");
        text(c,"NÍVEL "+level+" / 30",292,345,27,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,xp+" XP TOTAL  •  "+progress+" / 500 XP",292,369,10,0xFF9EB6C4,Typeface.DEFAULT_BOLD);
        p.setColor(0xFF233F4F);r.set(292,382,818,394);c.drawRoundRect(r,7,7,p);
        p.setColor(0xFF55DDE7);r.set(292,382,292+526*(progress/500f),394);c.drawRoundRect(r,7,7,p);
        panel(c,270,414,570,48,0xA91A3040,0x335D8495,13);
        text(c,"BÔNUS DIÁRIO  •  +100 XP  •  +50 DIAMANTES",290,444,10,Color.WHITE,Typeface.DEFAULT_BOLD);
        boolean dailyClaimed=prefs.getLong("daily_reward_day",-1L)==(System.currentTimeMillis()/86400000L);
        pill(c,681,423,138,30,dailyClaimed?"COLETADO":"RESGATAR",
            dailyClaimed?0xFF176A75:0xFF345D79,Color.WHITE);
        for(int i=0;i<ROYALE_LEVELS.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=476+row*88f;
            boolean claimed=prefs.getBoolean("royale_tier_claimed_"+i,false);
            boolean unlocked=level>=ROYALE_LEVELS[i];
            panel(c,x,y,174,78,claimed?0xB51A4650:unlocked?0xB1264A55:0x771A3040,
                claimed?0xFF4BD4C9:unlocked?0xFF51C9C4:0x335D8495,13);
            text(c,"MARCO "+(i+1),x+12,y+20,8,0xFF71DDE5,Typeface.DEFAULT_BOLD);
            text(c,"NÍVEL "+ROYALE_LEVELS[i]+"  •  +"+ROYALE_REWARDS[i]+" ◆",x+12,y+40,9,Color.WHITE,Typeface.DEFAULT_BOLD);
            pill(c,x+12,y+48,148,23,claimed?"RESGATADO":unlocked?"RESGATAR":"BLOQUEADO",
                claimed?0xFF176A75:unlocked?0xFF345D79:0x553B6172,Color.WHITE);
        }
        text(c,"Recompensas locais • sem compras reais ou conexão online.",272,666,9,0xFF8FAAB8,Typeface.DEFAULT);
    }

    private void claimDailyReward() {
        long day=System.currentTimeMillis()/86400000L;
        if(prefs.getLong("daily_reward_day",-1L)==day) {
            toast("A recompensa diária já foi coletada."); return;
        }
        long xp=prefs.getLong("xp",0L),diamonds=prefs.getLong("diamonds",0L);
        prefs.edit().putLong("xp",Math.min(Long.MAX_VALUE-100L,xp)+100L)
            .putLong("diamonds",Math.min(Long.MAX_VALUE-50L,diamonds)+50L)
            .putLong("daily_reward_day",day).apply();
        toast("+100 XP e +50 diamantes virtuais!");
    }
    private void showRedeemDialog() {
        EditText input=new EditText(getContext());
        input.setSingleLine(true); input.setHint("Digite 100K");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(getContext())
            .setTitle("Resgatar código secreto")
            .setMessage("O código 100K concede 100.000 diamantes virtuais uma única vez neste perfil local.")
            .setView(input).setNegativeButton("CANCELAR",null)
            .setPositiveButton("RESGATAR",(dialog,which)->redeemCode(input.getText().toString()))
            .show();
    }
    private void redeemCode(String rawCode) {
        String code=rawCode==null?"":rawCode.trim().toUpperCase(Locale.ROOT);
        if(!"100K".equals(code)) { toast("Código inválido."); return; }
        if(prefs.getBoolean("code_100k_redeemed",false)) {
            toast("O código 100K já foi resgatado neste perfil."); return;
        }
        long current=prefs.getLong("diamonds",0L);
        prefs.edit().putLong("diamonds",Math.min(Long.MAX_VALUE-100000L,current)+100000L)
            .putBoolean("code_100k_redeemed",true).apply();
        toast("+100.000 DIAMANTES VIRTUAIS!");
    }
    private void showNicknameDialog() {
        EditText input=new EditText(getContext());
        input.setSingleLine(true); input.setHint("Seu nickname");
        input.setText(prefs.getString("nickname","JOGADOR"));
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(getContext()).setTitle("Editar nickname").setView(input)
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("SALVAR",(dialog,which)->{
                String value=input.getText().toString().trim();
                if(value.isEmpty()) { toast("Digite um nickname."); return; }
                if(value.length()>16)value=value.substring(0,16);
                prefs.edit().putString("nickname",value).apply();
                toast("Nickname atualizado.");
            }).show();
    }
    private void purchaseShopItem(int index) {
        if(index<0||index>=SHOP_NAMES.length)return;
        if(prefs.getBoolean(SHOP_KEYS[index],false)) {
            toast("Este item já está no seu inventário.");return;
        }
        long balance=prefs.getLong("diamonds",0L),price=SHOP_PRICES[index];
        if(balance<price) { toast("Diamantes insuficientes.");return; }
        prefs.edit().putLong("diamonds",balance-price)
            .putBoolean(SHOP_KEYS[index],true).apply();
        toast(SHOP_NAMES[index]+" adquirido!");
    }

    private void drawTeam(Canvas c){
        panel(c,888,124,362,218,0x9B0B1D2C,0x337AA0B2,19);
        eyebrow(c,913,153,"SUA EQUIPE");
        text(c,"ESQUADRÃO DE OPERAÇÃO",913,183,16,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        String[] slots={"VOCÊ  •  "+CHARACTER_NAMES[character],"ALIADO IA  •  ATIVO","ALIADO IA  •  PRONTO","ALIADO IA  •  PRONTO"};
        for(int i=0;i<4;i++){
            float y=207+i*30;circle(c,925,y-4,7,i==0?0xFF38A9BA:0xFF35586A,0xFF8DDDE6);
            text(c,slots[i],945,y,11,i==0?Color.WHITE:0xFFB3C7D1,Typeface.DEFAULT_BOLD);
            if(i>0)text(c,"●",1208,y,9,0xFF6FD9C7,Typeface.DEFAULT_BOLD);
        }
        panel(c,888,356,362,107,0x83101F2C,0x226A8EA0,16);
        eyebrow(c,913,384,"STATUS DA SESSÃO");
        text(c,"●  MODO LOCAL",913,414,14,0xFF76E7CF,Typeface.DEFAULT_BOLD);
        text(c,"Partidas com bots • sem conexão online",913,438,10,0xFF9CB4C1,Typeface.DEFAULT);
        panel(c,888,477,362,183,0x74101F2C,0x226A8EA0,16);
        eyebrow(c,913,506,"DICA DE OPERAÇÃO");
        text(c,"Explore o mapa, colete recursos",913,540,13,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"e deixe os aliados em patrulha independente.",913,560,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Novos sistemas serão adicionados em",913,599,10,0xFF7FA4B7,Typeface.DEFAULT);
        text(c,"atualizações incrementais do projeto.",913,616,10,0xFF7FA4B7,Typeface.DEFAULT);
    }
    private void mapArtwork(Canvas c,int map,float x,float y,float w,float h){
        p.setShader(new LinearGradient(x,y,x+w,y+h,
                map==0?new int[]{0xFF54C2D1,0xFF1B665B}:map==1?new int[]{0xFF7B6BD6,0xFF171F49}:new int[]{0xFFE4B366,0xFF8B5737},
                null,Shader.TileMode.CLAMP));
        r.set(x,y,x+w,y+h);c.drawRoundRect(r,12,12,p);p.setShader(null);
        if(map==0){circle(c,x+108,y+66,19,0xFFE8D79B,0xFFFFE7B2);for(int i=0;i<3;i++)palmIcon(c,x+35+i*48,y+79);}
        else if(map==1){for(int i=0;i<5;i++){float bx=x+12+i*28;p.setColor(i%2==0?0xFF18284D:0xFF31295E);c.drawRoundRect(bx,y+20+(i%2)*18,bx+19,y+97,2,2,p);p.setColor(0xFF4FE4F0);for(int j=0;j<3;j++)c.drawRect(bx+5,y+33+j*18,bx+9,y+38+j*18,p);}}
        else{p.setColor(0xFF8B5737);c.drawOval(x+3,y+58,x+145,y+112,p);p.setColor(0xFFD59B58);c.drawOval(x+28,y+55,x+138,y+101,p);p.setColor(0xFF6C4934);c.drawRoundRect(x+69,y+36,x+95,y+90,5,5,p);}
    }
    private void palmIcon(Canvas c,float x,float y){
        p.setColor(0xFF175944);p.setStrokeWidth(3);c.drawLine(x,y,x+3,y-27,p);
        for(int i=0;i<5;i++){double a=-Math.PI*.85+i*Math.PI*.42;c.drawLine(x+3,y-26,x+3+(float)Math.cos(a)*15,y-26+(float)Math.sin(a)*11,p);}
    }
    private void drawGame(Canvas c){
        panel(c,22,18,430,85,0xD9081928,0x6679DDEB,17);
        text(c,"BATTLE UNIVERSE",42,48,19,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"OPERAÇÃO  •  "+MAP_NAMES[map],42,70,10,0xFF6FE0E6,Typeface.DEFAULT_BOLD);
        text(c,game.getGameStatus(),42,89,9,0xFFD5E7ED,Typeface.DEFAULT_BOLD);

        panel(c,22,112,278,46,0xB9071724,0x337AA0B2,13);
        text(c,"BATTLE ROYALE  •  IA LOCAL",38,141,11,0xFF79E8D0,Typeface.DEFAULT_BOLD);

        panel(c,1064,18,190,166,0xD9071724,0x6679DDEB,17);
        eyebrow(c,1080,40,"MINIMAPA");
        p.setColor(0xFF173A43);c.drawCircle(1159,105,59,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0xFF5DD6CE);c.drawCircle(1159,105,59,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2C775F);c.drawCircle(1159,105,43,p);
        float[] worldPosition=game.getPlayerPosition();
        float playerMapX=1159+Math.max(-48,Math.min(48,worldPosition[0]))*0.88f;
        float playerMapY=105+Math.max(-48,Math.min(48,worldPosition[1]))*0.88f;
        p.setColor(0xFF6ED5E2);c.drawCircle(playerMapX,playerMapY,6,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(Color.WHITE);c.drawCircle(playerMapX,playerMapY,8,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFC66F);c.drawCircle(1159+8*0.88f,105-9*0.88f,4,p);
        c.drawCircle(1159-10*0.88f,105-6*0.88f,4,p);
        c.drawCircle(1159+12*0.88f,105+10*0.88f,4,p);

        drawControl(c,890,310,38,game.isAimToggled()?"MIRA ON":"MIRA",
            0xFF285D79,0xFF8ADCF4,game.isAimToggled());
        drawControl(c,1002,310,43,"DISPARAR",0xFF185D7A,0xFF65E9F4,game.isFiring());
        drawControl(c,1165,403,43,"CONSTRUIR",0xFF66502A,0xFFFFD27A);
        drawControl(c,960,461,39,"CORRER",0xFF285D49,0xFF83E9B2);
        drawControl(c,1065,465,34,"ARMA",0xFF354F79,0xFF9DC7FF);
        drawControl(c,1152,562,47,"SALTAR",0xFF7B3D35,0xFFFFA28F);
        drawControl(c,960,605,39,"USAR",0xFF354F79,0xFF9DC7FF);

        String gameStatus=game.getGameStatus();
        if(!gameStatus.startsWith("EMBARQUE")&&!gameStatus.startsWith("AVIÃO")) {
            float cx=640,cy=360;
            if(game.isAimToggled()) {
                // Retículo com contorno escuro para continuar visível sobre qualquer cenário.
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5.0f);p.setColor(0xD9001019);
                c.drawCircle(cx,cy,13,p);
                p.setStrokeWidth(2.2f);p.setColor(0xFFF4FCFF);
                c.drawCircle(cx,cy,11,p);
                c.drawLine(cx-25,cy,cx-16,cy,p);c.drawLine(cx+16,cy,cx+25,cy,p);
                c.drawLine(cx,cy-25,cx,cy-16,p);c.drawLine(cx,cy+16,cx,cy+25,p);
                p.setStyle(Paint.Style.FILL);p.setColor(0xFF35E5F0);c.drawCircle(cx,cy,3.0f,p);
                text(c,"MIRA ATIVA  •  TOQUE PARA SOLTAR",537,397,9,0xFFBDFBFF,Typeface.DEFAULT_BOLD);
            } else {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.0f);p.setColor(0xD9001019);
                c.drawCircle(cx,cy,10,p);
                p.setStrokeWidth(1.8f);p.setColor(0xDDF1FFFF);
                c.drawCircle(cx,cy,9,p);
                c.drawLine(cx-21,cy,cx-11,cy,p);c.drawLine(cx+11,cy,cx+21,cy,p);
                c.drawLine(cx,cy-21,cx,cy-11,p);c.drawLine(cx,cy+11,cx,cy+21,p);
                p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFD27A);c.drawCircle(cx,cy,2.2f,p);
            }
        }

        p.setColor(0x553D7183);c.drawCircle(179,544,66,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(0xAA8CE8EF);c.drawCircle(179,544,66,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xAA8FCBD2);c.drawCircle(179,544,28,p);
        p.setColor(0xFFECFFFF);c.drawCircle(179,544,7,p);

        pill(c,24,625,160,49,"SAIR DA PARTIDA",0xDD173C4A,Color.WHITE);
        panel(c,420,650,440,40,0xBB071522,0x226A8EA0,14);
        text(c,"JOYSTICK: MOVER  •  MIRA: TOQUE FIXA/SOLTA  •  SEGURE DISPARAR",
            438,675,8,0xFFDAEAF0,Typeface.DEFAULT_BOLD);
        postInvalidateDelayed(120);
    }
    private void drawControl(Canvas c,float x,float y,float radius,String label,int fill,int edge){
        drawControl(c,x,y,radius,label,fill,edge,false);
    }
    private void drawControl(Canvas c,float x,float y,float radius,String label,int fill,int edge,boolean active){
        p.setColor(active?0xAA39DCEB:0x443ED9E8);c.drawCircle(x,y,radius+7,p);
        p.setColor(active?0xFF087F8D:fill);c.drawCircle(x,y,radius,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(active?3.0f:2.0f);
        p.setColor(active?0xFFFFFFFF:edge);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        p.setTextSize(label.length()>7?8:10);p.setColor(Color.WHITE);c.drawText(label,x,y+4,p);p.setTextAlign(Paint.Align.LEFT);
    }
    private void drawToast(Canvas c,float w,float h){
        float x=(w-370)/2f;p.setColor(0xEC102A38);c.drawRoundRect(x,h-86,x+370,h-34,16,16,p);
        text(c,toast,x+22,h-53,15,0xFF84E8DB,Typeface.DEFAULT_BOLD);
    }

    private void eyebrow(Canvas c,float x,float y,String s){text(c,s,x,y,10,0xFF60DCE6,Typeface.DEFAULT_BOLD);}
    private void title(Canvas c,String s,float x,float y){text(c,s,x,y,34,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));}
    private void subtitle(Canvas c,String s,float x,float y){text(c,s,x,y,13,0xFFB3C8D4,Typeface.DEFAULT);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface face){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTypeface(face);p.setTextSize(size);p.setColor(color);c.drawText(s,x,y,p);}
    private void stat(Canvas c,float x,float y,String v,String label){text(c,v,x,y,18,0xFF66E1E8,Typeface.DEFAULT_BOLD);text(c,label,x+31,y,8,0xFF9EB7C5,Typeface.DEFAULT_BOLD);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int stroke,float rad){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(fill);r.set(x,y,x+w,y+h);c.drawRoundRect(r,rad,rad,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(stroke);c.drawRoundRect(r,rad,rad,p);p.setStyle(Paint.Style.FILL);
    }
    private void pill(Canvas c,float x,float y,float w,float h,String s,int fill,int color){
        p.setShader(null);p.setColor(fill);r.set(x,y,x+w,y+h);c.drawRoundRect(r,h/2,h/2,p);
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(h<=26?8:9);p.setColor(color);c.drawText(s,x+(w-p.measureText(s))/2,y+h*.66f,p);
    }
    private void gradientButton(Canvas c,float x,float y,float w,float h,String label,String sub,int a,int b){
        p.setShader(new LinearGradient(x,y,x+w,y+h,a,b,Shader.TileMode.CLAMP));
        r.set(x,y,x+w,y+h);c.drawRoundRect(r,18,18,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x44FFFFFF);c.drawRoundRect(r,18,18,p);p.setStyle(Paint.Style.FILL);
        text(c,label,x+62,y+34,20,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,sub,x+62,y+55,9,0xFFFFF1E4,Typeface.DEFAULT_BOLD);
    }
    private void circle(Canvas c,float x,float y,float radius,int fill,int stroke){
        p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);p.setColor(stroke);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);
    }
    private void chevron(Canvas c,float x,float y){
        p.setColor(0xFF93C7D1);p.setStrokeWidth(2.5f);p.setStyle(Paint.Style.STROKE);
        c.drawLine(x-5,y-7,x+2,y,p);c.drawLine(x+2,y,x-5,y+7,p);p.setStyle(Paint.Style.FILL);
    }
    private float[] design(float x,float y){return new float[]{(x-offsetX)/scale,(y-offsetY)/scale};}
    private boolean hit(float x,float y,float rx,float ry,float rw,float rh){return x>=rx&&x<=rx+rw&&y>=ry&&y<=ry+rh;}

    @Override public boolean onTouchEvent(MotionEvent e){
        if(page==GAME){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                float[] q=design(e.getX(),e.getY());
                if(hit(q[0],q[1],24,625,160,49)){page=HOME;game.setGameScreen(false);invalidate();return true;}
                forwarding=true;
            }
            if(forwarding){
                game.onTouchEvent(e);
                if(e.getActionMasked()==MotionEvent.ACTION_CANCEL
                        ||(e.getActionMasked()==MotionEvent.ACTION_UP&&e.getPointerCount()==1))forwarding=false;
                return true;
            }
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP){float[] q=design(e.getX(),e.getY());tap(q[0],q[1]);invalidate();}
        return true;
    }
    private void tap(float x,float y){
        if(System.currentTimeMillis()-lastTap<110)return;lastTap=System.currentTimeMillis();
        if(hit(x,y,1000,17,246,60)){page=PROFILE;return;}
        for(int i=0;i<NAV.length;i++){float nx=27+i*136f;if(hit(x,y,nx,111,132,38)){page=i;return;}}
        if(page==HOME){
            if(hit(x,y,270,328,275,112)){page=MODES_PAGE;return;}
            if(hit(x,y,558,328,292,112)){page=MAPS_PAGE;return;}
            if(hit(x,y,270,468,342,78)){game.setCharacter(character);game.setMap(map);game.setMatchMode(mode);game.setGameScreen(true);page=GAME;return;}
        }else if(page==CHARACTERS){
            for(int i=0;i<4;i++)if(hit(x,y,270+i*146,295,132,210)){character=i;game.setCharacter(i);prefs.edit().putInt("character",i).apply();toast("Operador "+CHARACTER_NAMES[i]+" selecionado");return;}
        }else if(page==MODES_PAGE){
            for(int i=0;i<4;i++)if(hit(x,y,270+(i%2)*292,290+(i/2)*150,270,126)){mode=i;game.setMatchMode(i);prefs.edit().putInt("mode",i).apply();toast("Modo "+MODE_NAMES[i]+" selecionado");return;}
        }else if(page==MAPS_PAGE){
            for(int i=0;i<3;i++)if(hit(x,y,270+i*190,292,174,258)){map=i;game.setMap(i);prefs.edit().putInt("map",i).apply();toast("Mapa "+MAP_NAMES[i]+" selecionado");return;}
        }else if(page==INVENTORY){
            if(hit(x,y,270,408,570,148)){toast("Veja seus cosméticos e itens adquiridos.");return;}
        }else if(page==PROFILE){
            if(hit(x,y,270,486,570,58)){
                if(googleAccountAction!=null)googleAccountAction.run();
                else toast("Conta Google indisponível.");
                return;
            }
            if(hit(x,y,270,558,270,70)){showRedeemDialog();return;}
            if(hit(x,y,558,558,282,70)){showNicknameDialog();return;}
        }else if(page==SHOP){
            for(int i=0;i<3;i++)if(hit(x,y,270+i*192,338,174,230)){purchaseShopItem(i);return;}
        }else if(page==ROYALE){
            if(hit(x,y,294,556,220,30)){claimDailyReward();return;}
        }else if(page==SETTINGS){
            if(hit(x,y,515,306,300,60)){quality=x<618?0:x<714?1:2;game.setQuality(quality);prefs.edit().putInt("quality",quality).apply();toast("Qualidade: "+QUALITY_NAMES[quality]);return;}
            if(hit(x,y,715,414,110,52)){music=!music;prefs.edit().putBoolean("music",music).apply();toast(music?"Música ativada":"Música desativada");return;}
            if(hit(x,y,715,495,110,52)){effects=!effects;prefs.edit().putBoolean("effects",effects).apply();toast(effects?"Efeitos ativados":"Efeitos desativados");return;}
            if(hit(x,y,515,580,300,55)){lookSensitivity=x<618?0:x<714?1:2;game.setLookSensitivity(lookSensitivity);prefs.edit().putInt("look_sensitivity",lookSensitivity).apply();toast("Sensibilidade: "+new String[]{"baixa","média","alta"}[lookSensitivity]);}
        }
    }
    public void setGoogleAccountAction(Runnable action) {
        googleAccountAction=action;
    }
    public void setGoogleAccountStatus(boolean configured, boolean signedIn,
                                       String email, String message) {
        googleConfigured=configured;
        googleSignedIn=signedIn;
        googleEmail=email==null?"":email;
        googleMessage=message==null?"":message;
        invalidate();
    }
    private void toast(String s){toast=s;toastUntil=System.currentTimeMillis()+1800;invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
}
