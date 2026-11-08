package cn.family.learning.learning;

import cn.family.learning.common.ApiException;
import cn.family.learning.data.LearningMapper;
import cn.family.learning.data.Rows.Asset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class AssetService {
    private final LearningMapper db;
    private final LearningService learning;
    private final Path root;
    public AssetService(LearningMapper db,LearningService learning,@Value("${app.storage-path}")String root) throws IOException {this.db=db;this.learning=learning;this.root=Paths.get(root).toAbsolutePath().normalize();Files.createDirectories(this.root);}
    @Transactional public Asset upload(String owner,String child,MultipartFile file) throws IOException {
        learning.lockedChild(owner,child);
        if(file.isEmpty()||file.getSize()>15*1024*1024) throw ApiException.invalid("请选择15MB以内的图片");
        BufferedImage input;
        try(InputStream upload=file.getInputStream();ImageInputStream stream=ImageIO.createImageInputStream(upload)) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw ApiException.invalid("仅支持JPEG或PNG图片");
            ImageReader reader=readers.next();
            try {reader.setInput(stream);String format=reader.getFormatName();if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))throw ApiException.invalid("仅支持JPEG或PNG图片");
                long pixels=(long)reader.getWidth(0)*reader.getHeight(0);if(pixels>24000000||pixels<100)throw ApiException.invalid("图片尺寸不合适，请先裁剪或缩小");input=reader.read(0);
            }finally{reader.dispose();}
        }catch(javax.imageio.IIOException e){throw ApiException.invalid("图片损坏或无法解码，请重新拍摄");}
        BufferedImage normalized=new BufferedImage(input.getWidth(),input.getHeight(),BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=normalized.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,input.getWidth(),input.getHeight());g.drawImage(input,0,0,null);g.dispose();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(normalized,"jpg",bytes);
        return save(owner,child,bytes.toByteArray(),"image/jpeg",normalized.getWidth(),normalized.getHeight(),LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
    }
    public Asset saveAudio(String owner,String child,byte[] bytes,String type) throws IOException {return save(owner,child,bytes,type,0,0,null);}
    private Asset save(String owner,String child,byte[] bytes,String type,int width,int height,LocalDateTime expires) throws IOException {
        String key=UUID.randomUUID()+(type.startsWith("image")?".jpg":type.equals("audio/wav")?".wav":".mp3");Path target=path(key);Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(target);}catch(IOException ignored){}}});
        Asset a=new Asset();a.ownerId=owner;a.childId=child;a.objectKey=key;a.contentType=type;a.sizeBytes=(long)bytes.length;a.width=width;a.height=height;a.expiresAt=expires;
        try{db.assetInsert(a);}catch(RuntimeException e){Files.deleteIfExists(target);throw e;}return a;
    }
    public Asset asset(String owner,String child,String id){learning.child(owner,child);Asset a=db.asset(owner,child,id);if(a==null)throw ApiException.missing();return a;}
    public byte[] content(String owner,String child,String id) throws IOException {Asset a=asset(owner,child,id);Path file=path(a.objectKey);if(!Files.exists(file))throw ApiException.missing();return Files.readAllBytes(file);}
    private Path path(String key){Path p=root.resolve(key).normalize();if(!p.startsWith(root))throw ApiException.missing();return p;}
    @Scheduled(fixedDelay=3600000) public void cleanExpired() {for(Asset a:db.expiredAssets())try{Files.deleteIfExists(path(a.objectKey));db.assetRemove(a.id);}catch(IOException ignored){}}
}
