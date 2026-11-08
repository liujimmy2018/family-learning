package cn.family.learning.catalog;

import cn.family.learning.auth.Tokens;
import cn.family.learning.common.*;
import cn.family.learning.learning.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.family.learning.catalog.CatalogDtos.*;

@Service
public class ReviewService {
    private final JdbcTemplate jdbc;private final LearningService learning;
    public ReviewService(JdbcTemplate jdbc,LearningService learning){this.jdbc=jdbc;this.learning=learning;}
    private Map<String,Object> stored(String practice){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM dictation_review WHERE practice_id=?",practice);return rows.isEmpty()?null:rows.get(0);}
    private List<ReviewWord> source(String owner,String child,Dtos.PracticeDto p,Map<String,Object> row){
        if(row!=null)return Json.read((String)row.get("source_json"),new TypeReference<List<ReviewWord>>(){});
        List<ReviewWord> words=new ArrayList<>();int grade=learning.child(owner,child).grade;
        for(Dtos.SnapshotWord w:p.words()){ReviewWord rw=new ReviewWord();rw.id=w.id();rw.ordinal=w.ordinal();rw.english=w.text();rw.chinese=w.meaning();rw.phonetic=w.phonetic();rw.grade=grade;words.add(rw);}return words;
    }
    public Map<String,Object> review(String owner,String child,String practice){
        Ids.require(practice);Dtos.PracticeDto p=learning.practice(owner,child,practice);Map<String,Object> row=stored(practice);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("practiceId",practice);result.put("title",p.title());result.put("status",p.status());result.put("words",source(owner,child,p,row));result.put("rounds",p.settings().rounds());result.put("elapsedSeconds",row==null?0:row.get("elapsed_seconds"));result.put("graded",row!=null&&row.get("marks_json")!=null);result.put("marks",row==null||row.get("marks_json")==null?Collections.emptyList():Json.read((String)row.get("marks_json"),new TypeReference<List<Mark>>(){}));result.put("score",row==null?null:row.get("score"));result.put("correct",row==null?null:row.get("correct_count"));result.put("total",p.words().size()*p.settings().rounds());return result;
    }
    private Dtos.PracticeDto lock(String owner,String child,String practice){learning.lockedChild(owner,child);Dtos.PracticeDto p=learning.practice(owner,child,Ids.require(practice));jdbc.queryForList("SELECT id FROM practice_session WHERE id=? FOR UPDATE",practice);return p;}
    private Map<String,Object> ensure(String owner,String child,Dtos.PracticeDto p){
        Map<String,Object> row=stored(p.id());if(row!=null)return row;
        jdbc.update("INSERT INTO dictation_review(id,practice_id,owner_id,child_id,source_json) VALUES(?,?,?,?,?)",Ids.next(),p.id(),owner,child,Json.write(source(owner,child,p,null)));return stored(p.id());
    }
    @Transactional public Map<String,Object> timing(String owner,String child,String practice,Timing input){
        Dtos.PracticeDto p=lock(owner,child,practice);ensure(owner,child,p);
        jdbc.update("UPDATE dictation_review SET elapsed_seconds=GREATEST(elapsed_seconds,?) WHERE practice_id=? AND marks_json IS NULL",input.elapsedSeconds,practice);return review(owner,child,practice);
    }
    @Transactional public Map<String,Object> grade(String owner,String child,String practice,GradeInput input){
        Dtos.PracticeDto p=lock(owner,child,practice);
        if(!"COMPLETED".equals(p.status()))throw ApiException.invalid("请完成听写并同步记录后再批改");
        input.marks=ReviewRules.validate(input.marks,p.words().size(),p.settings().rounds());String hash=Tokens.hash(Json.write(input));
        Map<String,Object> row=ensure(owner,child,p);
        if(row.get("marks_json")!=null){LearningService.sameHash((String)row.get("request_hash"),hash);return review(owner,child,practice);}
        List<ReviewWord> words=source(owner,child,p,row);int correct=0;
        for(Mark m:input.marks){if(m.correct){correct++;continue;}ReviewWord w=words.get(m.index);
            String wordHash=CatalogRules.wordKey(w.english);
            if("CHINESE".equals(p.subject))wordHash=Tokens.hash("CHINESE:"+wordHash);
            jdbc.update("INSERT INTO wrong_word(id,owner_id,child_id,word_hash,english,chinese,phonetic,grade,semester,unit_name,mistake_count,last_practice_id,last_wrong_at) VALUES(?,?,?,?,?,?,?,?,?,?,1,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE english=VALUES(english),chinese=VALUES(chinese),phonetic=IF(VALUES(phonetic)='',phonetic,VALUES(phonetic)),grade=IF(VALUES(grade)=0,grade,VALUES(grade)),semester=IF(VALUES(semester)='',semester,VALUES(semester)),unit_name=IF(VALUES(unit_name)='',unit_name,VALUES(unit_name)),mistake_count=mistake_count+1,mastered=FALSE,last_practice_id=VALUES(last_practice_id),last_wrong_at=UTC_TIMESTAMP()",Ids.next(),owner,child,wordHash,w.english,w.chinese,w.phonetic,w.grade,w.semester,w.unit,practice);
        }
        jdbc.update("UPDATE dictation_review SET marks_json=?,elapsed_seconds=GREATEST(elapsed_seconds,?),total_count=?,correct_count=?,score=?,request_hash=?,graded_at=UTC_TIMESTAMP() WHERE practice_id=?",Json.write(input.marks),input.elapsedSeconds,input.marks.size(),correct,ReviewRules.score(correct,input.marks.size()),hash,practice);
        return review(owner,child,practice);
    }
    public List<Map<String,Object>> wrongWords(String owner,String child,String subject){
        learning.child(owner,child);
        return jdbc.queryForList("SELECT w.id,p.subject,w.english,w.chinese,w.phonetic,w.grade,w.semester,w.unit_name AS unit,w.mistake_count AS mistakeCount,w.mastered,DATE_FORMAT(w.last_wrong_at,'%Y-%m-%d %H:%i:%s') AS lastWrongAt FROM wrong_word w JOIN practice_session p ON w.last_practice_id=p.id WHERE w.owner_id=? AND w.child_id=? AND p.subject=? ORDER BY w.mastered,w.last_wrong_at DESC,w.id DESC LIMIT 5000",owner,child,subject);
    }
    public Map<String,Object> history(String owner,String child,String filter,int page,int size,String subject){
        learning.child(owner,child);
        String condition;
        switch(filter){
            case "ALL":condition="";break;
            case "PENDING":condition=" AND p.status NOT IN ('COMPLETED','STOPPED')";break;
            case "UNREVIEWED":condition=" AND p.status='COMPLETED' AND r.marks_json IS NULL";break;
            case "GRADED":condition=" AND r.marks_json IS NOT NULL";break;
            default:throw ApiException.invalid("历史记录筛选条件无效");
        }
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.id,COALESCE(r.elapsed_seconds,0) AS elapsedSeconds,r.score,r.correct_count AS correct,r.total_count AS total,r.marks_json IS NOT NULL AS graded,DATE_FORMAT(p.created_at,'%Y-%m-%dT%H:%i:%sZ') AS createdAt FROM practice_session p LEFT JOIN dictation_review r ON r.practice_id=p.id WHERE p.owner_id=? AND p.child_id=? AND p.subject=?"+condition+" ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?",owner,child,subject,size+1,page*size);
        boolean more=rows.size()>size;List<Map<String,Object>> items=new ArrayList<>();
        for(Map<String,Object> row:rows.subList(0,Math.min(size,rows.size()))){
            Dtos.PracticeDto practice=learning.practice(owner,child,(String)row.get("id"));
            Map<String,Object> item=new LinkedHashMap<>(row);item.remove("id");item.put("practice",practice);
            Object graded=row.get("graded");item.put("graded",Boolean.TRUE.equals(graded)||(graded instanceof Number&&((Number)graded).intValue()!=0));item.put("total",practice.words().size()*practice.settings().rounds());items.add(item);
        }
        return Values.map("items",items,"page",page,"size",size,"hasMore",more);
    }
    @Transactional public void mastery(String owner,String child,String id,Mastery input){learning.lockedChild(owner,child);if(jdbc.update("UPDATE wrong_word SET mastered=? WHERE id=? AND owner_id=? AND child_id=?",input.mastered,Ids.require(id),owner,child)!=1)throw ApiException.missing();}
}
