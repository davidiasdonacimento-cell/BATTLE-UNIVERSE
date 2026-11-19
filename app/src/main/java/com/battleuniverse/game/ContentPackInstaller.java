package com.battleuniverse.game;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ContentPackInstaller {
    private static final String MANIFEST_URL =
        "https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/download/data-pack-v2/content-pack-manifest.json";
    private static final long MAX_DOWNLOAD_BYTES = 450L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 520L * 1024L * 1024L;
    private static final int PACK_VERSION = 2;

    public interface Listener {
        void onProgress(long downloaded,long total,String status);
        void onSuccess();
        void onFailure(String message);
    }

    private final Context appContext;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running;

    public ContentPackInstaller(Context context,Listener listener) {
        appContext=context.getApplicationContext();
        this.listener=listener;
    }

    public static boolean isInstalled(Context context) {
        File root=new File(context.getFilesDir(),"battle-content");
        File marker=new File(root,"installed.json");
        File island=new File(root,"worlds/island.json");
        File model=new File(root,"models/operator_field.obj");
        File plane=new File(root,"models/transport_plane.obj");
        File texture=new File(root,"textures/island_ground_albedo_4k.png");
        if(!marker.isFile()||!island.isFile()||!model.isFile()||!plane.isFile()||!texture.isFile()) return false;
        try(FileInputStream in=new FileInputStream(marker)) {
            byte[] data=new byte[(int)Math.min(marker.length(),8192)];
            int n=in.read(data);
            JSONObject json=new JSONObject(new String(data,0,Math.max(0,n),"UTF-8"));
            return json.optInt("version",-1)==PACK_VERSION;
        } catch(Exception ignored) { return false; }
    }

    public static int[] readIslandSettings(Context context) {
        try {
            File file=new File(context.getFilesDir(),"battle-content/worlds/island.json");
            byte[] bytes=new byte[(int)Math.min(file.length(),65536)];
            try(FileInputStream in=new FileInputStream(file)) {
                int n=in.read(bytes);
                JSONObject json=new JSONObject(new String(bytes,0,Math.max(0,n),"UTF-8"));
                int limit=Math.max(4,Math.min(24,json.optInt("build_limit",12)));
                JSONArray nodes=json.optJSONArray("resource_nodes");
                int count=nodes==null?5:Math.max(1,Math.min(12,nodes.length()));
                return new int[]{limit,count};
            }
        } catch(Exception ignored) { }
        return new int[]{12,5};
    }

    public void start() {
        if(running) return;
        running=true;
        worker.execute(()->{
            File root=appContext.getFilesDir();
            File archive=new File(root,"battle-content-download.part");
            File staging=new File(root,"battle-content-staging");
            File installed=new File(root,"battle-content");
            File backup=new File(root,"battle-content-backup");
            try {
                postProgress(0,1,"CONSULTANDO VERSÃO DO PACOTE...");
                JSONObject manifest=downloadManifest();
                String url=manifest.getString("url");
                String expectedHash=manifest.getString("sha256").toLowerCase(Locale.US);
                long expectedSize=manifest.getLong("size_bytes");
                int version=manifest.getInt("version");
                if(version!=PACK_VERSION) throw new Exception("Versão de dados incompatível.");
                if(expectedSize<=250L*1024L*1024L||expectedSize>MAX_DOWNLOAD_BYTES)
                    throw new Exception("Tamanho do pacote fora do intervalo esperado para a Temporada 02.");
                StatFs storage=new StatFs(root.getAbsolutePath());
                long needed=expectedSize*2L+110L*1024L*1024L;
                if(storage.getAvailableBytes()<needed)
                    throw new Exception("Espaço insuficiente. Libere pelo menos "+
                        String.format(Locale.US,"%.0f MB",needed/1048576.0)+" e tente novamente.");
                downloadArchive(url,archive,expectedSize);
                String actualHash=sha256(archive);
                if(!actualHash.equals(expectedHash)) throw new Exception("Falha na verificação SHA-256. O pacote foi descartado.");
                postProgress(expectedSize,expectedSize,"VERIFICANDO E INSTALANDO ARQUIVOS...");
                deleteTree(staging);
                if(!staging.mkdirs()&&!staging.isDirectory()) throw new Exception("Não foi possível criar a pasta temporária.");
                extractAndVerify(archive,staging);
                JSONObject installedInfo=new JSONObject();
                installedInfo.put("version",version);
                installedInfo.put("archive_sha256",actualHash);
                installedInfo.put("installed_at",System.currentTimeMillis());
                try(FileOutputStream out=new FileOutputStream(new File(staging,"installed.json"))) {
                    out.write(installedInfo.toString().getBytes("UTF-8"));
                    out.getFD().sync();
                }
                deleteTree(backup);
                if(installed.exists()&&!installed.renameTo(backup))
                    throw new Exception("Não foi possível preservar os dados anteriores.");
                if(!staging.renameTo(installed)) {
                    if(backup.exists()) backup.renameTo(installed);
                    throw new Exception("Falha ao ativar os dados. A versão anterior foi preservada.");
                }
                deleteTree(backup);
                archive.delete();
                postProgress(expectedSize,expectedSize,"DADOS VERIFICADOS • REINICIANDO O JOGO...");
                main.post(()->{running=false;listener.onSuccess();});
            } catch(Exception error) {
                archive.delete();
                deleteTree(staging);
                String message=error.getMessage()==null?"Falha desconhecida durante a instalação.":error.getMessage();
                main.post(()->{running=false;listener.onFailure(message);});
            }
        });
    }

    private JSONObject downloadManifest() throws Exception {
        HttpURLConnection connection=open(MANIFEST_URL);
        try(InputStream in=new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count,total=0;
            while((count=in.read(buffer))!=-1) {
                total+=count;
                if(total>65536) throw new Exception("Manifesto maior que o limite permitido.");
                out.write(buffer,0,count);
            }
            return new JSONObject(out.toString("UTF-8"));
        } finally { connection.disconnect(); }
    }

    private void downloadArchive(String url,File output,long total) throws Exception {
        HttpURLConnection connection=open(url);
        try(InputStream in=new BufferedInputStream(connection.getInputStream());
            FileOutputStream file=new FileOutputStream(output);
            OutputStream out=new BufferedOutputStream(file)) {
            byte[] buffer=new byte[65536];int count;long received=0;
            while((count=in.read(buffer))!=-1) {
                received+=count;
                if(received>MAX_DOWNLOAD_BYTES) throw new Exception("Download excedeu o limite de segurança.");
                out.write(buffer,0,count);
                if(received%262144<buffer.length) postProgress(received,total,"BAIXANDO PACOTE DE CONTEÚDO...");
            }
            out.flush();file.getFD().sync();
            if(received!=total) throw new Exception("Download incompleto. O arquivo recebido tem tamanho diferente do manifesto.");
        } finally { connection.disconnect(); }
    }

    private HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(18000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent","BattleUniverse-Android/0.3");
        connection.setRequestProperty("Accept","application/octet-stream, application/json");
        int code=connection.getResponseCode();
        if(code<200||code>=300) {
            connection.disconnect();
            throw new Exception("Servidor de dados respondeu HTTP "+code+". Tente novamente.");
        }
        return connection;
    }

    private void extractAndVerify(File archive,File destination) throws Exception {
        long expanded=0;int entries=0;
        String rootPath=destination.getCanonicalPath()+File.separator;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>1500) throw new Exception("O pacote contém entradas demais.");
                String name=entry.getName().replace('\\','/');
                if(name.startsWith("/")||name.contains("../")||name.equals(".."))
                    throw new Exception("Caminho inseguro encontrado no pacote.");
                File out=new File(destination,name);
                String canonical=out.getCanonicalPath();
                if(!canonical.startsWith(rootPath)) throw new Exception("Arquivo fora da pasta do jogo.");
                if(entry.isDirectory()) {
                    if(!out.mkdirs()&&!out.isDirectory()) throw new Exception("Falha ao criar pasta do conteúdo.");
                } else {
                    File parent=out.getParentFile();
                    if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())
                        throw new Exception("Falha ao preparar pasta do conteúdo.");
                    try(FileOutputStream stream=new FileOutputStream(out)) {
                        int n;
                        while((n=zip.read(buffer))!=-1) {
                            expanded+=n;
                            if(expanded>MAX_EXTRACTED_BYTES) throw new Exception("O conteúdo descompactado excede o limite.");
                            stream.write(buffer,0,n);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        File packManifest=new File(destination,"manifest.json");
        if(!packManifest.isFile()) throw new Exception("Manifesto interno não encontrado.");
        JSONObject json=readJson(packManifest);
        if(json.optInt("version",-1)!=PACK_VERSION) throw new Exception("Versão interna do conteúdo não confere.");
        JSONArray files=json.optJSONArray("files");
        if(files==null||files.length()<10) throw new Exception("O pacote está incompleto.");
        for(int i=0;i<files.length();i++) {
            JSONObject item=files.getJSONObject(i);
            String path=item.getString("path");
            File file=new File(destination,path);
            if(!file.isFile()||file.length()!=item.getLong("size"))
                throw new Exception("Arquivo ausente ou incompleto: "+path);
            if(!sha256(file).equalsIgnoreCase(item.getString("sha256")))
                throw new Exception("Integridade inválida: "+path);
        }
        JSONArray required=json.optJSONArray("required");
        if(required==null) throw new Exception("Lista de arquivos obrigatórios ausente.");
        for(int i=0;i<required.length();i++) {
            if(!new File(destination,required.getString(i)).isFile())
                throw new Exception("Recurso obrigatório não encontrado: "+required.getString(i));
        }
    }

    private JSONObject readJson(File file) throws Exception {
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count,total=0;
            while((count=in.read(buffer))!=-1){total+=count;if(total>1024*1024)throw new Exception("JSON interno excessivo.");out.write(buffer,0,count);}
            return new JSONObject(out.toString("UTF-8"));
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];int count;
            while((count=in.read(buffer))!=-1) digest.update(buffer,0,count);
        }
        StringBuilder out=new StringBuilder();
        for(byte value:digest.digest()) out.append(String.format(Locale.US,"%02x",value&0xff));
        return out.toString();
    }

    private void postProgress(long done,long total,String status) {
        main.post(()->listener.onProgress(done,total,status));
    }
    private void deleteTree(File file) {
        if(file==null||!file.exists()) return;
        if(file.isDirectory()) {
            File[] children=file.listFiles();
            if(children!=null) for(File child:children) deleteTree(child);
        }
        file.delete();
    }
    public void shutdown() {
        worker.shutdownNow();
        running=false;
    }
}
