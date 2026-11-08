package cn.family.learning.data;

import java.time.LocalDateTime;
import cn.family.learning.common.Ids;

public final class Rows {
    private Rows() {}
    public static class Account { public String id=Ids.next(); public String loginName,passwordHash; }
    public static class Invite { public String id=Ids.next(); public String codeHash; public Integer remainingUses; public LocalDateTime expiresAt; }
    public static class Session { public String id=Ids.next(); public String ownerId,accessHash,refreshHash; public LocalDateTime accessExpires,refreshExpires; public Boolean revoked; }
    public static class Child { public String id=Ids.next(); public String ownerId,nickname,textbook; public Long version; public Integer grade; public Boolean deleted; }
    public static class Asset { public String id=Ids.next(); public String ownerId,childId,objectKey,contentType; public Long sizeBytes; public Integer width,height; public LocalDateTime expiresAt; }
    public static class Material { public String id=Ids.next(); public String ownerId,childId,title,type,assetIds; }
    public static class Job { public String id=Ids.next(); public String ownerId,childId,materialId,type,status,inputJson,resultJson,errorCode,errorMessage,claimToken,requestKey,requestHash; public Integer attemptCount; public LocalDateTime leaseUntil,nextRunAt; }
    public static class ListHead { public String id=Ids.next(); public String subject="ENGLISH"; public String ownerId,childId,materialId,recognitionJobId,title,requestKey,requestHash; public Integer currentVersion; }
    public static class ListVersion { public String id=Ids.next(); public String listId,title,requestKey,requestHash; public Integer version; }
    public static class Word { public String id=Ids.next(); public String versionId,text,meaning; public Integer ordinal; }
    public static class Practice { public String id=Ids.next(); public String subject="ENGLISH"; public String ownerId,childId,listId,title,snapshotJson,settingsJson,status,progressJson,requestKey,requestHash; public Long version; }
}
