package lolpatcher;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;
import nl.xupwup.Util.MiniHttpClient;

/**
 *
 * @author Rick
 */
public class ArchiveDownloadWorker extends Worker{
    
    LoLPatcher patcher;

    public ArchiveDownloadWorker(LoLPatcher patcher) {
        this.patcher = patcher;
    }

    @Override
    public void run() {
        try {
            try (MiniHttpClient htc = new MiniHttpClient("l3cdn.riotgames.com")) {
                htc.throwExceptionWhenNot200 = true;
                
                LoLPatcher.Archive task;
                while(true){
                    synchronized(patcher.archivesToPatch){
                        if(patcher.archivesToPatch.isEmpty() || patcher.done || patcher.error != null){
                            break;
                        }
                        task = patcher.archivesToPatch.remove(0);
                    }
                    startTime = System.currentTimeMillis();
                    progress = 0;
                    RAFArchive archive = patcher.getArchive(task.versionName);
                    for(int i = 0; i < task.files.size(); i++){
                        if(patcher.done || patcher.error != null){
                            break;
                        }
                        ReleaseManifest.File file = task.files.get(i);
                        current = file.name;
                        RAFArchive.RafFile raff = archive.dictionary.get(file.path + file.name);
                        
                        if(raff != null){
                            alternative = true;
                            InputStream in = archive.readFile(raff);
                            if(file.fileType == 22){
                                in = new InflaterInputStream(in);
                            }
                            if(checkHash(new BufferedInputStream(in), patcher, file, false)){
                                progress = (float) i / task.files.size();
                                continue;
                            }else{
                                System.out.println("bad file: " + file);
                                archive.fileList.remove(raff);
                                archive.dictionary.remove(file.path + file.name);
                            }
                        }
                        alternative = false;
                        downloadFileToArchive(file, htc, archive);
                        progress = (float) i / task.files.size();
                    }
                    archive.close();
                    progress = 1;
                    startTime = -1;
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(ArchiveDownloadWorker.class.getName()).log(Level.SEVERE, null, ex);
            if(patcher.error == null){
                patcher.error = ex;
            }
        }
    }
    
    private void downloadFileToArchive(ReleaseManifest.File f, MiniHttpClient hc, RAFArchive archive) throws IOException{
        String url = "/releases/"+patcher.branch+"/"+patcher.type+"/"
            + patcher.project + "/releases/" + f.release + "/files/" + 
            f.path.replaceAll(" ", "%20") + f.name.replaceAll(" ", "%20") + (f.fileType > 0 ? ".compressed" : "");
        MiniHttpClient.HttpResult hte = hc.get(url);

        try(InputStream in = (f.fileType == 6 ? new InflaterInputStream(hte.in) : hte.in)){
            archive.writeFile(f.path + f.name, in, patcher);
        }
    }
    
}
