package com.battleuniverse.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.Locale;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.HapticFeedbackConstants;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HudOverlay extends View {
    private static final int HOME=0, CHARACTERS=1, MODES_PAGE=2, MAPS_PAGE=3,
        INVENTORY=4, SETTINGS=5, PROFILE=6, SHOP=7, ROYALE=8, MISSIONS=9, WARDROBE=10,
        GAME=11, ROYALE_SPIN=12, EVENTS=13, FRIENDS=14, MAIL=15, ACHIEVEMENTS=16, PREMATCH=17;
    private static final String[] NAV={"INÍCIO","PERSONAGENS","MODOS","MAPAS",
        "INVENTÁRIO","AJUSTES","PERFIL","LOJA","ROYALE","MISSÕES","ROUPAS",
        "EVENTOS","EQUIPE","CORREIO","CONQUISTAS"};
    private static final int[] NAV_PAGES={HOME,CHARACTERS,MODES_PAGE,MAPS_PAGE,INVENTORY,SETTINGS,PROFILE,SHOP,ROYALE,MISSIONS,WARDROBE,EVENTS,FRIENDS,MAIL,ACHIEVEMENTS};
    private static final String[] SHOP_NAMES={"MOLDURA NEON","EMBLEMA BLOOB","TÍTULO CAMPEÃO",
        "CAMISA TÁTICA","CAMISA NEON","CAMISA FROST","CAMISA DESERTO",
        "BOTAS COMBATE","BOTAS NEON","BOTAS FROST","BOTAS DOURADAS",
        "CONJUNTO AURORA","CONJUNTO CAMPEÃO","CONJUNTO ORBITAL","CONJUNTO DESERTO"};
    private static final String[] SHOP_KEYS={"shop_frame_neon","shop_emblem_bloob","shop_title_champion",
        "wear_shirt_tactical","wear_shirt_neon","wear_shirt_frost","wear_shirt_desert",
        "wear_boots_combat","wear_boots_neon","wear_boots_frost","wear_boots_gold",
        "wear_set_aurora","wear_set_champion","wear_set_orbit","wear_set_desert"};
    private static final long[] SHOP_PRICES={500L,1000L,1500L,650L,800L,750L,700L,
        450L,600L,550L,700L,1800L,2200L,2500L,1900L};
    private static final int[] SHOP_COLORS={0xFF8D42D7,0xFF08A9D8,0xFFE0A62B,
        0xFF344C5C,0xFF8D42D7,0xFFB8DDE8,0xFFB87843,
        0xFF4B5058,0xFF2FAFC4,0xFFCBDCE4,0xFFE0AE38,
        0xFF55B7B8,0xFFE0AE38,0xFF6B83C4,0xFFB87843};
    private static final int[] ROYALE_LEVELS={2,4,6,8,10,12};
    private static final long[] ROYALE_REWARDS={40,60,80,100,120,150};
    private static final String[] MISSION_NAMES={"JOGUE UMA PARTIDA","ELIMINE 3 OPONENTES","COLETE 3 CRISTAIS"};
    private static final String[] MISSION_HINTS={"Entre no campo e saia após o pouso.",
        "Some eliminações durante as partidas.","Recolha cristais espalhados pelo mapa."};
    private static final int[] MISSION_GOALS={1,3,3};
    private static final long[] MISSION_XP={120,180,0};
    private static final long[] MISSION_DIAMONDS={0,0,80};
    private static final String[] MODE_NAMES={"BATTLE ROYALE","DUPLA","ESQUADRÃO","TREINAMENTO"};
    private static final String[] MAP_NAMES={"ILHA TROPICAL","NEON CITY","DESERT STORM","FROZEN FRONT","INDUSTRIAL ZONE","GREEN VALLEY","ORBITAL BASE","ABANDONED SECTOR"};
    private static final String[] MAP_DETAILS={"Praias • florestas • vilas","Arranha-céus • neon • avenidas","Dunas • cânions • ruínas","Geleiras • neve • bases polares","Fábricas • depósitos • chaminés","Campos • rios • florestas","Instalações • plataformas • tecnologia","Ruínas • depósitos • estruturas antigas"};
    private static final String[] CHARACTER_NAMES={"CAIO","MAYA","DANTE","LUNA"};
    private static final String[] QUALITY_NAMES={"ECONÔMICO","EQUILIBRADO","ALTO"};
    private final GameSurfaceView game;
    private final SharedPreferences prefs;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r=new RectF();
    private int page=HOME, mode, map, character, quality=1, lookSensitivity=1;
    private int selectedShirt,selectedBoots,selectedSet;
    private boolean music=true, effects=true, forwarding, resultSpectating;
    private boolean invertCameraY, showMinimap=true, hapticsEnabled=true;
    private boolean onlineMode, tacticalMapVisible;
    private float scale=1, offsetX, offsetY;
    private String toast="";
    public interface UsernameAccountAction {
        void authenticate(String username, String password, boolean createAccount);
    }
    public interface AiCoachAction { void ask(String question, AccountSyncManager.CoachListener callback); }
    public interface GameApiAction { void run(String value, AccountSyncManager.ApiActionListener callback); }
    private boolean googleConfigured, googleSignedIn;
    private String googleEmail="", googleMessage="Crie uma conta ou entre com nome e senha.";
    private UsernameAccountAction usernameAccountAction;
    private AiCoachAction aiCoachAction;
    private GameApiAction promoRedeemAction, dailyRewardAction, mailFetchAction, mailClaimAction, shopPurchaseAction, matchResultAction, rewardClaimAction, royaleSpinAction;
    private JSONArray mailMessages=new JSONArray();
    private boolean mailFetchStarted, activeMailClaimed;
    private String activeMailId="welcome_gift_01", activeMailTitle="BOAS-VINDAS AO BATTLE UNIVERSE", activeMailBody="Presente de entrada: 50 diamantes virtuais.";
    private long activeMailDiamonds=50L;
    private Runnable accountSyncAction, accountSignOutAction, packAnalyzerAction;
    private long toastUntil, lastTap, lastRoyaleSpinAt;
    private boolean shopPurchasePending, matchRewardPending, royaleSpinPending;
    private long matchRewardRetryAfter;
    private String activeMatchRequestId="";

    public HudOverlay(Context context, GameSurfaceView game, SharedPreferences prefs) {
        super(context);
        this.game=game; this.prefs=prefs;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setClickable(true); setFocusable(true);
        mode=prefs.getInt("mode",0); map=prefs.getInt("map",0);
        character=prefs.getInt("character",0); quality=prefs.getInt("quality",1);
        onlineMode=prefs.getBoolean("online_mode",false);
        map=Math.max(0,Math.min(MAP_NAMES.length-1,map));
        music=prefs.getBoolean("music",true); effects=prefs.getBoolean("effects",true);
        lookSensitivity=prefs.getInt("look_sensitivity",1);
        invertCameraY=prefs.getBoolean("invert_camera_y",false);
        showMinimap=prefs.getBoolean("show_minimap",true);
        hapticsEnabled=prefs.getBoolean("haptics",true);
        ensureStarterWardrobe();
        selectedShirt=prefs.getInt("selected_shirt",1);
        selectedBoots=prefs.getInt("selected_boots",1);
        selectedSet=prefs.getInt("selected_set",0);
        ensureDailyMissionWindow();
        game.setInvertCameraY(invertCameraY);
        game.setCharacter(character); game.setMap(map); game.setWardrobe(selectedShirt,selectedBoots,selectedSet);
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
        drawLobbyBackdrop(c);
        p.setColor(0xB9071421);c.drawRect(0,0,1280,96,p);
        p.setColor(0x5579DDEB);c.drawRect(0,94,1280,96,p);
        drawBrand(c);drawProfile(c);drawSidebar(c);drawPage(c);drawTeam(c);
    }
    private void drawLobbyBackdrop(Canvas c) {
        p.setShader(new LinearGradient(0,0,1280,720,
            new int[]{0x88071521,0x88102A3B,0x880A1825},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,1280,720,p);p.setShader(null);
        p.setColor(0x88102D3A);c.drawOval(680,300,1360,920,p);
        p.setColor(0x77123A45);c.drawOval(820,390,1410,850,p);
        p.setColor(0x66173D46);c.drawOval(920,450,1370,800,p);
        p.setColor(0x4429BFD0);c.drawCircle(1070,250,145,p);
        p.setColor(0x2227B7D2);c.drawCircle(1070,250,190,p);
        Path ridge=new Path();ridge.moveTo(0,520);ridge.lineTo(190,430);ridge.lineTo(330,488);
        ridge.lineTo(510,382);ridge.lineTo(690,470);ridge.lineTo(880,398);
        ridge.lineTo(1040,468);ridge.lineTo(1280,350);ridge.lineTo(1280,720);
        ridge.lineTo(0,720);ridge.close();p.setColor(0x990B2430);c.drawPath(ridge,p);
        p.setColor(0x4439D7E8);p.setStrokeWidth(1.2f);
        for(int i=0;i<7;i++)c.drawLine(0,570+i*22,1280,500+i*22,p);
        for(int i=0;i<34;i++) {
            float x=(i*173)%1280,y=170+(i*97)%380;
            p.setColor(i%3==0?0xAA70DDE6:0x557CA7B9);c.drawCircle(x,y,i%4==0?1.8f:1.0f,p);
        }
        p.setColor(0x3322D7E8);c.drawRect(0,96,1280,98,p);
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
        text(c,rankForXp(xp)+"  •  NÍVEL "+String.format(Locale.US,"%02d",level),1060,60,9,0xFF9BB6C6,Typeface.DEFAULT);
        text(c,"◆ "+formatDiamonds(diamonds),1176,51,11,0xFFFFC76B,Typeface.DEFAULT_BOLD);
    }
    private String formatDiamonds(long value) {
        return NumberFormat.getIntegerInstance(new Locale("pt","BR")).format(value);
    }
    private void drawSidebar(Canvas c) {
        // Navegação vertical de lobby mobile: libera o topo e mantém as telas dentro do jogo.
        panel(c,18,104,214,562,0xD90A1B2B,0x446CA4B7,18);
        text(c,"CENTRAL",38,130,9,0xFF79DCE7,Typeface.create("sans-serif-black",Typeface.BOLD));
        for(int i=0;i<NAV.length;i++) {
            float y=143+i*33.2f;boolean active=page==NAV_PAGES[i];boolean royale=NAV_PAGES[i]==ROYALE;
            panel(c,27,y,196,29,active?(royale?0xFFE1A849:0xDD136276):(royale?0x664B3517:0x22243B4D),
                    active?(royale?0xFFFFD98A:0xFF35D6E5):(royale?0x99E6B85D:0x225A8297),10);
            drawNavIcon(c,i,48,y+14.5f,active||royale);
            text(c,NAV[i],67,y+19.5f,royale?8.2f:7.7f,active||royale?Color.WHITE:0xFFB8CBD7,
                Typeface.create(active||royale?"sans-serif-black":"sans-serif-medium",Typeface.NORMAL));
        }
        text(c,"BATTLE ROYALE",38,655,8,0xFF7895A6,Typeface.DEFAULT_BOLD);
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
        else if(page==ROYALE_SPIN)drawRoyaleSpinPage(c);
        else if(page==MISSIONS)drawMissionsPage(c);
        else if(page==WARDROBE)drawWardrobePage(c);
        else if(page==EVENTS)drawEventsPage(c);
        else if(page==FRIENDS)drawFriendsPage(c);
        else if(page==MAIL)drawMailPage(c);
        else if(page==ACHIEVEMENTS)drawAchievementsPage(c);
        else if(page==PREMATCH)drawPreMatch(c);
    }
    private void drawHome(Canvas c){
        eyebrow(c,272,174,"BATTLE UNIVERSE BR 1.4.0  /  SEASON 03");
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
        text(c,MAP_DETAILS[map],578,417,11,0xFF9DB6C5,Typeface.DEFAULT);
        chevron(c,817,385);
        pill(c,270,442,164,24,onlineMode?"OFFLINE":"OFFLINE SELECIONADO",
            onlineMode?0x553B6172:0xFF176A75,Color.WHITE);
        pill(c,442,442,200,24,onlineMode?"ONLINE • SERVIDOR PENDENTE":"ONLINE • INDISPONÍVEL",
            onlineMode?0xFF7A4D25:0x553B6172,Color.WHITE);
        gradientButton(c,270,472,342,78,"JOGAR AGORA","ENTRAR NA PARTIDA",0xFFFFB45E,0xFFE97543);
        text(c,"▶",292,520,21,0xFFFFF4E7,Typeface.DEFAULT_BOLD);
        panel(c,628,472,222,78,0xB9183040,0x557AA9BC,17);
        text(c,"◆ PASSE DE TEMPORADA",650,498,11,0xFF76E7CF,Typeface.DEFAULT_BOLD);
        text(c,"MENU ROYALE",650,522,15,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,onlineMode?"ONLINE AINDA NÃO DISPONÍVEL • SERVIDOR MULTIPLAYER PENDENTE":
            "30 participantes offline • 1 jogador + 29 bots IA.",274,579,10,0xFF9CB5C2,Typeface.DEFAULT);
        panel(c,270,608,580,58,0xB9183040,0xFF4CD6E2,14);
        text(c,"✦ IA PROFESSORA • BATTLE UNIVERSE",292,635,15,0xFF70E8E8,Typeface.DEFAULT_BOLD);
        text(c,"Toque para perguntar como jogar, melhorar e sobreviver.",292,653,10,0xFFB5CBD5,Typeface.DEFAULT);
    }
    private void drawCharacters(Canvas c){
        eyebrow(c,270,175,"PERSONALIZAÇÃO");title(c,"SUA EQUIPE",270,224);
        subtitle(c,"Escolha entre quatro operadores masculinos e femininos. A seleção acompanha a partida.",270,252);
        String[] roles={"MASCULINO","FEMININO","MASCULINO","FEMININO"};
        int[] colors={0xFF08A9D8,0xFF344C5C,0xFF8D42D7,0xFFE0A62B};
        for(int i=0;i<4;i++){
            float x=270+i*146;boolean active=character==i;
            panel(c,x,295,132,210,active?0xD51B5B69:0xA51A3040,active?0xFF48D7E1:0x335D8495,16);
            circle(c,x+66,365,36,colors[i],active?0xFF68E9EF:0xFF8AA5B4);
            text(c,new String[]{"C","M","D","L"}[i],x+59,372,19,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,CHARACTER_NAMES[i],x+18,432,14,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,roles[i],x+14,453,9,0xFF9DB6C5,Typeface.DEFAULT);
            pill(c,x+(active?27:35),470,active?78:62,23,active?"SELECIONADO":"ESCOLHER",
                    active?0xFF176A75:0x553B6172,active?Color.WHITE:0xFFBBD0D9);
        }
        panel(c,270,535,570,92,0xA6172C3C,0x335D8495,17);
        eyebrow(c,292,563,"VISUALIZAÇÃO 3D");
        text(c,"Operador selecionado no cenário 3D e na partida local.",292,590,15,Color.WHITE,Typeface.DEFAULT_BOLD);
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
    private void drawPreMatch(Canvas c){
        eyebrow(c,270,175,"PREPARAÇÃO LOCAL • BATTLE ROYALE");
        title(c,"SALA PRÉ-PARTIDA",270,224);
        subtitle(c,"Confira o operador, o mapa e a composição antes de iniciar.",270,252);
        panel(c,270,270,570,350,0xB8193040,0x557AA9BC,18);
        eyebrow(c,292,300,"PARTICIPANTES DA PARTIDA OFFLINE");
        panel(c,292,316,526,48,0xAA123D4A,0x6654DCE5,12);
        text(c,"01  VOCÊ  •  "+CHARACTER_NAMES[character],308,346,13,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"29 VAGAS DE BOT IA  •  CARREGADAS AO INICIAR",292,390,11,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        for(int i=0;i<29;i++){
            int col=i%6,row=i/6;
            float x=292+col*88f,y=405+row*35f;
            panel(c,x,y,80,28,0x66304455,0x334F7B8D,7);
            text(c,"BOT IA "+String.format(Locale.US,"%02d",i+1),x+7,y+18,8.2f,0xFFB9CBD5,Typeface.DEFAULT_BOLD);
        }
        panel(c,292,596,526,1,0x557AA9BC,0x00000000,0);
        text(c,"MAPA: "+MAP_NAMES[map]+"   •   MODO: "+MODE_NAMES[mode],292,581,10,0xFF72DCE6,Typeface.DEFAULT_BOLD);
        panel(c,862,270,350,350,0xB8193040,0x557AA9BC,18);
        eyebrow(c,886,302,"OPERADOR E EQUIPAMENTO");
        circle(c,1037,390,62,0xFF1C5968,0xFF57D8E5);
        text(c,new String[]{"C","M","D","L"}[character],1019,405,42,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,CHARACTER_NAMES[character],886,490,20,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Camisa, botas e conjunto equipados",886,516,10,0xFF9DB6C5,Typeface.DEFAULT);
        text(c,"Partida offline • bots identificados como IA",886,546,10,0xFFFFCF83,Typeface.DEFAULT_BOLD);
        pill(c,886,572,144,25,"30 PARTICIPANTES",0x553A6B7C,0xFFB9E8EF);
        pill(c,1038,572,150,25,"SERVIDOR: LOCAL",0x553A6B7C,0xFFB9E8EF);
        pill(c,270,636,170,34,"VOLTAR AO LOBBY",0x553B6172,0xFFBBD0D9);
        gradientButton(c,862,636,350,34,"INICIAR PARTIDA","CARREGAR AVIÃO E MAPA",0xFFFFB45E,0xFFE97543);
    }
    private void beginOfflineMatch(){
        ensureDailyMissionWindow();
        activeMatchRequestId=java.util.UUID.randomUUID().toString();
        matchRewardPending=false;matchRewardRetryAfter=0L;
        prefs.edit().putString("active_match_request_id",activeMatchRequestId)
            .putBoolean("match_reward_recorded",false)
            .putBoolean("match_reward_attempted",false).apply();
        resultSpectating=false;game.setSpectatorMode(false);
        game.setCharacter(character);game.setMap(map);game.setMatchMode(mode);
        game.setWardrobe(selectedShirt,selectedBoots,selectedSet);
        game.setGameScreen(true);page=GAME;
    }
    private void drawMaps(Canvas c){
        eyebrow(c,270,175,"8 DESTINOS • MAPAS SELECIONÁVEIS");title(c,"ESCOLHA O MAPA",270,224);
        subtitle(c,"Cada arena usa uma identidade visual própria na partida.",270,252);
        int[] colors={0xFF1B7B72,0xFF554C91,0xFFB77B43,0xFF8CB8CE,0xFF59666C,0xFF43875A,0xFF344B70,0xFF62564A};
        for(int i=0;i<MAP_NAMES.length;i++){
            int col=i%4,row=i/4;float x=270+col*145f,y=282+row*178f;
            boolean active=map==i;
            panel(c,x,y,132,164,active?0xDB1B5B69:0xB0193040,active?0xFF48D7E1:0x335D8495,15);
            panel(c,x+8,y+9,116,76,colors[i],0x66FFFFFF,10);
            mapArtwork(c,i,x+8,y+9,116,76);
            text(c,MAP_NAMES[i],x+9,y+108,9.2f,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,MAP_DETAILS[i],x+9,y+126,7.1f,0xFFABC0CC,Typeface.DEFAULT);
            pill(c,x+9,y+137,active?105:92,20,active?"SELECIONADO":"SELECIONAR",
                    active?0xFF176A75:0x553B6172,active?Color.WHITE:0xFFBBD0D9);
        }
    }
    private void drawInventory(Canvas c){
        eyebrow(c,270,175,"COLEÇÃO DO OPERADOR");title(c,"INVENTÁRIO",270,224);
        subtitle(c,"Camisas, botas, conjuntos e itens de perfil já adquiridos.",270,252);
        panel(c,270,270,570,58,0xAE193040,0x335D8495,15);
        text(c,"◆ "+formatDiamonds(prefs.getLong("diamonds",0L))+" DIAMANTES",292,306,17,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        for(int i=0;i<SHOP_NAMES.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=340+row*57f;
            boolean owned=prefs.getBoolean(SHOP_KEYS[i],false);
            panel(c,x,y,174,50,owned?0xB51A4650:0x771A3040,
                owned?0xFF4BD4C9:0x335D8495,12);
            circle(c,x+24,y+24,14,SHOP_COLORS[i],0xFFB5D6E1);
            text(c,SHOP_NAMES[i],x+45,y+21,8,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,owned?"DESBLOQUEADO":"BLOQUEADO",x+45,y+37,7,
                owned?0xFF83DCD5:0xFF91A9B7,Typeface.DEFAULT_BOLD);
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
        eyebrow(c,270,175,googleSignedIn?"CONTA BATTLE UNIVERSE VINCULADA":"PERFIL E CONTA BATTLE UNIVERSE");
        title(c,"PERFIL DO JOGADOR",270,224);
        subtitle(c,"Crie uma conta com nome e senha. Não precisa entrar com Google.",270,252);
        long xp=prefs.getLong("xp",0L),diamonds=prefs.getLong("diamonds",0L);
        int level=(int)Math.min(30L,1L+xp/500L);
        panel(c,270,286,570,196,0xAE193040,0x557AA9BC,18);
        circle(c,326,365,38,0xFF1F7584,
            prefs.getBoolean("shop_frame_neon",false)?0xFFB978FF:0xFF65DCE7);
        text(c,prefs.getString("nickname","JOGADOR"),382,346,23,Color.WHITE,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"PATENTE "+rankForXp(xp)+"  •  NÍVEL "+level,382,374,13,0xFF9FC0CF,Typeface.DEFAULT_BOLD);
        text(c,"◆ "+formatDiamonds(diamonds)+" DIAMANTES",382,411,17,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        text(c,prefs.getBoolean("code_100k_redeemed",false)
            ?"CÓDIGO 100K JÁ RESGATADO NESTE PERFIL"
            :"CÓDIGO SECRETO 100K DISPONÍVEL",292,458,10,0xFF83DCD5,Typeface.DEFAULT_BOLD);
        panel(c,270,486,570,58,googleSignedIn?0xCC176A75:0xA91A3040,
            googleSignedIn?0xFF58DDE6:0x335D8495,15);
        text(c,googleSignedIn?"CONTA VINCULADA • TOQUE PARA SINCRONIZAR":
            (googleConfigured?"CRIAR CONTA / ENTRAR":"SERVIDOR DE CONTAS NÃO CONFIGURADO"),
            292,509,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        String accountLine=googleSignedIn&&!googleEmail.isEmpty()?"@"+googleEmail:googleMessage;
        if(accountLine.length()>68)accountLine=accountLine.substring(0,65)+"...";
        text(c,accountLine,292,530,9,0xFFBBD0D9,Typeface.DEFAULT);
        panel(c,270,558,270,70,0xCC176A75,0xFF58DDE6,16);
        text(c,"RESGATAR CÓDIGO",294,586,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Digite seu código secreto",294,607,10,0xFFD2F6F8,Typeface.DEFAULT);
        panel(c,558,558,282,70,0xA91A3040,0x335D8495,16);
        text(c,"EDITAR NICKNAME",582,586,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Nome do seu perfil",582,607,10,0xFF9EB6C4,Typeface.DEFAULT);
        panel(c,270,642,570,42,0xA91A3040,0xFF58DDE6,12);
        text(c,"ANALISAR / REUTILIZAR PACOTE ZIP OU BUPACK",292,669,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Use a mesma conta em APKs futuros para recuperar o progresso.",272,704,9,0xFF8FAAB8,Typeface.DEFAULT);
        if(googleSignedIn)pill(c,690,632,150,25,"SAIR DA CONTA",0x553B6172,0xFFBBD0D9);
    }
    private void drawShopPage(Canvas c) {
        eyebrow(c,270,175,"LOJA • PREÇOS FIXOS EM DIAMANTES VIRTUAIS");
        title(c,"ROUPAS E COSMÉTICOS",270,224);
        subtitle(c,"Camisas, botas e conjuntos completos. Cada item mostra seu preço antes da compra.",270,252);
        panel(c,270,266,570,48,0xA91A3040,0x335D8495,14);
        text(c,"SALDO  ◆ "+formatDiamonds(prefs.getLong("diamonds",0L)),292,297,16,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        for(int i=0;i<SHOP_NAMES.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=322+row*56f;
            boolean owned=prefs.getBoolean(SHOP_KEYS[i],false);
            panel(c,x,y,174,50,owned?0xB51A4650:0xA91A3040,
                owned?0xFF4BD4C9:0x335D8495,12);
            circle(c,x+24,y+24,14,SHOP_COLORS[i],0xFFB5D6E1);
            text(c,SHOP_NAMES[i],x+44,y+20,8,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,owned?"JÁ ADQUIRIDO":"◆ "+SHOP_PRICES[i]+" DIAMANTES",x+44,y+36,7,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        }
        text(c,"Itens cosméticos diretos • sem roleta paga ou prêmio aleatório.",272,664,9,0xFF8FAAB8,Typeface.DEFAULT);
    }
    private void drawWardrobePage(Canvas c) {
        eyebrow(c,270,175,"PERSONALIZAÇÃO EQUIPÁVEL");title(c,"CAMISAS, BOTAS E CONJUNTOS",270,224);
        subtitle(c,"Toque em um item desbloqueado para vestir o operador 3D.",270,252);
        pill(c,690,260,150,25,"REMOVER CONJUNTO",0x553B6172,0xFFD7E6ED);
        text(c,"CAMISAS",270,300,10,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        String[] shirts={"TÁTICA","NEON","FROST","DESERTO"};
        for(int i=0;i<4;i++) {
            float x=270+i*146f;boolean owned=prefs.getBoolean(SHOP_KEYS[3+i],false);
            boolean active=selectedShirt==i+1&&selectedSet==0;
            panel(c,x,312,132,72,active?0xB51A5B69:0xA91A3040,
                active?0xFF48D7E1:0x335D8495,12);
            circle(c,x+23,338,13,SHOP_COLORS[3+i],0xFFB5D6E1);
            text(c,shirts[i],x+43,338,8,Color.WHITE,Typeface.DEFAULT_BOLD);
            pill(c,x+12,350,108,24,active?"EQUIPADA":owned?"EQUIPAR":"BLOQUEADA",
                active?0xFF176A75:owned?0xFF345D79:0x553B6172,Color.WHITE);
        }
        text(c,"BOTAS",270,420,10,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        String[] boots={"COMBATE","NEON","FROST","DOURADAS"};
        for(int i=0;i<4;i++) {
            float x=270+i*146f;boolean owned=prefs.getBoolean(SHOP_KEYS[7+i],false);
            boolean active=selectedBoots==i+1&&selectedSet==0;
            panel(c,x,432,132,72,active?0xB51A5B69:0xA91A3040,
                active?0xFF48D7E1:0x335D8495,12);
            circle(c,x+23,458,13,SHOP_COLORS[7+i],0xFFB5D6E1);
            text(c,boots[i],x+43,458,8,Color.WHITE,Typeface.DEFAULT_BOLD);
            pill(c,x+12,470,108,24,active?"EQUIPADAS":owned?"EQUIPAR":"BLOQUEADAS",
                active?0xFF176A75:owned?0xFF345D79:0x553B6172,Color.WHITE);
        }
        text(c,"CONJUNTOS COMPLETOS",270,540,10,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        String[] sets={"AURORA","CAMPEÃO","ORBITAL","DESERTO"};
        for(int i=0;i<4;i++) {
            float x=270+i*146f;boolean owned=prefs.getBoolean(SHOP_KEYS[11+i],false);
            boolean active=selectedSet==i+1;
            panel(c,x,552,132,72,active?0xB51A5B69:0xA91A3040,
                active?0xFF48D7E1:0x335D8495,12);
            circle(c,x+23,578,13,SHOP_COLORS[11+i],0xFFB5D6E1);
            text(c,sets[i],x+43,578,8,Color.WHITE,Typeface.DEFAULT_BOLD);
            pill(c,x+12,590,108,24,active?"EQUIPADO":owned?"VESTIR":"BLOQUEADO",
                active?0xFF176A75:owned?0xFF345D79:0x553B6172,Color.WHITE);
        }
        text(c,"KIT INICIAL LIBERADO: CAMISA TÁTICA + BOTAS DE COMBATE.",270,654,9,0xFF82E5DC,Typeface.DEFAULT_BOLD);
    }

    private void drawRoyalePage(Canvas c) {
        eyebrow(c,270,175,"TEMPORADA 02 • PROGRESSÃO DA CONTA");
        title(c,"MENU ROYALE",270,224);
        pill(c,674,184,166,32,"GIRO ROYALE",0xFF5A4321,0xFFFFD27A);
        subtitle(c,"Passe de temporada e giro de recompensas com probabilidades visíveis.",270,252);
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
        text(c,"XP, missões e recompensas validados pela conta online.",272,666,9,0xFF8FAAB8,Typeface.DEFAULT);
    }

    private void drawRoyaleSpinPage(Canvas c) {
        eyebrow(c,270,175,"RECOMPENSAS ALEATÓRIAS • SOMENTE DIAMANTES VIRTUAIS");
        title(c,"GIRO ROYALE",270,224);
        pill(c,674,184,166,32,"VOLTAR AO PASSE",0x553B6172,0xFFD7E6ED);
        panel(c,270,264,570,48,0xA91A3040,0x557AA9BC,14);
        text(c,"CUSTO  ◆ 100 DIAMANTES",292,294,14,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        text(c,"SALDO  ◆ "+formatDiamonds(prefs.getLong("diamonds",0L)),590,294,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        String[] prizes={"CAMISA TÁTICA","BOTAS COMBATE","CONJUNTO AURORA","BARBINHA","CALÇA ANGELICAL"};
        String[] chances={"40%","30%","20%","9%","1%"};
        int[] colors={0xFF344C5C,0xFF4B5058,0xFF55B7B8,0xFFB87843,0xFF6B83C4};
        for(int i=0;i<prizes.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=326+row*94f;
            panel(c,x,y,174,82,0xB51A3040,0x557AA9BC,13);
            circle(c,x+25,y+28,15,colors[i],0xFFB5D6E1);
            text(c,prizes[i],x+48,y+27,8.5f,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,"CHANCE DE GANHAR",x+14,y+54,7,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
            text(c,chances[i],x+14,y+72,13,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        }
        panel(c,270,520,570,126,0xA91A3040,0x557AA9BC,16);
        gradientButton(c,288,546,220,68,"GIRAR • 100 ◆","USAR DIAMANTES VIRTUAIS",0xFF9A6527,0xFFE1A848);
        String last=prefs.getString("royale_last_reward","NENHUM GIRO REALIZADO");
        if(last.length()>38)last=last.substring(0,35)+"...";
        text(c,"ÚLTIMO PRÊMIO",530,559,9,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        text(c,last,530,587,13,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"GIROS REGISTRADOS: "+prefs.getInt("royale_spin_count",0),530,612,9,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        text(c,"SORTEIO ONLINE • RESULTADO, SALDO E INVENTÁRIO CONFIRMADOS PELO SERVIDOR.",272,668,9,0xFF8FAAB8,Typeface.DEFAULT);
    }

    private void performRoyaleSpin() {
        if(!googleSignedIn){toast("Entre na conta para usar o Giro Royale online.");page=PROFILE;return;}
        if(royaleSpinPending)return;
        if(royaleSpinAction==null){toast("Giro Royale online indisponível.");return;}
        long now=System.currentTimeMillis();
        if(now-lastRoyaleSpinAt<2200L){toast("Aguarde antes de girar novamente.");return;}
        lastRoyaleSpinAt=now;royaleSpinPending=true;
        String requestId=java.util.UUID.randomUUID().toString();
        toast("Validando giro e saldo no servidor...");
        royaleSpinAction.run(requestId,(result,error)->{
            royaleSpinPending=false;
            if(error!=null){toast("Giro não concluído: "+error);postInvalidate();return;}
            if(result==null||!result.has("prize_id")){toast("O servidor não confirmou o prêmio.");postInvalidate();return;}
            JSONObjectWalletUpdate(new JSONObject().put("wallet",new JSONObject().put("diamonds",result.optLong("diamonds_balance",prefs.getLong("diamonds",0L)))));
            String prize=result.optString("prize_name","PRÊMIO");
            String message=result.optBoolean("duplicate")?prize+" • ITEM REPETIDO • COMPENSAÇÃO +"+result.optLong("duplicate_compensation",0L)+" ◆":prize;
            prefs.edit().putString("royale_last_reward",message)
                .putInt("royale_spin_count",prefs.getInt("royale_spin_count",0)+(result.optBoolean("replayed")?0:1)).apply();
            toast("🟢 ROYALE: "+message);postInvalidate();
        });
    }

    private void ensureStarterWardrobe() {
        if(prefs.getBoolean("wardrobe_starter_v2_granted",false)) return;
        SharedPreferences.Editor editor=prefs.edit()
            .putBoolean(SHOP_KEYS[3],true)
            .putBoolean(SHOP_KEYS[7],true);
        if(prefs.getInt("selected_shirt",0)==0) editor.putInt("selected_shirt",1);
        if(prefs.getInt("selected_boots",0)==0) editor.putInt("selected_boots",1);
        editor.putBoolean("wardrobe_starter_v2_granted",true).apply();
    }

    private void ensureDailyMissionWindow() {
        long day=System.currentTimeMillis()/86400000L;
        if(prefs.getLong("missions_day",-1L)!=day) {
            prefs.edit().putLong("missions_day",day)
                .putInt("mission_matches",0).putInt("mission_eliminations",0)
                .putInt("mission_crystals",0)
                .putBoolean("mission_claimed_0",false)
                .putBoolean("mission_claimed_1",false)
                .putBoolean("mission_claimed_2",false).apply();
        }
    }
    private int missionProgress(int index) {
        if(index==0)return prefs.getInt("mission_matches",0);
        if(index==1)return prefs.getInt("mission_eliminations",0);
        if(index==2)return prefs.getInt("mission_crystals",0);
        return 0;
    }
    private boolean missionClaimed(int index) {
        return prefs.getBoolean("mission_claimed_"+index,false);
    }
    private boolean isMatchOver() {
        String status=game.getGameStatus();
        return status.contains("VITÓRIA")||status.contains("ELIMINADO");
    }
    private static final String[] RANK_NAMES={"BRONZE I","BRONZE II","BRONZE III",
        "PRATA I","PRATA II","OURO I","OURO II","PLATINA","DIAMANTE","MESTRE"};
    private static final long[] RANK_XP={0L,500L,1200L,2200L,3500L,5200L,7500L,10500L,14500L,20000L};
    private String rankForXp(long xp) {
        int rank=0;
        for(int i=1;i<RANK_XP.length;i++)if(xp>=RANK_XP[i])rank=i;
        return RANK_NAMES[rank];
    }
    private void returnToLobbyAfterMatch() {
        forwarding=false;
        tacticalMapVisible=false;
        resultSpectating=false;
        page=HOME;
        game.returnToLobby();
        postInvalidateOnAnimation();
    }
    private void applyMatchRewards(boolean victory) {
        if(prefs.getBoolean("match_reward_recorded",false)||matchRewardPending)return;
        long now=System.currentTimeMillis();
        if(now<matchRewardRetryAfter)return;
        if(!googleSignedIn||matchResultAction==null) {
            if(!prefs.getBoolean("match_reward_attempted",false)) {
                prefs.edit().putBoolean("match_reward_attempted",true).apply();
                toast("Partida concluída. Entre na conta para receber recompensas sincronizadas.");
            }
            return;
        }
        if(activeMatchRequestId.isEmpty())activeMatchRequestId=prefs.getString("active_match_request_id","");
        if(activeMatchRequestId.isEmpty()) {
            activeMatchRequestId=java.util.UUID.randomUUID().toString();
            prefs.edit().putString("active_match_request_id",activeMatchRequestId).apply();
        }
        int[] stats=game.getMatchStats();
        int kills=Math.max(0,Math.min(29,game.getEliminations()));
        int damage=Math.max(0,Math.min(50000,stats.length>0?stats[0]:0));
        int resources=Math.max(0,Math.min(100,game.getCollectedResources()));
        int remaining=Math.max(0,stats.length>3?stats[3]:29);
        int placement=victory?1:Math.min(30,Math.max(2,remaining+1));
        try {
            JSONObject payload=new JSONObject().put("request_id",activeMatchRequestId)
                .put("victory",victory).put("placement",placement).put("kills",kills)
                .put("damage",damage).put("resources",resources);
            matchRewardPending=true;
            prefs.edit().putBoolean("match_reward_attempted",true).apply();
            matchResultAction.run(payload.toString(),(result,error)->{
                matchRewardPending=false;
                if(error!=null||result==null||!result.optBoolean("recorded",false)) {
                    matchRewardRetryAfter=System.currentTimeMillis()+15000L;
                    toast("Recompensa pendente: "+(error==null?"servidor não confirmou":error));
                    postInvalidate();return;
                }
                JSONObjectWalletUpdate(result);
                prefs.edit().putLong("last_match_reward_diamonds",result.optLong("diamonds_awarded",0L))
                    .putLong("last_match_reward_xp",result.optLong("xp_awarded",0L))
                    .putInt("last_match_placement",placement)
                    .putBoolean("match_reward_recorded",true).apply();
                JSONObject profile=result.optJSONObject("profile");
                if(profile!=null) {
                    prefs.edit().putLong("xp",profile.optLong("xp",prefs.getLong("xp",0L)))
                        .putInt("matches_played",profile.optInt("matches_played",prefs.getInt("matches_played",0)))
                        .putInt("match_wins",profile.optInt("match_wins",prefs.getInt("match_wins",0)))
                        .putInt("match_kills",profile.optInt("match_kills",prefs.getInt("match_kills",0)))
                        .putInt("match_damage",profile.optInt("match_damage",prefs.getInt("match_damage",0)))
                        .putInt("match_resources",profile.optInt("match_resources",prefs.getInt("match_resources",0))).apply();
                }
                toast("🟢 Recompensas confirmadas pela conta online!");
                postInvalidate();
            });
        } catch(Exception e) {
            matchRewardPending=false;matchRewardRetryAfter=System.currentTimeMillis()+15000L;
            toast("Não foi possível preparar o resultado da partida.");
        }
    }
    private void drawMatchResult(Canvas c,boolean victory) {
        int[] stats=game.getMatchStats();
        p.setColor(0xD9000811);c.drawRect(0,0,1280,720,p);
        panel(c,225,76,830,570,0xF20B1C2A,0xFF4BCDD5,25);
        eyebrow(c,275,116,"BATTLE UNIVERSE  •  RELATÓRIO DA PARTIDA");
        title(c,victory?"VITÓRIA ROYALE":"VOCÊ FOI ELIMINADO",275,164);
        subtitle(c,(victory?"1º LUGAR • VITÓRIA. ":"POSIÇÃO "+prefs.getInt("last_match_placement",30)+"º. ")+"Recompensas calculadas pelo seu desempenho.",275,190);
        panel(c,275,220,330,104,0xCC123B39,0xFF45D6AE,16);
        text(c,"RECOMPENSAS RECEBIDAS",296,247,10,0xFFB7EBDD,Typeface.DEFAULT_BOLD);
        text(c,"+"+prefs.getLong("last_match_reward_diamonds",0L)+" DIAMANTES",296,278,19,0xFFFFD36D,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"+"+prefs.getLong("last_match_reward_xp",0L)+" XP  •  PATENTE "+rankForXp(prefs.getLong("xp",0L)),296,302,10,0xFF8BE6D1,Typeface.DEFAULT_BOLD);
        panel(c,625,220,380,104,0xCC132B43,0xFF4B9BD0,16);
        text(c,"RESULTADO DA EQUIPE",648,247,10,0xFFAED8F0,Typeface.DEFAULT_BOLD);
        text(c,""+stats[3]+" OPONENTES RESTANTES",648,278,17,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,""+game.getCollectedResources()+" RECURSOS COLETADOS",648,302,10,0xFFB6D7E8,Typeface.DEFAULT_BOLD);
        statCard(c,275,350,220,82,""+game.getEliminations(),"ELIMINAÇÕES");
        statCard(c,515,350,220,82,""+stats[0],"DANO CAUSADO");
        statCard(c,755,350,250,82,""+stats[1],"DANO RECEBIDO");
        statCard(c,275,448,340,70,""+stats[2],"ELIMINAÇÕES FEITAS PELA IA");
        statCard(c,635,448,370,70,""+stats[3],"BOTS AINDA NA PARTIDA");
        gradientButton(c,300,548,300,68,"PAINEL PRINCIPAL","Salvar resultado e voltar ao lobby",0xFF126C72,0xFF164C70);
        gradientButton(c,680,548,300,68,"ASSISTIR PARTIDA","Acompanhar os bots restantes",0xFF694B20,0xFF9A6426);
    }
    private void statCard(Canvas c,float x,float y,float w,float h,String value,String label) {
        panel(c,x,y,w,h,0xCC102B3A,0x4469C5D2,13);
        text(c,value,x+18,y+31,22,0xFF73E4E9,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,label,x+18,y+h-13,9,0xFFB3C9D4,Typeface.DEFAULT_BOLD);
    }
    private void drawSpectatorBanner(Canvas c) {
        panel(c,410,18,460,64,0xD9081928,0xFF54D6CF,16);
        text(c,"MODO ESPECTADOR",435,44,15,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"CÂMERA ACOMPANHANDO OS SOBREVIVENTES",435,65,9,0xFF9FE7DC,Typeface.DEFAULT_BOLD);
        pill(c,24,625,190,49,"VOLTAR AO PAINEL",0xDD173C4A,Color.WHITE);
    }
    private void recordMissionProgressFromMatch() {
        ensureDailyMissionWindow();
        if(prefs.getBoolean("match_recorded_for_missions",false))return;
        String status=game.getGameStatus();
        if(!(status.startsWith("VIDA ")||status.startsWith("VITÓRIA")||status.startsWith("ELIMINADO")))return;
        int eliminations=Math.max(0,game.getEliminations());
        int crystals=Math.max(0,game.getCollectedResources());
        prefs.edit()
            .putInt("mission_matches",prefs.getInt("mission_matches",0)+1)
            .putInt("mission_eliminations",Math.min(999,prefs.getInt("mission_eliminations",0)+eliminations))
            .putInt("mission_crystals",Math.min(999,prefs.getInt("mission_crystals",0)+crystals))
            .putBoolean("match_recorded_for_missions",true).apply();
        toast("Progresso das missões diárias atualizado.");
    }
    private void claimMission(int index) {
        if(index<0||index>=MISSION_GOALS.length)return;
        ensureDailyMissionWindow();
        if(missionClaimed(index)){toast("Esta missão já foi resgatada hoje.");return;}
        if(missionProgress(index)<MISSION_GOALS[index]){toast("Continue jogando para concluir a missão.");return;}
        claimServerReward("mission",String.valueOf(index),"Missão resgatada!");
    }
    private void drawMissionsPage(Canvas c) {
        ensureDailyMissionWindow();
        eyebrow(c,270,175,"DESAFIOS DIÁRIOS • ATUALIZAÇÃO AUTOMÁTICA");
        title(c,"CENTRAL DE MISSÕES",270,224);
        subtitle(c,"O progresso é registrado quando você sai de uma partida após o pouso.",270,252);
        for(int i=0;i<MISSION_NAMES.length;i++) {
            float x=270+i*192f;
            int progress=Math.min(MISSION_GOALS[i],missionProgress(i));
            boolean claimed=missionClaimed(i),ready=progress>=MISSION_GOALS[i];
            panel(c,x,292,174,254,claimed?0xB51A4650:0xA91A3040,
                claimed?0xFF4BD4C9:ready?0xFF51C9C4:0x335D8495,16);
            pill(c,x+14,309,76,22,"DIÁRIA",0x553A6B7C,0xFFB9E8EF);
            text(c,MISSION_NAMES[i],x+14,360,10,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,MISSION_HINTS[i],x+14,390,9,0xFFB3C8D4,Typeface.DEFAULT);
            text(c,"PROGRESSO",x+14,438,8,0xFF69DCE5,Typeface.DEFAULT_BOLD);
            p.setColor(0xFF233F4F);r.set(x+14,451,x+160,461);c.drawRoundRect(r,5,5,p);
            p.setColor(0xFF55DDE7);r.set(x+14,451,x+14+146f*(progress/(float)MISSION_GOALS[i]),461);c.drawRoundRect(r,5,5,p);
            text(c,progress+" / "+MISSION_GOALS[i],x+14,484,10,Color.WHITE,Typeface.DEFAULT_BOLD);
            String reward=MISSION_XP[i]>0?"RECOMPENSA  "+MISSION_XP[i]+" XP":"RECOMPENSA  "+MISSION_DIAMONDS[i]+" ◆";
            text(c,reward,x+14,507,8,0xFFFFD27A,Typeface.DEFAULT_BOLD);
            pill(c,x+14,516,146,24,claimed?"RESGATADA":ready?"RESGATAR":"EM ANDAMENTO",
                claimed?0xFF176A75:ready?0xFF345D79:0x553B6172,Color.WHITE);
        }
        panel(c,270,568,570,68,0x74101F2C,0x226A8EA0,15);
        text(c,"3 DESAFIOS • RECOMPENSAS VIRTUAIS • RESET DIÁRIO",292,595,10,0xFF82E5DC,Typeface.DEFAULT_BOLD);
        text(c,"Jogue, elimine adversários e colete cristais para avançar.",292,616,9,0xFF9CB4C1,Typeface.DEFAULT);
    }
    private void claimRoyaleTier(int index) {
        if(index<0||index>=ROYALE_LEVELS.length)return;
        int level=(int)Math.min(30L,1L+prefs.getLong("xp",0L)/500L);
        if(prefs.getBoolean("royale_tier_claimed_"+index,false)){toast("Esta recompensa já foi resgatada.");return;}
        if(level<ROYALE_LEVELS[index]){toast("Alcance o nível "+ROYALE_LEVELS[index]+" do passe.");return;}
        claimServerReward("royale_tier",String.valueOf(index),"Recompensa Royale confirmada!");
    }
    private void claimDailyReward() {
        if(!googleSignedIn){toast("Entre na sua conta para coletar a recompensa online.");page=PROFILE;return;}
        if(dailyRewardAction==null){toast("Recompensa online indisponível.");return;}
        toast("Confirmando recompensa no servidor...");
        dailyRewardAction.run("",(result,error)->{
            if(error!=null){toast("Falha: "+error);return;}
            JSONObjectWalletUpdate(result);
            prefs.edit().putLong("daily_reward_day",System.currentTimeMillis()/86400000L).apply();
            boolean claimed=result.optBoolean("claimed",false);
            long amount=result.optLong("diamonds_awarded",0L);
            toast(claimed?"+"+amount+" DIAMANTES ONLINE!":"Recompensa diária já coletada.");
        });
    }

    private void showRedeemDialog() {
        EditText input=new EditText(getContext());
        input.setSingleLine(true); input.setHint("Digite seu código promocional");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(getContext())
            .setTitle("Resgatar código promocional")
            .setMessage("Os códigos são validados pela API oficial e vinculados à sua conta.")
            .setView(input).setNegativeButton("CANCELAR",null)
            .setPositiveButton("RESGATAR",(dialog,which)->redeemCode(input.getText().toString()))
            .show();
    }
    private void redeemCode(String rawCode) {
        String code=rawCode==null?"":rawCode.trim().toUpperCase(Locale.ROOT);
        if(code.length()<3){toast("Digite um código válido.");return;}
        if(!googleSignedIn){toast("Entre na sua conta para resgatar códigos.");page=PROFILE;return;}
        if(promoRedeemAction==null){toast("Resgate pela API ainda não conectado.");return;}
        toast("Validando código na API...");
        promoRedeemAction.run(code,(result,error)->{
            if(error!=null){toast("Resgate recusado: "+error);return;}
            long awarded=result.optLong("diamonds_awarded",0L);
            JSONObject wallet=result.optJSONObject("wallet");
            if(wallet!=null) prefs.edit().putLong("diamonds",wallet.optLong("diamonds",prefs.getLong("diamonds",0L))).apply();
            else if(result.has("diamonds_balance"))prefs.edit().putLong("diamonds",result.optLong("diamonds_balance")).apply();
            toast("+"+awarded+" DIAMANTES • CÓDIGO RESGATADO!");
        });
    }

    private void JSONObjectWalletUpdate(org.json.JSONObject result) {
        org.json.JSONObject wallet=result.optJSONObject("wallet");
        if(wallet!=null)prefs.edit().putLong("diamonds",wallet.optLong("diamonds",prefs.getLong("diamonds",0L))).apply();
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
        if(!googleSignedIn) {
            toast("Entre na conta para comprar com a carteira online.");
            page=PROFILE;invalidate();return;
        }
        if(shopPurchasePending)return;
        if(shopPurchaseAction==null) {
            toast("Loja online indisponível. Tente novamente mais tarde.");return;
        }
        shopPurchasePending=true;
        toast("Confirmando compra no servidor...");
        String requestId=java.util.UUID.randomUUID().toString();
        shopPurchaseAction.run(SHOP_KEYS[index]+"|"+requestId,(result,error)->{
            shopPurchasePending=false;
            if(error!=null) {
                toast(error.toLowerCase(java.util.Locale.ROOT).contains("diamantes insuficientes")
                    ?"Diamantes insuficientes na carteira online.":"Compra não concluída: "+error);
                invalidate();return;
            }
            if(result==null||!result.optBoolean("purchased",false)) {
                toast("O servidor não confirmou a compra.");invalidate();return;
            }
            prefs.edit().putBoolean(SHOP_KEYS[index],true).apply();
            JSONObjectWalletUpdate(result);
            toast(SHOP_NAMES[index]+" adquirido na conta!");
            invalidate();
        });
    }

    private void drawEventsPage(Canvas c) {
        eyebrow(c,270,175,"EVENTOS DA TEMPORADA • RECOMPENSAS LOCAIS");
        title(c,"CENTRAL DE EVENTOS",270,224);
        subtitle(c,"Participe dos desafios ativos e acompanhe as recompensas disponíveis.",270,252);
        boolean claimed=prefs.getBoolean("event_season_claimed",false);
        panel(c,270,286,570,146,claimed?0xA91A4650:0xAE193040,claimed?0xFF4BD4C9:0x557AA9BC,18);
        pill(c,292,306,112,24,"TEMPORADA 03",0x553A6B7C,0xFFB9E8EF);
        text(c,"DESAFIO DE BOAS-VINDAS",292,365,20,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Entre no lobby e resgate o pacote de participação.",292,392,11,0xFFB3C8D4,Typeface.DEFAULT);
        pill(c,675,350,140,42,claimed?"RESGATADO":"RESGATAR",claimed?0xFF176A75:0xFF345D79,Color.WHITE);
        panel(c,270,452,570,120,0x771A3040,0x335D8495,15);
        text(c,"PRÓXIMOS EVENTOS",292,483,12,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        text(c,"Eventos competitivos e torneios online serão ativados quando o servidor multiplayer estiver disponível.",292,514,10,0xFFB3C8D4,Typeface.DEFAULT);
        pill(c,292,535,205,24,"ONLINE • AGUARDANDO SERVIDOR",0x553B6172,0xFFBBD0D9);
    }

    private void drawFriendsPage(Canvas c) {
        eyebrow(c,270,175,"GRUPO E CONTATOS");
        title(c,"AMIGOS E EQUIPE",270,224);
        subtitle(c,"Gerencie nomes salvos neste aparelho. Convites online dependem do servidor.",270,252);
        String friend=prefs.getString("local_friend_1","");
        panel(c,270,286,570,102,0xAE193040,0x557AA9BC,16);
        circle(c,318,337,25,0xFF1F7584,0xFF65DCE7);
        text(c,friend.isEmpty()?"?":friend.substring(0,1).toUpperCase(Locale.ROOT),311,344,18,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,friend.isEmpty()?"NENHUM CONTATO SALVO":friend,362,330,15,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,friend.isEmpty()?"Adicione um nome para organizar sua equipe local.":"Contato salvo localmente • não conectado",362,354,10,0xFF9FB8C6,Typeface.DEFAULT);
        pill(c,670,319,145,36,friend.isEmpty()?"ADICIONAR":"EDITAR NOME",0xFF176A75,Color.WHITE);
        panel(c,270,408,570,128,0x771A3040,0x335D8495,15);
        text(c,"SISTEMA DE GRUPOS ONLINE",292,440,12,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        text(c,"Salas, convites, presença e formação de esquadrão ainda não estão ativos.",292,470,10,0xFFB3C8D4,Typeface.DEFAULT);
        pill(c,292,490,210,25,"MULTIPLAYER • NÃO CONECTADO",0x553B6172,0xFFBBD0D9);
    }

    private void drawMailPage(Canvas c) {
        eyebrow(c,270,175,"CAIXA DE ENTRADA ONLINE");
        title(c,"CORREIO",270,224);
        subtitle(c,"Mensagens globais e presentes enviados pelo ADM.",270,252);
        if(googleSignedIn && mailFetchAction!=null && !mailFetchStarted) {
            mailFetchStarted=true;
            mailFetchAction.run("",(result,error)->{
                if(error!=null){mailFetchStarted=false;toast("Correio: "+error);}
                else {mailMessages=result==null?new JSONArray():result.optJSONArray("messages");
                    if(mailMessages==null)mailMessages=new JSONArray();
                    postInvalidate();}
            });
        }
        JSONObject message=mailMessages.length()>0?mailMessages.optJSONObject(mailMessages.length()-1):null;
        if(message!=null) {
            activeMailId=message.optString("id","welcome_gift_01");
            activeMailTitle=message.optString("title","AVISO DO JOGO");
            activeMailBody=message.optString("body","");
            activeMailDiamonds=message.optLong("diamonds",0L);
            activeMailClaimed=message.optBoolean("claimed",false);
        } else {
            activeMailId="welcome_gift_01";
            activeMailTitle="BOAS-VINDAS AO BATTLE UNIVERSE";
            activeMailBody="Presente de entrada: 50 diamantes virtuais.";
            activeMailDiamonds=50L;
            activeMailClaimed=prefs.getBoolean("mail_welcome_claimed",false);
        }
        panel(c,270,286,570,112,activeMailClaimed?0xA91A4650:0xAE193040,
            activeMailClaimed?0xFF4BD4C9:0x557AA9BC,16);
        circle(c,310,326,18,0xFF1F7584,0xFF65DCE7);
        text(c,"✉",301,333,17,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,activeMailTitle.length()>34?activeMailTitle.substring(0,34):activeMailTitle,
            350,322,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        String bodyLine=activeMailBody.length()>58?activeMailBody.substring(0,58):activeMailBody;
        text(c,bodyLine,350,347,10,0xFFB3C8D4,Typeface.DEFAULT);
        text(c,activeMailDiamonds+" DIAMANTES VIRTUAIS",350,371,9,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        pill(c,675,318,140,36,activeMailClaimed?"COLETADO":"RESGATAR",
            activeMailClaimed?0xFF176A75:0xFF345D79,Color.WHITE);
        panel(c,270,418,570,102,0x771A3040,0x335D8495,15);
        text(c,"MENSAGENS RECEBIDAS: "+mailMessages.length(),292,450,12,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        text(c,googleSignedIn?"O correio é sincronizado com sua conta. Cada presente pode ser resgatado uma vez.":"Entre na conta para carregar mensagens enviadas pelo ADM.",292,480,10,0xFFB3C8D4,Typeface.DEFAULT);
    }

    private void drawAchievementsPage(Canvas c) {
        eyebrow(c,270,175,"PROGRESSO DA CONTA");
        title(c,"CONQUISTAS",270,224);
        subtitle(c,"Marcos de experiência com recompensas resgatáveis uma única vez.",270,252);
        long xp=prefs.getLong("xp",0L);
        int[] goals={500,1500,3000};long[] rewards={50,100,200};
        String[] names={"PRIMEIROS PASSOS","OPERADOR DEDICADO","LENDÁRIO"};
        String[] keys={"achievement_500","achievement_1500","achievement_3000"};
        for(int i=0;i<3;i++) {
            float x=270+i*192f;boolean ready=xp>=goals[i],claimed=prefs.getBoolean(keys[i],false);
            panel(c,x,292,174,190,claimed?0xA91A4650:0xAE193040,claimed?0xFF4BD4C9:ready?0xFF51C9C4:0x335D8495,16);
            pill(c,x+13,308,78,22,"MARCO XP",0x553A6B7C,0xFFB9E8EF);
            text(c,names[i],x+13,362,9,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,goals[i]+" XP",x+13,392,12,0xFF69DCE5,Typeface.DEFAULT_BOLD);
            text(c,"RECOMPENSA  "+rewards[i]+" ◆",x+13,421,8,0xFFFFD27A,Typeface.DEFAULT_BOLD);
            pill(c,x+13,440,148,26,claimed?"RESGATADA":ready?"RESGATAR":"BLOQUEADA",claimed?0xFF176A75:ready?0xFF345D79:0x553B6172,Color.WHITE);
        }
        panel(c,270,506,570,76,0x771A3040,0x335D8495,15);
        text(c,"EXPERIÊNCIA TOTAL: "+xp+" XP",292,537,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Jogue partidas e complete missões para desbloquear novos marcos.",292,560,9,0xFF9CB4C1,Typeface.DEFAULT);
    }

    private void showLocalFriendDialog() {
        EditText input=new EditText(getContext());input.setSingleLine(true);
        input.setHint("Nome do contato");input.setText(prefs.getString("local_friend_1",""));
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(getContext()).setTitle("Contato local").setView(input)
            .setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",(dialog,which)->{
                String value=input.getText().toString().trim();if(value.length()>18)value=value.substring(0,18);
                prefs.edit().putString("local_friend_1",value).apply();
                toast(value.isEmpty()?"Contato removido.":"Contato salvo neste aparelho.");
            }).show();
    }

    private void claimEventReward() {
        if(prefs.getBoolean("event_season_claimed",false)){toast("Recompensa do evento já coletada.");return;}
        claimServerReward("event","season03_welcome","Evento concluído: recompensa recebida!");
    }
    private void claimMailReward() {
        if(activeMailClaimed){toast("Presente desta mensagem já coletado.");return;}
        if(!googleSignedIn||mailClaimAction==null){toast("Entre na sua conta para resgatar o presente online.");return;}
        mailClaimAction.run(activeMailId,(result,error)->{
            if(error!=null){toast("Não foi possível resgatar: "+error);return;}
            long balance=result==null?-1L:result.optLong("diamonds_balance",-1L);
            long awarded=result==null?0L:result.optLong("diamonds_awarded",0L);
            if(balance>=0)prefs.edit().putLong("diamonds",balance).apply();
            if("welcome_gift_01".equals(activeMailId))prefs.edit().putBoolean("mail_welcome_claimed",true).apply();
            activeMailClaimed=true;
            for(int i=0;i<mailMessages.length();i++){
                JSONObject item=mailMessages.optJSONObject(i);
                if(item!=null&&activeMailId.equals(item.optString("id")))try{item.put("claimed",true);}catch(Exception ignored){}
            }
            toast("🟢 Presente resgatado: +"+awarded+" diamantes.");
            postInvalidate();
        });
    }
    private void claimServerReward(String type,String key,String successMessage) {
        if(!googleSignedIn){toast("Entre na conta para resgatar recompensas online.");page=PROFILE;return;}
        if(rewardClaimAction==null){toast("Sistema de recompensas online indisponível.");return;}
        rewardClaimAction.run(type+"|"+key,(result,error)->{
            if(error!=null){toast("Resgate não concluído: "+error);postInvalidate();return;}
            if(result==null){toast("O servidor não confirmou a recompensa.");return;}
            JSONObjectWalletUpdate(result);
            long xp=result.optLong("xp_awarded",0L);
            toast(result.optBoolean("claimed",false)?successMessage+(xp>0?" +"+xp+" XP":"")
                :"Esta recompensa já consta como resgatada.");
            postInvalidate();
        });
    }
    private void claimAchievement(int index) {
        int[] goals={500,1500,3000};long[] rewards={50,100,200};
        String[] keys={"achievement_500","achievement_1500","achievement_3000"};
        if(index<0||index>=goals.length)return;
        if(prefs.getBoolean(keys[index],false)){toast("Conquista já resgatada.");return;}
        if(prefs.getLong("xp",0L)<goals[index]){toast("Ainda faltam pontos de experiência.");return;}
        claimServerReward("achievement",String.valueOf(goals[index]),"Conquista resgatada!");
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
        text(c,"MISSÕES  •  ROYALE  •  EVENTOS",913,599,10,0xFF7FA4B7,Typeface.DEFAULT_BOLD);
        pill(c,913,628,300,24,"ANALISAR ZIP JÁ BAIXADO",0x553B6172,0xFFBFEAF0);
    }
    private void mapArtwork(Canvas c,int map,float x,float y,float w,float h){
        int[][] palettes={{0xFF54C2D1,0xFF1B665B},{0xFF7B6BD6,0xFF171F49},{0xFFE4B366,0xFF8B5737},
            {0xFFB9E5F2,0xFF567F9D},{0xFF89979A,0xFF26343A},{0xFF83C66D,0xFF1B6041},{0xFF5479A9,0xFF111D3A},{0xFF82715E,0xFF302B28}};
        int index=Math.max(0,Math.min(palettes.length-1,map));
        p.setShader(new LinearGradient(x,y,x+w,y+h,palettes[index],null,Shader.TileMode.CLAMP));
        r.set(x,y,x+w,y+h);c.drawRoundRect(r,12,12,p);p.setShader(null);
        if(map==0){circle(c,x+w*.72f,y+h*.48f,h*.17f,0xFFE8D79B,0xFFFFE7B2);for(int i=0;i<3;i++)palmIcon(c,x+w*(.22f+i*.27f),y+h*.82f);}
        else if(map==1){for(int i=0;i<5;i++){float bx=x+8+i*22;p.setColor(i%2==0?0xFF18284D:0xFF31295E);c.drawRoundRect(bx,y+14+(i%2)*9,bx+15,y+h-7,2,2,p);p.setColor(0xFF4FE4F0);for(int j=0;j<3;j++)c.drawRect(bx+4,y+22+j*13,bx+8,y+26+j*13,p);}}
        else if(map==2){p.setColor(0xFF8B5737);c.drawOval(x+3,y+h*.55f,x+w-4,y+h+8,p);p.setColor(0xFFD59B58);c.drawOval(x+w*.2f,y+h*.48f,x+w*.9f,y+h*.9f,p);}
        else if(map==3){p.setColor(0xFFECF8FF);for(int i=0;i<4;i++)c.drawCircle(x+12+i*29,y+22+(i%2)*10,7,p);p.setColor(0xFF6C9DB7);c.drawRect(x+8,y+h*.68f,x+w-8,y+h*.82f,p);}
        else if(map==4){for(int i=0;i<4;i++){float bx=x+13+i*27;p.setColor(0xFF39484D);c.drawRect(bx,y+17,bx+12,y+h-8,p);p.setColor(0xFFDB8A48);c.drawRect(bx+3,y+25,bx+9,y+31,p);}p.setColor(0xFF263238);c.drawRect(x+w*.72f,y+8,x+w*.83f,y+h-5,p);}
        else if(map==5){p.setColor(0xFF1C6947);for(int i=0;i<5;i++)c.drawCircle(x+12+i*23,y+h*.62f,10,p);p.setColor(0xFF8BD58B);c.drawOval(x+10,y+h*.65f,x+w-10,y+h+6,p);}
        else if(map==6){p.setColor(0xFF172C55);for(int i=0;i<4;i++){float bx=x+12+i*27;c.drawRoundRect(bx,y+12,bx+13,y+h-8,3,3,p);}p.setColor(0xFF5BE7F2);c.drawCircle(x+w*.5f,y+h*.45f,10,p);}
        else{p.setColor(0xFF443D35);for(int i=0;i<4;i++){float bx=x+8+i*27;c.drawRect(bx,y+25,bx+17,y+h-6,p);}p.setColor(0xFFB69A70);c.drawRect(x+w*.38f,y+12,x+w*.62f,y+h-10,p);}
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

        String gameStatus=game.getGameStatus();
        if(showMinimap) {
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
            pill(c,1080,150,158,25,"MAPA TÁTICO",0xFF176A75,Color.WHITE);
        }
        if(gameStatus.contains("PARAQUEDAS")||gameStatus.contains("QUEDA LIVRE")) {
            panel(c,22,170,330,76,0xD9081928,0x5579DDEB,16);
            eyebrow(c,40,195,"POUSO EM ANDAMENTO");
            text(c,"CÂMERA ACOMPANHANDO O OPERADOR",40,222,10,0xFF9FC6D3,Typeface.DEFAULT_BOLD);
        } else if(gameStatus.startsWith("EMBARQUE")||gameStatus.startsWith("AVIÃO")) {
            panel(c,22,170,330,110,0xD9081928,0x5579DDEB,16);
            eyebrow(c,40,195,"AERONAVE DE TRANSPORTE");
            boolean rampReady=gameStatus.contains("RAMPA PRONTA");
            text(c,gameStatus.startsWith("EMBARQUE")?"EMBARQUE E AGUARDE A RAMPA":
                    (rampReady?"SAÍDA LIBERADA • TOQUE SALTAR":"CAMINHE PELO CORREDOR ATÉ A RAMPA"),
                40,222,11,rampReady?0xFF83E9B2:Color.WHITE,
                Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,"ANALÓGICO: MOVER  •  LADO DIREITO: CÂMERA",40,240,8,0xFF9FC6D3,Typeface.DEFAULT_BOLD);
            text(c,"30 PARTICIPANTES  •  1 JOGADOR + 29 BOTS IA",40,272,8,0xFFB9DCE5,Typeface.DEFAULT_BOLD);
            pill(c,40,250,160,20,gameStatus.startsWith("EMBARQUE")?"PORTA TRASEIRA FECHADA":
                    (rampReady?"RAMPA PRONTA":"CAMINHE ATÉ A RAMPA"),
                rampReady?0xFF176A75:0x553A6B7C,0xFFB9E8EF);
        } else if(gameStatus.contains("SEM ARMA")||gameStatus.contains("SEM MUNIÇÃO")) {
            panel(c,22,170,340,84,0xD9081928,0x5579DDEB,15);
            eyebrow(c,40,194,"EQUIPAMENTO NO CHÃO");
            text(c,gameStatus.contains("SEM ARMA")?"VOCÊ ESTÁ SEM ARMA":"MUNIÇÃO ESGOTADA",
                40,219,12,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,"APROXIME-SE DO ITEM E TOQUE USAR PARA PEGAR",40,240,8,0xFF9FC6D3,Typeface.DEFAULT_BOLD);
        }

        drawControl(c,890,310,38,game.isAimToggled()?"MIRA ON":"MIRA",
            0xFF285D79,0xFF8ADCF4,game.isAimToggled());
        drawControl(c,1002,310,43,"DISPARAR",0xFF185D7A,0xFF65E9F4,game.isFiring());
        drawControl(c,1165,403,43,gameStatus.contains("PRÉVIA DE")?"CONFIRMAR":"CONSTRUIR",0xFF176C83,0xFF8CEBFF,gameStatus.contains("PRÉVIA DE"));
        drawControl(c,960,461,39,"CORRER",0xFF285D49,0xFF83E9B2);
        drawControl(c,1065,465,34,"ARMA",0xFF354F79,0xFF9DC7FF);
        drawControl(c,1152,562,47,"SALTAR",0xFF7B3D35,0xFFFFA28F);
        drawControl(c,960,605,39,"USAR",0xFF354F79,0xFF9DC7FF);
        drawControl(c,850,562,39,"AGACHAR",0xFF315B50,0xFF8DE7C1);

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

        // Analógico dinâmico: a base aparece no toque e o botão acompanha o dedo.
        float baseX=game.isMovingTouchActive()?game.getMoveOriginDesignX():179f;
        float baseY=game.isMovingTouchActive()?game.getMoveOriginDesignY():544f;
        float joyX=baseX+(game.isMovingTouchActive()?game.getMoveTouchX()*54f:0f);
        float joyY=baseY-(game.isMovingTouchActive()?game.getMoveTouchY()*54f:0f);
        p.setColor(0x553D7183);c.drawCircle(baseX,baseY,66,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(0xAA8CE8EF);c.drawCircle(baseX,baseY,66,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xAA8FCBD2);c.drawCircle(joyX,joyY,28,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);p.setColor(0xFFE9FFFF);c.drawCircle(joyX,joyY,28,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFFECFFFF);c.drawCircle(joyX,joyY,7,p);
        if(game.isMovingTouchActive()) {
            p.setColor(0xAA7CEAF2);c.drawLine(baseX,baseY,joyX,joyY,p);
        }

        pill(c,24,625,160,49,"SAIR DA PARTIDA",0xDD173C4A,Color.WHITE);
        panel(c,420,650,440,40,0xBB071522,0x226A8EA0,14);
        text(c,"JOYSTICK: MOVER  •  AGACHAR: SEGURE  •  GELO: TOQUE PARA VER / CONFIRMAR",
            438,675,8,0xFFDAEAF0,Typeface.DEFAULT_BOLD);
        if(gameStatus.contains("DERRUBADO")) {
            panel(c,430,92,420,66,0xE90B2633,0xFF67DDEB,16);
            eyebrow(c,455,116,"VOCÊ ESTÁ DERRUBADO");
            text(c,gameStatus,455,140,10,0xFFFFD58A,Typeface.DEFAULT_BOLD);
        }
        if(gameStatus.contains("PRÉVIA DE")) {
            pill(c,470,188,340,30,"PRÉVIA TRANSLÚCIDA • TOQUE CONSTRUIR PARA CONFIRMAR",0xDD0D5266,0xFFBDF8FF);
        }
        if(resultSpectating) drawSpectatorBanner(c);
        else if(isMatchOver()) {
            boolean victory=game.getGameStatus().contains("VITÓRIA");
            applyMatchRewards(victory);
            drawMatchResult(c,victory);
        }
        if(tacticalMapVisible)drawTacticalMap(c);
        postInvalidateDelayed(120);
    }
    private void drawTacticalMap(Canvas c) {
        float[] info=game.getFlightInfo();
        p.setColor(0xE9000812);c.drawRect(0,0,1280,720,p);
        panel(c,280,38,720,644,0xF20B1C2A,0xFF4BCDD5,24);
        eyebrow(c,320,76,"MAPA TÁTICO • ROTA E MARCADORES");
        title(c,"VISÃO DA ILHA",320,116);
        pill(c,816,55,154,34,"FECHAR MAPA",0xFF176A75,Color.WHITE);
        final float cx=640.0f,cy=372.0f,scaleMap=1.45f;
        p.setShader(new LinearGradient(cx-220,cy-220,cx+220,cy+220,
            new int[]{0xFF315E62,0xFF244C42,0xFF17313B},null,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,218,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.0f);p.setColor(0xFF82D2BC);
        c.drawCircle(cx,cy,218,p);c.drawCircle(cx,cy,145,p);c.drawCircle(cx,cy,72,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF557A58);c.drawOval(cx-165,cy-96,cx-42,cy+84,p);
        p.setColor(0xFF6C8560);c.drawOval(cx+22,cy-150,cx+172,cy-18,p);
        p.setColor(0xFF647D65);c.drawOval(cx+18,cy+48,cx+136,cy+166,p);
        p.setColor(0xFF496F63);c.drawOval(cx-154,cy+72,cx-32,cy+160,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xFFB7A27A);
        c.drawLine(cx-160,cy-20,cx+165,cy+72,p);c.drawLine(cx-60,cy-164,cx+65,cy+164,p);
        p.setStyle(Paint.Style.FILL);
        float zoneRadius=Math.max(0f,Math.min(112f,info[6]))*scaleMap;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(0xAA66E6B8);
        c.drawCircle(cx,cy,zoneRadius,p);p.setStyle(Paint.Style.FILL);
        float routeY=cy-32.0f*scaleMap;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.0f);p.setColor(0xFFFFD27A);
        c.drawLine(cx-110.0f*scaleMap,routeY,cx+112.0f*scaleMap,routeY,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFD27A);
        c.drawCircle(cx-110.0f*scaleMap,routeY,5,p);c.drawCircle(cx+112.0f*scaleMap,routeY,5,p);
        float playerX=cx+info[3]*scaleMap,playerY=cy+info[4]*scaleMap;
        p.setColor(0xFF55E5F0);c.drawCircle(playerX,playerY,8,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.WHITE);
        c.drawCircle(playerX,playerY,11,p);p.setStyle(Paint.Style.FILL);
        if(info[0]>=1.0f&&info[0]<2.0f) {
            float planeX=cx+info[1]*scaleMap,planeY=cy+info[2]*scaleMap;
            p.setColor(0xFFFFB45E);Path plane=new Path();
            plane.moveTo(planeX,planeY-11);plane.lineTo(planeX+7,planeY+8);
            plane.lineTo(planeX,planeY+5);plane.lineTo(planeX-7,planeY+8);
            plane.close();c.drawPath(plane,p);
            text(c,"AERONAVE",planeX+12,planeY-8,8,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        }
        if(info[9]>0.5f) {
            float wx=cx+info[7]*scaleMap,wy=cy+info[8]*scaleMap;
            p.setColor(0xFFFFD27A);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
            c.drawCircle(wx,wy,12,p);c.drawLine(wx-17,wy,wx+17,wy,p);
            c.drawLine(wx,wy-17,wx,wy+17,p);p.setStyle(Paint.Style.FILL);
        }
        text(c,"N",cx-4,cy-228,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"TOQUE NA ILHA PARA MARCAR UM LOCAL DE POUSO",322,622,10,0xFFB8D9E3,Typeface.DEFAULT_BOLD);
        text(c,"ROTA DA AERONAVE",322,649,9,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        text(c,"JOGADOR",505,649,9,0xFF55E5F0,Typeface.DEFAULT_BOLD);
        text(c,"ZONA SEGURA",625,649,9,0xFF79E3B7,Typeface.DEFAULT_BOLD);
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
                if(tacticalMapVisible) {
                    if(hit(q[0],q[1],816,55,154,34)) { tacticalMapVisible=false;invalidate();return true; }
                    float mx=q[0]-640.0f,my=q[1]-372.0f;
                    if(mx*mx+my*my<=218.0f*218.0f) {
                        game.setWaypoint(mx/1.45f,my/1.45f);
                        toast("LOCAL DE POUSO MARCADO NO MAPA.");
                        tacticalMapVisible=false;invalidate();return true;
                    }
                    return true;
                }
                if(hit(q[0],q[1],1080,150,158,25)) { tacticalMapVisible=true;invalidate();return true; }
                if(!resultSpectating&&isMatchOver()) {
                    boolean victory=game.getGameStatus().contains("VITÓRIA");
                    if(hit(q[0],q[1],300,548,300,68)) {
                        applyMatchRewards(victory);
                        recordMissionProgressFromMatch();
                        returnToLobbyAfterMatch();
                        return true;
                    }
                    if(hit(q[0],q[1],680,548,300,68)) {
                        resultSpectating=true;game.setSpectatorMode(true);invalidate();return true;
                    }
                    return true;
                }
                if(hit(q[0],q[1],24,625,resultSpectating?190:160,49)){
                    if(isMatchOver())applyMatchRewards(game.getGameStatus().contains("VITÓRIA"));
                    recordMissionProgressFromMatch();
                    returnToLobbyAfterMatch();
                    return true;
                }
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
        if(hapticsEnabled)performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if(hit(x,y,1000,17,246,60)){page=PROFILE;return;}
        for(int i=0;i<NAV.length;i++){
            float ny=143+i*33.2f;
            if(hit(x,y,27,ny,196,29)){page=NAV_PAGES[i];return;}
        }
        if(page==HOME){
            if(hit(x,y,913,628,300,24)){if(packAnalyzerAction!=null)packAnalyzerAction.run();return;}
            if(hit(x,y,270,442,164,24)){
                onlineMode=false;prefs.edit().putBoolean("online_mode",false).apply();
                toast("MODO OFFLINE SELECIONADO.");return;
            }
            if(hit(x,y,442,442,200,24)){
                onlineMode=true;prefs.edit().putBoolean("online_mode",true).apply();
                toast("ONLINE AINDA PRECISA DE UM SERVIDOR DE PARTIDAS.");return;
            }
            if(hit(x,y,270,328,275,112)){page=MODES_PAGE;return;}
            if(hit(x,y,558,328,292,112)){page=MAPS_PAGE;return;}
            if(hit(x,y,270,472,342,78)){
                if(onlineMode){
                    toast("ONLINE INDISPONÍVEL: SERVIDOR MULTIPLAYER AINDA NÃO CONFIGURADO. SELECIONE OFFLINE.");
                    return;
                }
                page=PREMATCH;return;
            }
            if(hit(x,y,628,472,222,78)){page=ROYALE;return;}
            if(hit(x,y,270,608,580,58)){showAiCoachDialog();return;}
        }else if(page==PREMATCH){
            if(hit(x,y,270,636,170,34)){page=HOME;return;}
            if(hit(x,y,862,636,350,34)){beginOfflineMatch();return;}
        }else if(page==CHARACTERS){
            for(int i=0;i<4;i++)if(hit(x,y,270+i*146,295,132,210)){
                character=i;game.setCharacter(i);prefs.edit().putInt("character",i).apply();
                toast("Operador "+CHARACTER_NAMES[i]+" selecionado");return;
            }
        }else if(page==MODES_PAGE){
            for(int i=0;i<4;i++)if(hit(x,y,270+(i%2)*292,290+(i/2)*150,270,126)){
                mode=i;game.setMatchMode(i);prefs.edit().putInt("mode",i).apply();
                toast("Modo "+MODE_NAMES[i]+" selecionado");return;
            }
        }else if(page==MAPS_PAGE){
            for(int i=0;i<MAP_NAMES.length;i++){
                int col=i%4,row=i/4;float mx=270+col*145f,my=282+row*178f;
                if(hit(x,y,mx,my,132,164)){
                map=i;game.setMap(i);prefs.edit().putInt("map",i).apply();
                toast("Mapa "+MAP_NAMES[i]+" selecionado");return;
                }
            }
        }else if(page==PROFILE){
            if(hit(x,y,270,486,570,58)){
                if(googleSignedIn) {
                    if(accountSyncAction!=null)accountSyncAction.run();
                } else showUsernameAccountDialog();
                return;
            }
            if(googleSignedIn&&hit(x,y,690,632,150,25)){
                if(accountSignOutAction!=null)accountSignOutAction.run();
                return;
            }
            if(hit(x,y,270,558,270,70)){showRedeemDialog();return;}
            if(hit(x,y,558,558,282,70)){showNicknameDialog();return;}
            if(hit(x,y,270,642,570,42)){if(packAnalyzerAction!=null)packAnalyzerAction.run();return;}
        }else if(page==SHOP){
            for(int i=0;i<SHOP_NAMES.length;i++){
                float sx=270+(i%3)*192f,sy=322+(i/3)*56f;
                if(hit(x,y,sx,sy,174,50)){purchaseShopItem(i);return;}
            }
        }else if(page==WARDROBE){
            if(hit(x,y,690,260,150,25)) {
                selectedSet=0;selectedShirt=0;selectedBoots=0;
                prefs.edit().putInt("selected_set",0).putInt("selected_shirt",0).putInt("selected_boots",0).apply();
                game.setWardrobe(0,0,0);toast("Conjunto padrão equipado.");return;
            }
            for(int i=0;i<4;i++) {
                float sx=270+i*146f;
                if(hit(x,y,sx,312,132,72)) {
                    if(!prefs.getBoolean(SHOP_KEYS[3+i],false)){toast("Compre esta camisa na loja primeiro.");return;}
                    selectedShirt=i+1;selectedSet=0;
                    prefs.edit().putInt("selected_shirt",selectedShirt).putInt("selected_set",0).apply();
                    game.setWardrobe(selectedShirt,selectedBoots,0);toast("Camisa "+new String[]{"TÁTICA","NEON","FROST","DESERTO"}[i]+" equipada.");return;
                }
                if(hit(x,y,sx,432,132,72)) {
                    if(!prefs.getBoolean(SHOP_KEYS[7+i],false)){toast("Compre estas botas na loja primeiro.");return;}
                    selectedBoots=i+1;selectedSet=0;
                    prefs.edit().putInt("selected_boots",selectedBoots).putInt("selected_set",0).apply();
                    game.setWardrobe(selectedShirt,selectedBoots,0);toast("Botas equipadas.");return;
                }
                if(hit(x,y,sx,552,132,72)) {
                    if(!prefs.getBoolean(SHOP_KEYS[11+i],false)){toast("Compre este conjunto na loja primeiro.");return;}
                    selectedSet=i+1;
                    prefs.edit().putInt("selected_set",selectedSet).apply();
                    game.setWardrobe(selectedShirt,selectedBoots,selectedSet);toast("Conjunto "+new String[]{"AURORA","CAMPEÃO","ORBITAL","DESERTO"}[i]+" equipado.");return;
                }
            }
        }else if(page==ROYALE){
            if(hit(x,y,674,184,166,32)){page=ROYALE_SPIN;return;}
            if(hit(x,y,681,423,138,30)){claimDailyReward();return;}
        }else if(page==ROYALE_SPIN){
            if(hit(x,y,674,184,166,32)){page=ROYALE;return;}
            if(hit(x,y,288,546,220,68)){performRoyaleSpin();return;}
            for(int i=0;i<ROYALE_LEVELS.length;i++){
                float rx=270+(i%3)*192f,ry=476+(i/3)*88f;
                if(hit(x,y,rx,ry,174,78)){claimRoyaleTier(i);return;}
            }
        }else if(page==MISSIONS){
            for(int i=0;i<MISSION_NAMES.length;i++){
                float mx=270+i*192f;
                if(hit(x,y,mx,292,174,254)){claimMission(i);return;}
            }
        }else if(page==EVENTS){
            if(hit(x,y,675,350,140,42)){claimEventReward();return;}
        }else if(page==FRIENDS){
            if(hit(x,y,670,319,145,36)){showLocalFriendDialog();return;}
        }else if(page==MAIL){
            if(hit(x,y,675,318,140,36)){claimMailReward();return;}
        }else if(page==ACHIEVEMENTS){
            for(int i=0;i<3;i++){
                float ax=270+i*192f;
                if(hit(x,y,ax,292,174,190)){claimAchievement(i);return;}
            }
        }else if(page==SETTINGS){
            if(hit(x,y,515,292,300,60)){
                quality=x<620?0:x<720?1:2;game.setQuality(quality);
                prefs.edit().putInt("quality",quality).apply();toast("Qualidade: "+QUALITY_NAMES[quality]);return;
            }
            if(hit(x,y,420,386,110,42)){
                music=!music;prefs.edit().putBoolean("music",music).apply();
                toast(music?"Música ativada":"Música desativada");return;
            }
            if(hit(x,y,718,386,110,42)){
                effects=!effects;prefs.edit().putBoolean("effects",effects).apply();
                toast(effects?"Efeitos ativados":"Efeitos desativados");return;
            }
            if(hit(x,y,515,460,300,50)){
                lookSensitivity=x<620?0:x<720?1:2;game.setLookSensitivity(lookSensitivity);
                prefs.edit().putInt("look_sensitivity",lookSensitivity).apply();
                toast("Sensibilidade: "+new String[]{"baixa","média","alta"}[lookSensitivity]);return;
            }
            if(hit(x,y,680,545,145,38)){
                invertCameraY=!invertCameraY;game.setInvertCameraY(invertCameraY);
                prefs.edit().putBoolean("invert_camera_y",invertCameraY).apply();toast("Inversão da câmera atualizada");return;
            }
            if(hit(x,y,680,578,145,38)){
                showMinimap=!showMinimap;prefs.edit().putBoolean("show_minimap",showMinimap).apply();
                toast(showMinimap?"Minimapa visível":"Minimapa oculto");return;
            }
            if(hit(x,y,680,610,145,38)){
                hapticsEnabled=!hapticsEnabled;prefs.edit().putBoolean("haptics",hapticsEnabled).apply();
                toast(hapticsEnabled?"Vibração ativada":"Vibração desativada");return;
            }
        }
    }

    public void setUsernameAccountActions(UsernameAccountAction authAction,
                                          Runnable syncAction, Runnable signOutAction) {
        usernameAccountAction=authAction;
        accountSyncAction=syncAction;
        accountSignOutAction=signOutAction;
    }
    public void setPackAnalyzerAction(Runnable action){packAnalyzerAction=action;}
    public void setAiCoachAction(AiCoachAction action){aiCoachAction=action;}
    public void setGameApiActions(GameApiAction promo, GameApiAction daily,
                                   GameApiAction mailFetch, GameApiAction mailClaim){
        promoRedeemAction=promo;dailyRewardAction=daily;
        mailFetchAction=mailFetch;mailClaimAction=mailClaim;
    }
    public void setShopPurchaseAction(GameApiAction action){shopPurchaseAction=action;}
    public void setMatchResultAction(GameApiAction action){matchResultAction=action;}
    public void setRewardClaimAction(GameApiAction action){rewardClaimAction=action;}
    public void setRoyaleSpinAction(GameApiAction action){royaleSpinAction=action;}
    private void showAiCoachDialog(){
        if(aiCoachAction==null){
            new AlertDialog.Builder(getContext()).setTitle("IA PROFESSORA")
                .setMessage("O serviço de ajuda ainda está inicializando. Feche esta janela e tente novamente.")
                .setPositiveButton("OK",null).show();
            return;
        }
        LinearLayout box=new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(18*getResources().getDisplayMetrics().density);
        box.setPadding(pad,pad,pad,pad);
        TextView hint=new TextView(getContext());
        hint.setText("Pergunte sobre controles, mapas, modos, mira, posicionamento ou estratégias.");
        box.addView(hint);
        EditText input=new EditText(getContext());
        input.setSingleLine(false);input.setMinLines(2);input.setMaxLines(4);
        input.setHint("Ex.: como sobreviver mais tempo no Battle Royale?");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(input);
        new AlertDialog.Builder(getContext()).setTitle("IA PROFESSORA")
            .setView(box).setNegativeButton("CANCELAR",null)
            .setPositiveButton("PERGUNTAR",(dialog,which)->{
                String question=input.getText().toString().trim();
                if(question.length()<2){toast("Escreva uma pergunta.");return;}
                if(!googleSignedIn){
                    new AlertDialog.Builder(getContext()).setTitle("ENTRE NA SUA CONTA")
                        .setMessage("A conversa abre normalmente, mas para enviar a pergunta à IA você precisa entrar ou criar uma conta BATTLE UNIVERSE.")
                        .setNegativeButton("CANCELAR",null)
                        .setPositiveButton("IR PARA CONTA",(d,w)->{page=PROFILE;invalidate();showUsernameAccountDialog();}).show();
                    return;
                }
                toast("IA analisando sua dúvida...");
                aiCoachAction.ask(question,(answer,error)->{
                    if(error!=null){new AlertDialog.Builder(getContext()).setTitle("IA BATTLE UNIVERSE")
                        .setMessage(error).setPositiveButton("OK",null).show();return;}
                    new AlertDialog.Builder(getContext()).setTitle("RESPOSTA DA IA")
                        .setMessage(answer).setPositiveButton("FECHAR",null).show();
                });
            }).show();
    }
    public void setGoogleAccountStatus(boolean configured, boolean signedIn,
                                       String username, String message) {
        googleConfigured=configured;
        googleSignedIn=signedIn;
        googleEmail=username==null?"":username;
        googleMessage=message==null?"":message;
        invalidate();
    }
    private void showUsernameAccountDialog() {
        LinearLayout form=new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(18*getResources().getDisplayMetrics().density);
        form.setPadding(pad,pad,pad,0);
        EditText username=new EditText(getContext());
        username.setSingleLine(true);
        username.setHint("Nome de usuário (3 a 18 caracteres)");
        username.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText password=new EditText(getContext());
        password.setSingleLine(true);
        password.setHint("Senha (mínimo 8 caracteres)");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView note=new TextView(getContext());
        note.setText("Use letras sem acento, números ou _. Guarde sua senha: não há recuperação por e-mail nesta versão.");
        note.setTextSize(12);
        form.addView(username);
        form.addView(password);
        form.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(getContext())
            .setTitle("CONTA BATTLE UNIVERSE")
            .setView(form)
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CRIAR CONTA",null)
            .setNeutralButton("ENTRAR",null)
            .create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(usernameAccountAction!=null)usernameAccountAction.authenticate(
                    username.getText().toString(),password.getText().toString(),true);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                if(usernameAccountAction!=null)usernameAccountAction.authenticate(
                    username.getText().toString(),password.getText().toString(),false);
                dialog.dismiss();
            });
        });
        dialog.show();
    }
    private void toast(String s){toast=s;toastUntil=System.currentTimeMillis()+1800;invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
}
