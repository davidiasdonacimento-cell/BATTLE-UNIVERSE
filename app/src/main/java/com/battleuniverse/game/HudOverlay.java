package com.battleuniverse.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
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
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.UUID;
public final class HudOverlay extends View {
    private static final int HOME=0, CHARACTERS=1, MODES_PAGE=2, MAPS_PAGE=3,
        INVENTORY=4, SETTINGS=5, PROFILE=6, SHOP=7, ROYALE=8, MISSIONS=9, WARDROBE=10,
        GAME=11, ROYALE_SPIN=12, EVENTS=13, FRIENDS=14, MAIL=15, ACHIEVEMENTS=16, PREMATCH=17, ACCESSORIES=18, DEV_MENU=19;
    private static final String[] NAV={"INÍCIO","PERSONAGENS","MODOS","MAPAS",
        "COFRE","AJUSTES","PERFIL","LOJA","ROYALE","MISSÕES","ROUPAS",
        "EVENTOS","EQUIPE","CORREIO","CONQUISTAS"};
    private static final int[] NAV_PAGES={HOME,CHARACTERS,MODES_PAGE,MAPS_PAGE,INVENTORY,SETTINGS,PROFILE,SHOP,ROYALE,MISSIONS,WARDROBE,EVENTS,FRIENDS,MAIL,ACHIEVEMENTS};
    private static final String[] SHOP_NAMES={"MOLDURA NEON","EMBLEMA BLOOB","TÍTULO CAMPEÃO",
        "CAMISA TÁTICA","CAMISA NEON","CAMISA FROST","CAMISA DESERTO",
        "BOTAS COMBATE","BOTAS NEON","BOTAS FROST","BOTAS DOURADAS",
        "CONJUNTO AURORA","CONJUNTO CAMPEÃO","CONJUNTO ORBITAL","CONJUNTO DESERTO",
        "SKIN VULCÃO","SKIN NEBULAR","SKIN CERRADO","BARBINHA DO VELHO","CALÇA ANGELICAL"};
    private static final String[] SHOP_KEYS={"shop_frame_neon","shop_emblem_bloob","shop_title_champion",
        "wear_shirt_tactical","wear_shirt_neon","wear_shirt_frost","wear_shirt_desert",
        "wear_boots_combat","wear_boots_neon","wear_boots_frost","wear_boots_gold",
        "wear_set_aurora","wear_set_champion","wear_set_orbit","wear_set_desert",
        "wear_set_volcano","wear_set_nebula","wear_set_cerrado","wear_beard_old","wear_pants_angelical"};
    private static final long[] SHOP_PRICES={500L,1000L,1500L,650L,800L,750L,700L,
        450L,600L,550L,700L,1800L,2200L,2500L,1900L,2900L,3200L,3500L,900L,1500L};
    private static final int[] SHOP_COLORS={0xFF8D42D7,0xFF08A9D8,0xFFE0A62B,
        0xFF344C5C,0xFF8D42D7,0xFFB8DDE8,0xFFB87843,
        0xFF4B5058,0xFF2FAFC4,0xFFCBDCE4,0xFFE0AE38,
        0xFF55B7B8,0xFFE0AE38,0xFF6B83C4,0xFFB87843,
        0xFFE64B24,0xFF3C70D8,0xFF5D9B39,0xFF9B806B,0xFF168BEA};
    private static final int[] ROYALE_LEVELS={2,4,6,8,10,12};
    private static final long[] ROYALE_REWARDS={40,60,80,100,120,150};
    private static final String[] MISSION_NAMES={"JOGUE UMA PARTIDA","ELIMINE 3 OPONENTES","COLETE 3 CRISTAIS"};
    private static final String[] MISSION_HINTS={"Entre no campo e saia após o pouso.",
        "Some eliminações durante as partidas.","Recolha cristais espalhados pelo mapa."};
    private static final int[] MISSION_GOALS={1,3,3};
    private static final long[] MISSION_XP={120,180,0};
    private static final long[] MISSION_DIAMONDS={0,0,80};
    private static final String[] MODE_NAMES={"BATTLE ROYALE","DUPLA","ESQUADRÃO","TREINAMENTO","GLADIADORES"};
    private static final String[] DUEL_WEAPONS={"DESERT EAGLE","MINI UZI","XM8","UMP"};
    private static final String[] MAP_NAMES={"BERMUDA"};
    private static final String[] MAP_DETAILS={"Ilha tropical • cidades • florestas • praias"};
    private static final String[] CHARACTER_NAMES={"CAIO","MAYA","DANTE","LUNA"};
    private static final String[] QUALITY_NAMES={"ECONÔMICO","EQUILIBRADO","ALTO"};
    private final GameSurfaceView game;
    private final SharedPreferences prefs;
    private final PlayerProfileStore profile;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r=new RectF();
    private int page=HOME, mode, map, character, quality=2, lookSensitivity=1, selectedDuelWeapon=1;
    private int generalSensitivity=100, aimSensitivity=100;
    private int selectedShirt,selectedBoots,selectedSet;
    private int equippedBeard,equippedHelmet,equippedBackpack,equippedVest;
    private boolean music=true, effects=true, forwarding, resultSpectating;
    private boolean invertCameraY, showMinimap=true, hapticsEnabled=true;
    private boolean onlineMode, tacticalMapVisible;
    private float scale=1, offsetX, offsetY;
    private String toast="";
    public interface UsernameAccountAction {
        void authenticate(String username, String password, boolean createAccount);
    }
    public interface RoyaleSpinAction {
        void spin(String requestId,RoyaleResult callback);
    }
    public interface RoyaleResult {
        void onResult(boolean success,String prizeId,String prizeName,boolean duplicate,long balance,String message);
    }
    public interface DailyRewardAction {
        void claim(DailyRewardResult callback);
    }
    public interface DailyRewardResult {
        void onResult(boolean claimed,long diamonds,String message);
    }
    public interface PromoCodeAction {
        void redeem(String code,PromoCodeResult callback);
    }
    public interface PromoCodeResult {
        void onResult(boolean success,long diamonds,String message);
    }
    private boolean googleConfigured, googleSignedIn;
    private String googleEmail="", googleMessage="Crie uma conta ou entre com nome e senha.";
    private UsernameAccountAction usernameAccountAction;
    private Runnable accountSyncAction, accountSignOutAction, recoveryCodeAction;
    private String deviceIdentity="";
    private long onlineDiamonds=-1L;
    private RoyaleSpinAction royaleSpinAction;
    private DailyRewardAction dailyRewardAction;
    private PromoCodeAction promoCodeAction;
    private boolean royaleRequesting,royaleServerDuplicate;
    private String royaleServerPrizeName="";
    private String pendingRoyaleRequestId="";
    private long toastUntil, lastTap;
    private int collectionPage=0;
    private int cofreTab=0;
    private int selectedPants;
    private AlertDialog accountDialog;
    private TextView accountDialogStatus;
    private int royaleRevealItem=-1;
    private long royaleRevealUntil=0L;
    private static final int[] ROYALE_PRIZE_ITEMS={3,7,11,18,19};
    private static final int[] ROYALE_CHANCES={40,30,20,9,1};
    private final SecureRandom royaleRandom=new SecureRandom();
    private boolean royaleSpinning,royalePreviewActive;
    private long royaleSpinStartedAt;
    private float royaleWheelRotation,royaleSpinStartRotation,royaleSpinEndRotation;
    private int royalePendingPrize=-1,royaleSelectedPreview=0;
    private String royaleResultMessage="";
    private int royalePreviewOriginalShirt,royalePreviewOriginalBoots,royalePreviewOriginalSet;
    private int royalePreviewOriginalBeard,royalePreviewOriginalHelmet,royalePreviewOriginalBackpack;
    private int royalePreviewOriginalVest,royalePreviewOriginalPants;

    public HudOverlay(Context context, GameSurfaceView game, SharedPreferences prefs) {
        super(context);
        this.game=game; this.prefs=prefs; this.profile=new PlayerProfileStore(prefs);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setClickable(true); setFocusable(true);
        mode=Math.max(0,Math.min(4,prefs.getInt("mode",0))); map=0;
        selectedDuelWeapon=Math.max(0,Math.min(3,prefs.getInt("duel_weapon",1)));
        character=prefs.getInt("character",0); quality=prefs.getInt("quality",2);
        onlineMode=prefs.getBoolean("online_mode",false);
        map=Math.max(0,Math.min(MAP_NAMES.length-1,map));
        music=prefs.getBoolean("music",true); effects=prefs.getBoolean("effects",true);
        lookSensitivity=prefs.getInt("look_sensitivity",1);
        generalSensitivity=prefs.getInt("sensitivity_general",lookSensitivity==0?65:(lookSensitivity==2?145:100));
        aimSensitivity=prefs.getInt("sensitivity_aim",100);
        generalSensitivity=Math.max(25,Math.min(200,generalSensitivity));
        aimSensitivity=Math.max(25,Math.min(200,aimSensitivity));
        invertCameraY=prefs.getBoolean("invert_camera_y",false);
        showMinimap=prefs.getBoolean("show_minimap",true);
        hapticsEnabled=prefs.getBoolean("haptics",true);
        ensureStarterWardrobe();
        selectedShirt=prefs.getInt("selected_shirt",1);
        selectedBoots=prefs.getInt("selected_boots",1);
        selectedSet=prefs.getInt("selected_set",0);
        selectedPants=prefs.getInt("selected_pants",0);
        equippedBeard=prefs.getInt("equipped_beard",0);
        equippedHelmet=prefs.getInt("equipped_helmet",0);
        equippedBackpack=prefs.getInt("equipped_backpack",0);
        equippedVest=prefs.getInt("equipped_vest",0);
        ensureDailyMissionWindow();
        game.setInvertCameraY(invertCameraY);
        game.setCharacter(character); game.setMap(map); game.setWardrobe(selectedShirt,selectedBoots,selectedSet);
        game.setPantsStyle(selectedPants);
        game.setEquipment(equippedBeard,equippedHelmet,equippedBackpack,equippedVest);
        game.setMatchMode(mode); game.setDuelWeapon(selectedDuelWeapon); game.setQuality(quality);
        game.setLookSensitivity(generalSensitivity/100.0f,aimSensitivity/100.0f); game.setGameScreen(false);
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
        drawBrand(c);drawProfile(c);
        // A tela inicial usa composição de lobby; a navegação lateral fica nas páginas internas.
        if(page!=HOME)drawSidebar(c);
        drawPage(c);
    }
    private void drawLobbyBackdrop(Canvas c) {
        // Vinheta translúcida para preservar a cena 3D renderizada pela engine.
        p.setShader(new LinearGradient(0,0,1280,720,
            new int[]{0x44051320,0x100C2835,0x5506131E},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,1280,720,p);p.setShader(null);
        p.setColor(0x1829BFD0);c.drawCircle(660,300,220,p);
        p.setColor(0x2229BFD0);c.drawCircle(660,300,300,p);
        p.setShader(new LinearGradient(0,0,0,720,
            new int[]{0x22030A12,0x00000000,0x44030A12},null,Shader.TileMode.CLAMP));
        c.drawRect(0,96,1280,720,p);p.setShader(null);
    }

    private void drawBrand(Canvas c) {
        String gameName=prefs.getString("server_game_name","BATTLE UNIVERSE");
        if("BATTLE UNIVERSE".equalsIgnoreCase(gameName)) {
            text(c,"BATTLE",34,47,30,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,"UNIVERSE",151,47,30,0xFF49D9E8,Typeface.create("sans-serif-black",Typeface.BOLD));
        } else {
            text(c,gameName,34,47,25,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        }
        text(c,"MUNDO  •  SOBREVIVÊNCIA  •  AVENTURA",36,68,10,0xFF9AB7C9,Typeface.DEFAULT_BOLD);
        pill(c,272,25,128,29,"LOBBY PRINCIPAL",0x332FD2E5,0xFF9BEAF4);
    }
    private void drawProfile(Canvas c) {
        long xp=profile.getXp();
        long diamonds=onlineDiamonds>=0L?onlineDiamonds:profile.getDiamonds();
        int level=profile.getLevel();
        String nickname=profile.getNickname();
        panel(c,1000,17,246,60,0xC5152938,0x557AA0B8,15);
        circle(c,1028,47,18,0xFF1F7584,
            prefs.getBoolean("shop_frame_neon",false)?0xFFB978FF:0xFF65DCE7);
        if(prefs.getBoolean("shop_emblem_bloob",false))
            text(c,"B",1025,51,10,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,nickname.length()>10?nickname.substring(0,10):nickname,1052,42,10,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"NÍVEL "+String.format(Locale.US,"%02d",level)+"  •  "+xp+" XP",1052,60,8,0xFF9BB6C6,Typeface.DEFAULT);
        if(prefs.getBoolean("shop_title_champion",false))
            text(c,"CAMPEÃO",1052,73,6,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        panel(c,1164,29,72,36,0x553B5262,0x446A9BAB,10);
        text(c,"◆ "+formatDiamonds(diamonds),1170,51,8,0xFFFFD16E,Typeface.DEFAULT_BOLD);
    }
    private String characterName(int index) {
        int safe=Math.max(0,Math.min(3,index));
        if(safe==character) {
            String accountName=prefs.getString("character_display_name","").trim();
            if(!accountName.isEmpty())return accountName;
        }
        try {
            JSONObject names=new JSONObject(prefs.getString("server_character_names","{}"));
            String[] ids={"caio","maya","dante","luna"};
            String value=names.optString(ids[safe],"").trim();
            if(!value.isEmpty())return value;
        } catch(Exception ignored) {}
        return CHARACTER_NAMES[safe];
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
        else if(page==ACCESSORIES)drawAccessoriesPage(c);
        else if(page==EVENTS)drawEventsPage(c);
        else if(page==FRIENDS)drawFriendsPage(c);
        else if(page==MAIL)drawMailPage(c);
        else if(page==ACHIEVEMENTS)drawAchievementsPage(c);
        else if(page==PREMATCH)drawPreMatch(c);
        else if(page==DEV_MENU)drawDevMenu(c);
    }
    private void drawHome(Canvas c){
        // Lobby principal com composição inspirada no gênero battle royale,
        // mantendo identidade, rótulos e navegação próprios do BATTLE UNIVERSE.
        eyebrow(c,270,133,"BATTLE UNIVERSE  •  TEMPORADA 03");
        pill(c,270,149,240,27,"PERSONAGEM  •  "+characterName(character),
            0xAA123D4A,0xFF9BEAF4);
        text(c,"Prepare-se para a próxima partida",272,198,13,0xFFE5F2F7,
            Typeface.create("sans-serif-medium",Typeface.NORMAL));
        text(c,"Bermuda",272,222,10,0xFF91B6C6,Typeface.DEFAULT_BOLD);

        // Menu principal à esquerda: cada atalho abre uma página existente.
        panel(c,20,119,226,340,0xC90A1B2B,0x557AA9BC,18);
        text(c,"CENTRAL DE JOGO",40,145,10,0xFF79DCE7,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        String[] menuNames={"LOJA","ROYALE","MISSÕES","EVENTOS","AMIGOS"};
        int[] menuPages={SHOP,ROYALE,MISSIONS,EVENTS,FRIENDS};
        int[] menuColors={0xFF285D79,0xFF73501F,0xFF315D55,0xFF425C7A,0xFF1A6372};
        for(int i=0;i<menuNames.length;i++){
            float y=158+i*57f;
            panel(c,30,y,206,47,0xA91B3040,0x335A8297,11);
            circle(c,53,y+23.5f,14,menuColors[i],0xFF9BC9D2);
            drawNavIcon(c,i==0?2:i==1?3:i==2?4:i==3?0:1,53,y+23.5f,true);
            text(c,menuNames[i],77,y+28,10,0xFFEAF4F8,
                Typeface.create("sans-serif-black",Typeface.BOLD));
            chevron(c,220,y+24);
        }

        // Painel de equipe transparente: vagas não são apresentadas como jogadores reais.
        panel(c,1000,112,252,304,0xC90A1B2B,0x557AA9BC,17);
        text(c,"SUA EQUIPE",1020,140,11,0xFFEAF4F8,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        pill(c,1130,121,103,24,"OFFLINE",0x553B6172,0xFFBBD0D9);
        for(int i=0;i<3;i++){
            float y=157+i*62f;
            panel(c,1016,y,220,52,0x77172C3B,0x335A8297,10);
            circle(c,1043,y+26,16,0xFF254A5C,0xFF6F9AAA);
            text(c,"+",1038,y+32,18,0xFFD5E7EE,Typeface.DEFAULT_BOLD);
            text(c,"VAGA DISPONÍVEL",1070,y+23,9,0xFFDCEAF0,Typeface.DEFAULT_BOLD);
            text(c,"Convites online indisponíveis",1070,y+37,7,0xFF8FAAB8,Typeface.DEFAULT);
        }
        pill(c,1018,354,216,39,"GERENCIAR AMIGOS",0xFF176A75,Color.WHITE);
        text(c,"Contatos locais não representam jogadores online.",1019,407,7,
            0xFF91AAB7,Typeface.DEFAULT);

        // Barra inferior de atalhos para as páginas já implementadas.
        panel(c,265,626,574,70,0xC90A1B2B,0x557AA9BC,15);
        String[] bottomNames={"COFRE","PERSONAGENS","ROUPAS","PERFIL"};
        int[] bottomPages={INVENTORY,CHARACTERS,WARDROBE,PROFILE};
        float[] bottomX={278,418,558,698};
        float[] bottomW={130,130,130,128};
        for(int i=0;i<bottomNames.length;i++){
            panel(c,bottomX[i],640,bottomW[i],42,0xA91B3040,0x335A8297,10);
            text(c,bottomNames[i],bottomX[i]+12,666,8,0xFFE2EDF2,
                Typeface.create("sans-serif-black",Typeface.BOLD));
            chevron(c,bottomX[i]+bottomW[i]-13,661);
        }

        // Seleção de modo, mapa único Bermuda e entrada na partida.
        panel(c,858,520,394,176,0xD20A1B2B,0x667AA9BC,17);
        eyebrow(c,878,543,"PRÓXIMA PARTIDA");
        panel(c,876,553,176,45,0xD51B3C4D,0x557AA9BC,10);
        text(c,MODE_NAMES[mode],891,572,11,Color.WHITE,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,mode==0?"SOLO":mode==1?"DUPLA":mode==2?"ESQUADRÃO":
            mode==3?"TREINAMENTO":"GLADIADORES",891,588,7,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        chevron(c,1038,576);
        panel(c,1062,553,172,45,0xD51B3C4D,0x557AA9BC,10);
        text(c,"MAPA",1077,570,7,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        text(c,"BERMUDA",1077,588,11,Color.WHITE,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        pill(c,878,609,92,29,"OFFLINE",onlineMode?0x553B6172:0xFF176A75,Color.WHITE);
        pill(c,977,609,92,29,"ONLINE",onlineMode?0xFF7A4D25:0x553B6172,Color.WHITE);
        gradientButton(c,1080,608,154,58,"JOGAR","ENTRAR",0xFFFFB45E,0xFFE97543);
        text(c,"Partida offline: jogador + bots identificados",878,680,7,
            0xFF9DB6C5,Typeface.DEFAULT);
    }
    private void drawCharacters(Canvas c){
        eyebrow(c,270,175,"PERSONALIZAÇÃO • MODELO DO LOBBY");
        title(c,"OPERADORES",270,224);
        subtitle(c,"Selecione um operador. O personagem 3D central acompanha a escolha em tempo real.",270,252);

        panel(c,270,276,190,354,0xA9152938,0x557AA9BC,17);
        eyebrow(c,290,304,"SELECIONE");
        String[] roles={"MASCULINO","FEMININO","MASCULINO","FEMININO"};
        int[] colors={0xFF08A9D8,0xFF344C5C,0xFF8D42D7,0xFFE0A62B};
        for(int i=0;i<4;i++){
            float y=320+i*72f;boolean active=character==i;
            panel(c,282,y,166,62,active?0xD51B5B69:0x77304758,
                active?0xFF48D7E1:0x335D8495,12);
            circle(c,306,y+30,18,colors[i],active?0xFF68E9EF:0xFF8AA5B4);
            text(c,new String[]{"C","M","D","L"}[i],300,y+36,13,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,characterName(i),334,y+27,12,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,roles[i],334,y+44,8,0xFF9DB6C5,Typeface.DEFAULT);
            if(active)text(c,"●",430,y+36,9,0xFF76E7CF,Typeface.DEFAULT_BOLD);
        }

        // Mantém o centro livre para a visualização 3D renderizada pela engine OpenGL.
        p.setColor(0x223BD8E5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.0f);
        c.drawOval(488,520,850,594,p);p.setStyle(Paint.Style.FILL);
        pill(c,548,286,242,27,"VISUALIZAÇÃO 3D AO VIVO",0xAA123D4A,0xFF9BEAF4);
        text(c,"O modelo aparece no cenário atrás desta interface.",492,620,9,0xFF9CB5C2,Typeface.DEFAULT);

        panel(c,870,276,342,354,0xB8193040,0x557AA9BC,17);
        eyebrow(c,894,306,"OPERADOR SELECIONADO");
        text(c,CHARACTER_NAMES[character],894,350,27,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,roles[character]+"  •  NÍVEL DE CONTA",894,374,9,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        panel(c,894,398,294,1,0x557AA9BC,0x00000000,0);
        eyebrow(c,894,428,"EQUIPAMENTO ATUAL");
        String[] shirts={"SEM CAMISA","TÁTICA","NEON","FROST","DESERTO"};
        String[] boots={"SEM BOTAS","COMBATE","NEON","FROST","DOURADAS"};
        String[] sets={"SEM CONJUNTO","AURORA","CAMPEÃO","ORBITAL","DESERTO"};
        int shirt=Math.max(0,Math.min(4,selectedShirt));
        int boot=Math.max(0,Math.min(4,selectedBoots));
        int set=Math.max(0,Math.min(4,selectedSet));
        text(c,"CAMISA  •  "+shirts[shirt],894,461,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"BOTAS  •  "+boots[boot],894,487,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"CONJUNTO  •  "+sets[set],894,513,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"As peças equipadas acompanham o operador",894,552,9,0xFF9DB6C5,Typeface.DEFAULT);
        text(c,"na partida local. Aparência sem bônus de poder.",894,568,9,0xFF9DB6C5,Typeface.DEFAULT);
        gradientButton(c,894,584,294,34,"ABRIR GUARDA-ROUPA","PERSONALIZAR VISUAL",0xFF21B9C4,0xFF176A75);
    }
    private void drawModes(Canvas c){
        eyebrow(c,270,175,"SELECIONE A EXPERIÊNCIA");title(c,"MODOS DE JOGO",270,224);
        subtitle(c,"Escolha uma experiência offline com regras próprias e IA identificada.",270,252);
        String[] desc={"Você contra 29 bots IA","Você + 1 aliado • 28 bots IA","Você + 3 aliados • 26 bots IA","Alvos, treino e exploração","Duelo 1 contra 1 • primeiro a 5 eliminações"};
        String[] counts={"30 PARTICIPANTES","30 PARTICIPANTES","30 PARTICIPANTES","TREINAMENTO","2 PARTICIPANTES"};
        for(int i=0;i<5;i++){
            float x=270+(i%3)*292,y=290+(i/3)*150;boolean active=mode==i;
            panel(c,x,y,270,126,active?0xD51B5B69:0xAA193040,active?0xFF48D7E1:0x335D8495,17);
            pill(c,x+17,y+16,92,23,counts[i],0x553A6B7C,0xFFB9E8EF);
            text(c,MODE_NAMES[i],x+18,y+70,20,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,desc[i],x+18,y+96,11,0xFFAAC1CE,Typeface.DEFAULT);
            if(active)circle(c,x+242,y+25,7,0xFF5CE4DA,0xFFB6FFF5);
        }
    }
    private void drawPreMatch(Canvas c){
        boolean gladiators=mode==4;
        eyebrow(c,270,175,gladiators?"PREPARAÇÃO LOCAL • DUELO GLADIADORES":"PREPARAÇÃO LOCAL • BATTLE ROYALE");
        title(c,gladiators?"DUELO GLADIADORES":"SALA PRÉ-PARTIDA",270,224);
        subtitle(c,gladiators?"Escolha seu equipamento. A IA também recebe uma arma válida.":"Confira o operador, o mapa e a composição antes de iniciar.",270,252);
        panel(c,270,270,570,350,0xB8193040,0x557AA9BC,18);
        eyebrow(c,292,300,gladiators?"PARTICIPANTES DO DUELO":"PARTICIPANTES DA PARTIDA OFFLINE");
        panel(c,292,316,526,48,0xAA123D4A,0x6654DCE5,12);
        text(c,"01  VOCÊ  •  "+CHARACTER_NAMES[character],308,346,13,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        int botSlots=gladiators?1:29;
        text(c,gladiators?"01 OPONENTE IA  •  PRIMEIRO A 5 ELIMINAÇÕES":"29 VAGAS DE BOT IA  •  CARREGADAS AO INICIAR",292,390,11,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        for(int i=0;i<botSlots;i++){
            int col=gladiators?0:i%6,row=gladiators?0:i/6;
            float x=292+col*88f,y=405+row*35f;
            panel(c,x,y,80,28,0x66304455,0x334F7B8D,7);
            text(c,gladiators?"IA 1V1":"BOT IA "+String.format(Locale.US,"%02d",i+1),x+7,y+18,8.2f,0xFFB9CBD5,Typeface.DEFAULT_BOLD);
        }
        panel(c,292,596,526,1,0x557AA9BC,0x00000000,0);
        text(c,"MAPA: "+MAP_NAMES[map]+"   •   MODO: "+MODE_NAMES[mode],292,581,10,0xFF72DCE6,Typeface.DEFAULT_BOLD);
        panel(c,862,270,350,350,0xB8193040,0x557AA9BC,18);
        eyebrow(c,886,302,"OPERADOR E EQUIPAMENTO");
        circle(c,1037,390,62,0xFF1C5968,0xFF57D8E5);
        text(c,new String[]{"C","M","D","L"}[character],1019,405,42,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,CHARACTER_NAMES[character],886,490,20,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Camisa, botas e conjunto equipados",886,516,10,0xFF9DB6C5,Typeface.DEFAULT);
        text(c,gladiators?"IA adversária • equipamento próprio":"Partida offline • bots identificados como IA",886,546,10,0xFFFFCF83,Typeface.DEFAULT_BOLD);
        if(gladiators) {
            text(c,"SUA ARMA",886,565,8,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
            for(int i=0;i<4;i++)pill(c,886+i*76,572,70,25,DUEL_WEAPONS[i],
                selectedDuelWeapon==i?0xFF176A75:0x553A6B7C,0xFFB9E8EF);
            text(c,"IA: EQUIPAMENTO AUTOMÁTICO • 2 PARTICIPANTES",886,610,8,0xFF9DB6C5,Typeface.DEFAULT_BOLD);
        } else {
            pill(c,886,572,144,25,"30 PARTICIPANTES",0x553A6B7C,0xFFB9E8EF);
            pill(c,1038,572,150,25,"SERVIDOR: LOCAL",0x553A6B7C,0xFFB9E8EF);
        }
        pill(c,270,636,170,34,"VOLTAR AO LOBBY",0x553B6172,0xFFBBD0D9);
        gradientButton(c,862,636,350,34,mode==4?"INICIAR DUELO":"INICIAR PARTIDA",mode==4?"ARENA 1V1 • META DE 5":"CARREGAR AVIÃO E MAPA",0xFFFFB45E,0xFFE97543);
    }
    private void beginOfflineMatch(){
        ensureDailyMissionWindow();
        prefs.edit().putBoolean("match_recorded_for_missions",false)
            .putBoolean("match_reward_recorded",false)
            .putLong("match_reward_session_id",System.currentTimeMillis()).apply();
        resultSpectating=false;game.setSpectatorMode(false);
        game.setCharacter(character);game.setMap(map);game.setMatchMode(mode);game.setDuelWeapon(selectedDuelWeapon);
        game.setTrainingGodMode(prefs.getBoolean("modmenu2_god_mode",false)&&mode==3&&!onlineMode);
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
        eyebrow(c,270,175,"COFRE • CATEGORIAS EM LISTA COM PRÉVIAS INDIVIDUAIS");
        title(c,"COFRE",270,224);
        subtitle(c,"COLEÇÃO e MODA em seções empilhadas. Cada prévia mostra somente a peça.",270,252);
        panel(c,270,266,570,40,0xAE193040,0x335D8495,13);
        text(c,"◆ "+formatDiamonds(profile.getDiamonds())+" DIAMANTES   •   ● "+formatDiamonds(profile.getCoins())+" MOEDAS",288,292,11,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        pill(c,270,312,150,29,cofreTab==0?"COLEÇÃO  ✓":"COLEÇÃO",cofreTab==0?0xFF176A75:0x553B6172,Color.WHITE);
        pill(c,430,312,150,29,cofreTab==1?"MODA  ✓":"MODA",cofreTab==1?0xFF176A75:0x553B6172,Color.WHITE);
        String[][] labels=cofreTab==0
            ?new String[][]{{"SOMBRA","VIGOR","ORION","VELHO"},{"TÁTICO","FROST","VISOR"},{"CAMPO","ORBITAL","MÉDICA"},{"TÁTICO","PESADO","TECNO"}}
            :new String[][]{{"PADRÃO","ANGELICAL"},{"TÁTICA","NEON","FROST","DESERTO"},{"COMBATE","NEON","FROST","DOURADAS"},{"AURORA","CAMPEÃO","ORBITAL","DESERTO"}};
        String[] categories=cofreTab==0
            ?new String[]{"BARBAS","CAPACETES","MOCHILAS","COLETES"}
            :new String[]{"CALÇAS","CAMISAS","BOTAS","CONJUNTOS"};
        int[] types=cofreTab==0?new int[]{0,1,2,3}:new int[]{8,4,5,6};
        int[] colors=cofreTab==0?new int[]{0xFF9B806B,0xFF65A9C4,0xFF58A68A,0xFFB18A55}
            :new int[]{0xFF168BEA,0xFF8D42D7,0xFF2FAFC4,0xFF55B7B8};
        for(int section=0;section<4;section++){
            float y=350+section*78f;
            panel(c,270,y,570,70,0xA91A3040,0x335D8495,12);
            text(c,categories[section],284,y+39,9,0xFF70DCE6,Typeface.create("sans-serif-black",Typeface.BOLD));
            for(int item=0;item<labels[section].length;item++){
                float x=390+item*110f;
                boolean owned=true,active=false;int variant=item+1;String state="EQUIPAR";
                if(cofreTab==0){
                    if(section==0){owned=variant!=4||prefs.getBoolean("wear_beard_old",false);active=equippedBeard==variant;}
                    else if(section==1)active=equippedHelmet==variant;
                    else if(section==2)active=equippedBackpack==variant;
                    else active=equippedVest==variant;
                }else if(section==0){
                    variant=item;owned=item==0||prefs.getBoolean("wear_pants_angelical",false);active=selectedPants==item;
                }else if(section==1){
                    owned=prefs.getBoolean(SHOP_KEYS[3+item],false);active=selectedSet==0&&selectedShirt==item+1;
                }else if(section==2){
                    owned=prefs.getBoolean(SHOP_KEYS[7+item],false);active=selectedSet==0&&selectedBoots==item+1;
                }else{
                    owned=prefs.getBoolean(SHOP_KEYS[11+item],false);active=selectedSet==item+1;
                }
                state=active?"EQUIPADO":owned?"EQUIPAR":"NO ROYALE";
                panel(c,x,y+7,100,56,active?0xFF176A75:0x77304758,active?0xFF58DDE6:0x335D8495,8);
                drawCollectionPreview(c,x+17,y+28,types[section],variant,colors[section]);
                text(c,labels[section][item],x+31,y+29,6.5f,Color.WHITE,Typeface.DEFAULT_BOLD);
                text(c,state,x+31,y+43,5.8f,owned?0xFF9ED8D5:0xFFFFC66F,Typeface.DEFAULT_BOLD);
            }
        }
        String[] history=profile.getRecentLedgerEntries(1);
        if(history.length>0){
            String recent=history[0].length()>78?history[0].substring(0,75)+"...":history[0];
            text(c,"ÚLTIMA MOVIMENTAÇÃO • "+recent,270,674,7.0f,0xFF9CB4C1,Typeface.DEFAULT);
        }
    }

    private void drawCollectionPreview(Canvas c,float cx,float cy,int type,int variant,int color){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(0xFF142B39);
        c.drawRoundRect(cx-15,cy-15,cx+15,cy+15,7,7,p);
        p.setColor(color);
        if(type==0){
            Path beard=new Path();beard.moveTo(cx-10,cy-6);beard.cubicTo(cx-7,cy-1,cx-7,cy+8,cx,cy+12);
            beard.cubicTo(cx+7,cy+8,cx+7,cy-1,cx+10,cy-6);beard.cubicTo(cx+5,cy-2,cx-5,cy-2,cx-10,cy-6);
            c.drawPath(beard,p);
        }else if(type==1){
            c.drawArc(cx-11,cy-10,cx+11,cy+10,180,180,false,p);c.drawRect(cx-11,cy,cx+11,cy+5,p);
            c.drawRect(cx-13,cy+4,cx+13,cy+7,p);
        }else if(type==2){
            c.drawRoundRect(cx-9,cy-12,cx+9,cy+12,5,5,p);
            p.setColor(0xFF142B39);c.drawRoundRect(cx-4,cy-3,cx+4,cy+5,2,2,p);
        }else if(type==3){
            Path vest=new Path();vest.moveTo(cx-10,cy-11);vest.lineTo(cx-3,cy-11);vest.lineTo(cx,cy-4);
            vest.lineTo(cx+3,cy-11);vest.lineTo(cx+10,cy-11);vest.lineTo(cx+8,cy+11);
            vest.lineTo(cx-8,cy+11);vest.close();c.drawPath(vest,p);
            p.setColor(0xFF142B39);c.drawLine(cx,cy-3,cx,cy+10,p);
        }else if(type==4){
            Path shirt=new Path();shirt.moveTo(cx-6,cy-11);shirt.lineTo(cx-14,cy-5);shirt.lineTo(cx-10,cy+1);
            shirt.lineTo(cx-7,cy-1);shirt.lineTo(cx-7,cy+11);shirt.lineTo(cx+7,cy+11);
            shirt.lineTo(cx+7,cy-1);shirt.lineTo(cx+10,cy+1);shirt.lineTo(cx+14,cy-5);
            shirt.lineTo(cx+6,cy-11);shirt.close();c.drawPath(shirt,p);
        }else if(type==5){
            Path boot=new Path();boot.moveTo(cx-7,cy-11);boot.lineTo(cx+4,cy-11);boot.lineTo(cx+4,cy+4);
            boot.lineTo(cx+12,cy+8);boot.lineTo(cx+12,cy+12);boot.lineTo(cx-10,cy+12);
            boot.lineTo(cx-10,cy+6);boot.lineTo(cx-7,cy+4);boot.close();c.drawPath(boot,p);
        }else if(type==6){
            Path set=new Path();set.moveTo(cx,cy-13);set.lineTo(cx+12,cy);set.lineTo(cx,cy+13);
            set.lineTo(cx-12,cy);set.close();c.drawPath(set,p);
        }else if(type==8){
            Path pants=new Path();pants.moveTo(cx-10,cy-12);pants.lineTo(cx+10,cy-12);
            pants.lineTo(cx+8,cy+11);pants.lineTo(cx+2,cy+11);pants.lineTo(cx,cy+1);
            pants.lineTo(cx-2,cy+11);pants.lineTo(cx-8,cy+11);pants.close();c.drawPath(pants,p);
            p.setColor(0xFFEAF8FF);c.drawLine(cx,cy-10,cx,cy+2,p);
            p.setColor(0xFF20D7F0);c.drawLine(cx-7,cy-5,cx-6,cy+8,p);
            c.drawLine(cx+7,cy-5,cx+6,cy+8,p);
        }else{
            c.drawRoundRect(cx-10,cy-10,cx+10,cy+10,4,4,p);
            p.setColor(0xFFFFFFFF);c.drawCircle(cx,cy,4,p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawSensitivityBar(Canvas c,String label,int value,float y){
        text(c,label,292,y+6,11,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,value+"%",455,y+6,10,0xFF75E2EA,Typeface.DEFAULT_BOLD);
        for(int i=1;i<=8;i++){
            float x=520+(i-1)*37f;
            pill(c,x,y-13,32,27,i==8?"200":String.valueOf(i*25),
                value>=i*25?0xFF176A75:0x553B6172,Color.WHITE);
        }
    }
    private void drawSettings(Canvas c){
        eyebrow(c,270,175,"AJUSTES DO MOTOR • SALVOS NESTE APARELHO");
        title(c,"CONFIGURAÇÕES",270,224);
        subtitle(c,"Sensibilidade aplicada à câmera; qualidade aplicada à renderização.",270,252);
        panel(c,270,278,570,104,0xAE193040,0x335D8495,16);
        eyebrow(c,292,304,"SENSIBILIDADE GERAL • CÂMERA LIVRE");
        drawSensitivityBar(c,"GERAL",generalSensitivity,342);
        panel(c,270,394,570,104,0xAE193040,0x335D8495,16);
        eyebrow(c,292,420,"SENSIBILIDADE AO MIRAR");
        drawSensitivityBar(c,"MIRA",aimSensitivity,458);
        panel(c,270,510,570,78,0xAE193040,0x335D8495,15);
        eyebrow(c,292,536,"QUALIDADE GRÁFICA • ENGINE OPENGL ES");
        for(int i=0;i<3;i++)pill(c,520+i*100,545,92,32,QUALITY_NAMES[i],
            quality==i?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,600,570,64,0xAE193040,0x335D8495,14);
        text(c,"INVERTER EIXO VERTICAL",290,625,10,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,470,609,105,30,invertCameraY?"ATIVADO":"DESATIVADO",invertCameraY?0xFF176A75:0x553B6172,Color.WHITE);
        text(c,"MINIMAPA",590,625,10,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,675,609,75,30,showMinimap?"ON":"OFF",showMinimap?0xFF176A75:0x553B6172,Color.WHITE);
        text(c,"HÁPTICA",765,625,9,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,790,609,42,30,hapticsEnabled?"ON":"OFF",hapticsEnabled?0xFF176A75:0x553B6172,Color.WHITE);
        pill(c,270,660,180,28,"ABRIR MODMENU2",0xFF176A75,0xFFFFFFFF);
    }

    private void drawDevMenu(Canvas c) {
        eyebrow(c,270,175,"FERRAMENTAS DE DESENVOLVIMENTO • MODMENU2");
        title(c,"MODMENU2",270,224);
        subtitle(c,"Recursos de teste isolados do modo online e da carteira da conta.",270,252);
        panel(c,270,278,570,118,0xAE193040,0x557AA9BC,16);
        eyebrow(c,292,307,"GOD MODE • SOMENTE TREINAMENTO OFFLINE");
        boolean enabled=prefs.getBoolean("modmenu2_god_mode",false);
        text(c,"Imunidade a dano e vida restaurada durante o Treinamento.",292,337,10,0xFFB3C8D4,Typeface.DEFAULT);
        pill(c,650,316,160,42,enabled?"GOD MODE: ON":"GOD MODE: OFF",
            enabled?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,414,570,116,0xAE193040,0x557AA9BC,16);
        eyebrow(c,292,444,"CARTEIRA ONLINE");
        text(c,"Diamantes e moedas são controlados pelo servidor; este menu não altera saldos.",292,472,10,0xFFB3C8D4,Typeface.DEFAULT);
        text(c,"Para ajustes administrativos, use a API autenticada de administração.",292,493,9,0xFF8EDFD4,Typeface.DEFAULT);
        if(mode!=3||onlineMode) {
            panel(c,270,552,570,58,0xAA5A3428,0xFFFFB45E,13);
            text(c,"Selecione TREINAMENTO e OFFLINE para ativar o God Mode.",292,587,10,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        } else {
            panel(c,270,552,570,58,0xAA176A75,0xFF58DDE6,13);
            text(c,"AMBIENTE DE TESTE LIBERADO • SEM EFEITO EM PARTIDAS ONLINE",292,587,9,Color.WHITE,Typeface.DEFAULT_BOLD);
        }
    }

    private void drawProfilePage(Canvas c) {
        eyebrow(c,270,175,googleSignedIn?"CONTA BATTLE UNIVERSE VINCULADA":"PERFIL E CONTA BATTLE UNIVERSE");
        title(c,"PERFIL DO JOGADOR",270,224);
        subtitle(c,"Crie uma conta com nome e senha. Não precisa entrar com Google.",270,252);
        long xp=profile.getXp(),diamonds=profile.getDiamonds();
        int level=profile.getLevel();
        panel(c,270,286,570,196,0xAE193040,0x557AA9BC,18);
        circle(c,326,365,38,0xFF1F7584,
            prefs.getBoolean("shop_frame_neon",false)?0xFFB978FF:0xFF65DCE7);
        text(c,prefs.getString("nickname","JOGADOR"),382,346,23,Color.WHITE,
            Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"NÍVEL "+level+"  •  "+xp+" XP",382,374,13,0xFF9FC0CF,Typeface.DEFAULT_BOLD);
        text(c,"◆ "+formatDiamonds(diamonds)+" DIAMANTES",382,399,15,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        text(c,"● "+formatDiamonds(profile.getCoins())+" MOEDAS",382,420,11,0xFF82E5DC,Typeface.DEFAULT_BOLD);
        text(c,"PARTIDAS "+profile.getMatchesPlayed()+"  •  VITÓRIAS "+profile.getWins()
            +"  •  ELIMS "+profile.getEliminations(),292,440,9,0xFF82E5DC,Typeface.DEFAULT_BOLD);
        text(c,prefs.getBoolean("code_100k_redeemed",false)
            ?"CÓDIGO 100K JÁ RESGATADO NESTE PERFIL"
            :"CÓDIGO SECRETO 100K DISPONÍVEL",292,458,9,0xFF83DCD5,Typeface.DEFAULT_BOLD);
        String deviceLine=deviceIdentity.isEmpty()?"ID DO DISPOSITIVO • REGISTRANDO...":"ID DO DISPOSITIVO • "+deviceIdentity;
        if(deviceLine.length()>68)deviceLine=deviceLine.substring(0,65)+"...";
        text(c,deviceLine,292,476,8,0xFF9BC4D0,Typeface.DEFAULT_BOLD);
        panel(c,270,486,570,58,googleSignedIn?0xCC176A75:0xA91A3040,
            googleSignedIn?0xFF58DDE6:0x335D8495,15);
        text(c,googleSignedIn?"CONTA VINCULADA • TOQUE PARA SINCRONIZAR":
            (googleConfigured?"CRIAR CONTA / ENTRAR":"SERVIDOR DE CONTAS NÃO CONFIGURADO"),
            292,509,12,Color.WHITE,Typeface.DEFAULT_BOLD);
        String accountLine=googleSignedIn&&!googleEmail.isEmpty()?"@"+googleEmail:googleMessage;
        if(accountLine.length()>68)accountLine=accountLine.substring(0,65)+"...";
        text(c,accountLine,292,530,9,0xFFBBD0D9,Typeface.DEFAULT);
        panel(c,270,558,270,70,0xCC176A75,0xFF58DDE6,16);
        text(c,googleSignedIn?"CÓDIGO DE RECUPERAÇÃO":"RESGATAR CÓDIGO",294,586,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,googleSignedIn?"Gerar um novo código secreto":"Digite seu código secreto",294,607,10,0xFFD2F6F8,Typeface.DEFAULT);
        panel(c,558,558,282,70,0xA91A3040,0x335D8495,16);
        text(c,"EDITAR NICKNAME",582,586,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Nome do seu perfil",582,607,10,0xFF9EB6C4,Typeface.DEFAULT);
        text(c,"Use a mesma conta em APKs futuros para recuperar o progresso.",272,660,10,0xFF8FAAB8,Typeface.DEFAULT);
        if(googleSignedIn)pill(c,690,632,150,25,"SAIR DA CONTA",0x553B6172,0xFFBBD0D9);
    }
    private void drawShopPage(Canvas c) {
        eyebrow(c,270,175,"LOJA • PREÇOS FIXOS EM DIAMANTES VIRTUAIS");
        title(c,"ROUPAS E COSMÉTICOS",270,224);
        subtitle(c,"Camisas, botas e conjuntos completos. Cada item mostra seu preço antes da compra.",270,252);
        panel(c,270,266,570,48,0xA91A3040,0x335D8495,14);
        text(c,"SALDO  ◆ "+formatDiamonds(profile.getDiamonds())+"   ● "+formatDiamonds(profile.getCoins()),292,297,13,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        for(int i=0;i<SHOP_NAMES.length;i++) {
            int col=i%3,row=i/3;float x=270+col*192f,y=320+row*47f;
            boolean owned=prefs.getBoolean(SHOP_KEYS[i],false);
            panel(c,x,y,174,42,owned?0xB51A4650:0xA91A3040,
                owned?0xFF4BD4C9:0x335D8495,10);
            circle(c,x+21,y+21,12,SHOP_COLORS[i],0xFFB5D6E1);
            text(c,SHOP_NAMES[i],x+39,y+17,7.2f,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            String priceLabel=i<3?"● "+SHOP_PRICES[i]+" MOEDAS":"◆ "+SHOP_PRICES[i]+" DIAMANTES";
            text(c,owned?"JÁ ADQUIRIDO":priceLabel,x+39,y+32,6.5f,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        }
        text(c,"Itens cosméticos diretos • sem roleta paga ou prêmio aleatório.",272,664,9,0xFF8FAAB8,Typeface.DEFAULT);
    }
    private void drawWardrobePage(Canvas c) {
        eyebrow(c,270,175,"PERSONALIZAÇÃO EQUIPÁVEL");title(c,"CAMISAS, BOTAS E CONJUNTOS",270,224);
        subtitle(c,"Toque em um item desbloqueado para vestir o operador 3D.",270,252);
        pill(c,270,260,150,25,"REMOVER CONJUNTO",0x553B6172,0xFFD7E6ED);
        pill(c,690,260,150,25,"ACESSÓRIOS 3D",0xFF176A75,0xFFD7E6ED);
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
        String[] sets={"AURORA","CAMPEÃO","ORBITAL","DESERTO","VULCÃO","NEBULAR","CERRADO"};
        for(int i=0;i<7;i++) {
            int col=i%4,row=i/4;float x=270+col*146f,y=552+row*72f;
            boolean owned=prefs.getBoolean(SHOP_KEYS[11+i],false);
            boolean active=selectedSet==i+1;
            panel(c,x,y,132,64,active?0xB51A5B69:0xA91A3040,
                active?0xFF48D7E1:0x335D8495,12);
            circle(c,x+20,y+20,11,SHOP_COLORS[11+i],0xFFB5D6E1);
            text(c,sets[i],x+38,y+23,7.5f,Color.WHITE,Typeface.DEFAULT_BOLD);
            pill(c,x+8,y+34,116,23,active?"EQUIPADO":owned?"VESTIR":"BLOQUEADO",
                active?0xFF176A75:owned?0xFF345D79:0x553B6172,Color.WHITE);
        }
    }

    private void drawAccessoriesPage(Canvas c) {
        eyebrow(c,270,175,"OFICINA DE EQUIPAMENTOS • MODELOS 3D");
        title(c,"ACESSÓRIOS DO OPERADOR",270,224);
        subtitle(c,"Barbas, capacetes, mochilas e coletes acompanham o corpo no lobby e na partida.",270,252);
        pill(c,674,184,166,32,"VOLTAR ÀS ROUPAS",0x553B6172,0xFFD7E6ED);
        String[] names={"BARBAS 3D","CAPACETES","MOCHILAS","COLETES"};
        String[][] options={{"SEM","SOMBRA","VIGOR","ORION","VELHO"},{"SEM","TÁTICO","FROST","VISOR"},
            {"SEM","CAMPO","ORBITAL","MÉDICO"},{"SEM","TÁTICO","PESADO","TECNO"}};
        int[] selected={equippedBeard,equippedHelmet,equippedBackpack,equippedVest};
        int[] accents={0xFF9B806B,0xFF65A9C4,0xFF58A68A,0xFFB18A55};
        for(int group=0;group<4;group++) {
            int col=group%2,row=group/2;float x=270+col*292f,y=280+row*178f;
            panel(c,x,y,272,158,0xB51A3040,0x557AA9BC,14);
            eyebrow(c,x+16,y+25,names[group]);
            text(c,"GEOMETRIA 3D • ENCAIXE NO CORPO",x+16,y+47,7.5f,0xFF9DB6C5,Typeface.DEFAULT);
            int optionCount=group==0?5:4;
            float step=group==0?49f:63f,cardWidth=group==0?45f:58f;
            for(int item=0;item<optionCount;item++) {
                float bx=x+12+item*step,by=y+77;
                boolean active=selected[group]==item;
                panel(c,bx,by,cardWidth,52,active?0xFF176A75:0x77304758,
                    active?0xFF58E0E8:0x335D8495,8);
                circle(c,bx+cardWidth/2f,by+15,7,item==0?0xFF526775:accents[group],0xFFB5D6E1);
                text(c,options[group][item],bx+cardWidth/2f,by+39,group==0?5.4f:6.5f,Color.WHITE,Typeface.DEFAULT_BOLD);
            }
        }
        text(c,"Cada peça pode ser removida sem alterar as outras categorias.",270,650,9,0xFF82E5DC,Typeface.DEFAULT_BOLD);
    }

    private void drawRoyalePage(Canvas c) {
        eyebrow(c,270,175,"ROYALE • RECOMPENSAS E RELÍQUIAS");
        title(c,"MENU ROYALE",270,224);
        subtitle(c,"Gire a roleta com diamantes, confira as chances e teste os cosméticos antes.",270,252);
        panel(c,270,266,570,48,0xAE193040,0x557AA9BC,14);
        text(c,"SALDO ONLINE  ◆ "+formatDiamonds(onlineDiamonds>=0?onlineDiamonds:0L),290,296,11,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        pill(c,642,276,186,29,"PRESENTE DIÁRIO",0xFF176A75,Color.WHITE);
        String[] names={"ROULETA ROYALE","BARBINHA DO VELHO","CALÇA ANGELICAL","COFRE","MODA","EVENTOS"};
        String[] hints={"100 diamantes • sorteio animado","900 diamantes • barba 3D","1.500 diamantes • calça azul","Ver itens e equipamentos","Camisas, botas e conjuntos","Conteúdo da temporada"};
        int[] featured={-1,18,19,-1,-1,-1};
        int owned=0;for(String key:SHOP_KEYS)if(prefs.getBoolean(key,false))owned++;
        for(int i=0;i<names.length;i++){
            int col=i%3,row=i/3;float x=270+col*192f,y=326+row*151f;
            boolean featuredItem=featured[i]>=0;
            boolean ownedItem=featuredItem&&prefs.getBoolean(SHOP_KEYS[featured[i]],false);
            panel(c,x,y,174,132,i==0?0xB55A4220:(ownedItem?0xB51A4650:0xB51A3040),
                i==0?0xFFFFD27A:(ownedItem?0xFF4BD4C9:0x557AA9BC),14);
            if(i==0){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5f);p.setColor(0xFFFFD27A);
                c.drawCircle(x+34,y+39,22,p);p.setStyle(Paint.Style.FILL);
                text(c,"★",x+26,y+47,25,0xFFFFE5A6,Typeface.DEFAULT_BOLD);
            }else if(featuredItem)drawCollectionPreview(c,x+34,y+39,featured[i]==18?0:8,featured[i]==18?4:1,SHOP_COLORS[featured[i]]);
            else circle(c,x+34,y+39,19,i==3?0xFF24677A:i==4?0xFF8D42D7:0xFF2FAFC4,0xFF9BC9D2);
            text(c,names[i],x+13,y+77,8.2f,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
            text(c,hints[i],x+13,y+94,7.0f,0xFF9DB6C5,Typeface.DEFAULT);
            pill(c,x+12,y+101,150,23,i==0?"ABRIR ROLETA":(featuredItem?(ownedItem?"ADQUIRIDO":"COMPRAR"):"ABRIR"),
                i==0?0xFF80551E:(featuredItem?0xFF73501F:0xFF176A75),Color.WHITE);
        }
        text(c,owned+" ITENS DESBLOQUEADOS • PRÊMIOS ENTREGUES NO COFRE",270,660,8,0xFF8FAAB8,Typeface.DEFAULT_BOLD);
    }

    private void drawRoyaleSpinPage(Canvas c) {
        eyebrow(c,270,175,"ROYALE • GIRO DE PRÊMIOS");
        title(c,"ROLETA ROYALE",270,224);
        pill(c,674,184,166,32,"VOLTAR AO ROYALE",0x553B6172,0xFFD7E6ED);
        panel(c,270,264,570,48,0xA91A3040,0x557AA9BC,14);
        text(c,"SALDO ONLINE  ◆ "+formatDiamonds(onlineDiamonds>=0?onlineDiamonds:0L),290,294,11,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        text(c,"CUSTO POR GIRO: 100 ◆",610,294,10,Color.WHITE,Typeface.DEFAULT_BOLD,Paint.Align.RIGHT);

        panel(c,270,326,390,316,0xA9152938,0x557AA9BC,18);
        float cx=465,cy=470,radius=133;
        float[] sweeps={144f,108f,72f,32.4f,3.6f};
        int[] colors={0xFF267D9A,0xFF287B75,0xFF7553A5,0xFFB07835,0xFFB43B66};
        c.save();c.rotate(royaleWheelRotation,cx,cy);
        float angle=-90f;
        for(int i=0;i<sweeps.length;i++){
            r.set(cx-radius,cy-radius,cx+radius,cy+radius);
            p.setColor(colors[i]);p.setStyle(Paint.Style.FILL);
            c.drawArc(r,angle,sweeps[i],true,p);
            p.setColor(0xFFD8F3F5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);
            c.drawArc(r,angle,sweeps[i],true,p);
            angle+=sweeps[i];
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
        p.setColor(0xFFFFD27A);
        Path pointer=new Path();pointer.moveTo(cx,cy-radius-14);pointer.lineTo(cx-15,cy-radius-39);
        pointer.lineTo(cx+15,cy-radius-39);pointer.close();c.drawPath(pointer,p);
        p.setColor(0xFF0B1B2A);c.drawCircle(cx,cy,36,p);
        p.setColor(0xFFFFD27A);c.drawCircle(cx,cy,25,p);
        text(c,"BU",cx,cy+7,19,0xFF10283A,Typeface.create("sans-serif-black",Typeface.BOLD),Paint.Align.CENTER);
        if(royaleSpinning){
            long elapsed=System.currentTimeMillis()-royaleSpinStartedAt;
            float phase=Math.max(0f,Math.min(1f,elapsed/3900f));
            float eased=1f-(float)Math.pow(1f-phase,3.0);
            royaleWheelRotation=royaleSpinStartRotation+(royaleSpinEndRotation-royaleSpinStartRotation)*eased;
            if(phase>=1f)finishRoyaleSpin();
            else postInvalidateDelayed(16);
        }
        text(c,royaleRequesting?"VALIDANDO NO SERVIDOR...":(royaleSpinning?"SORTEANDO PRÊMIO...":"GIRE A ROLETA"),465,625,10,
            royaleSpinning?0xFFFFD27A:0xFF8BDCE7,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);

        panel(c,680,326,532,316,0xA9152938,0x557AA9BC,18);
        text(c,"PRÊMIOS E PROBABILIDADES",704,354,12,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        for(int i=0;i<ROYALE_PRIZE_ITEMS.length;i++){
            int item=ROYALE_PRIZE_ITEMS[i];
            float y=370+i*43f;
            boolean selected=royaleSelectedPreview==i;
            panel(c,698,y,496,38,selected?0xCC285A67:0x77304758,
                selected?0xFF65E4E5:0x335D8495,9);
            circle(c,718,y+19,11,colors[i],0xFFB5D6E1);
            text(c,SHOP_NAMES[item],740,y+16,9,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,ROYALE_CHANCES[i]+"%",1172,y+24,12,0xFFFFD27A,Typeface.DEFAULT_BOLD,Paint.Align.RIGHT);
            if(selected)text(c,"SELECIONADO",1090,y+15,7,0xFF8BE7DE,Typeface.DEFAULT_BOLD,Paint.Align.RIGHT);
        }
        text(c,"Chances: 40% • 30% • 20% • 9% • 1%  |  Repetidos: +100 ◆",704,598,8,0xFFB7CED8,Typeface.DEFAULT);
        text(c,"GIROS: "+prefs.getInt("royale_spin_count",0)+"  •  ÚLTIMO: "+compactLabel(prefs.getString("royale_last_reward","SEM GIROS"),44),704,618,8,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        pill(c,698,652,220,30,"TESTAR ITEM",0xFF176A75,Color.WHITE);
        pill(c,938,652,256,30,(royaleSpinning||royaleRequesting)?"AGUARDE...":"GIRAR • 100 ◆",0xFF80551E,Color.WHITE);
        if(!royaleResultMessage.isEmpty()){
            panel(c,270,646,390,42,0xDD173E43,0xFF5ADACB,10);
            text(c,royaleResultMessage,465,672,9,Color.WHITE,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
        }
    }

    private void startRoyaleSpin() {
        if(royaleSpinning||royaleRequesting)return;
        if(royaleSpinAction==null){toast("Royale online ainda não conectado ao servidor.");return;}
        if(onlineDiamonds<100L){toast("Carteira online sem diamantes suficientes. Resgate o presente diário.");return;}
        restoreRoyalePreview();
        royaleRequesting=true;
        royaleResultMessage="";
        toast("Confirmando seu giro no servidor...");
        postInvalidate();
        if(pendingRoyaleRequestId.isEmpty())pendingRoyaleRequestId=UUID.randomUUID().toString();
        royaleSpinAction.spin(pendingRoyaleRequestId,(success,prizeId,prizeName,duplicate,balance,message)->{
            royaleRequesting=false;
            if(!success){
                royaleResultMessage=message==null?"GIRO NÃO CONFIRMADO":message;
                toast(royaleResultMessage);
                postInvalidate();
                return;
            }
            pendingRoyaleRequestId="";
            onlineDiamonds=Math.max(0L,balance);
            String[] ids={"shirt_tactical","boots_combat","set_aurora","beard_old","pants_angelical"};
            int prize=-1;
            for(int i=0;i<ids.length;i++)if(ids[i].equals(prizeId)){prize=i;break;}
            if(prize<0){
                royaleResultMessage="PRÊMIO RECEBIDO • ITEM NÃO RECONHECIDO";
                toast("O servidor retornou um item não reconhecido. Saldo atualizado.");
                postInvalidate();return;
            }
            float[] sweeps={144f,108f,72f,32.4f,3.6f};
            float center=-90f;
            for(int i=0;i<prize;i++)center+=sweeps[i];
            center+=sweeps[prize]*.5f;
            float delta=(-90f-center-royaleWheelRotation)%360f;
            if(delta<0)delta+=360f;
            royaleSpinStartRotation=royaleWheelRotation;
            royaleSpinEndRotation=royaleWheelRotation+2160f+delta;
            royalePendingPrize=prize;
            royaleServerDuplicate=duplicate;
            royaleServerPrizeName=prizeName==null?"RECOMPENSA":prizeName;
            royaleSpinStartedAt=System.currentTimeMillis();
            royaleSpinning=true;
            royaleResultMessage="";
            postInvalidate();
        });
    }

    private void finishRoyaleSpin() {
        if(!royaleSpinning||royalePendingPrize<0)return;
        int item=ROYALE_PRIZE_ITEMS[royalePendingPrize];
        String result=royaleServerPrizeName+(royaleServerDuplicate?" REPETIDO • +100 DIAMANTES":"");
        if(!royaleServerDuplicate){
            prefs.edit().putBoolean(SHOP_KEYS[item],true)
                .putInt("royale_spin_count",prefs.getInt("royale_spin_count",0)+1)
                .putString("royale_last_reward",result).apply();
            if(item==3){selectedShirt=1;selectedSet=0;game.setWardrobe(selectedShirt,selectedBoots,selectedSet);}
            else if(item==7){selectedBoots=1;selectedSet=0;game.setWardrobe(selectedShirt,selectedBoots,selectedSet);}
            else if(item==11){selectedSet=1;game.setWardrobe(selectedShirt,selectedBoots,selectedSet);}
            else if(item==18){equippedBeard=4;game.setEquipment(equippedBeard,equippedHelmet,equippedBackpack,equippedVest);}
            else if(item==19){selectedPants=1;game.setPantsStyle(1);}
        }else{
            prefs.edit().putInt("royale_spin_count",prefs.getInt("royale_spin_count",0)+1)
                .putString("royale_last_reward",result).apply();
        }
        royaleSpinning=false;
        royalePendingPrize=-1;
        royaleRevealItem=item;royaleRevealUntil=System.currentTimeMillis()+1800L;
        royaleResultMessage="PRÊMIO: "+result;
        toast("Royale: "+result);
        postInvalidate();
    }

    private void applyRoyalePreview(int prizeIndex) {
        if(prizeIndex<0||prizeIndex>=ROYALE_PRIZE_ITEMS.length)return;
        restoreRoyalePreview();
        royalePreviewOriginalShirt=selectedShirt;
        royalePreviewOriginalBoots=selectedBoots;
        royalePreviewOriginalSet=selectedSet;
        royalePreviewOriginalBeard=equippedBeard;
        royalePreviewOriginalHelmet=equippedHelmet;
        royalePreviewOriginalBackpack=equippedBackpack;
        royalePreviewOriginalVest=equippedVest;
        royalePreviewOriginalPants=selectedPants;
        royalePreviewActive=true;
        int item=ROYALE_PRIZE_ITEMS[prizeIndex];
        if(item==3)game.setWardrobe(1,selectedBoots,selectedSet);
        else if(item==7)game.setWardrobe(selectedShirt,1,selectedSet);
        else if(item==11)game.setWardrobe(selectedShirt,selectedBoots,1);
        else if(item==18)game.setEquipment(4,equippedHelmet,equippedBackpack,equippedVest);
        else if(item==19)game.setPantsStyle(1);
        toast("Prévia temporária ativada. Não houve compra.");
    }

    private void restoreRoyalePreview() {
        if(!royalePreviewActive)return;
        game.setWardrobe(royalePreviewOriginalShirt,royalePreviewOriginalBoots,royalePreviewOriginalSet);
        game.setEquipment(royalePreviewOriginalBeard,royalePreviewOriginalHelmet,
            royalePreviewOriginalBackpack,royalePreviewOriginalVest);
        game.setPantsStyle(royalePreviewOriginalPants);
        royalePreviewActive=false;
    }

    private String compactLabel(String value,int max) {
        if(value==null)return "";
        if(value.length()<=max)return value;
        return value.substring(0,Math.max(0,max-1))+"…";
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
    private void applyMatchRewards(boolean victory) {
        int[] stats=game.getMatchStats();
        int kills=Math.max(0,game.getEliminations());
        long sessionId=prefs.getLong("match_reward_session_id",0L);
        if(mode==4) {
            if(!prefs.getBoolean("match_reward_recorded",false)) {
                prefs.edit().putBoolean("match_reward_recorded",true)
                    .putLong("last_match_reward_diamonds",0L).putLong("last_match_reward_xp",0L)
                    .putLong("last_match_reward_coins",0L).commit();
                profile.recordMatch("match_"+sessionId,victory,kills,stats[0],0);
                recordMissionProgressFromMatch();
            }
            return;
        }
        if(prefs.getBoolean("match_reward_recorded",false)){
            profile.recordMatch("match_"+sessionId,victory,kills,stats[0],game.getCollectedResources());
            return;
        }
        long diamonds=(victory?250L:60L)+kills*(victory?15L:10L);
        long xp=(victory?500L:120L)+kills*(victory?50L:25L);
        long coins=(victory?120L:40L)+kills*5L;
        if(!profile.claimOnce("match_reward_"+sessionId,"match_reward_recorded",coins,diamonds,xp)){
            return;
        }
        prefs.edit().putLong("last_match_reward_diamonds",diamonds)
            .putLong("last_match_reward_xp",xp).putLong("last_match_reward_coins",coins).commit();
        profile.recordMatch("match_"+sessionId,victory,kills,stats[0],game.getCollectedResources());
        recordMissionProgressFromMatch();
    }
    private void drawMatchResult(Canvas c,boolean victory) {
        int[] stats=game.getMatchStats();
        int[] duelStats=game.getDuelStats();
        p.setColor(0xD9000811);c.drawRect(0,0,1280,720,p);
        panel(c,225,76,830,570,0xF20B1C2A,0xFF4BCDD5,25);
        eyebrow(c,275,116,"BATTLE UNIVERSE  •  RELATÓRIO DA PARTIDA");
title(c,mode==4?(victory?"VITÓRIA NO DUELO":"DERROTA NO DUELO"):(victory?"VITÓRIA ROYALE":"VOCÊ FOI ELIMINADO"),275,164);
        subtitle(c,mode==4?(victory?"Você alcançou 5 eliminações primeiro.":"A IA venceu o duelo. Seu placar ficou abaixo de 5."):(victory?"Você ficou entre os últimos e venceu a operação.":"A operação terminou. Confira seu desempenho."),275,190);
        panel(c,275,220,330,104,0xCC123B39,0xFF45D6AE,16);
        text(c,mode==4?"DUELO OFFLINE":"RECOMPENSAS RECEBIDAS",296,247,10,0xFFB7EBDD,Typeface.DEFAULT_BOLD);
        text(c,mode==4?game.getEliminations()+"/5 ELIMINAÇÕES":"+"+prefs.getLong("last_match_reward_diamonds",0L)+" DIAMANTES",296,278,19,0xFFFFD36D,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,mode==4?"SEM CRÉDITO DE MOEDAS OU DIAMANTES OFFLINE":"+"+prefs.getLong("last_match_reward_xp",0L)+" XP  •  +"+prefs.getLong("last_match_reward_coins",0L)+" MOEDAS",296,302,mode==4?8:10,0xFF8BE6D1,Typeface.DEFAULT_BOLD);
        panel(c,625,220,380,104,0xCC132B43,0xFF4B9BD0,16);
        text(c,mode==4?"PLACAR FINAL":"RESULTADO DA EQUIPE",648,247,10,0xFFAED8F0,Typeface.DEFAULT_BOLD);
        text(c,mode==4?"VOCÊ "+duelStats[0]+"  —  IA "+duelStats[1]:""+stats[3]+" OPONENTES RESTANTES",648,278,17,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,mode==4?"META: PRIMEIRO A 5 PONTOS":""+game.getCollectedResources()+" RECURSOS COLETADOS",648,302,10,0xFFB6D7E8,Typeface.DEFAULT_BOLD);
        statCard(c,275,350,220,82,""+game.getEliminations(),mode==4?"PONTOS NO DUELO":"ELIMINAÇÕES");
        statCard(c,515,350,220,82,""+stats[0],"DANO CAUSADO");
        statCard(c,755,350,250,82,""+stats[1],"DANO RECEBIDO");
        statCard(c,275,448,340,70,""+stats[2],"ELIMINAÇÕES FEITAS PELA IA");
        statCard(c,635,448,370,70,mode==4?""+duelStats[1]:""+stats[3],mode==4?"PONTOS DA IA":"BOTS AINDA NA PARTIDA");
        gradientButton(c,300,548,300,68,"PAINEL PRINCIPAL","Salvar resultado e voltar ao lobby",0xFF126C72,0xFF164C70);
        gradientButton(c,680,548,300,68,mode==4?(victory?"VOLTAR AO LOBBY":"ASSISTIR IA"):"ASSISTIR PARTIDA",mode==4?(victory?"ENCERRAR DUELO":"VER A IA NA ARENA"):"Acompanhar os bots restantes",0xFF694B20,0xFF9A6426);
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
        if(!(status.startsWith("VIDA ")||status.startsWith("VITÓRIA")||status.startsWith("ELIMINADO")||status.startsWith("GLADIADORES")))return;
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
        long day=prefs.getLong("missions_day",System.currentTimeMillis()/86400000L);
        if(!profile.claimOnce("mission_"+day+"_"+index,"mission_claimed_"+index,
                100L+index*50L,MISSION_DIAMONDS[index],MISSION_XP[index])){
            toast("A recompensa desta missão já foi registrada.");return;
        }
        toast("Recompensa recebida: +"+(100L+index*50L)+" moedas, +"+MISSION_XP[index]+" XP e +"+MISSION_DIAMONDS[index]+" diamantes.");
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
        String key="royale_tier_claimed_"+index;
        if(prefs.getBoolean(key,false)){toast("Esta recompensa já foi resgatada.");return;}
        if(level<ROYALE_LEVELS[index]){toast("Alcance o nível "+ROYALE_LEVELS[index]+" do passe.");return;}
        if(!profile.claimOnce("royale_tier_"+index,key,200L+index*100L,ROYALE_REWARDS[index],0L)){
            toast("Esta recompensa já foi registrada.");return;
        }
        toast("Recompensa Royale resgatada!");
    }
    private void claimDailyReward() {
        long day=System.currentTimeMillis()/86400000L;
        if(prefs.getLong("daily_reward_day",-1L)==day) {
            toast("A recompensa diária já foi coletada."); return;
        }
        if(!profile.claimOnce("daily_reward_"+day,"daily_reward_claimed_"+day,300L,50L,100L)){
            toast("A recompensa diária já foi registrada.");return;
        }
        prefs.edit().putLong("daily_reward_day",day).commit();
        toast("+100 XP, +50 diamantes e +300 moedas virtuais!");
    }

    private void showRedeemDialog() {
        EditText input=new EditText(getContext());
        input.setSingleLine(true); input.setHint("Digite seu código promocional");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(getContext())
            .setTitle("Resgatar código secreto")
            .setMessage("Resgate códigos ativos no servidor. Cada código segue seu limite e regras de uso.")
            .setView(input).setNegativeButton("CANCELAR",null)
            .setPositiveButton("RESGATAR",(dialog,which)->redeemCode(input.getText().toString()))
            .show();
    }
    private void redeemCode(String rawCode) {
        String code=rawCode==null?"":rawCode.trim().toUpperCase(Locale.ROOT);
        if(promoCodeAction==null) {
            toast("Resgate online indisponível. Entre na conta e tente novamente.");
            return;
        }
        promoCodeAction.redeem(code,(success,balance,message)->{
            if(success)onlineDiamonds=Math.max(0L,balance);
            toast(message);
            invalidate();
        });
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
    private boolean purchaseShopItem(int index) {
        if(index<0||index>=SHOP_NAMES.length)return false;
        if(prefs.getBoolean(SHOP_KEYS[index],false)) {
            toast("Este item já está no seu inventário.");return false;
        }
        PlayerProfileStore.PurchaseResult result=index<3
            ?profile.purchaseWithCoins(SHOP_KEYS[index],SHOP_PRICES[index])
            :profile.purchase(SHOP_KEYS[index],SHOP_PRICES[index]);
        if(result==PlayerProfileStore.PurchaseResult.INSUFFICIENT_FUNDS){
            toast(index<3?"Moedas insuficientes.":"Diamantes insuficientes.");return false;
        }
        if(result==PlayerProfileStore.PurchaseResult.ALREADY_OWNED){toast("Este item já está no seu inventário.");return false;}
        if(result!=PlayerProfileStore.PurchaseResult.PURCHASED){toast("Falha ao salvar a compra. Saldo preservado.");return false;}
        toast(SHOP_NAMES[index]+" adquirido com "+(index<3?"moedas!":"diamantes!"));
        return true;
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
        eyebrow(c,270,175,"CAIXA DE ENTRADA DO JOGO");
        title(c,"CORREIO",270,224);
        subtitle(c,"Avisos e recompensas locais do BATTLE UNIVERSE.",270,252);
        boolean claimed=prefs.getBoolean("mail_welcome_claimed",false);
        panel(c,270,286,570,112,claimed?0xA91A4650:0xAE193040,claimed?0xFF4BD4C9:0x557AA9BC,16);
        circle(c,310,326,18,0xFF1F7584,0xFF65DCE7);
        text(c,"✉",301,333,17,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"BOAS-VINDAS AO BATTLE UNIVERSE",350,322,13,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"Presente de entrada: 50 diamantes virtuais.",350,347,10,0xFFB3C8D4,Typeface.DEFAULT);
        pill(c,675,318,140,36,claimed?"COLETADO":"COLETAR",claimed?0xFF176A75:0xFF345D79,Color.WHITE);
        panel(c,270,418,570,102,0x771A3040,0x335D8495,15);
        text(c,"AVISO DO SERVIDOR",292,450,12,0xFF69DCE5,Typeface.DEFAULT_BOLD);
        text(c,"Notificações de partidas, manutenção e presentes online aparecerão após a conexão do servidor.",292,480,10,0xFFB3C8D4,Typeface.DEFAULT);
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
        if(!profile.claimOnce("event_season_03","event_season_claimed",250L,75L,150L)){
            toast("Recompensa do evento já registrada.");return;
        }
        toast("Evento concluído: +150 XP, +75 diamantes e +250 moedas.");
    }
    private void claimMailReward() {
        if(prefs.getBoolean("mail_welcome_claimed",false)){toast("Presente já coletado.");return;}
        if(!profile.claimOnce("mail_welcome_01","mail_welcome_claimed",100L,50L,0L)){
            toast("Presente já registrado.");return;
        }
        toast("Presente: +50 diamantes e +100 moedas virtuais.");
    }
    private void claimAchievement(int index) {
        int[] goals={500,1500,3000};long[] rewards={50,100,200};
        String[] keys={"achievement_500","achievement_1500","achievement_3000"};
        if(index<0||index>=goals.length)return;
        if(prefs.getBoolean(keys[index],false)){toast("Conquista já resgatada.");return;}
        if(prefs.getLong("xp",0L)<goals[index]){toast("Ainda faltam pontos de experiência.");return;}
        if(!profile.claimOnce("achievement_"+goals[index],keys[index],150L*(index+1),rewards[index],0L)){
            toast("Conquista já registrada.");return;
        }
        toast("Conquista: +"+rewards[index]+" diamantes e +"+(150L*(index+1))+" moedas.");
    }

    private void drawTeam(Canvas c){
        panel(c,888,124,362,218,0x9B0B1D2C,0x337AA0B2,19);
        eyebrow(c,913,153,"ELENCO DO LOBBY");
        text(c,"OPERADORES DO CENÁRIO",913,183,16,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        String[] slots={"VOCÊ  •  "+CHARACTER_NAMES[character],"OPERADOR DE CENA  •  DEMO 01","OPERADOR DE CENA  •  DEMO 02","BOTS IA  •  AO INICIAR"};
        for(int i=0;i<4;i++){
            float y=207+i*30;circle(c,925,y-4,7,i==0?0xFF38A9BA:0xFF35586A,0xFF8DDDE6);
            text(c,slots[i],945,y,10,i==0?Color.WHITE:0xFFB3C7D1,Typeface.DEFAULT_BOLD);
            if(i>0&&i<3)text(c,"◌",1208,y,10,0xFF9CB4C1,Typeface.DEFAULT_BOLD);
        }
        panel(c,888,356,362,107,0x83101F2C,0x226A8EA0,16);
        eyebrow(c,913,384,"STATUS DA SESSÃO");
        text(c,"●  MODO LOCAL",913,414,14,0xFF76E7CF,Typeface.DEFAULT_BOLD);
        text(c,"Partidas com bots • sem conexão online",913,438,10,0xFF9CB4C1,Typeface.DEFAULT);
        panel(c,888,477,362,183,0x74101F2C,0x226A8EA0,16);
        long xp=profile.getXp();
        int level=profile.getLevel();
        long levelProgress=profile.getLevelProgress();
        eyebrow(c,913,506,"PROGRESSO DO OPERADOR");
        text(c,"NÍVEL "+level+" / 30",913,539,17,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,xp+" XP TOTAL  •  "+levelProgress+" / 500 XP",913,558,9,0xFFB3C7D1,Typeface.DEFAULT_BOLD);
        p.setColor(0xFF233F4F);r.set(913,570,1224,579);c.drawRoundRect(r,5,5,p);
        p.setColor(0xFF55DDE7);r.set(913,570,913+311*(levelProgress/500f),579);c.drawRoundRect(r,5,5,p);
        text(c,"PARTIDAS "+profile.getMatchesPlayed()+"  •  VITÓRIAS "+profile.getWins()
            +"  •  ELIMS "+profile.getEliminations(),913,601,8,0xFFFFD27A,Typeface.DEFAULT_BOLD);
        pill(c,913,615,154,29,"ABRIR MISSÕES",0xFF176A75,Color.WHITE);
        text(c,"Progresso local salvo neste aparelho",1080,634,8,0xFF8FAAB8,Typeface.DEFAULT);
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
        text(c,mode==4?"GLADIADORES  •  DUELO 1V1":"BATTLE ROYALE  •  IA LOCAL",38,141,11,0xFF79E8D0,Typeface.DEFAULT_BOLD);

        String gameStatus=game.getGameStatus();
        if(showMinimap&&mode!=4) {
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
        if(mode!=4)drawControl(c,1165,403,43,gameStatus.contains("PRÉVIA DE")?"CONFIRMAR":"CONSTRUIR",0xFF176C83,0xFF8CEBFF,gameStatus.contains("PRÉVIA DE"));
        drawControl(c,960,461,39,"CORRER",0xFF285D49,0xFF83E9B2);
        drawControl(c,1065,465,34,"ARMA",0xFF354F79,0xFF9DC7FF);
        drawControl(c,1152,562,47,"SALTAR",0xFF7B3D35,0xFFFFA28F);
        if(mode!=4)drawControl(c,960,605,39,"USAR",0xFF354F79,0xFF9DC7FF);
        drawControl(c,850,562,39,"AGACHAR",0xFF315B50,0xFF8DE7C1);
        if(mode!=4)drawControl(c,1065,605,35,"KIT "+game.getMedkits(),0xFF176447,0xFF9CF0B9,game.getMedkits()>0);

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
        text(c,mode==4?"JOYSTICK: MOVER  •  MIRA: AJUSTAR CÂMERA  •  DISPARAR: DUELO 1V1":"JOYSTICK: MOVER  •  AGACHAR: SEGURE  •  GELO: TOQUE PARA VER / CONFIRMAR",
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
        // Botão tático em camadas: halo, corpo com gradiente, aro externo e núcleo.
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(active?0x663BEAF4:0x55243C4D);c.drawCircle(x,y,radius+9,p);
        int top=active?0xFF159BA8:blendControl(fill,0xFF172B3A,0.28f);
        int bottom=active?0xFF075B72:fill;
        p.setShader(new LinearGradient(x-radius,y-radius,x+radius,y+radius,top,bottom,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,radius,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);p.setColor(0x66FFFFFF);
        c.drawCircle(x,y,radius-4,p);
        p.setStrokeWidth(active?3.0f:2.0f);p.setColor(active?0xFFFFFFFF:edge);
        c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);
        p.setColor(active?0xFFBDFBFF:0x553BDAE8);c.drawCircle(x,y-radius+7,3.2f,p);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        p.setTextSize(label.length()>7?8:10);p.setColor(Color.WHITE);c.drawText(label,x,y+4,p);p.setTextAlign(Paint.Align.LEFT);
    }
    private int blendControl(int color,int overlay,float amount){
        int a=(int)(((color>>>24)&255)*(1-amount)+((overlay>>>24)&255)*amount);
        int r=(int)(((color>>>16)&255)*(1-amount)+((overlay>>>16)&255)*amount);
        int g=(int)(((color>>>8)&255)*(1-amount)+((overlay>>>8)&255)*amount);
        int b=(int)((color&255)*(1-amount)+(overlay&255)*amount);
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
    private void drawToast(Canvas c,float w,float h){
        float x=(w-370)/2f;p.setColor(0xEC102A38);c.drawRoundRect(x,h-86,x+370,h-34,16,16,p);
        text(c,toast,x+22,h-53,15,0xFF84E8DB,Typeface.DEFAULT_BOLD);
    }

    private void eyebrow(Canvas c,float x,float y,String s){text(c,s,x,y,10,0xFF60DCE6,Typeface.DEFAULT_BOLD);}
    private void title(Canvas c,String s,float x,float y){text(c,s,x,y,34,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));}
    private void subtitle(Canvas c,String s,float x,float y){text(c,s,x,y,13,0xFFB3C8D4,Typeface.DEFAULT);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface face){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTypeface(face);p.setTextSize(size);p.setColor(color);c.drawText(s,x,y,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface face,Paint.Align align){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTypeface(face);p.setTextSize(size);
        p.setTextAlign(align);p.setColor(color);c.drawText(s,x,y,p);p.setTextAlign(Paint.Align.LEFT);
    }
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
                if(mode!=4&&hit(q[0],q[1],1080,150,158,25)) { tacticalMapVisible=true;invalidate();return true; }
                if(!resultSpectating&&isMatchOver()) {
                    boolean victory=game.getGameStatus().contains("VITÓRIA");
                    if(hit(q[0],q[1],300,548,300,68)) {
                        recordMissionProgressFromMatch();resultSpectating=false;game.setSpectatorMode(false);
                        page=HOME;game.setGameScreen(false);invalidate();return true;
                    }
                    if(hit(q[0],q[1],680,548,300,68)) {
                        if(mode==4&&victory) {
                            recordMissionProgressFromMatch();resultSpectating=false;
                            page=HOME;game.setGameScreen(false);invalidate();return true;
                        }
                        resultSpectating=true;game.setSpectatorMode(true);invalidate();return true;
                    }
                    return true;
                }
                if(hit(q[0],q[1],24,625,resultSpectating?190:160,49)){
                    recordMissionProgressFromMatch();resultSpectating=false;game.setSpectatorMode(false);
                    page=HOME;game.setGameScreen(false);invalidate();return true;
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
        if(hit(x,y,1000,17,246,60)){if(page==ROYALE_SPIN)restoreRoyalePreview();page=PROFILE;return;}
        if(page!=HOME){
            for(int i=0;i<NAV.length;i++){
                float ny=143+i*33.2f;
                if(hit(x,y,27,ny,196,29)){if(page==ROYALE_SPIN)restoreRoyalePreview();page=NAV_PAGES[i];return;}
            }
        }
        if(page==HOME){
            if(hit(x,y,30,158,206,47)){page=SHOP;return;}
            if(hit(x,y,30,215,206,47)){page=ROYALE;return;}
            if(hit(x,y,30,272,206,47)){page=MISSIONS;return;}
            if(hit(x,y,30,329,206,47)){page=EVENTS;return;}
            if(hit(x,y,30,386,206,47)){page=FRIENDS;return;}
            if(hit(x,y,1018,354,216,39)){page=FRIENDS;return;}
            if(hit(x,y,278,640,130,42)){page=INVENTORY;return;}
            if(hit(x,y,418,640,130,42)){page=CHARACTERS;return;}
            if(hit(x,y,558,640,130,42)){page=WARDROBE;return;}
            if(hit(x,y,698,640,128,42)){page=PROFILE;return;}
            if(hit(x,y,876,553,176,45)){page=MODES_PAGE;return;}
            if(hit(x,y,1062,553,172,45)){page=MAPS_PAGE;return;}
            if(hit(x,y,878,609,92,29)){
                onlineMode=false;prefs.edit().putBoolean("online_mode",false).apply();
                toast("MODO OFFLINE SELECIONADO.");return;
            }
            if(hit(x,y,977,609,92,29)){
                onlineMode=true;game.setTrainingGodMode(false);
                prefs.edit().putBoolean("online_mode",true).putBoolean("modmenu2_god_mode",false).apply();
                toast("ONLINE AINDA PRECISA DE UM SERVIDOR DE PARTIDAS.");return;
            }
            if(hit(x,y,1080,608,154,58)){
                if(onlineMode){
                    toast("ONLINE INDISPONÍVEL: SERVIDOR MULTIPLAYER AINDA NÃO CONFIGURADO. SELECIONE OFFLINE.");
                    return;
                }
                page=PREMATCH;return;
            }
        }else if(page==PREMATCH){
            if(hit(x,y,270,636,170,34)){page=HOME;return;}
            if(mode==4)for(int i=0;i<4;i++)if(hit(x,y,886+i*76,572,70,25)) {
                selectedDuelWeapon=i;game.setDuelWeapon(i);
                prefs.edit().putInt("duel_weapon",i).apply();
                toast("Equipamento do duelo: "+DUEL_WEAPONS[i]);return;
            }
            if(hit(x,y,862,636,350,34)){beginOfflineMatch();return;}
        }else if(page==CHARACTERS){
            for(int i=0;i<4;i++)if(hit(x,y,282,320+i*72f,166,62)){
                character=i;game.setCharacter(i);prefs.edit().putInt("character",i).apply();
                toast("Operador "+CHARACTER_NAMES[i]+" selecionado");return;
            }
            if(hit(x,y,894,584,294,34)){page=WARDROBE;return;}
        }else if(page==MODES_PAGE){
            for(int i=0;i<5;i++)if(hit(x,y,270+(i%3)*292,290+(i/3)*150,270,126)){
                mode=i;game.setMatchMode(i);game.setTrainingGodMode(false);prefs.edit().putInt("mode",i).apply();
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
            if(hit(x,y,270,558,270,70)){if(googleSignedIn){if(recoveryCodeAction!=null)recoveryCodeAction.run();}else showRedeemDialog();return;}
            if(hit(x,y,558,558,282,70)){showNicknameDialog();return;}
        }else if(page==INVENTORY){
            if(hit(x,y,270,312,150,29)){cofreTab=0;return;}
            if(hit(x,y,430,312,150,29)){cofreTab=1;return;}
            for(int section=0;section<4;section++){
                float rowY=350+section*78f;
                int count=cofreTab==0?(section==0?4:3):(section==0?2:4);
                for(int item=0;item<count;item++){
                    float ix=390+item*110f;
                    if(!hit(x,y,ix,rowY+7,100,56))continue;
                    if(cofreTab==0){
                        int value=item+1;
                        if(section==0){
                            if(value==4&&!prefs.getBoolean("wear_beard_old",false)){page=ROYALE;toast("A Barbinha do Velho pode ser comprada no Royale.");return;}
                            equippedBeard=value;
                        }else if(section==1)equippedHelmet=value;
                        else if(section==2)equippedBackpack=value;
                        else equippedVest=value;
                        prefs.edit().putInt("equipped_beard",equippedBeard).putInt("equipped_helmet",equippedHelmet)
                            .putInt("equipped_backpack",equippedBackpack).putInt("equipped_vest",equippedVest).apply();
                        game.setEquipment(equippedBeard,equippedHelmet,equippedBackpack,equippedVest);
                        toast("Item equipado no Cofre.");return;
                    }
                    if(section==0){
                        if(item==1&&!prefs.getBoolean("wear_pants_angelical",false)){page=SHOP;toast("Compre a Calça Angelical na loja.");return;}
                        selectedPants=item;prefs.edit().putInt("selected_pants",selectedPants).apply();
                        game.setPantsStyle(selectedPants);toast(item==1?"Calça Angelical equipada.":"Calça padrão equipada.");return;
                    }
                    if(section==1){
                        int shopIndex=3+item;
                        if(!prefs.getBoolean(SHOP_KEYS[shopIndex],false)){page=SHOP;toast("Camisa bloqueada. Consulte a loja.");return;}
                        selectedShirt=item+1;selectedSet=0;
                    }else if(section==2){
                        int shopIndex=7+item;
                        if(!prefs.getBoolean(SHOP_KEYS[shopIndex],false)){page=SHOP;toast("Bota bloqueada. Consulte a loja.");return;}
                        selectedBoots=item+1;selectedSet=0;
                    }else{
                        int shopIndex=11+item;
                        if(!prefs.getBoolean(SHOP_KEYS[shopIndex],false)){page=SHOP;toast("Conjunto bloqueado. Consulte a loja.");return;}
                        selectedSet=item+1;
                    }
                    prefs.edit().putInt("selected_shirt",selectedShirt).putInt("selected_boots",selectedBoots)
                        .putInt("selected_set",selectedSet).apply();
                    game.setWardrobe(selectedShirt,selectedBoots,selectedSet);
                    toast("Peça equipada no operador.");return;
                }
            }
        }else if(page==SHOP){
            for(int i=0;i<SHOP_NAMES.length;i++){
                float sx=270+(i%3)*192f,sy=320+(i/3)*47f;
                if(hit(x,y,sx,sy,174,42)){purchaseShopItem(i);return;}
            }
        }else if(page==WARDROBE){
            if(hit(x,y,690,260,150,25)){page=ACCESSORIES;return;}
            if(hit(x,y,270,260,150,25)) {
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
            }
            for(int i=0;i<7;i++) {
                int col=i%4,row=i/4;float sx=270+col*146f,sy=552+row*72f;
                if(hit(x,y,sx,sy,132,64)) {
                    if(!prefs.getBoolean(SHOP_KEYS[11+i],false)){toast("Compre esta skin na loja primeiro.");return;}
                    selectedSet=i+1;
                    prefs.edit().putInt("selected_set",selectedSet).apply();
                    game.setWardrobe(selectedShirt,selectedBoots,selectedSet);
                    toast("Skin "+new String[]{"AURORA","CAMPEÃO","ORBITAL","DESERTO","VULCÃO","NEBULAR","CERRADO"}[i]+" equipada.");return;
                }
            }
        }else if(page==ACCESSORIES){
            if(hit(x,y,674,184,166,32)){page=WARDROBE;return;}
            if(hit(x,y,270,280,272,158)||hit(x,y,562,280,272,158)
                    ||hit(x,y,270,458,272,158)||hit(x,y,562,458,272,158)) {
                int group=x<550?(y<450?0:2):(y<450?1:3);
                float originX=(group%2==0?282f:574f);
                float itemStep=group==0?49f:63f;
                int item=Math.max(0,Math.min(group==0?4:3,(int)((x-originX)/itemStep)));
                if(group==0&&item==4&&!prefs.getBoolean("wear_beard_old",false)){
                    page=ROYALE;toast("Compre a Barbinha do Velho no Royale.");return;
                }
                if(group==0)equippedBeard=item;
                else if(group==1)equippedHelmet=item;
                else if(group==2)equippedBackpack=item;
                else equippedVest=item;
                prefs.edit().putInt("equipped_beard",equippedBeard)
                    .putInt("equipped_helmet",equippedHelmet)
                    .putInt("equipped_backpack",equippedBackpack)
                    .putInt("equipped_vest",equippedVest).apply();
                game.setEquipment(equippedBeard,equippedHelmet,equippedBackpack,equippedVest);
                String[] labels={"barba","capacete","mochila","colete"};
                toast((item==0?"Removido: ":"Equipado: ")+labels[group]+" • opção "+item);
                return;
            }
        }else if(page==ROYALE){
            if(hit(x,y,642,276,186,29)){
                if(dailyRewardAction!=null)dailyRewardAction.claim((claimed,coins,diamonds,message)->{
                    onlineDiamonds=diamonds;toast(message);invalidate();
                });
                else toast("Presente diário indisponível.");
                return;
            }
            for(int i=0;i<6;i++){
                float rx=270+(i%3)*192f,ry=326+(i/3)*151f;
                if(!hit(x,y,rx,ry,174,132))continue;
                if(i==0){page=ROYALE_SPIN;royaleSelectedPreview=0;return;}
                if(i==1){
                    if(purchaseShopItem(18)){
                        equippedBeard=4;prefs.edit().putInt("equipped_beard",4).apply();
                        game.setEquipment(equippedBeard,equippedHelmet,equippedBackpack,equippedVest);
                    }
                }else if(i==2){
                    if(purchaseShopItem(19)){
                        selectedPants=1;prefs.edit().putInt("selected_pants",1).apply();
                        game.setPantsStyle(1);
                    }
                }else{
                    int[] destinations={-1,-1,-1,INVENTORY,WARDROBE,EVENTS};
                    page=destinations[i];
                }
                return;
            }
        }else if(page==ROYALE_SPIN){
            if(hit(x,y,674,184,166,32)){restoreRoyalePreview();page=ROYALE;return;}
            if(royaleSpinning||royaleRequesting)return;
            for(int i=0;i<ROYALE_PRIZE_ITEMS.length;i++){
                float ry=370+i*43f;
                if(hit(x,y,698,ry,496,38)){royaleSelectedPreview=i;postInvalidate();return;}
            }
            if(hit(x,y,698,652,220,30)){applyRoyalePreview(royaleSelectedPreview);return;}
            if(hit(x,y,938,652,256,30)){startRoyaleSpin();return;}
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
            if(hit(x,y,270,657,180,34)){page=DEV_MENU;return;}
            if(hit(x,y,515,329,300,36)){
                generalSensitivity=Math.max(25,Math.min(200,Math.round((x-520)/37f+1)*25));
                game.setLookSensitivity(generalSensitivity/100.0f,aimSensitivity/100.0f);
                prefs.edit().putInt("sensitivity_general",generalSensitivity).apply();
                toast("Sensibilidade geral: "+generalSensitivity+"%");return;
            }
            if(hit(x,y,515,445,300,36)){
                aimSensitivity=Math.max(25,Math.min(200,Math.round((x-520)/37f+1)*25));
                game.setLookSensitivity(generalSensitivity/100.0f,aimSensitivity/100.0f);
                prefs.edit().putInt("sensitivity_aim",aimSensitivity).apply();
                toast("Sensibilidade ao mirar: "+aimSensitivity+"%");return;
            }
            if(hit(x,y,515,540,300,45)){
                quality=x<620?0:x<720?1:2;game.setQuality(quality);
                prefs.edit().putInt("quality",quality).apply();toast("Qualidade: "+QUALITY_NAMES[quality]);return;
            }
            if(hit(x,y,470,605,105,38)){
                invertCameraY=!invertCameraY;game.setInvertCameraY(invertCameraY);
                prefs.edit().putBoolean("invert_camera_y",invertCameraY).apply();toast("Inversão vertical "+(invertCameraY?"ativada":"desativada"));return;
            }
            if(hit(x,y,675,605,75,38)){
                showMinimap=!showMinimap;prefs.edit().putBoolean("show_minimap",showMinimap).apply();
                toast(showMinimap?"Minimapa visível":"Minimapa oculto");return;
            }
            if(hit(x,y,790,605,42,38)){
                hapticsEnabled=!hapticsEnabled;prefs.edit().putBoolean("haptics",hapticsEnabled).apply();
                toast(hapticsEnabled?"Vibração ativada":"Vibração desativada");return;
            }
        }else if(page==DEV_MENU){
            if(hit(x,y,650,316,160,42)){
                if(mode!=3||onlineMode){
                    toast("GOD MODE DISPONÍVEL SOMENTE EM TREINAMENTO OFFLINE.");
                    return;
                }
                boolean enabled=!prefs.getBoolean("modmenu2_god_mode",false);
                prefs.edit().putBoolean("modmenu2_god_mode",enabled).apply();
                game.setTrainingGodMode(enabled);
                toast(enabled?"MODMENU2 • GOD MODE ATIVADO NO TREINAMENTO.":"MODMENU2 • GOD MODE DESATIVADO.");
                return;
            }
        }
    }

    public void setOnlineWallet(long diamonds) {
        onlineDiamonds=Math.max(0L,diamonds);invalidate();
    }
    public void setRoyaleSpinAction(RoyaleSpinAction action) { royaleSpinAction=action; }
    public void setDailyRewardAction(DailyRewardAction action) { dailyRewardAction=action; }
    public void setPromoCodeAction(PromoCodeAction action) { promoCodeAction=action; }
    public void setDeviceIdentity(String code) { deviceIdentity=code==null?"":code; invalidate(); }
    public void setRecoveryCodeAction(Runnable action) { recoveryCodeAction=action; }
    public void showRecoveryCode(String code) {
        if(code==null||code.isEmpty())return;
        EditText field=new EditText(getContext());field.setText(code);field.setTextIsSelectable(true);
        new AlertDialog.Builder(getContext()).setTitle("NOVO CÓDIGO DE RECUPERAÇÃO")
            .setMessage("Guarde este código fora do aparelho. Ele aparece somente agora; o código anterior foi invalidado.")
            .setView(field).setPositiveButton("ENTENDI",null).setCancelable(false).show();
    }

    public void setUsernameAccountActions(UsernameAccountAction authAction,
                                          Runnable syncAction, Runnable signOutAction) {
        usernameAccountAction=authAction;
        accountSyncAction=syncAction;
        accountSignOutAction=signOutAction;
    }
    public void setGoogleAccountStatus(boolean configured, boolean signedIn,
                                       String username, String message) {
        String previous=googleMessage;
        googleConfigured=configured;
        googleSignedIn=signedIn;
        googleEmail=username==null?"":username;
        googleMessage=message==null?"":message;
        if(accountDialogStatus!=null){
            accountDialogStatus.setText(googleMessage);
            String lower=googleMessage.toLowerCase(Locale.ROOT);
            accountDialogStatus.setTextColor(lower.contains("erro")||lower.contains("inválid")
                ||lower.contains("não foi")||lower.contains("não configurado")?0xFFFF7777:0xFF168B70);
        }
        if(accountDialog!=null&&accountDialog.isShowing()){
            String lower=googleMessage.toLowerCase(Locale.ROOT);
            boolean busy=lower.contains("criando")||lower.contains("entrando");
            if(accountDialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)accountDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
            if(accountDialog.getButton(AlertDialog.BUTTON_NEUTRAL)!=null)accountDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
            if(signedIn){accountDialog.dismiss();accountDialog=null;accountDialogStatus=null;}
        }
        String feedback=googleMessage.toLowerCase(Locale.ROOT);
        boolean important=!googleMessage.equals(previous)&&(
            feedback.contains("criando")||feedback.contains("entrando")||
            feedback.contains("conta criada")||feedback.contains("login realizado")||
            feedback.contains("nickname")||feedback.contains("senha")||
            feedback.contains("não foi possível")||feedback.contains("indisponível")||
            feedback.contains("expirada")||feedback.contains("inválida")||
            feedback.contains("suspensa")||feedback.contains("falha")||
            feedback.contains("erro")||feedback.contains("não configurado")||
            feedback.contains("sessão"));
        if(important){Toast.makeText(getContext(),googleMessage,Toast.LENGTH_LONG).show();toast(googleMessage);}
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
        accountDialogStatus=new TextView(getContext());
        accountDialogStatus.setText(googleMessage.isEmpty()?"Digite seus dados para continuar.":googleMessage);
        accountDialogStatus.setTextSize(13);
        accountDialogStatus.setPadding(0,pad/2,0,pad/2);
        accountDialogStatus.setTextColor(0xFF168B70);
        TextView note=new TextView(getContext());
        note.setText("Nickname: 3 a 18 letras, números ou _. Senha: 8 a 72 caracteres. Guarde sua senha.");
        note.setTextSize(12);
        form.addView(username);form.addView(password);form.addView(accountDialogStatus);form.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(getContext())
            .setTitle("CONTA BATTLE UNIVERSE").setView(form)
            .setNegativeButton("FECHAR",null).setPositiveButton("CRIAR CONTA",null)
            .setNeutralButton("ENTRAR",null).create();
        accountDialog=dialog;
        dialog.setOnDismissListener(ignored->{if(accountDialog==dialog){accountDialog=null;accountDialogStatus=null;}});
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->submitAccountForm(dialog,username,password,true));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->submitAccountForm(dialog,username,password,false));
        });
        dialog.show();
    }
    private void submitAccountForm(AlertDialog dialog,EditText username,EditText password,boolean create) {
        String name=username.getText().toString().trim().toLowerCase(Locale.ROOT);
        String secret=password.getText().toString();
        String error="";
        if(!googleConfigured)error="Servidor de contas não configurado neste APK.";
        else if(!name.matches("[a-z0-9_]{3,18}"))error="Nickname inválido: use 3 a 18 letras, números ou _.";
        else if(secret.length()<8||secret.length()>72)error="Senha inválida: use de 8 a 72 caracteres.";
        else if(usernameAccountAction==null)error="Serviço de contas não conectado à tela.";
        if(!error.isEmpty()){
            if(accountDialogStatus!=null){accountDialogStatus.setText(error);accountDialogStatus.setTextColor(0xFFFF7777);}
            Toast.makeText(getContext(),error,Toast.LENGTH_LONG).show();return;
        }
        if(accountDialogStatus!=null){accountDialogStatus.setText(create?"Criando conta no servidor...":"Entrando na conta...");accountDialogStatus.setTextColor(0xFF168B70);}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        usernameAccountAction.authenticate(name,secret,create);
    }
    private void toast(String s){toast=s;toastUntil=System.currentTimeMillis()+1800;invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
}
