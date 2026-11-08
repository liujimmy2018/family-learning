package cn.family.learning.learning;

import cn.family.learning.common.Values;

import cn.family.learning.auth.Tokens;
import cn.family.learning.common.*;
import cn.family.learning.data.LearningMapper;
import cn.family.learning.data.Rows.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.family.learning.learning.Dtos.*;

@Service
public class LearningService {
    private final LearningMapper db;
    public LearningService(LearningMapper db) {this.db=db;}
    public Child child(String owner,String id) {Ids.require(owner);Ids.require(id);Child c=db.child(owner,id); if(c==null) throw ApiException.missing(); return c;}
    public Child lockedChild(String owner,String id) {Ids.require(owner);Ids.require(id);Child c=db.lockChild(owner,id); if(c==null) throw ApiException.missing(); return c;}
    private ChildDto dto(Child c) {return new ChildDto(c.id.toString(),c.nickname,c.grade,c.textbook,c.version==null?1:c.version);}
    public List<ChildDto> children(String owner) {return db.children(owner).stream().map(this::dto).collect(java.util.stream.Collectors.toList());}
    @Transactional public ChildDto createChild(String owner,ChildInput r) {
        if(db.children(owner).size()>=10) throw ApiException.invalid("每个账号最多建立10个孩子档案");
        Child c=new Child(); c.ownerId=owner; c.nickname=r.nickname().trim(); c.grade=r.grade(); c.textbook=r.textbook().trim(); db.childInsert(c); return dto(c);
    }
    @Transactional public ChildDto updateChild(String owner,String id,ChildInput r) {
        Child c=child(owner,id); c.nickname=r.nickname().trim(); c.grade=r.grade(); c.textbook=r.textbook().trim(); c.version=r.expectedVersion();
        if(db.childUpdate(c)!=1) throw ApiException.conflict(); return dto(child(owner,id));
    }
    @Transactional public void archiveChild(String owner,String id) {lockedChild(owner,id); db.childDelete(owner,id); db.cancelChildJobs(owner,id);}
    public List<ListDto> lists(String owner,String child) {child(owner,child); return db.lists(owner,child).stream().map(this::listDto).collect(java.util.stream.Collectors.toList());}
    public ListDto list(String owner,String child,String id) {child(owner,child); ListHead l=db.list(owner,child,id); if(l==null) throw ApiException.missing(); return listDto(l);}
    private ListDto listDto(ListHead l) {return new ListDto(l.id.toString(),l.title,l.currentVersion,db.words(l.id,l.currentVersion).stream().map(w->new WordDto(w.id.toString(),w.ordinal,w.text,w.meaning)).collect(java.util.stream.Collectors.toList()));}
    @Transactional public ListDto saveList(String owner,String child,String id,String key,ListInput r) {
        lockedChild(owner,child); validateKey(key); String hash=Tokens.hash(Json.write(r));
        ListHead l;
        if(id==null) {
            l=db.listByKey(owner,child,key); if(l!=null) {sameHash(l.requestHash,hash);return listDto(l);}
            l=new ListHead();l.ownerId=owner;l.childId=child;l.title=r.title().trim();l.currentVersion=1;l.requestKey=key;l.requestHash=hash;
            if(r.recognitionJobId()!=null && !r.recognitionJobId().trim().isEmpty()) {
                Job j=db.job(owner,child,requireId(r.recognitionJobId()));
                if(j==null || !"RECOGNITION".equals(j.type) || !"SUCCEEDED".equals(j.status)) throw ApiException.invalid("请先完成照片识别，或改为手动创建");
                ListHead previous=db.listByJob(owner,child,j.id); if(previous!=null) {sameHash(previous.requestHash,hash);return listDto(previous);}
                l.recognitionJobId=j.id;l.materialId=j.materialId;
            }
            db.listInsert(l);
        } else {
            l=db.list(owner,child,id); if(l==null) throw ApiException.missing();
            ListVersion previous=db.versionByKey(id,key);
            if(previous!=null){sameHash(previous.requestHash,hash);return new ListDto(id.toString(),previous.title,previous.version,db.words(id,previous.version).stream().map(w->new WordDto(w.id.toString(),w.ordinal,w.text,w.meaning)).collect(java.util.stream.Collectors.toList()));}
            if(db.bumpList(id,r.title().trim(),r.expectedVersion())!=1) throw ApiException.conflict();
            l.title=r.title().trim();l.currentVersion++;
        }
        ListVersion v=new ListVersion();v.listId=l.id;v.version=l.currentVersion;v.title=l.title;v.requestKey=key;v.requestHash=hash;db.versionInsert(v);
        int n=0;for(WordInput input:r.words()) { Word w=new Word();w.versionId=v.id;w.ordinal=n++;w.text=input.text().trim();w.meaning=input.meaning().trim();db.wordInsert(w); }
        return listDto(l);
    }
    @Transactional public PracticeDto createPractice(String owner,String child,String key,PracticeInput r) {
        lockedChild(owner,child);validateKey(key);String hash=Tokens.hash(Json.write(r));
        Practice previous=db.practiceByKey(owner,child,key);if(previous!=null){sameHash(previous.requestHash,hash);return practiceDto(previous);}
        ListDto list=list(owner,child,requireId(r.listId())); if(list.version()!=r.expectedListVersion()) throw ApiException.conflict();
        Set<String> ids=new LinkedHashSet<>(r.itemIds()); if(ids.size()!=r.itemIds().size()) throw ApiException.invalid("听写词条不能重复选择");
        Map<String,WordDto> available=new HashMap<>();list.words().forEach(w->available.put(w.id(),w));
        List<SnapshotWord> chosen=new ArrayList<>();for(String id:ids){WordDto w=available.get(id);if(w==null) throw ApiException.invalid("词条已更新，请刷新词表");chosen.add(new SnapshotWord(w.id(),w.ordinal(),w.text(),w.meaning()));}
        if(r.settings().randomOrder()) Collections.shuffle(chosen,new java.security.SecureRandom());
        Practice p=new Practice();p.ownerId=owner;p.childId=child;p.listId=requireId(r.listId());p.title=list.title();p.snapshotJson=Json.write(chosen);p.settingsJson=Json.write(r.settings());p.requestKey=key;p.requestHash=hash;
        db.practiceInsert(p);return practiceDto(db.practice(owner,child,p.id));
    }
    public PracticeDto practice(String owner,String child,String id) {child(owner,child);Practice p=db.practice(owner,child,id);if(p==null)throw ApiException.missing();return practiceDto(p);}
    public List<PracticeDto> practices(String owner,String child) {child(owner,child);return db.practices(owner,child).stream().map(this::practiceDto).collect(java.util.stream.Collectors.toList());}
    private PracticeDto practiceDto(Practice p) {return new PracticeDto(p.id.toString(),p.title,Json.read(p.snapshotJson,new TypeReference<List<SnapshotWord>>(){}),Json.read(p.settingsJson,Settings.class),p.status,p.version,p.progressJson==null?Values.map():Json.read(p.progressJson,new TypeReference<Map<String,Object>>(){}),p.subject);}
    @Transactional public PracticeDto progress(String owner,String child,String id,ProgressInput r) {
        lockedChild(owner,child); Practice p=db.practice(owner,child,id);if(p==null)throw ApiException.missing();
        PracticeDto snapshot=practiceDto(p);
        if(r.round()>=snapshot.settings().rounds() || r.itemIndex()>=snapshot.words().size())throw ApiException.invalid("听写进度超出范围");
        if("COMPLETED".equals(p.status)||"STOPPED".equals(p.status)) {
            if(Objects.equals(p.status,r.status()))return snapshot;
            throw ApiException.conflict();
        }
        if(p.version!=r.expectedVersion())throw ApiException.conflict();
        for(Event e:r.events()) {if(e.round()>=snapshot.settings().rounds()||e.itemIndex()>=snapshot.words().size())throw ApiException.invalid("播放事件超出范围");db.event(Ids.next(),id,e.eventId(),Json.write(e));}
        p.progressJson=Json.write(Values.map("round",r.round(),"itemIndex",r.itemIndex()));p.status=r.status();
        if(db.progress(p)!=1)throw ApiException.conflict(); return practiceDto(db.practice(owner,child,id));
    }
    public static void validateKey(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))throw ApiException.invalid("需要有效的Idempotency-Key");}
    public static void sameHash(String old,String value){if(!Objects.equals(old,value))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","相同请求标识不能提交不同内容");}
    public static String requireId(String id){return Ids.require(id);}
}
