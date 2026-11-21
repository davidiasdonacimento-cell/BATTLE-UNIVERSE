package com.battleuniverse.game;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ContentPackInstaller {
    private static final long MAX_DOWNLOAD_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 3L * 1024L * 1024L * 1024L;
    private static final int PACK_VERSION = 2;

    public interface Listener {
        void onProgress(long downloaded,long total,String status);
        void onFileProgress(String fileName,int index,int count,long done,long size);
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
            File archive=new File(root,"battle-content-embedded.part");
            File staging=new File(root,"battle-content-staging");
            File installed=new File(root,"battle-content");
            File backup=new File(root,"battle-content-backup");
            long expectedPackSize=-1L;
            try {
                postProgress(0,1,"PREPARANDO A PRIMEIRA CONFIGURAÇÃO...");
                JSONObject manifest=readEmbeddedManifest();
                String expectedHash=manifest.getString("sha256").toLowerCase(Locale.US);
                long expectedSize=manifest.getLong("size_bytes");
                expectedPackSize=expectedSize;
                int version=manifest.getInt("version");
                if(version!=PACK_VERSION) throw new Exception("Versão de conteúdo incompatível com este APK.");
                if(expectedSize<=250L*1024L*1024L||expectedSize>MAX_DOWNLOAD_BYTES)
                    throw new Exception("Tamanho do conteúdo integrado fora do intervalo esperado.");
                StatFs storage=new StatFs(root.getAbsolutePath());
                long needed=expectedSize*2L+256L*1024L*1024L;
                if(storage.getAvailableBytes()<needed)
                    throw new Exception("Espaço insuficiente para configurar o conteúdo. Libere pelo menos "+
                        String.format(Locale.US,"%.0f MB",needed/1048576.0)+" e tente novamente.");
                copyEmbeddedArchive(archive,expectedSize);
                postProgress(expectedSize,expectedSize,"CONFERINDO A INTEGRIDADE DO CONTEÚDO...");
                String actualHash=sha256(archive);
                if(!actualHash.equals(expectedHash))
                    throw new Exception("A verificação SHA-256 do conteúdo integrado falhou.");
                postProgress(0,1,"PREPARANDO OS RECURSOS DO UNIVERSO...");
                deleteTree(staging);
                if(!staging.mkdirs()&&!staging.isDirectory())
                    throw new Exception("Não foi possível preparar os arquivos temporários.");
                extractAndVerify(archive,staging);
                JSONObject installedInfo=new JSONObject();
                installedInfo.put("version",version);
                installedInfo.put("archive_sha256",actualHash);
                installedInfo.put("source","embedded_apk");
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
                postProgress(1,1,"UNIVERSO CONFIGURADO • ABRINDO O BATTLE UNIVERSE...");
                main.post(()->{running=false;listener.onSuccess();});
            } catch(Exception error) {
                if(expectedPackSize>0L && archive.isFile()
                        && archive.length()>=expectedPackSize) archive.delete();
                deleteTree(staging);
                String message=error.getMessage()==null?
                    "Falha desconhecida durante a configuração.":error.getMessage();
                main.post(()->{running=false;listener.onFailure(message);});
            }
        });
    }

    private JSONObject readEmbeddedManifest() throws Exception {
        try(InputStream in=new BufferedInputStream(appContext.getAssets().open("season02-manifest.json"));
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count,total=0;
            while((count=in.read(buffer))!=-1) {
                total+=count;
                if(total>65536) throw new Exception("Manifesto integrado excede o limite permitido.");
                out.write(buffer,0,count);
            }
            return new JSONObject(out.toString("UTF-8"));
        }
    }

    private void copyEmbeddedArchive(File output,long expectedSize) throws Exception {
        long copied=0L;
        try(InputStream in=new BufferedInputStream(appContext.getAssets().open("season02.zip"));
            FileOutputStream out=new FileOutputStream(output,false)) {
            byte[] buffer=new byte[65536];int count;long lastReport=0L;
            while((count=in.read(buffer))!=-1) {
                copied+=count;
                if(copied>MAX_DOWNLOAD_BYTES||copied>expectedSize)
                    throw new Exception("O conteúdo integrado excedeu o tamanho declarado.");
                out.write(buffer,0,count);
                if(copied-lastReport>=1048576L) {
                    postProgress(copied,expectedSize,"CONFIGURANDO ARQUIVOS INTEGRADOS...");
                    postFileProgress("CONTEÚDO DA TEMPORADA 02",1,1,copied,expectedSize);
                    lastReport=copied;
                }
            }
            out.getFD().sync();
        }
        if(copied!=expectedSize)
            throw new Exception("O conteúdo integrado está incompleto dentro do APK.");
        postFileProgress("CONTEÚDO DA TEMPORADA 02",1,1,copied,expectedSize);
    }

    private void extractAndVerify(File archive,File destination) throws Exception {
        long totalExpanded=0,expanded=0;
        int totalFiles=0;
        String rootPath=destination.getCanonicalPath()+File.separator;
        try(ZipFile zip=new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> scan=zip.entries();
            while(scan.hasMoreElements()) {
                ZipEntry entry=scan.nextElement();
                if(entry.isDirectory()) continue;
                if(++totalFiles>1500) throw new Exception("O pacote contém arquivos demais.");
                long size=entry.getSize();
                if(size<0) throw new Exception("Não foi possível medir um arquivo do pacote.");
                totalExpanded+=size;
                if(totalExpanded>MAX_EXTRACTED_BYTES)
                    throw new Exception("O conteúdo descompactado excede o limite permitido.");
            }
            if(totalFiles==0) throw new Exception("O pacote não contém arquivos.");

            Enumeration<? extends ZipEntry> entries=zip.entries();
            int currentFile=0;
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                String name=entry.getName().replace('\\','/');
                if(name.startsWith("/")||name.contains("../")||name.equals(".."))
                    throw new Exception("Caminho inseguro encontrado no pacote.");
                File out=new File(destination,name);
                String canonical=out.getCanonicalPath();
                if(!canonical.startsWith(rootPath)) throw new Exception("Arquivo fora da pasta do jogo.");
                if(entry.isDirectory()) {
                    if(!out.mkdirs()&&!out.isDirectory()) throw new Exception("Falha ao criar pasta do conteúdo.");
                    continue;
                }

                currentFile++;
                File parent=out.getParentFile();
                if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())
                    throw new Exception("Falha ao preparar pasta do conteúdo.");
                long fileDone=0,fileSize=entry.getSize(),lastReport=0;
                try(InputStream input=new BufferedInputStream(zip.getInputStream(entry));
                    FileOutputStream output=new FileOutputStream(out)) {
                    byte[] buffer=new byte[32768];
                    int n;
                    while((n=input.read(buffer))!=-1) {
                        expanded+=n;
                        fileDone+=n;
                        if(expanded>MAX_EXTRACTED_BYTES) throw new Exception("O conteúdo descompactado excede o limite.");
                        output.write(buffer,0,n);
                        long now=System.currentTimeMillis();
                        if(now-lastReport>=100) {
                            postProgress(expanded,totalExpanded,"INSTALANDO ARQUIVO "+currentFile+"/"+totalFiles+"...");
                            postFileProgress(name,currentFile,totalFiles,fileDone,fileSize);
                            lastReport=now;
                        }
                    }
                    output.getFD().sync();
                }
                if(fileDone!=fileSize) throw new Exception("Extração incompleta: "+name);
                postProgress(expanded,totalExpanded,"INSTALANDO ARQUIVO "+currentFile+"/"+totalFiles+"...");
                postFileProgress(name,currentFile,totalFiles,fileDone,fileSize);
            }
        }

        File packManifest=new File(destination,"manifest.json");
        if(!packManifest.isFile()) throw new Exception("Manifesto interno não encontrado.");
        JSONObject json=readJson(packManifest);
        if(json.optInt("version",-1)!=PACK_VERSION) throw new Exception("Versão interna do conteúdo não confere.");
        JSONArray files=json.optJSONArray("files");
        if(files==null||files.length()<10) throw new Exception("O pacote está incompleto.");
        long verifyTotal=0;
        for(int i=0;i<files.length();i++) verifyTotal+=files.getJSONObject(i).getLong("size");
        long verified=0;
        for(int i=0;i<files.length();i++) {
            JSONObject item=files.getJSONObject(i);
            String path=item.getString("path");
            File file=new File(destination,path);
            if(!file.isFile()||file.length()!=item.getLong("size"))
                throw new Exception("Arquivo ausente ou incompleto: "+path);
            if(!sha256(file).equalsIgnoreCase(item.getString("sha256")))
                throw new Exception("Integridade inválida: "+path);
            verified+=file.length();
            postProgress(verified,verifyTotal,"CONFERINDO SHA-256 "+(i+1)+"/"+files.length()+"...");
            postFileProgress(path,i+1,files.length(),file.length(),file.length());
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
    private void postFileProgress(String name,int index,int count,long done,long size) {
        main.post(()->listener.onFileProgress(name,index,count,done,size));
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
