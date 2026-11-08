package cn.family.learning;

import cn.family.learning.auth.AuthService;
import cn.family.learning.common.*;
import cn.family.learning.catalog.ExcelCatalog;
import cn.family.learning.learning.LearningService;
import cn.family.learning.learning.Dtos;
import cn.family.learning.data.Rows.Child;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class CatalogIntegrationTest {
    static final String INVITE="catalog-"+UUID.randomUUID();
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));r.add("spring.datasource.username",()->System.getenv().getOrDefault("TEST_DB_USER","family_test"));r.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));r.add("app.invite-code",()->INVITE);r.add("app.role",()->"api");r.add("app.ai-key",()->"");r.add("app.storage-path",()->System.getProperty("java.io.tmpdir")+"/family-catalog-test-"+UUID.randomUUID());}
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired LearningService learning;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExcelCatalog excel;
    final OkHttpClient http=new OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS).build();
    AuthService.Credentials user(){return auth.register(new AuthService.Register("catalog_"+UUID.randomUUID().toString().substring(0,12).replace("-",""),"Catalog_2026!",INVITE));}
    String child(AuthService.Credentials u){return learning.createChild(u.accountId(),new Dtos.ChildInput("词表测试",3,"人教版",0)).id();}
    static class Reply {int status;JsonNode body;JsonNode data(){return body.path("data");}}
    Reply call(String method,String path,Object body,AuthService.Credentials user,String key)throws Exception {
        Request.Builder b=new Request.Builder().url("http://127.0.0.1:"+port+path);if(user!=null)b.header("Authorization","Bearer "+user.accessToken());if(key!=null)b.header("Idempotency-Key",key);
        RequestBody payload=body==null?null:RequestBody.create(Json.write(body),MediaType.get("application/json; charset=utf-8"));if(payload==null&&Arrays.asList("POST","PATCH").contains(method))payload=RequestBody.create(new byte[0]);b.method(method,payload);
        try(Response response=http.newCall(b.build()).execute()){Reply r=new Reply();r.status=response.code();r.body=Json.MAPPER.readTree(response.body().string());return r;}
    }
    Map<String,Object> entry(int grade,String semester,String unit,String en,String zh){return Values.map("grade",grade,"semester",semester,"unit",unit,"english",en,"chinese",zh,"phonetic","/test/");}
    JsonNode catalog(AuthService.Credentials u)throws Exception {
        Reply r=call("POST","/api/v1/catalog/collections",Values.map("title","课本词表","entries",Values.list(entry(3,"上册","Unit 1","apple","苹果"),entry(3,"上册","Unit 1","banana","香蕉"),entry(3,"上册","Unit 2","book","书"),entry(3,"下册","Unit 1","school","学校"),entry(4,"上册","Unit 1","bird","鸟"))),u,UUID.randomUUID().toString());assertEquals(200,r.status,r.body.toString());return r.data();
    }
    Map<String,Object> settings(int rounds){return Values.map("mode","CHINESE","countdownSeconds",0,"intervalSeconds",3,"repeats",1,"repeatGapSeconds",0.5,"rounds",rounds,"randomOrder",false);}
    JsonNode start(AuthService.Credentials u,String child,JsonNode catalog,String key,int rounds)throws Exception {
        Reply r=call("POST","/api/v1/children/"+child+"/catalog-practices",Values.map("collectionId",catalog.path("collection").path("id").asText(),"expectedRevision",catalog.path("collection").path("revision").asInt(),"grade",3,"semester","上册","units",Values.list("Unit 1"),"selection","ALL","count",1,"settings",settings(rounds)),u,key);assertEquals(200,r.status,r.body.toString());assertEquals("课本词表 · 3年级上册 · Unit 1",r.data().path("title").asText());assertEquals("/test/",r.data().path("words").path(0).path("phonetic").asText());return r.data();
    }
    void finish(AuthService.Credentials u,String child,JsonNode p)throws Exception{Reply r=call("POST","/api/v1/children/"+child+"/practices/"+p.path("id").asText()+"/events",Values.map("expectedVersion",p.path("version").asLong(),"status","COMPLETED","round",p.path("settings").path("rounds").asInt()-1,"itemIndex",p.path("words").size()-1,"events",Values.list()),u,null);assertEquals(200,r.status,r.body.toString());}

    @Test void chineseTemplateCreatesDictationWithPinyinAndSeparateHistory() throws Exception {
        AuthService.Credentials owner=user();
        String child=child(owner);
        String catalogPath="/api/v1/catalog/collections";
        String subject="?subject=CHINESE";
        MultipartBody file=new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file","chinese.xlsx",RequestBody.create(excel.template(true),MediaType.get("application/octet-stream")))
            .build();
        Request upload=new Request.Builder().url("http://127.0.0.1:"+port+"/api/v1/catalog/preview"+subject)
            .header("Authorization","Bearer "+owner.accessToken()).post(file).build();
        JsonNode preview;
        try(Response response=http.newCall(upload).execute()){
            assertEquals(200,response.code());
            preview=Json.MAPPER.readTree(response.body().string()).path("data");
        }
        assertTrue(preview.path("valid").asBoolean(),preview.toString());
        Reply imported=call("POST",catalogPath+subject,Values.map("title","语文测试","entries",preview.path("entries")),
            owner,UUID.randomUUID().toString());
        assertEquals(200,imported.status,imported.body.toString());
        assertEquals(0,call("GET",catalogPath,null,owner,null).data().size());
        assertEquals(1,call("GET",catalogPath+subject,null,owner,null).data().size());
        String base="/api/v1/children/"+child;
        Map<String,Object> input=Values.map("collectionId",imported.data().path("collection").path("id").asText(),
            "expectedRevision",1,"grade",1,"semester","下册","units",Values.list("第一单元"),"settings",settings(1));
        Reply created=call("POST",base+"/catalog-practices",input,owner,UUID.randomUUID().toString());
        assertEquals(200,created.status,created.body.toString());
        JsonNode practice=created.data();
        assertEquals("CHINESE",practice.path("subject").asText());
        assertEquals(2,practice.path("words").size());
        assertEquals("春",practice.path("words").path(0).path("text").asText());
        assertEquals("chūn",practice.path("words").path(0).path("phonetic").asText());
        assertTrue(practice.path("title").asText().endsWith("第一单元"));
        assertEquals(0,call("GET",base+"/lists",null,owner,null).data().size());
        finish(owner,child,practice);
        Reply graded=call("POST",base+"/practices/"+practice.path("id").asText()+"/review",
            Values.map("elapsedSeconds",8,"marks",Values.list(
                Values.map("round",0,"index",0,"correct",false),Values.map("round",0,"index",1,"correct",true))),owner,null);
        assertEquals(200,graded.status,graded.body.toString());
        assertEquals(50.0,graded.data().path("score").asDouble());
        assertEquals(1,call("GET",base+"/dictation-history"+subject,null,owner,null).data().path("items").size());
        assertEquals(0,call("GET",base+"/dictation-history",null,owner,null).data().path("items").size());
        assertEquals("春",call("GET",base+"/wrong-words"+subject,null,owner,null).data().path(0).path("english").asText());
        assertEquals(0,call("GET",base+"/wrong-words",null,owner,null).data().size());
    }

    @Test void recitationRecordsRemainScopedAndKeepTheOriginalLessonTitle() throws Exception {
        AuthService.Credentials owner=user(),other=user();
        String child=child(owner);
        String lessons="/api/v1/recitation/lessons";
        Map<String,Object> lesson=Values.map("title","练习课文","grade",1,"semester","下册",
            "unit","第一单元","content","第一行\r\n第二行");
        String importKey=UUID.randomUUID().toString();
        Reply saved=call("POST",lessons,lesson,owner,importKey);
        assertEquals(200,saved.status,saved.body.toString());
        String id=saved.data().path("id").asText();
        assertEquals(id,call("POST",lessons,lesson,owner,importKey).data().path("id").asText());
        assertEquals("第一行\n第二行",call("GET",lessons+"/"+id,null,owner,null).data().path("content").asText());
        assertEquals(404,call("GET",lessons+"/"+id,null,other,null).status);
        String records="/api/v1/children/"+child+"/recitation-attempts";
        Map<String,Object> result=Values.map("lessonId",id,"lessonRevision",1,"result","COMPLETED");
        String recordKey=UUID.randomUUID().toString();
        Reply recorded=call("POST",records,result,owner,recordKey);
        assertEquals(200,recorded.status,recorded.body.toString());
        assertEquals(404,call("GET",records,null,other,null).status);
        assertEquals(200,call("DELETE",lessons+"/"+id+"?expectedRevision=1",null,owner,null).status);
        assertEquals(recorded.data(),call("POST",records,result,owner,recordKey).data());
        JsonNode history=call("GET",records,null,owner,null).data();
        assertEquals(1,history.size());
        assertEquals("练习课文",history.path(0).path("title").asText());
        assertEquals("COMPLETED",history.path(0).path("result").asText());
    }

    @Test void completeWorkflowKeepsSnapshotsAndGradesIdempotentlyAcrossRounds()throws Exception {
        AuthService.Credentials u=user(),other=user();String child=child(u);JsonNode cat=catalog(u);String key=UUID.randomUUID().toString();JsonNode p=start(u,child,cat,key,2);assertEquals(2,p.path("words").size());assertEquals(p,start(u,child,cat,key,2));
        String path="/api/v1/children/"+child+"/practices/"+p.path("id").asText();
        assertEquals(400,call("POST",path+"/review",Values.map("elapsedSeconds",30,"marks",Values.list()),u,null).status);
        finish(u,child,p);
        assertEquals(200,call("POST",path+"/timing",Values.map("elapsedSeconds",73),u,null).status);
        List<Map<String,Object>> marks=Values.list(Values.map("round",0,"index",0,"correct",true),Values.map("round",0,"index",1,"correct",false,"written","banan"),Values.map("round",1,"index",0,"correct",false,"written","appl"),Values.map("round",1,"index",1,"correct",true));
        Map<String,Object> body=Values.map("elapsedSeconds",70,"marks",marks);Reply grade=call("POST",path+"/review",body,u,null);assertEquals(200,grade.status,grade.body.toString());assertEquals(50.0,grade.data().path("score").asDouble());assertEquals(73,grade.data().path("elapsedSeconds").asInt());assertEquals("/test/",grade.data().path("words").path(0).path("phonetic").asText());
        assertEquals(200,call("POST",path+"/review",body,u,null).status);
        JsonNode wrong=call("GET","/api/v1/children/"+child+"/wrong-words",null,u,null).data();assertEquals(2,wrong.size());for(JsonNode word:wrong){assertEquals(1,word.path("mistakeCount").asInt());assertFalse(word.path("mastered").asBoolean());assertEquals("Unit 1",word.path("unit").asText());}
        Map<String,Object> changed=new LinkedHashMap<>(body);List<Map<String,Object>> changedMarks=new ArrayList<>(marks);changedMarks.set(0,Values.map("round",0,"index",0,"correct",false));changed.put("marks",changedMarks);assertEquals(409,call("POST",path+"/review",changed,u,null).status);
        assertEquals(404,call("GET",path+"/review",null,other,null).status);
        String wrongId=wrong.path(0).path("id").asText();assertEquals(200,call("PATCH","/api/v1/children/"+child+"/wrong-words/"+wrongId,Values.map("mastered",true),u,null).status);
        String catalogId=cat.path("collection").path("id").asText(),entryId=cat.path("entries").path(0).path("id").asText();Reply edit=call("PATCH","/api/v1/catalog/collections/"+catalogId+"/entries/"+entryId,Values.map("expectedRevision",1,"entry",entry(3,"上册","Unit 1","changed","已修改")),u,null);assertEquals(200,edit.status,edit.body.toString());
        JsonNode savedReview=call("GET",path+"/review",null,u,null).data();assertEquals("apple",savedReview.path("words").path(0).path("english").asText());assertEquals("课本词表 · 3年级上册 · Unit 1",savedReview.path("title").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dictation_review WHERE practice_id=? AND marks_json IS NOT NULL",Integer.class,p.path("id").asText()));
    }
    @Test void collectionOwnershipRevisionValidationAndRandomScopeAreEnforced()throws Exception {
        AuthService.Credentials u=user(),other=user();String child=child(u);JsonNode cat=catalog(u);String id=cat.path("collection").path("id").asText();
        assertEquals(404,call("GET","/api/v1/catalog/collections/"+id,null,other,null).status);
        assertEquals(409,call("PATCH","/api/v1/catalog/collections/"+id,Values.map("title","新标题","expectedRevision",99),u,null).status);
        assertEquals(400,call("POST","/api/v1/catalog/collections",Values.map("title","重复词","entries",Values.list(entry(3,"上册","Unit 1","APPLE","苹果"),entry(3,"上册","Unit 1","apple","苹果"))),u,UUID.randomUUID().toString()).status);
        Map<String,Object> input=new LinkedHashMap<>(Values.map("collectionId",id,"expectedRevision",1,"grade",3,"semester","下册","units",Values.list("Unit 1"),"selection","RANDOM","count",1,"settings",settings(1)));Reply p=call("POST","/api/v1/children/"+child+"/catalog-practices",input,u,UUID.randomUUID().toString());assertEquals(200,p.status,p.body.toString());assertEquals("school",p.data().path("words").path(0).path("text").asText());
        input.put("count",2);assertEquals(400,call("POST","/api/v1/children/"+child+"/catalog-practices",input,u,UUID.randomUUID().toString()).status);
        assertEquals(404,call("POST","/api/v1/children/"+child+"/catalog-practices",input,other,UUID.randomUUID().toString()).status);
    }
    @Test void webIsPublicButWordDataAndTemplateRequireAuthentication()throws Exception {
        assertEquals(401,call("GET","/api/v1/catalog/collections",null,null,null).status);
        try(Response response=http.newCall(new Request.Builder().url("http://127.0.0.1:"+port+"/web/index.html").build()).execute()){assertEquals(200,response.code());assertTrue(response.body().string().contains("词表管理"));}
        try(Response response=http.newCall(new Request.Builder().url("http://127.0.0.1:"+port+"/api/v1/catalog/template.xlsx").build()).execute()){assertEquals(401,response.code());}
    }
    @Test void excelPreviewAndImportRetryAreValidatedThroughHttp()throws Exception {
        AuthService.Credentials u=user();MultipartBody body=new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file","template.xlsx",RequestBody.create(excel.template(),MediaType.get("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))).build();
        JsonNode preview;try(Response response=http.newCall(new Request.Builder().url("http://127.0.0.1:"+port+"/api/v1/catalog/preview").header("Authorization","Bearer "+u.accessToken()).post(body).build()).execute()){assertEquals(200,response.code());preview=Json.MAPPER.readTree(response.body().string()).path("data");assertTrue(preview.path("valid").asBoolean());assertEquals(3,preview.path("count").asInt());}
        Map<String,Object> imported=new LinkedHashMap<>(Values.map("title","模板导入","entries",preview.path("entries")));String key=UUID.randomUUID().toString();Reply first=call("POST","/api/v1/catalog/collections",imported,u,key);assertEquals(200,first.status,first.body.toString());Reply repeated=call("POST","/api/v1/catalog/collections",imported,u,key);assertEquals(first.data(),repeated.data());imported.put("title","另一份标题");assertEquals(409,call("POST","/api/v1/catalog/collections",imported,u,key).status);
    }
    @Test void historyShowsCompletionScoresAndSupportsOwnerScopedPaging()throws Exception {
        AuthService.Credentials u=user(),other=user();String child=child(u);JsonNode cat=catalog(u);
        JsonNode first=start(u,child,cat,UUID.randomUUID().toString(),1);
        JsonNode second=start(u,child,cat,UUID.randomUUID().toString(),1);finish(u,child,first);
        String root="/api/v1/children/"+child;
        Reply unreviewed=call("GET",root+"/dictation-history?filter=UNREVIEWED",null,u,null);assertEquals(200,unreviewed.status,unreviewed.body.toString());assertEquals(1,unreviewed.data().path("items").size());assertFalse(unreviewed.data().path("items").path(0).path("graded").asBoolean());assertTrue(unreviewed.data().path("items").path(0).path("score").isNull());
        Reply pending=call("GET",root+"/dictation-history?filter=PENDING",null,u,null);assertEquals(second.path("id").asText(),pending.data().path("items").path(0).path("practice").path("id").asText());
        call("POST",root+"/practices/"+first.path("id").asText()+"/review",Values.map("elapsedSeconds",31,"marks",Values.list(Values.map("round",0,"index",0,"correct",true),Values.map("round",0,"index",1,"correct",false))),u,null);
        Reply graded=call("GET",root+"/dictation-history?filter=GRADED",null,u,null);assertEquals(200,graded.status,graded.body.toString());JsonNode item=graded.data().path("items").path(0);assertEquals(50.0,item.path("score").asDouble());assertTrue(item.path("graded").asBoolean());assertEquals(31,item.path("elapsedSeconds").asInt());assertEquals(2,item.path("total").asInt());assertTrue(item.path("createdAt").asText().endsWith("Z"));
        Reply page=call("GET",root+"/dictation-history?size=1&page=0",null,u,null);assertTrue(page.data().path("hasMore").asBoolean());assertEquals(1,page.data().path("items").size());Reply next=call("GET",root+"/dictation-history?size=1&page=1",null,u,null);assertFalse(next.data().path("hasMore").asBoolean());assertNotEquals(page.data().path("items").path(0).path("practice").path("id"),next.data().path("items").path(0).path("practice").path("id"));
        assertEquals(404,call("GET",root+"/dictation-history",null,other,null).status);
        assertEquals(400,call("GET",root+"/dictation-history?filter=BAD",null,u,null).status);
        assertEquals(400,call("GET",root+"/dictation-history?page=-1",null,u,null).status);
    }
    @Test void supportsOneAndTwoSecondWritingIntervalsButRejectsZero()throws Exception {
        AuthService.Credentials u=user();String child=child(u);JsonNode cat=catalog(u);
        Map<String,Object> settings=new LinkedHashMap<>(settings(1));
        Map<String,Object> input=Values.map("collectionId",cat.path("collection").path("id").asText(),"expectedRevision",1,"grade",3,"semester","上册","units",Values.list("Unit 1"),"selection","ALL","count",2,"settings",settings);
        for(int seconds:new int[]{1,2,10}){settings.put("intervalSeconds",seconds);Reply result=call("POST","/api/v1/children/"+child+"/catalog-practices",input,u,UUID.randomUUID().toString());assertEquals(200,result.status,result.body.toString());assertEquals(seconds,result.data().path("settings").path("intervalSeconds").asInt());}
        settings.put("intervalSeconds",0);Reply rejected=call("POST","/api/v1/children/"+child+"/catalog-practices",input,u,UUID.randomUUID().toString());
        assertEquals(400,rejected.status,rejected.body.toString());
        Map<String,Object> invalidEntry=new LinkedHashMap<>(entry(3,"上册","Unit 1","apple","苹果"));invalidEntry.put("english","");
        Reply invalidImport=call("POST","/api/v1/catalog/collections",Values.map("title","无效导入","entries",Values.list(invalidEntry)),u,UUID.randomUUID().toString());assertEquals(400,invalidImport.status,invalidImport.body.toString());
        Reply invalidEdit=call("POST","/api/v1/catalog/collections/"+cat.path("collection").path("id").asText()+"/entries",Values.map("expectedRevision",1,"entry",invalidEntry),u,null);assertEquals(400,invalidEdit.status,invalidEdit.body.toString());
    }
    @Test void wholeBookCanExceedOldHundredWordLimitAndGradeAllRounds()throws Exception {
        AuthService.Credentials u=user();String child=child(u);List<Map<String,Object>> entries=new ArrayList<>();for(int n=0;n<500;n++)entries.add(entry(3,"上册",n%2==0?"Unit 1":"Unit 2","word"+n,"词"+n));
        String longTitle=String.join("",Collections.nCopies(100,"词"));Reply imported=call("POST","/api/v1/catalog/collections",Values.map("title",longTitle,"entries",entries),u,UUID.randomUUID().toString());assertEquals(200,imported.status,imported.body.toString());String id=imported.data().path("collection").path("id").asText();
        Map<String,Object> input=new LinkedHashMap<>(Values.map("collectionId",id,"expectedRevision",1,"grade",3,"semester","上册","units",Values.list(),"selection","ALL","count",20,"settings",settings(3)));
        Reply result=call("POST","/api/v1/children/"+child+"/catalog-practices",input,u,UUID.randomUUID().toString());assertEquals(200,result.status,result.body.toString());assertEquals(500,result.data().path("words").size());assertEquals(100,result.data().path("title").asText().length());assertTrue(result.data().path("title").asText().endsWith(" · 3年级上册 · 整册"));finish(u,child,result.data());
        List<Map<String,Object>> marks=new ArrayList<>();for(int round=0;round<3;round++)for(int n=0;n<500;n++)marks.add(Values.map("round",round,"index",n,"correct",true));
        Reply graded=call("POST","/api/v1/children/"+child+"/practices/"+result.data().path("id").asText()+"/review",Values.map("elapsedSeconds",1000,"marks",marks),u,null);assertEquals(200,graded.status,graded.body.toString());assertEquals(1500,graded.data().path("total").asInt());assertEquals(100.0,graded.data().path("score").asDouble());
        assertEquals(200,call("POST","/api/v1/catalog/collections/"+id+"/entries",Values.map("expectedRevision",1,"entry",entry(3,"上册","Unit 1","extra","额外")),u,null).status);input.put("expectedRevision",2);
        assertEquals(400,call("POST","/api/v1/children/"+child+"/catalog-practices",input,u,UUID.randomUUID().toString()).status);
    }
}
