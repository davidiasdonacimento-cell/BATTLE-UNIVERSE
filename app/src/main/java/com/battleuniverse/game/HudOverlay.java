package com.battleuniverse.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public final class HudOverlay extends View {
    private static final int HOME=0, CHARACTERS=1, MODES_PAGE=2, MAPS_PAGE=3, INVENTORY=4, SETTINGS=5, GAME=6;
    private static final String[] NAV={"INÍCIO","PERSONAGENS","MODOS","MAPAS","INVENTÁRIO","AJUSTES"};
    private static final String[] MODE_NAMES={"SOLO","DUPLA","ESQUADRÃO","TREINAMENTO"};
    private static final String[] MAP_NAMES={"ILHA TROPICAL","NEON CITY","DESERT STORM"};
    private static final String[] CHARACTER_NAMES={"NOVA","ATLAS","EMBER","VOLT"};
    private static final String[] QUALITY_NAMES={"ECONÔMICO","EQUILIBRADO","ALTO"};
    private final GameSurfaceView game;
    private final SharedPreferences prefs;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r=new RectF();
    private int page=HOME, mode, map, character, quality=1;
    private boolean music=true, effects=true, forwarding;
    private float scale=1, offsetX, offsetY;
    private String toast="";
    private long toastUntil, lastTap;

    public HudOverlay(Context context, GameSurfaceView game, SharedPreferences prefs) {
        super(context);
        this.game=game; this.prefs=prefs;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setClickable(true); setFocusable(true);
        mode=prefs.getInt("mode",0); map=prefs.getInt("map",0);
        character=prefs.getInt("character",0); quality=prefs.getInt("quality",1);
        music=prefs.getBoolean("music",true); effects=prefs.getBoolean("effects",true);
        game.setCharacter(character); game.setMap(map);
        game.setMatchMode(mode); game.setQuality(quality); game.setGameScreen(false);
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
        panel(c,1000,17,246,60,0xC5152938,0x557AA0B8,15);
        circle(c,1032,47,18,0xFF1F7584,0xFF65DCE7);
        text(c,"D",1027,53,17,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"JOGADOR",1060,42,13,Color.WHITE,Typeface.DEFAULT_BOLD);
        text(c,"NÍVEL 01  •  0 XP",1060,60,10,0xFF9BB6C6,Typeface.DEFAULT);
        text(c,"◆ 1.250",1176,51,12,0xFFFFC76B,Typeface.DEFAULT_BOLD);
    }
    private void drawSidebar(Canvas c) {
        panel(c,24,120,210,565,0xA80A1B2B,0x226CA4B7,22);
        text(c,"MENU PRINCIPAL",45,151,10,0xFF6D91A7,Typeface.DEFAULT_BOLD);
        for(int i=0;i<NAV.length;i++){
            float y=170+i*72; boolean active=page==i;
            panel(c,35,y,188,56,active?0xDD136276:0x32243B4D,active?0xFF35D6E5:0x225A8297,14);
            if(active){p.setColor(0xFF39D8E8);r.set(35,y+13,39,y+43);c.drawRoundRect(r,2,2,p);}
            drawNavIcon(c,i,58,y+28,active);
            text(c,NAV[i],82,y+34,13,active?Color.WHITE:0xFFB8CBD7,
                    Typeface.create("sans-serif",active?Typeface.BOLD:Typeface.MEDIUM));
        }
        text(c,"VERSÃO 0.2  •  ANDROID",45,666,10,0xFF69899B,Typeface.DEFAULT);
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
    }
    private void drawHome(Canvas c){
        eyebrow(c,272,174,"TEMPORADA 01  /  OPERAÇÃO ILHA");
        text(c,"SUA PRÓXIMA",272,224,37,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"JORNADA COMEÇA AQUI.",272,267,37,0xFF55DDE7,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Monte sua equipe, escolha o destino e entre na batalha.",274,296,14,0xFFB3C8D4,Typeface.DEFAULT);
        panel(c,270,328,275,112,0xC9183040,0x557AA9BC,18);
        panel(c,558,328,292,112,0xC9183040,0x557AA9BC,18);
        eyebrow(c,290,357,"MODO DE JOGO");
        text(c,MODE_NAMES[mode],290,390,21,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,mode==0?"Cada jogador por si":mode==1?"Você + 1 companheiro IA":mode==2?"Você + 3 companheiros IA":"Explore e pratique livremente",290,417,11,0xFF9DB6C5,Typeface.DEFAULT);
        chevron(c,515,385);
        eyebrow(c,578,357,"MAPA SELECIONADO");
        text(c,MAP_NAMES[map],578,390,19,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,map==0?"Praias • Florestas • Vilas":map==1?"Cidade moderna • Neon":"Dunas • Cânions • Ruínas",578,417,11,0xFF9DB6C5,Typeface.DEFAULT);
        chevron(c,817,385);
        gradientButton(c,270,468,342,78,"JOGAR AGORA","ENTRAR NA PARTIDA",0xFFFFB45E,0xFFE97543);
        text(c,"▶",292,516,21,0xFFFFF4E7,Typeface.DEFAULT_BOLD);
        panel(c,628,468,222,78,0xB9183040,0x557AA9BC,17);
        text(c,"● SERVIDOR LOCAL",650,498,11,0xFF76E7CF,Typeface.DEFAULT_BOLD);
        text(c,"TREINAMENTO",650,522,15,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Partidas iniciais com inteligência artificial",274,579,10,0xFF9CB5C2,Typeface.DEFAULT);
        panel(c,270,608,580,58,0x70152B3A,0x225B8296,14);
        stat(c,292,642,"04","VAGAS NA EQUIPE");stat(c,468,642,"03","MAPAS PLANEJADOS");stat(c,665,642,"AI","COMPANHEIROS");
    }
    private void drawCharacters(Canvas c){
        eyebrow(c,270,175,"PERSONALIZAÇÃO");title(c,"SUA EQUIPE",270,224);
        subtitle(c,"Escolha seu operador. A seleção altera o personagem no cenário 3D.",270,252);
        String[] roles={"RECONHECIMENTO","DEFESA","EXPLORAÇÃO","ESTRATÉGIA"};
        int[] colors={0xFF287A91,0xFF6A647F,0xFF966344,0xFF437D66};
        for(int i=0;i<4;i++){
            float x=270+i*146;boolean active=character==i;
            panel(c,x,295,132,210,active?0xD51B5B69:0xA51A3040,active?0xFF48D7E1:0x335D8495,16);
            circle(c,x+66,365,36,colors[i],active?0xFF68E9EF:0xFF8AA5B4);
            text(c,new String[]{"N","A","E","V"}[i],x+59,372,19,Color.WHITE,Typeface.DEFAULT_BOLD);
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
        subtitle(c,"A equipe e os adversários são controlados por IA nesta versão.",270,252);
        String[] desc={"1 jogador • todos contra todos","Você + 1 aliado controlado por IA","Você + 3 aliados controlados por IA","Sem pressão • pratique e explore"};
        String[] counts={"SOLO","2 JOGADORES","4 JOGADORES","LIVRE"};
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
        eyebrow(c,270,175,"EQUIPAMENTO E RECOMPENSAS");title(c,"INVENTÁRIO",270,224);
        subtitle(c,"Gerencie os itens cosméticos desbloqueados no seu perfil local.",270,252);
        panel(c,270,292,570,92,0xAE193040,0x335D8495,17);
        eyebrow(c,292,320,"CARTEIRA");
        text(c,"◆ 1.250",292,355,24,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"Moedas demonstrativas • progresso salvo neste aparelho",465,352,11,0xFF9EB6C4,Typeface.DEFAULT);
        String[] items={"UNIFORME AZUL","MOCHILA TÁTICA","EMBLEMA INICIAL"};
        for(int i=0;i<3;i++){
            float x=270+i*192;panel(c,x,408,174,148,0xA91A3040,0x335D8495,15);
            circle(c,x+87,461,24,new int[]{0xFF2467A2,0xFF485D70,0xFFC58B43}[i],0xFFB5D6E1);
            text(c,items[i],x+18,514,11,Color.WHITE,Typeface.DEFAULT_BOLD);
            text(c,"DISPONÍVEL",x+18,535,9,0xFF83DCD5,Typeface.DEFAULT_BOLD);
        }
    }
    private void drawSettings(Canvas c){
        eyebrow(c,270,175,"PREFERÊNCIAS DO DISPOSITIVO");title(c,"CONFIGURAÇÕES",270,224);
        subtitle(c,"Ajustes locais, aplicados imediatamente e salvos no aparelho.",270,252);
        panel(c,270,292,570,94,0xAE193040,0x335D8495,17);
        eyebrow(c,294,321,"QUALIDADE GRÁFICA");
        text(c,QUALITY_NAMES[quality],294,354,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        for(int i=0;i<3;i++)pill(c,522+i*96,316,88,42,QUALITY_NAMES[i],quality==i?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,408,570,66,0xAE193040,0x335D8495,15);
        text(c,"MÚSICA",294,448,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,730,422,86,34,music?"ATIVADA":"DESATIVADA",music?0xFF176A75:0x553B6172,Color.WHITE);
        panel(c,270,490,570,66,0xAE193040,0x335D8495,15);
        text(c,"EFEITOS SONOROS",294,530,14,Color.WHITE,Typeface.DEFAULT_BOLD);
        pill(c,730,504,86,34,effects?"ATIVADOS":"DESATIVADOS",effects?0xFF176A75:0x553B6172,Color.WHITE);
        text(c,"Áudio será conectado ao sistema de jogo conforme os módulos forem adicionados.",272,590,10,0xFF8FAAB8,Typeface.DEFAULT);
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
        text(c,"e mantenha sua equipe por perto.",913,560,13,Color.WHITE,Typeface.DEFAULT_BOLD);
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
        panel(c,22,20,250,64,0xB6071724,0x557AA0B2,16);
        text(c,"BATTLE UNIVERSE",42,48,18,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(c,"TREINAMENTO  •  "+MAP_NAMES[map],42,67,10,0xFF6FE0E6,Typeface.DEFAULT_BOLD);
        pill(c,24,625,160,49,"VOLTAR AO LOBBY",0xDD173C4A,Color.WHITE);
        panel(c,420,650,440,40,0xBB071522,0x226A8EA0,14);
        text(c,"ESQUERDA: MOVER  •  DIREITA: OLHAR  •  TOQUE: PULAR / COLETAR",438,675,10,0xFFDAEAF0,Typeface.DEFAULT_BOLD);
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
            if(forwarding){game.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)forwarding=false;return true;}
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP){float[] q=design(e.getX(),e.getY());tap(q[0],q[1]);invalidate();}
        return true;
    }
    private void tap(float x,float y){
        if(System.currentTimeMillis()-lastTap<110)return;lastTap=System.currentTimeMillis();
        for(int i=0;i<6;i++){float ny=170+i*72;if(hit(x,y,35,ny,188,56)){page=i;return;}}
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
            if(hit(x,y,270,408,570,148)){character=(character+1)%4;game.setCharacter(character);prefs.edit().putInt("character",character).apply();toast("Visual do operador atualizado");return;}
        }else if(page==SETTINGS){
            if(hit(x,y,515,306,300,60)){quality=x<618?0:x<714?1:2;game.setQuality(quality);prefs.edit().putInt("quality",quality).apply();toast("Qualidade: "+QUALITY_NAMES[quality]);return;}
            if(hit(x,y,715,414,110,52)){music=!music;prefs.edit().putBoolean("music",music).apply();toast(music?"Música ativada":"Música desativada");return;}
            if(hit(x,y,715,495,110,52)){effects=!effects;prefs.edit().putBoolean("effects",effects).apply();toast(effects?"Efeitos ativados":"Efeitos desativados");}
        }
    }
    private void toast(String s){toast=s;toastUntil=System.currentTimeMillis()+1800;invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
}
