package com.battleuniverse.game;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Strict analyzer/importer for the official Season 02 content archive. */
public final class ContentPackAnalyzer {
    public interface Listener {
        void onStatus(String message);
        void onConflict(String message,Runnable replaceAction);
        void onSuccess(String message);
        void onFailure(String message);
    }
    private static final Object INSTALL_LOCK=new Object();
    private static final String MANIFEST_URL="https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/download/data-pack-v2/content-pack-manifest.json";
    private static final String PACK_ID="battle_universe_season_02";
    private static final int VERSION=2;
    private static final long MAX_ARCHIVE=2L*1024*1024*1024;
    private static final long MAX_EXPANDED=3L*1024*1024*1024;
    private static final int MAX_ENTRIES=1500;
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    public ContentPackAnalyzer(Context c,Listener l){context=c.getApplicationContext();listener=l;}

    public void analyze(Uri uri) {
        if(uri==null){fail("Nenhum arquivo foi selecionado.");return;}
        worker.execute(()->{
            String operationId=UUID.randomUUID().toString();
            File archive=new File(context.getCacheDir(),"bu-pack-import-"+operationId+".part");
            File staging=new File(context.getFilesDir(),"battle-content-import-staging-"+operationId);
            try {
                status("LENDO O ARQUIVO QUE VOCÊ SELECIONOU...");
                // O nome externo pode ser qualquer um. A validação é feita pelo conteúdo,
                // manifesto oficial e SHA-256, nunca pelo nome exibido pelo Android.
                copyUri(uri,archive);
                if(archive.length()<1024||archive.length()>MAX_ARCHIVE)throw new Exception("Tamanho do arquivo inválido.");
                // Diferencia o ZIP de distribuição do APK do ZIP de conteúdo Season 02.
                // O APK compactado não deve ser comparado ao tamanho do pacote 4K.
                try(ZipFile selected=new ZipFile(archive)) {
                    boolean hasApk=false,hasManifest=false;
                    Enumeration<? extends ZipEntry> selectedEntries=selected.entries();
                    while(selectedEntries.hasMoreElements()) {
                        String name=selectedEntries.nextElement().getName().toLowerCase(Locale.ROOT);
                        if(name.endsWith(".apk"))hasApk=true;
                        if("manifest.json".equals(name))hasManifest=true;
                    }
                    if(hasApk&&!hasManifest)
                        throw new Exception("Este ZIP contém um APK de instalação, não o pacote de conteúdo Season 02. Extraia e instale o arquivo app-release.apk; para o analisador, selecione o ZIP Season 02 4K.");
                }
                status("CONFERINDO ASSINATURA OFICIAL DO PACOTE...");
                JSONObject remote=downloadJson(MANIFEST_URL);
                long expectedArchiveSize=remote.getLong("size_bytes");
                if(archive.length()!=expectedArchiveSize)
                    throw new Exception("Tamanho diferente do pacote Season 02 oficial. Arquivo selecionado: "
                        +String.format(Locale.US,"%.2f MB",archive.length()/1048576.0)
                        +"; tamanho oficial esperado: "
                        +String.format(Locale.US,"%.2f MB",expectedArchiveSize/1048576.0)
                        +". Confira se selecionou o ZIP Season 02 4K, e não o ZIP do APK.");
                String archiveHash=sha256(archive);
                if(!archiveHash.equalsIgnoreCase(remote.getString("sha256")))
                    throw new Exception("SHA-256 do pacote não confere com a versão oficial. Arquivo rejeitado.");
                status("PROCURANDO MANIFESTO E NOMES INTERNOS...");
                deleteTree(staging);
                if(!staging.mkdirs()&&!staging.isDirectory())throw new Exception("Não foi possível preparar a análise.");
                extractAndVerify(archive,staging);
                File installed=new File(context.getFilesDir(),"battle-content");
                JSONObject marker=readMarker(new File(installed,"installed.json"));
                if(marker!=null&&archiveHash.equalsIgnoreCase(marker.optString("archive_sha256",""))
                        &&ContentPackInstaller.isInstalled(context)) {
                    deleteTree(staging);archive.delete();
                    success("PACOTE JÁ INSTALADO E ÍNTEGRO. Nenhum download ou substituição foi necessário.");
                    return;
                }
                if(installed.exists()) {
                    final String hash=archiveHash;
                    status("PACOTE VÁLIDO. AGUARDANDO CONFIRMAÇÃO PARA SUBSTITUIR A VERSÃO INSTALADA...");
                    main.post(()->{
                        if(!closed)listener.onConflict("Já existe um pacote instalado com outro SHA-256. Deseja substituir pela versão oficial selecionada?",()->{
                            if(!closed) {
                                try { worker.execute(()->activate(staging,installed,archive,hash)); }
                                catch(java.util.concurrent.RejectedExecutionException ignored) { deleteTree(staging);archive.delete(); }
                            }
                        });
                        else { deleteTree(staging);archive.delete(); }
                    });
                } else activate(staging,installed,archive,archiveHash);
            } catch(Exception e) {
                deleteTree(staging);archive.delete();
                fail(e.getMessage()==null?"Falha ao analisar o pacote.":e.getMessage());
            }
        });
    }

    private void activate(File staging,File installed,File archive,String hash) {
        synchronized(INSTALL_LOCK) {
        if(closed){deleteTree(staging);archive.delete();return;}
        File backup=new File(context.getFilesDir(),"battle-content-import-backup");
        try {
            JSONObject marker=new JSONObject().put("version",VERSION).put("pack_id",PACK_ID)
                .put("archive_sha256",hash).put("installed_at",System.currentTimeMillis())
                .put("source","local_import");
            try(FileOutputStream out=new FileOutputStream(new File(staging,"installed.json"))){
                out.write(marker.toString().getBytes("UTF-8"));out.getFD().sync();
            }
            deleteTree(backup);
            if(installed.exists()&&!installed.renameTo(backup))throw new Exception("Não foi possível preservar a versão anterior.");
            if(!staging.renameTo(installed)) {
                if(backup.exists())backup.renameTo(installed);
                throw new Exception("Não foi possível ativar o pacote. A versão anterior foi restaurada.");
            }
            deleteTree(backup);archive.delete();
            success("PACOTE ANALISADO E INSTALADO. Feche e abra o jogo para carregar os novos dados.");
        } catch(Exception e) {
            deleteTree(staging);
            fail(e.getMessage()==null?"Falha ao ativar o pacote.":e.getMessage());
        }
        }
    }

    private void extractAndVerify(File archive,File dest)throws Exception {
        Set<String> names=new HashSet<>();
        Set<String> extracted=new HashSet<>();
        long total=0;int count=0;
        String root=dest.getCanonicalPath()+File.separator;
        try(ZipFile zip=new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> scan=zip.entries();
            while(scan.hasMoreElements()) {
                ZipEntry e=scan.nextElement();
                String path=normalize(e.getName());
                if(!safe(path))throw new Exception("Caminho inseguro no ZIP: "+path);
                String key=path.toLowerCase(Locale.ROOT);
                if(!names.add(key))throw new Exception("Conflito: o ZIP contém nomes duplicados ("+path+").");
                if(!e.isDirectory()) {
                    if(++count>MAX_ENTRIES)throw new Exception("O pacote contém arquivos demais.");
                    if(e.getSize()<0)throw new Exception("Não foi possível medir "+path+".");
                    total+=e.getSize();
                    if(total>MAX_EXPANDED)throw new Exception("O pacote excede o limite descompactado.");
                }
            }
            if(!names.contains("manifest.json"))throw new Exception("Manifesto manifest.json não encontrado na raiz.");
            Enumeration<? extends ZipEntry> entries=zip.entries();
            long written=0;
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();String path=normalize(e.getName());
                File out=new File(dest,path);
                if(!out.getCanonicalPath().startsWith(root))throw new Exception("Arquivo fora da pasta de destino.");
                if(e.isDirectory()){if(!out.mkdirs()&&!out.isDirectory())throw new Exception("Falha ao criar diretório.");continue;}
                extracted.add(path.toLowerCase(Locale.ROOT));
                File parent=out.getParentFile();
                if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())throw new Exception("Falha ao criar pasta interna.");
                try(InputStream in=new BufferedInputStream(zip.getInputStream(e));FileOutputStream file=new FileOutputStream(out)){
                    byte[] b=new byte[32768];int n;long size=0;
                    while((n=in.read(b))!=-1){size+=n;written+=n;if(written>MAX_EXPANDED)throw new Exception("Limite de extração excedido.");file.write(b,0,n);}
                    file.getFD().sync();
                    if(size!=e.getSize())throw new Exception("Extração incompleta: "+path);
                }
            }
        }
        JSONObject manifest=readJson(new File(dest,"manifest.json"));
        if(!PACK_ID.equals(manifest.optString("pack_id",""))||manifest.optInt("version",-1)!=VERSION)
            throw new Exception("O identificador interno não corresponde ao BATTLE UNIVERSE Season 02.");
        JSONArray files=manifest.optJSONArray("files");
        if(files==null||files.length()<10)throw new Exception("A lista de arquivos do manifesto está incompleta.");
        Set<String> listed=new HashSet<>();
        for(int i=0;i<files.length();i++) {
            JSONObject item=files.getJSONObject(i);String path=normalize(item.getString("path"));
            if(!safe(path)||!listed.add(path.toLowerCase(Locale.ROOT)))throw new Exception("Manifesto contém caminho duplicado ou inválido: "+path);
            File file=new File(dest,path);
            if(!file.isFile()||file.length()!=item.getLong("size"))throw new Exception("Nome ou tamanho divergente: "+path);
            if(!sha256(file).equalsIgnoreCase(item.getString("sha256")))throw new Exception("SHA-256 inválido: "+path);
        }
        extracted.remove("manifest.json");
        if(!extracted.equals(listed))throw new Exception("O ZIP contém arquivos extras ou faltando em relação ao manifesto.");
        JSONArray required=manifest.optJSONArray("required");
        if(required==null||required.length()==0)throw new Exception("Lista de recursos obrigatórios ausente.");
        for(int i=0;i<required.length();i++){
            String path=normalize(required.getString(i));
            if(!safe(path)||!new File(dest,path).isFile())throw new Exception("Recurso obrigatório ausente: "+path);
        }
        for(String requiredName:new String[]{"worlds/island.json","models/operator_field.obj","models/transport_plane.obj","textures/island_ground_albedo_4k.png"})
            if(!listed.contains(requiredName))throw new Exception("Manifesto não lista o arquivo principal: "+requiredName);
    }

    private boolean safe(String p){
        if(p.isEmpty()||p.startsWith("/")||p.matches("^[A-Za-z]:.*")||p.contains("//"))return false;
        for(String part:p.split("/"))if(part.isEmpty()||".".equals(part)||"..".equals(part))return false;
        return true;
    }
    private String normalize(String p){return p==null?"":p.replace('\\','/').replaceAll("^\\./","");}
    private String displayName(Uri uri)throws Exception {
        ContentResolver cr=context.getContentResolver();
        try(Cursor c=cr.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}
        }
        String tail=uri.getLastPathSegment();return tail==null?"":tail;
    }
    private void copyUri(Uri uri,File out)throws Exception {
        try(InputStream in=new BufferedInputStream(context.getContentResolver().openInputStream(uri));FileOutputStream file=new FileOutputStream(out)){
            if(in==null)throw new Exception("Não foi possível abrir o arquivo selecionado.");
            byte[] b=new byte[65536];int n;long total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>MAX_ARCHIVE)throw new Exception("Arquivo maior que o limite de 2 GB.");file.write(b,0,n);}
            file.getFD().sync();
        }
    }
    private JSONObject downloadJson(String url)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>65536)throw new Exception("Manifesto remoto excessivo.");out.write(b,0,n);}
            return new JSONObject(out.toString("UTF-8"));
        }finally{c.disconnect();}
    }
    private JSONObject readJson(File file)throws Exception {
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>1024*1024)throw new Exception("Manifesto interno excessivo.");out.write(b,0,n);}
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private JSONObject readMarker(File file) {
        try{return file.isFile()?readJson(file):null;}catch(Exception e){return null;}
    }
    private String sha256(File file)throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();
    }
    private void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] c=f.listFiles();if(c!=null)for(File x:c)deleteTree(x);}f.delete();}
    private void status(String s){main.post(()->{if(!closed&&listener!=null)listener.onStatus(s);});}
    private void success(String s){main.post(()->{if(!closed&&listener!=null)listener.onSuccess(s);});}
    private void fail(String s){main.post(()->{if(!closed&&listener!=null)listener.onFailure(s);});}
    public void close(){closed=true;worker.shutdownNow();}
}
