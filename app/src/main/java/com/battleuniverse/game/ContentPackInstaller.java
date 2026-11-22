package com.battleuniverse.game;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ContentPackInstaller {
    private static final long MIN_DOWNLOAD_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_DOWNLOAD_BYTES = 5L * 1024L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 3L * 1024L * 1024L * 1024L;
    private static final int PACK_VERSION = 3;
    private static final String MANIFEST_URL = "https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/download/data-pack-v3/content-pack-manifest.json";
    private static final String ARCHIVE_URL = "https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/download/data-pack-v3/battle-universe-season-02-pack.zip";

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
    private volatile Uri selectedArchiveUri;

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

    public void startFromUri(Uri archiveUri) {
        if(archiveUri==null) throw new IllegalArgumentException("Arquivo ZIP inválido.");
        selectedArchiveUri=archiveUri;
        start();
    }

    public void start() {
        if(running) return;
        running=true;
        worker.execute(()->{
            File root=appContext.getFilesDir();
            File archive=new File(root,"battle-content-season02.part");
            File staging=new File(root,"battle-content-staging");
            File installed=new File(root,"battle-content");
            File backup=new File(root,"battle-content-backup");
            long expectedPackSize=-1L;
            try {
                postProgress(0,1,"PREPARANDO A PRIMEIRA CONFIGURAÇÃO...");
                if(!isWifiConnected()) throw new Exception("A Season 02 é instalada somente por Wi-Fi. Conecte-se a uma rede Wi-Fi e tente novamente.");
                JSONObject manifest=readRemoteManifest();
                String expectedHash=manifest.getString("sha256").toLowerCase(Locale.US);
                long expectedSize=manifest.getLong("size_bytes");
                expectedPackSize=expectedSize;
                int version=manifest.getInt("version");
                if(version!=PACK_VERSION) throw new Exception("Versão de conteúdo incompatível com este APK.");
                if(expectedSize<MIN_DOWNLOAD_BYTES||expectedSize>MAX_DOWNLOAD_BYTES)
                    throw new Exception("O pacote Season 02 precisa ter entre 1 e 5 GiB.");
                StatFs storage=new StatFs(root.getAbsolutePath());
                long needed=expectedSize*2L+MAX_EXTRACTED_BYTES+256L*1024L*1024L;
                if(storage.getAvailableBytes()<needed)
                    throw new Exception("Espaço insuficiente para configurar o conteúdo. Libere pelo menos "+
                        String.format(Locale.US,"%.0f MB",needed/1048576.0)+" e tente novamente.");
                boolean imported=selectedArchiveUri!=null;
                boolean restored=false;
                if(imported) {
                    Uri source=selectedArchiveUri;
                    selectedArchiveUri=null;
                    copySelectedArchive(source,archive,expectedSize);
                    postProgress(expectedSize,expectedSize,"ARQUIVO ZIP SELECIONADO • CONFERINDO...");
                } else {
                    restored=restoreSavedArchive(archive,expectedSize);
                    if(!restored) downloadArchive(archive,expectedSize);
                }
                postProgress(expectedSize,expectedSize,"CONFERINDO A INTEGRIDADE DO CONTEÚDO...");
                String actualHash=sha256(archive);
                if(!actualHash.equals(expectedHash) && restored) {
                    archive.delete();
                    restored=false;
                    postProgress(0,expectedSize,"O ARQUIVO SALVO NÃO PASSOU NA VERIFICAÇÃO. BAIXANDO UMA CÓPIA NOVA...");
                    downloadArchive(archive,expectedSize);
                    actualHash=sha256(archive);
                }
                if(!actualHash.equals(expectedHash))
                    throw new Exception("A verificação SHA-256 do conteúdo integrado falhou.");
                if(!restored) saveArchiveToDownloads(archive);
                postProgress(0,1,"PREPARANDO OS RECURSOS DO UNIVERSO...");
                deleteTree(staging);
                if(!staging.mkdirs()&&!staging.isDirectory())
                    throw new Exception("Não foi possível preparar os arquivos temporários.");
                extractAndVerify(archive,staging);
                JSONObject installedInfo=new JSONObject();
                installedInfo.put("version",version);
                installedInfo.put("archive_sha256",actualHash);
                installedInfo.put("source","verified_github_release");
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
                archive.delete(); // Keep the shared Downloads copy; remove only the private working copy.
                postProgress(1,1,"PACOTE SALVO NO CELULAR • UNIVERSO CONFIGURADO...");

                main.post(()->{running=false;listener.onSuccess();});
            } catch(Exception error) {
                // Keep complete downloads for retry and future APK updates.
                deleteTree(staging);
                String message=error.getMessage()==null?
                    "Falha desconhecida durante a configuração.":error.getMessage();
                main.post(()->{running=false;listener.onFailure(message);});
            }
        });
    }


    private static final String SAVED_ARCHIVE_NAME = "battle-universe-season-02-pack.zip";
    private static final String SAVED_ARCHIVE_FOLDER = "Download/Battle Universe/";

    /** Copies the user's original ZIP as-is; extraction and validation happen afterward. */
    private void copySelectedArchive(Uri source,File output,long expectedSize) throws Exception {
        String displayName="";
        try(Cursor cursor=appContext.getContentResolver().query(source,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME,
                             android.provider.OpenableColumns.SIZE},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()) {
                displayName=cursor.getString(0);
                if(!cursor.isNull(1)) {
                    long reported=cursor.getLong(1);
                    if(reported>0&&reported!=expectedSize)
                        throw new Exception("O ZIP selecionado não corresponde ao tamanho da Season 02.");
                }
            }
        }
        if(displayName!=null&&!displayName.toLowerCase(Locale.US).endsWith(".zip"))
            throw new Exception("Selecione o arquivo .zip original da Season 02, sem extrair.");
        try(InputStream in=new BufferedInputStream(appContext.getContentResolver().openInputStream(source));
            FileOutputStream out=new FileOutputStream(output,false)) {
            if(in==null) throw new Exception("Não foi possível abrir o ZIP selecionado.");
            byte[] buffer=new byte[65536];int count;long copied=0,lastReport=0;
            while((count=in.read(buffer))!=-1) {
                copied+=count;
                if(copied>expectedSize||copied>MAX_DOWNLOAD_BYTES)
                    throw new Exception("O ZIP selecionado é maior que o pacote esperado.");
                out.write(buffer,0,count);
                if(copied-lastReport>=4L*1024L*1024L) {
                    postProgress(copied,expectedSize,"LENDO O ZIP ORIGINAL...");
                    lastReport=copied;
                }
            }
            out.getFD().sync();
            if(copied!=expectedSize)
                throw new Exception("O ZIP está incompleto ou não é o pacote Season 02 correto.");
        } catch(Exception error) {
            output.delete();
            throw error;
        }
    }

    /** Restore the public Downloads copy to the private working file without downloading it again. */
    private boolean restoreSavedArchive(File output,long expectedSize) throws Exception {
        if(Build.VERSION.SDK_INT>=29) {
            Uri collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] columns={MediaStore.Downloads._ID,MediaStore.Downloads.SIZE,MediaStore.Downloads.RELATIVE_PATH};
            String selection=MediaStore.Downloads.DISPLAY_NAME+"=?";
            try(Cursor cursor=appContext.getContentResolver().query(
                    collection,columns,selection,new String[]{SAVED_ARCHIVE_NAME},
                    MediaStore.Downloads.DATE_MODIFIED+" DESC")) {
                if(cursor!=null) while(cursor.moveToNext()) {
                    String relative=cursor.getString(2);
                    if(relative==null||!SAVED_ARCHIVE_FOLDER.equals(relative)) continue;
                    long size=cursor.getLong(1);
                    if(size!=expectedSize) continue;
                    Uri uri=Uri.withAppendedPath(collection,cursor.getString(0));
                    try(InputStream in=new BufferedInputStream(appContext.getContentResolver().openInputStream(uri));
                        FileOutputStream out=new FileOutputStream(output,false)) {
                        if(in==null) continue;
                        byte[] buffer=new byte[65536];int count;long copied=0;
                        while((count=in.read(buffer))!=-1) {
                            copied+=count;
                            if(copied>expectedSize) throw new Exception("O arquivo salvo excede o tamanho esperado.");
                            out.write(buffer,0,count);
                        }
                        out.getFD().sync();
                        if(copied==expectedSize) {
                            postProgress(copied,expectedSize,"PACOTE EXISTENTE ENCONTRADO NO CELULAR...");
                            return true;
                        }
                    } catch(Exception ignored) {
                        output.delete();
                    }
                }
            }
        } else {
            File publicCopy=new File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),SAVED_ARCHIVE_NAME);
            if(publicCopy.isFile()&&publicCopy.length()==expectedSize) {
                try(InputStream in=new FileInputStream(publicCopy);FileOutputStream out=new FileOutputStream(output,false)) {
                    byte[] buffer=new byte[65536];int count;
                    while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
                    out.getFD().sync();
                }
                return output.length()==expectedSize;
            }
        }
        output.delete();
        return false;
    }

    /** Keep a verified archive in the user's Downloads folder; it is intentionally not deleted after extraction. */
    private void saveArchiveToDownloads(File source) throws Exception {
        if(Build.VERSION.SDK_INT>=29) {
            Uri collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String selection=MediaStore.Downloads.DISPLAY_NAME+"=? AND "+MediaStore.Downloads.RELATIVE_PATH+"=?";
            appContext.getContentResolver().delete(collection,selection,
                    new String[]{SAVED_ARCHIVE_NAME,SAVED_ARCHIVE_FOLDER});
            ContentValues values=new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME,SAVED_ARCHIVE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE,"application/zip");
            values.put(MediaStore.Downloads.RELATIVE_PATH,SAVED_ARCHIVE_FOLDER);
            values.put(MediaStore.Downloads.IS_PENDING,1);
            Uri target=appContext.getContentResolver().insert(collection,values);
            if(target==null) throw new Exception("Não foi possível salvar o pacote na pasta Downloads.");
            boolean complete=false;
            try {
                try(InputStream in=new FileInputStream(source);
                    java.io.OutputStream out=appContext.getContentResolver().openOutputStream(target,"w")) {
                    if(out==null) throw new Exception("Não foi possível gravar o pacote no celular.");
                    byte[] buffer=new byte[65536];int count;long copied=0;
                    while((count=in.read(buffer))!=-1) {copied+=count;out.write(buffer,0,count);}
                    out.flush();
                    if(copied!=source.length()) throw new Exception("Falha ao copiar o pacote para Downloads.");
                }
                ContentValues ready=new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING,0);
                appContext.getContentResolver().update(target,ready,null,null);
                complete=true;
            } finally {
                if(!complete) appContext.getContentResolver().delete(target,null,null);
            }
        } else {
            File folder=appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(folder==null) throw new Exception("Armazenamento externo indisponível.");
            if(!folder.exists()&&!folder.mkdirs()) throw new Exception("Não foi possível criar a pasta de downloads.");
            File target=new File(folder,SAVED_ARCHIVE_NAME);
            try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(target,false)) {
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
                out.getFD().sync();
            }
        }
        postProgress(source.length(),source.length(),"PACOTE SALVO NO CELULAR...");
    }

    private boolean isWifiConnected() {
        ConnectivityManager manager=(ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)return false;
        Network active=manager.getActiveNetwork();
        if(active==null)return false;
        NetworkCapabilities caps=manager.getNetworkCapabilities(active);
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private JSONObject readRemoteManifest() throws Exception {
        if(!isWifiConnected())throw new Exception("Conecte-se ao Wi-Fi para preparar a Season 02.");
        HttpURLConnection connection=(HttpURLConnection)new URL(MANIFEST_URL).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept","application/json");
        try {
            int code=connection.getResponseCode();
            if(code!=200)throw new Exception("Não foi possível consultar a versão da Season 02 (HTTP "+code+").");
            try(InputStream in=new BufferedInputStream(connection.getInputStream());
                ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count,total=0;
                while((count=in.read(buffer))!=-1) {
                    total+=count;
                    if(total>65536)throw new Exception("Manifesto remoto excede o limite permitido.");
                    out.write(buffer,0,count);
                }
                JSONObject manifest=new JSONObject(out.toString("UTF-8"));
                if(manifest.optInt("version",-1)!=PACK_VERSION)
                    throw new Exception("Esta versão do APK não corresponde à Season 02 publicada.");
                if(!ARCHIVE_URL.equals(manifest.optString("url","")))
                    throw new Exception("Endereço do pacote não reconhecido.");
                String hash=manifest.optString("sha256","").toLowerCase(Locale.US);
                if(!hash.matches("[a-f0-9]{64}"))
                    throw new Exception("Hash do pacote inválido no manifesto.");
                long size=manifest.optLong("size_bytes",-1L);
                if(size<MIN_DOWNLOAD_BYTES||size>MAX_DOWNLOAD_BYTES)
                    throw new Exception("O pacote remoto está fora do limite de 1 a 5 GiB.");
                return manifest;
            }
        } finally {connection.disconnect();}
    }

    private void downloadArchive(File output,long expectedSize) throws Exception {
        if(!isWifiConnected())throw new Exception("O Wi-Fi foi desconectado. O download será retomado quando você voltar ao Wi-Fi.");
        long offset=output.isFile()?output.length():0L;
        if(offset>expectedSize){output.delete();offset=0L;}
        HttpURLConnection connection=(HttpURLConnection)new URL(ARCHIVE_URL).openConnection();
        connection.setConnectTimeout(25000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept","application/zip");
        if(offset>0L)connection.setRequestProperty("Range","bytes="+offset+"-");
        try {
            int code=connection.getResponseCode();
            boolean append=offset>0L&&code==206;
            if(!append&&code!=200)throw new Exception("Falha ao acessar o pacote Season 02 (HTTP "+code+").");
            if(!append)offset=0L;
            long copied=offset,lastReport=offset;
            try(InputStream in=new BufferedInputStream(connection.getInputStream());
                FileOutputStream out=new FileOutputStream(output,append)) {
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1) {
                    if(copied%1048576L<65536L&&!isWifiConnected())
                        throw new Exception("Wi-Fi desconectado. O download parcial foi preservado para continuar depois.");
                    copied+=count;
                    if(copied>expectedSize||copied>MAX_DOWNLOAD_BYTES)
                        throw new Exception("O pacote recebido excedeu o tamanho declarado.");
                    out.write(buffer,0,count);
                    if(copied-lastReport>=1048576L) {
                        postProgress(copied,expectedSize,"PREPARANDO A TEMPORADA 02...");
                        postFileProgress("PACOTE SEASON 02",1,1,copied,expectedSize);
                        lastReport=copied;
                    }
                }
                out.getFD().sync();
            }
            if(copied!=expectedSize)
                throw new Exception("Download incompleto. O trecho recebido foi preservado para retomada.");
            postFileProgress("PACOTE SEASON 02",1,1,copied,expectedSize);
        } finally {connection.disconnect();}
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
