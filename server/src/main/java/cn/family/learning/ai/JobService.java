package cn.family.learning.ai;

import cn.family.learning.common.Values;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import cn.family.learning.auth.Tokens;
import cn.family.learning.common.*;
import cn.family.learning.data.*;
import cn.family.learning.data.Rows.*;
import cn.family.learning.learning.*;
import cn.family.learning.learning.Dtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.TransientDataAccessException;
import java.util.*;

@Service
public class JobService {
    private final LearningMapper db;private final LearningService learning;private final AssetService assets;private final int dailyLimit;private final DashScopeProvider ai;
    private final TransactionTemplate claimTransaction;
    public JobService(LearningMapper db,LearningService learning,AssetService assets,DashScopeProvider ai,@Value("${app.daily-job-limit}")int dailyLimit,PlatformTransactionManager transactions){
        this.db=db;this.learning=learning;this.assets=assets;this.ai=ai;this.dailyLimit=dailyLimit;
        claimTransaction=new TransactionTemplate(transactions);
        claimTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        claimTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        claimTransaction.setTimeout(15);
    }
    public static final class RecognitionInput {
        public final String type;
        public final List<String> assetIds;
        public final int grade;
        @JsonCreator
        public RecognitionInput(@JsonProperty("type") String type, @JsonProperty("assetIds") List<String> assetIds, @JsonProperty("grade") int grade) {
            this.type = type;
            this.assetIds = assetIds;
            this.grade = grade;
        }
        public String type() { return type; }
        public List<String> assetIds() { return assetIds; }
        public int grade() { return grade; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            RecognitionInput value = (RecognitionInput) other;
            return java.util.Objects.equals(type, value.type) && java.util.Objects.equals(assetIds, value.assetIds) && java.util.Objects.equals(grade, value.grade);
        }
        @Override public int hashCode() { return java.util.Objects.hash(type, assetIds, grade); }
    }
    @Transactional public JobDto recognize(String owner,String child,String key,MaterialInput r){
        db.lockAccount(owner);cn.family.learning.data.Rows.Child c=learning.lockedChild(owner,child);LearningService.validateKey(key);String hash=Tokens.hash(Json.write(r));
        Job previous=db.jobByKey(owner,child,"RECOGNITION",key);if(previous!=null){LearningService.sameHash(previous.requestHash,hash);return dto(previous);}
        quota(owner);
        for(String asset:r.assetIds()){Asset a=assets.asset(owner,child,LearningService.requireId(asset));if(!a.contentType.startsWith("image/"))throw ApiException.invalid("请选择学习图片");}
        Material m=new Material();m.ownerId=owner;m.childId=child;m.title=r.title();m.type=r.type();m.assetIds=Json.write(r.assetIds());db.materialInsert(m);
        Job j=new Job();j.ownerId=owner;j.childId=child;j.materialId=m.id;j.type="RECOGNITION";j.inputJson=Json.write(new RecognitionInput(r.type(),r.assetIds(),c.grade));j.requestKey=key;j.requestHash=hash;db.jobInsert(j);
        return dto(db.job(owner,child,j.id));
    }
    @Transactional public JobDto speech(String owner,String child,SpeechInput r){
        db.lockAccount(owner);learning.lockedChild(owner,child);String input=Json.write(r),hash=Tokens.hash(input+ai.speechNamespace());String key="speech_"+hash;
        Job usable=db.usableJob(owner,child,"SPEECH",hash);if(usable!=null)return dto(usable);
        Job previous=db.jobByKey(owner,child,"SPEECH",key);
        quota(owner);
        Job j=new Job();j.ownerId=owner;j.childId=child;j.type="SPEECH";j.inputJson=input;j.requestHash=hash;j.requestKey=previous==null?key:UUID.randomUUID().toString();db.jobInsert(j);return dto(db.job(owner,child,j.id));
    }
    public static final class WordLookupInput {
        public final String text;
        public final int grade;
        @JsonCreator
        public WordLookupInput(@JsonProperty("text") String text, @JsonProperty("grade") int grade) {
            this.text = text;
            this.grade = grade;
        }
        public String text() { return text; }
        public int grade() { return grade; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            WordLookupInput value = (WordLookupInput) other;
            return java.util.Objects.equals(text, value.text) && java.util.Objects.equals(grade, value.grade);
        }
        @Override public int hashCode() { return java.util.Objects.hash(text, grade); }
    }
    @Transactional public JobDto lookup(String owner,String child,LookupInput r){
        db.lockAccount(owner);cn.family.learning.data.Rows.Child c=learning.lockedChild(owner,child);String input=Json.write(new WordLookupInput(r.text().trim(),c.grade));String hash=Tokens.hash(input+ai.lookupNamespace()),key="lookup_"+hash;
        Job usable=db.usableJob(owner,child,"WORD_LOOKUP",hash);if(usable!=null)return dto(usable);
        Job previous=db.jobByKey(owner,child,"WORD_LOOKUP",key);
        quota(owner);Job j=new Job();j.ownerId=owner;j.childId=child;j.type="WORD_LOOKUP";j.inputJson=input;j.requestHash=hash;j.requestKey=previous==null?key:UUID.randomUUID().toString();db.jobInsert(j);return dto(db.job(owner,child,j.id));
    }
    private void quota(String owner){if(db.dailyJobs(owner)>=dailyLimit)throw new ApiException(429,"DAILY_AI_LIMIT","已达到本账号今天的AI任务上限，请明天再试");}
    public JobDto get(String owner,String child,String id){learning.child(owner,child);Job j=db.job(owner,child,id);if(j==null)throw ApiException.missing();return dto(j);}
    private JobDto dto(Job j){return new JobDto(j.id.toString(),j.status,j.type,j.resultJson==null?null:Json.read(j.resultJson,Object.class),j.errorCode,j.errorMessage);}
    public Job claim(){
        for(int attempt=0;;attempt++) {
            try {
                return claimTransaction.execute(status->{
                    db.recoverJobs();String token=UUID.randomUUID().toString();
                    if(db.claimNextJob(token)!=1)return null;
                    return db.claimedJob(token);
                });
            }catch(TransientDataAccessException e) {
                // The template has rolled back before retrying; never reuse a failed claim transaction.
                if(attempt>=4||Thread.currentThread().isInterrupted())throw e;
                try{Thread.sleep((attempt+1)*java.util.concurrent.ThreadLocalRandom.current().nextInt(5,26));}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw e;}
            }
        }
    }
    public boolean renew(Job j){return db.renewJob(j.id,j.claimToken)==1;}
    @Transactional public void success(Job j,Object value){learning.lockedChild(j.ownerId,j.childId);if(db.finishJob(j.id,j.claimToken,Json.write(value))!=1)throw ApiException.conflict();}
    @Transactional public void audioSuccess(Job j,DashScopeProvider.Audio audio)throws java.io.IOException {
        learning.lockedChild(j.ownerId,j.childId);Job latest=db.job(j.ownerId,j.childId,j.id);
        if(latest==null||!Objects.equals(latest.claimToken,j.claimToken)||!"RUNNING".equals(latest.status))throw ApiException.conflict();
        Asset a=assets.saveAudio(j.ownerId,j.childId,audio.bytes(),audio.contentType());
        if(db.finishJob(j.id,j.claimToken,Json.write(Values.map("assetId",a.id.toString(),"contentType",a.contentType)))!=1)throw ApiException.conflict();
    }
    @Transactional public void failure(Job j,String code,String message,boolean retry){db.failJob(j.id,j.claimToken,retry&&j.attemptCount<3?"QUEUED":"FAILED",code,message);}
}
