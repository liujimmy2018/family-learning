package cn.family.learning.chinese;

import cn.family.learning.auth.Tokens;
import cn.family.learning.catalog.CatalogRules;
import cn.family.learning.common.ApiException;
import cn.family.learning.common.BmpText;
import cn.family.learning.common.Ids;
import cn.family.learning.common.Json;
import cn.family.learning.data.LearningMapper;
import cn.family.learning.learning.LearningService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@Service
public class RecitationService {
    private static final String LESSON_FIELDS = "id,grade,semester,unit_name AS unit,title,revision";
    private static final String ATTEMPT_FIELDS = "id,lesson_id AS lessonId,grade,semester,unit_name AS unit,title,"
        + "lesson_revision AS lessonRevision,result,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') AS createdAt";

    public static class LessonInput {
        @Min(1) @Max(6) public int grade;
        @NotNull @Pattern(regexp="上册|下册") public String semester;
        @BmpText @NotBlank @Size(max=40) public String unit;
        @BmpText @NotBlank @Size(max=100) public String title;
        @BmpText @NotBlank @Size(max=12000) public String content;
        @Min(0) public int expectedRevision;
    }

    public static class AttemptInput {
        @NotNull @Pattern(regexp=Ids.PATTERN) public String lessonId;
        @Min(1) public int lessonRevision;
        @NotNull @Pattern(regexp="COMPLETED|REVIEW") public String result;
    }

    private final JdbcTemplate jdbc;
    private final LearningMapper db;
    private final LearningService learning;

    public RecitationService(JdbcTemplate jdbc, LearningMapper db, LearningService learning) {
        this.jdbc=jdbc;
        this.db=db;
        this.learning=learning;
    }

    public List<Map<String,Object>> lessons(String owner) {
        return jdbc.queryForList("SELECT "+LESSON_FIELDS+" FROM recitation_lesson WHERE owner_id=? AND deleted=FALSE "
            +"ORDER BY grade,semester,unit_name,title,id",owner);
    }

    public Map<String,Object> lesson(String owner,String id) {
        return lesson(owner,id,false);
    }

    private Map<String,Object> lesson(String owner,String id,boolean lock) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT "+LESSON_FIELDS+",content FROM recitation_lesson "
            +"WHERE owner_id=? AND id=? AND deleted=FALSE"+(lock?" FOR UPDATE":""),owner,id);
        if(rows.isEmpty())throw ApiException.missing();
        return rows.get(0);
    }

    private void normalize(LessonInput input) {
        input.title=CatalogRules.text(input.title,100,"课文标题",false);
        input.unit=CatalogRules.text(input.unit,40,"所属单元",false);
        input.content=CatalogRules.text(input.content.replace("\r\n","\n").replace('\r','\n'),12000,"课文正文",false);
    }

    @Transactional
    public Map<String,Object> create(String owner,String key,LessonInput input) {
        LearningService.validateKey(key);
        db.lockAccount(owner);
        normalize(input);
        String hash=Tokens.hash(Json.write(input));
        List<Map<String,Object>> previous=jdbc.queryForList(
            "SELECT id,request_hash FROM recitation_lesson WHERE owner_id=? AND request_key=?",owner,key);
        if(!previous.isEmpty()){
            Map<String,Object> row=previous.get(0);
            LearningService.sameHash((String)row.get("request_hash"),hash);
            return lesson(owner,(String)row.get("id"));
        }
        String id=Ids.next();
        jdbc.update("INSERT INTO recitation_lesson(id,owner_id,grade,semester,unit_name,title,content,request_key,request_hash) "
            +"VALUES(?,?,?,?,?,?,?,?,?)",id,owner,input.grade,input.semester,input.unit,input.title,input.content,key,hash);
        return lesson(owner,id);
    }

    @Transactional
    public Map<String,Object> update(String owner,String id,LessonInput input) {
        normalize(input);
        checkRevision(lesson(owner,id,true),input.expectedRevision);
        jdbc.update("UPDATE recitation_lesson SET grade=?,semester=?,unit_name=?,title=?,content=?,revision=revision+1 WHERE id=?",
            input.grade,input.semester,input.unit,input.title,input.content,id);
        return lesson(owner,id);
    }

    @Transactional
    public void delete(String owner,String id,int revision) {
        checkRevision(lesson(owner,id,true),revision);
        jdbc.update("UPDATE recitation_lesson SET deleted=TRUE WHERE id=?",id);
    }

    @Transactional
    public Map<String,Object> record(String owner,String child,String key,AttemptInput input) {
        LearningService.validateKey(key);
        learning.lockedChild(owner,child);
        String hash=Tokens.hash(Json.write(input));
        List<Map<String,Object>> previous=jdbc.queryForList("SELECT "+ATTEMPT_FIELDS+",request_hash FROM recitation_attempt "
            +"WHERE owner_id=? AND child_id=? AND request_key=?",owner,child,key);
        if(!previous.isEmpty()){
            Map<String,Object> row=previous.get(0);
            LearningService.sameHash((String)row.remove("request_hash"),hash);
            return row;
        }
        Map<String,Object> lesson=lesson(owner,input.lessonId,true);
        checkRevision(lesson,input.lessonRevision);
        String id=Ids.next();
        jdbc.update("INSERT INTO recitation_attempt(id,owner_id,child_id,lesson_id,title,grade,semester,unit_name,"
            +"lesson_revision,result,created_at,request_key,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),?,?)",
            id,owner,child,input.lessonId,lesson.get("title"),lesson.get("grade"),lesson.get("semester"),lesson.get("unit"),
            input.lessonRevision,input.result,key,hash);
        return jdbc.queryForMap("SELECT "+ATTEMPT_FIELDS+" FROM recitation_attempt WHERE id=?",id);
    }

    public List<Map<String,Object>> history(String owner,String child) {
        learning.child(owner,child);
        return jdbc.queryForList("SELECT "+ATTEMPT_FIELDS+" FROM recitation_attempt WHERE owner_id=? AND child_id=? "
            +"ORDER BY created_at DESC,id DESC LIMIT 100",owner,child);
    }

    private void checkRevision(Map<String,Object> lesson,int expected) {
        if(((Number)lesson.get("revision")).intValue()!=expected)throw ApiException.conflict();
    }
}
