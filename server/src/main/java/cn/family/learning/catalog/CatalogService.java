package cn.family.learning.catalog;

import cn.family.learning.auth.Tokens;
import cn.family.learning.common.*;
import cn.family.learning.data.LearningMapper;
import cn.family.learning.data.Rows.*;
import cn.family.learning.learning.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static cn.family.learning.catalog.CatalogDtos.*;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;private final LearningMapper db;private final LearningService learning;
    public CatalogService(JdbcTemplate jdbc,LearningMapper db,LearningService learning){this.jdbc=jdbc;this.db=db;this.learning=learning;}
    private final RowMapper<Entry> entryMapper=(r,n)->{Entry e=new Entry();e.id=r.getString("id");e.grade=r.getInt("grade");e.semester=r.getString("semester");e.unit=r.getString("unit_name");e.english=r.getString("english");e.chinese=r.getString("chinese");e.phonetic=r.getString("phonetic");e.ordinal=r.getInt("ordinal");return e;};
    private Map<String,Object> collection(String owner,String id,boolean lock){
        Ids.require(id);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,title,revision,subject FROM vocabulary_collection WHERE id=? AND owner_id=? AND deleted=FALSE"+(lock?" FOR UPDATE":""),id,owner);
        if(rows.isEmpty())throw ApiException.missing();return rows.get(0);
    }
    public List<Map<String,Object>> collections(String owner,String subject){return jdbc.queryForList("SELECT c.id,c.title,c.revision,c.subject,(SELECT COUNT(*) FROM vocabulary_entry e WHERE e.collection_id=c.id) AS count FROM vocabulary_collection c WHERE c.owner_id=? AND c.deleted=FALSE AND c.subject=? ORDER BY c.created_at DESC,c.id DESC",owner,subject);}
    public Map<String,Object> detail(String owner,String id){Map<String,Object> book=collection(owner,id,false);return Values.map("collection",book,"entries",entries(id));}
    private List<Entry> entries(String id){return jdbc.query("SELECT * FROM vocabulary_entry WHERE collection_id=? ORDER BY grade,semester,ordinal,id",entryMapper,id);}
    @Transactional public Map<String,Object> importEntries(String owner,String key,ImportInput input,String subject){
        LearningService.validateKey(key);db.lockAccount(owner);boolean chinese="CHINESE".equals(subject);CatalogRules.unique(input.entries,chinese);String title=CatalogRules.text(input.title,100,"词表名称",false);
        String hash=Tokens.hash(Json.write(input));List<Map<String,Object>> previous=jdbc.queryForList("SELECT id,request_hash,subject FROM vocabulary_collection WHERE owner_id=? AND request_key=?",owner,key);
        if(!previous.isEmpty()){if(!subject.equals(previous.get(0).get("subject")))throw ApiException.conflict();LearningService.sameHash((String)previous.get(0).get("request_hash"),hash);return detail(owner,(String)previous.get(0).get("id"));}
        String id=Ids.next();jdbc.update("INSERT INTO vocabulary_collection(id,owner_id,title,request_key,request_hash,subject) VALUES(?,?,?,?,?,?)",id,owner,title,key,hash,subject);
        int ordinal=0;for(Entry e:input.entries)insert(id,e,ordinal++);
        return detail(owner,id);
    }
    private void insert(String collection,Entry e,int ordinal){jdbc.update("INSERT INTO vocabulary_entry(id,collection_id,grade,semester,unit_name,english,chinese,phonetic,ordinal,word_hash) VALUES(?,?,?,?,?,?,?,?,?,?)",Ids.next(),collection,e.grade,e.semester,e.unit,e.english,e.chinese,e.phonetic,ordinal,CatalogRules.wordKey(e.english));}
    private void revision(Map<String,Object> book,int expected){if(((Number)book.get("revision")).intValue()!=expected)throw ApiException.conflict();}
    @Transactional public Map<String,Object> rename(String owner,String id,Rename r){Map<String,Object> c=collection(owner,id,true);revision(c,r.expectedRevision);jdbc.update("UPDATE vocabulary_collection SET title=?,revision=revision+1 WHERE id=?",CatalogRules.text(r.title,100,"词表名称",false),id);return detail(owner,id);}
    @Transactional public void deleteCollection(String owner,String id,int expected){revision(collection(owner,id,true),expected);jdbc.update("UPDATE vocabulary_collection SET deleted=TRUE,revision=revision+1 WHERE id=?",id);}
    @Transactional public Map<String,Object> saveEntry(String owner,String id,String entryId,EntryChange r){
        Map<String,Object> book=collection(owner,id,true);revision(book,r.expectedRevision);Entry e=CatalogRules.entry(r.entry,"CHINESE".equals(book.get("subject")));
        if(entryId==null){if(entries(id).size()>=5000)throw ApiException.invalid("每份词表最多5000行");Integer max=jdbc.queryForObject("SELECT COALESCE(MAX(ordinal),-1) FROM vocabulary_entry WHERE collection_id=?",Integer.class,id);insert(id,e,max+1);}
        else {Ids.require(entryId);if(jdbc.update("UPDATE vocabulary_entry SET grade=?,semester=?,unit_name=?,english=?,chinese=?,phonetic=?,word_hash=? WHERE id=? AND collection_id=?",e.grade,e.semester,e.unit,e.english,e.chinese,e.phonetic,CatalogRules.wordKey(e.english),entryId,id)!=1)throw ApiException.missing();}
        jdbc.update("UPDATE vocabulary_collection SET revision=revision+1 WHERE id=?",id);return detail(owner,id);
    }
    @Transactional public Map<String,Object> deleteEntry(String owner,String id,String entryId,int expected){revision(collection(owner,id,true),expected);Ids.require(entryId);if(jdbc.update("DELETE FROM vocabulary_entry WHERE id=? AND collection_id=?",entryId,id)!=1)throw ApiException.missing();jdbc.update("UPDATE vocabulary_collection SET revision=revision+1 WHERE id=?",id);return detail(owner,id);}
    @Transactional public Dtos.PracticeDto createPractice(String owner,String child,String key,Selection r){
        learning.lockedChild(owner,child);LearningService.validateKey(key);String hash=Tokens.hash(Json.write(r));
        Practice previous=db.practiceByKey(owner,child,key);if(previous!=null){LearningService.sameHash(previous.requestHash,hash);return learning.practice(owner,child,previous.id);}
        Map<String,Object> book=collection(owner,r.collectionId,true);revision(book,r.expectedRevision);
        String subject=(String)book.get("subject");
        if("CHINESE".equals(subject)&&!"CHINESE".equals(r.settings.mode()))
            throw ApiException.invalid("生字听写使用普通话播报");
        List<Entry> eligible=entries(r.collectionId).stream().filter(e->e.grade==r.grade&&e.semester.equals(r.semester)&&(r.units.isEmpty()||r.units.contains(e.unit))).collect(Collectors.toList());
        if(eligible.isEmpty())throw ApiException.invalid("所选年级、上下册和单元没有单词");
        if("RANDOM".equals(r.selection)){if(r.count>eligible.size())throw ApiException.invalid("抽取数量不能超过可用单词数");Collections.shuffle(eligible,new java.security.SecureRandom());eligible=new ArrayList<>(eligible.subList(0,r.count));}
        if(eligible.size()>500)throw ApiException.invalid("每次词库听写最多500个单词，请缩小单元范围或选择随机抽取");
        if(r.settings.randomOrder())Collections.shuffle(eligible,new java.security.SecureRandom());
        ListHead list=new ListHead();list.ownerId=owner;list.childId=child;list.subject=subject;list.title=CatalogRules.practiceTitle((String)book.get("title"),r);list.currentVersion=1;list.requestKey="catalog_"+Tokens.hash(key);list.requestHash=hash;db.listInsert(list);
        ListVersion version=new ListVersion();version.listId=list.id;version.version=1;version.title=list.title;version.requestKey=list.requestKey;version.requestHash=hash;db.versionInsert(version);
        List<Dtos.SnapshotWord> snapshot=new ArrayList<>();List<ReviewWord> origin=new ArrayList<>();int n=0;
        for(Entry e:eligible){Word w=new Word();w.versionId=version.id;w.ordinal=n++;w.text=e.english;w.meaning=e.chinese;db.wordInsert(w);snapshot.add(new Dtos.SnapshotWord(w.id,w.ordinal,w.text,w.meaning,e.phonetic));ReviewWord rw=new ReviewWord();rw.id=w.id;rw.ordinal=w.ordinal;rw.english=e.english;rw.chinese=e.chinese;rw.phonetic=e.phonetic;rw.grade=e.grade;rw.semester=e.semester;rw.unit=e.unit;origin.add(rw);}
        Practice p=new Practice();p.ownerId=owner;p.childId=child;p.subject=subject;p.listId=list.id;p.title=list.title;p.snapshotJson=Json.write(snapshot);p.settingsJson=Json.write(r.settings);p.requestKey=key;p.requestHash=hash;db.practiceInsert(p);
        jdbc.update("INSERT INTO dictation_review(id,practice_id,owner_id,child_id,source_json) VALUES(?,?,?,?,?)",Ids.next(),p.id,owner,child,Json.write(origin));
        return learning.practice(owner,child,p.id);
    }
}
