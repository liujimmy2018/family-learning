package cn.family.learning;

import cn.family.learning.common.Values;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import cn.family.learning.ai.JobWorker;
import cn.family.learning.ai.JobService;
import cn.family.learning.learning.Dtos.SpeechInput;
import cn.family.learning.common.ApiException;
import cn.family.learning.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import okhttp3.*;
import java.util.concurrent.*;
import cn.family.learning.data.Rows.Job;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class PhaseOneIntegrationTest {
    static final MockWebServer provider=new MockWebServer();
    static final String INVITE="integration-"+UUID.randomUUID();
    static {try{provider.start();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));
        r.add("spring.datasource.username",()->System.getenv().getOrDefault("TEST_DB_USER","family_test"));
        r.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
        r.add("app.invite-code",()->INVITE);
        r.add("app.role",()->"api");r.add("app.ai-key",()->"contract-test-key");
        r.add("app.ai-base-url",()->provider.url("").toString());
        r.add("app.daily-job-limit",()->2);
        r.add("app.storage-path",()->System.getProperty("java.io.tmpdir")+"/family-learning-test-"+UUID.randomUUID());
    }
    @LocalServerPort int port;
    @Autowired JobWorker worker;
    @Autowired JobService jobs;
    @Autowired cn.family.learning.auth.AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired cn.family.learning.data.LearningMapper mapper;
    final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build();
    static final class Reply {
        public final int status;
        public final JsonNode body;
        @JsonCreator
        public Reply(@JsonProperty("status") int status, @JsonProperty("body") JsonNode body) {
            this.status = status;
            this.body = body;
        }
        public int status() { return status; }
        public JsonNode body() { return body; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Reply value = (Reply) other;
            return java.util.Objects.equals(status, value.status) && java.util.Objects.equals(body, value.body);
        }
        @Override public int hashCode() { return java.util.Objects.hash(status, body); }
        JsonNode data(){return body.path("data");}
    }
    static final class User {
        public final String name;
        public final String access;
        public final String refresh;
        @JsonCreator
        public User(@JsonProperty("name") String name, @JsonProperty("access") String access, @JsonProperty("refresh") String refresh) {
            this.name = name;
            this.access = access;
            this.refresh = refresh;
        }
        public String name() { return name; }
        public String access() { return access; }
        public String refresh() { return refresh; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            User value = (User) other;
            return java.util.Objects.equals(name, value.name) && java.util.Objects.equals(access, value.access) && java.util.Objects.equals(refresh, value.refresh);
        }
        @Override public int hashCode() { return java.util.Objects.hash(name, access, refresh); }
    }
    Reply request(String method,String path,Object body,String access,String key)throws Exception {
        Request.Builder b=new Request.Builder().url("http://127.0.0.1:"+port+path);
        if(access!=null)b.header("Authorization","Bearer "+access);if(key!=null)b.header("Idempotency-Key",key);
        RequestBody payload=body==null?null:RequestBody.create(Json.write(body),MediaType.get("application/json; charset=utf-8"));
        if(payload==null && (method.equals("POST")||method.equals("PUT")||method.equals("PATCH")))payload=RequestBody.create(new byte[0]);
        b.method(method,payload);
        try(Response r=http.newCall(b.build()).execute()){return new Reply(r.code(),Json.MAPPER.readTree(r.body().string()));}
    }
    User user()throws Exception {
        String name="test_"+UUID.randomUUID().toString().replace("-","").substring(0,15);
        Reply r=request("POST","/api/v1/auth/register",Values.map("loginName",name,"password","Learning_2026!","inviteCode",INVITE),null,null);
        assertEquals(200,r.status(),r.body().toString());return new User(name,r.data().path("accessToken").asText(),r.data().path("refreshToken").asText());
    }
    String child(User u)throws Exception {Reply r=request("POST","/api/v1/children",Values.map("nickname","小朋友","grade",3,"textbook","人教版","expectedVersion",0),u.access(),null);assertEquals(200,r.status(),r.body().toString());return r.data().path("id").asText();}
    String base(String child){return "/api/v1/children/"+child;}
    Map<String,Object> listBody(String title){return Values.map("title",title,"expectedVersion",0,"words",Values.list(Values.map("text","apple","meaning","苹果"),Values.map("text","school","meaning","学校"),Values.map("text","book","meaning","书")));}
    JsonNode list(User u,String c)throws Exception {Reply r=request("POST",base(c)+"/lists",listBody("第三课单词"),u.access(),UUID.randomUUID().toString());assertEquals(200,r.status(),r.body().toString());return r.data();}
    Map<String,Object> settings(){return Values.map("mode","CHINESE","countdownSeconds",0,"intervalSeconds",3,"repeats",2,"repeatGapSeconds",0.5,"rounds",2,"randomOrder",true);}
    JsonNode practice(User u,String c,JsonNode l,String key)throws Exception {
        List<String> ids=new ArrayList<>();l.path("words").forEach(w->ids.add(w.path("id").asText()));
        Reply r=request("POST",base(c)+"/practices",Values.map("listId",l.path("id").asText(),"expectedListVersion",l.path("version").asInt(),"itemIds",ids,"settings",settings()),u.access(),key);assertEquals(200,r.status(),r.body().toString());return r.data();
    }
    @AfterAll static void closeProvider()throws Exception{provider.shutdown();}
    @Test void invitationPasswordAndAuthenticationAreRequired()throws Exception {
        assertEquals(401,request("GET","/api/v1/children",null,null,null).status());
        assertEquals(400,request("POST","/api/v1/auth/register",Values.map("loginName","invalid_user","password","Learning_2026!","inviteCode","wrong-code"),null,null).status());
        User u=user();assertEquals(401,request("POST","/api/v1/auth/login",Values.map("loginName",u.name(),"password","incorrect_password"),null,null).status());
        assertEquals(200,request("POST","/api/v1/auth/login",Values.map("loginName",u.name(),"password","Learning_2026!"),null,null).status());
    }
    @Test void java8DtosKeepNestedValidationAndUnknownRouteEnvelope()throws Exception {
        User u=user();String c=child(u);
        assertEquals(400,request("POST",base(c)+"/lists",Values.map("title","校验词表","expectedVersion",0,
                "words",Values.list(Values.map("text","apple","meaning",""))),u.access(),UUID.randomUUID().toString()).status());
        JsonNode l=list(u,c);
        Map<String,Object> invalidSettings=new LinkedHashMap<>(settings());invalidSettings.put("intervalSeconds",0);
        assertEquals(400,request("POST",base(c)+"/practices",Values.map("listId",l.path("id").asText(),
                "expectedListVersion",1,"itemIds",Values.list(l.path("words").path(0).path("id").asText()),"settings",invalidSettings),u.access(),UUID.randomUUID().toString()).status());
        Reply missing=request("GET","/api/v1/does-not-exist",null,u.access(),null);
        assertEquals(404,missing.status());assertEquals("NOT_FOUND",missing.body().path("code").asText());
    }
    @Test void refreshRotatesBothCredentialsAndLogoutRevokesSession()throws Exception {
        User u=user();Reply rotated=request("POST","/api/v1/auth/refresh",Values.map("refreshToken",u.refresh()),null,null);assertEquals(200,rotated.status());
        assertEquals(401,request("GET","/api/v1/children",null,u.access(),null).status());
        assertEquals(401,request("POST","/api/v1/auth/refresh",Values.map("refreshToken",u.refresh()),null,null).status());
        String token=rotated.data().path("accessToken").asText();assertEquals(200,request("GET","/api/v1/children",null,token,null).status());
        assertEquals(200,request("POST","/api/v1/auth/logout",Values.map(),token,null).status());
        assertEquals(401,request("GET","/api/v1/children",null,token,null).status());
        assertEquals(401,request("POST","/api/v1/auth/refresh",Values.map("refreshToken",rotated.data().path("refreshToken").asText()),null,null).status());
    }
    @Test void childrenAndVersionsAreIsolatedAcrossParents()throws Exception {
        User a=user(),b=user();String c=child(a);
        assertEquals(404,request("GET",base(c)+"/lists",null,b.access(),null).status());
        assertEquals(404,request("PATCH",base(c),Values.map("nickname","其他人","grade",2,"textbook","","expectedVersion",1),b.access(),null).status());
        assertEquals(409,request("PATCH",base(c),Values.map("nickname","新昵称","grade",4,"textbook","","expectedVersion",9),a.access(),null).status());
        Reply r=request("PATCH",base(c),Values.map("nickname","新昵称","grade",4,"textbook","","expectedVersion",1),a.access(),null);
        assertEquals(200,r.status());assertEquals(2,r.data().path("version").asInt());
    }
    @Test void listConfirmationIsIdempotentAndPracticeKeepsItsSnapshot()throws Exception {
        User u=user();String c=child(u),key=UUID.randomUUID().toString();
        Reply first=request("POST",base(c)+"/lists",listBody("原词表"),u.access(),key),again=request("POST",base(c)+"/lists",listBody("原词表"),u.access(),key);
        assertEquals(first.data().path("id"),again.data().path("id"));
        assertEquals(409,request("POST",base(c)+"/lists",listBody("另一内容"),u.access(),key).status());
        String pk=UUID.randomUUID().toString();JsonNode p=practice(u,c,first.data(),pk),p2=practice(u,c,first.data(),pk);assertEquals(p.path("id"),p2.path("id"));
        String revisionKey=UUID.randomUUID().toString();Map<String,Object> revision=Values.map("title","修改后的词表","expectedVersion",1,"words",Values.list(Values.map("text","pear","meaning","梨")));
        String revisionPath=base(c)+"/lists/"+first.data().path("id").asText()+"/versions";
        Reply version=request("POST",revisionPath,revision,u.access(),revisionKey);assertEquals(200,version.status());
        Reply retried=request("POST",revisionPath,revision,u.access(),revisionKey);assertEquals(200,retried.status());assertEquals(2,retried.data().path("version").asInt());
        Reply saved=request("GET",base(c)+"/practices/"+p.path("id").asText(),null,u.access(),null);
        Set<String> texts=new HashSet<>();saved.data().path("words").forEach(w->texts.add(w.path("text").asText()));assertEquals(Values.set("apple","school","book"),texts);assertEquals(2,saved.data().path("settings").path("rounds").asInt());
    }
    @Test void progressRejectsOutOfBoundsAndDeduplicatesPlaybackEvents()throws Exception {
        User u=user();String c=child(u);JsonNode p=practice(u,c,list(u,c),UUID.randomUUID().toString());String path=base(c)+"/practices/"+p.path("id").asText()+"/events";
        Map<String,Object> event=Values.map("eventId",UUID.randomUUID().toString(),"action","PLAY","round",0,"itemIndex",0,"occurredAt",System.currentTimeMillis());
        assertEquals(400,request("POST",path,Values.map("expectedVersion",1,"status","PAUSED","round",2,"itemIndex",0,"events",Values.list(event)),u.access(),null).status());
        assertEquals(200,request("POST",path,Values.map("expectedVersion",1,"status","PAUSED","round",0,"itemIndex",0,"events",Values.list(event)),u.access(),null).status());
        assertEquals(409,request("POST",path,Values.map("expectedVersion",1,"status","PAUSED","round",0,"itemIndex",1,"events",Values.list()),u.access(),null).status());
        assertEquals(200,request("POST",path,Values.map("expectedVersion",2,"status","PAUSED","round",0,"itemIndex",1,"events",Values.list(event)),u.access(),null).status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM play_event WHERE practice_id=?",Integer.class,p.path("id").asText()));
    }
    String upload(User u,String c,byte[] bytes)throws Exception {
        String boundary="boundary"+UUID.randomUUID();ByteArrayOutputStream data=new ByteArrayOutputStream();
        data.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes());data.write(bytes);data.write(("\r\n--"+boundary+"--\r\n").getBytes());
        Request req=new Request.Builder().url("http://127.0.0.1:"+port+base(c)+"/assets").header("Authorization","Bearer "+u.access())
                .post(RequestBody.create(data.toByteArray(),MediaType.get("multipart/form-data; boundary="+boundary))).build();
        try(Response r=http.newCall(req).execute()){String body=r.body().string();assertEquals(200,r.code(),body);return Json.MAPPER.readTree(body).path("data").path("id").asText();}
    }
    @Test void importedImageRemainsPrivateAndRecognitionRequiresConfirmation()throws Exception {
        User u=user(),other=user();String c=child(u);BufferedImage image=new BufferedImage(120,80,BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"jpg",bytes);String asset=upload(u,c,bytes.toByteArray());
        try(Response denied=http.newCall(new Request.Builder().url("http://127.0.0.1:"+port+base(c)+"/assets/"+asset+"/content").header("Authorization","Bearer "+other.access()).get().build()).execute()){assertEquals(404,denied.code());}
        provider.enqueue(new MockResponse().addHeader("Content-Type","application/json").setBody(Json.write(Values.map("choices",Values.list(Values.map("message",Values.map("content","{\"words\":[{\"text\":\"apple\",\"meaning\":\"苹果\"}]}")))))));
        Reply job=request("POST",base(c)+"/recognitions",Values.map("title","照片词表","type","WORD_TABLE","assetIds",Values.list(asset)),u.access(),UUID.randomUUID().toString());assertEquals(202,job.status());
        assertTrue(worker.runOne());Reply ready=request("GET",base(c)+"/jobs/"+job.data().path("id").asText(),null,u.access(),null);assertEquals("SUCCEEDED",ready.data().path("status").asText(),ready.body().toString());
        assertEquals(0,request("GET",base(c)+"/lists",null,u.access(),null).data().size());
        Reply confirmed=request("POST",base(c)+"/lists",Values.map("title","家长修正后的词表","recognitionJobId",job.data().path("id").asText(),"expectedVersion",0,"words",Values.list(Values.map("text","Apple","meaning","苹果"))),u.access(),UUID.randomUUID().toString());assertEquals(200,confirmed.status());assertEquals("Apple",confirmed.data().path("words").path(0).path("text").asText());
    }
    @Test void archivedChildCannotReceiveNewRecords()throws Exception {
        User u=user();String c=child(u);JsonNode l=list(u,c);
        assertEquals(200,request("DELETE",base(c),null,u.access(),null).status());
        assertEquals(404,request("GET",base(c)+"/lists/"+l.path("id").asText(),null,u.access(),null).status());
        assertEquals(404,request("POST",base(c)+"/lists",listBody("不应保存"),u.access(),UUID.randomUUID().toString()).status());
    }
    @Test void mysqlWorkersClaimDifferentJobsAndExpiredLeaseCannotPublish()throws Exception {
        User u=user();String c=child(u);String owner=jdbc.queryForObject("SELECT id FROM parent_account WHERE login_name=?",String.class,u.name());
        jobs.speech(owner,c,new SpeechInput("apple","en-US"));jobs.speech(owner,c,new SpeechInput("school","en-US"));
        assertEquals("DAILY_AI_LIMIT",assertThrows(ApiException.class,()->jobs.speech(owner,c,new SpeechInput("pear","en-US"))).code);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            List<Future<Job>> results=pool.invokeAll(Values.<Callable<Job>>list(jobs::claim,jobs::claim));
            Job old=results.get(0).get();Job second=results.get(1).get();assertNotNull(old);assertNotNull(second);assertNotEquals(old.id,second.id);
            assertTrue(jobs.renew(old));jdbc.update("UPDATE ai_job SET lease_until=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?",old.id);
            Job replacement=jobs.claim();assertNotNull(replacement);assertEquals(old.id,replacement.id);assertNotEquals(old.claimToken,replacement.claimToken);
            assertThrows(ApiException.class,()->jobs.success(old,Values.map("mustNotPublish",true)));
            jobs.success(replacement,Values.map("verified",true));
            assertEquals("SUCCEEDED",jobs.get(owner,c,replacement.id).status());
            request("DELETE",base(c),null,u.access(),null);
            assertThrows(ApiException.class,()->jobs.success(second,Values.map("mustNotPublish",true)));
            assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?",String.class,second.id));
        }finally{pool.shutdownNow();}
    }
    @Test void wordLookupIsPrivateAndReusesSuccessfulResult()throws Exception {
        User u=user(),other=user();String c=child(u);
        provider.enqueue(new MockResponse().addHeader("Content-Type","application/json").setBody(Json.write(Values.map("choices",Values.list(Values.map("message",Values.map("content","{\"words\":[{\"text\":\"apple\",\"meaning\":\"苹果；苹果树\"}]}")))))));
        Reply requested=request("POST",base(c)+"/word-lookups",Values.map("text","apple"),u.access(),null);assertEquals(202,requested.status());
        assertTrue(worker.runOne());String job=requested.data().path("id").asText();
        Reply ready=request("GET",base(c)+"/jobs/"+job,null,u.access(),null);assertEquals("苹果；苹果树",ready.data().path("result").path("word").path("meaning").asText());
        assertEquals(404,request("GET",base(c)+"/jobs/"+job,null,other.access(),null).status());
        assertEquals(job,request("POST",base(c)+"/word-lookups",Values.map("text","apple"),u.access(),null).data().path("id").asText());
    }
    @Test void operatorPasswordResetRevokesAllPreviousCredentials()throws Exception {
        User u=user();auth.resetPassword(u.name(),"Reset_Learning_2026!");
        assertEquals(401,request("GET","/api/v1/children",null,u.access(),null).status());
        assertEquals(401,request("POST","/api/v1/auth/refresh",Values.map("refreshToken",u.refresh()),null,null).status());
        assertEquals(401,request("POST","/api/v1/auth/login",Values.map("loginName",u.name(),"password","Learning_2026!"),null,null).status());
        assertEquals(200,request("POST","/api/v1/auth/login",Values.map("loginName",u.name(),"password","Reset_Learning_2026!"),null,null).status());
    }
    @Test void legacyDatetimeHandlerRetainsUtcAndSessionLifetime()throws Exception {
        User u=user();
        String hash=cn.family.learning.auth.Tokens.hash(u.access());
        cn.family.learning.data.Rows.Session session=mapper.byAccess(hash);
        assertNotNull(session);
        long mappedSeconds=java.time.Duration.between(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),session.accessExpires).getSeconds();
        Long databaseSeconds=jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),access_expires) FROM auth_session WHERE access_hash=?",Long.class,hash);
        assertTrue(mappedSeconds>=880 && mappedSeconds<=900,"UTC mapping must retain the 15 minute lifetime");
        assertTrue(Math.abs(mappedSeconds-databaseSeconds)<=2,"JDBC 4.0 datetime mapping must agree with database UTC");
    }
    @Test void legacyUtf8RejectsSupplementaryTextBeforeDatabaseWrite()throws Exception {
        User u=user();
        Reply invalid=request("POST","/api/v1/children",Values.map("nickname","小朋友\uD83D\uDE00","grade",3,"textbook","人教版","expectedVersion",0),u.access(),null);
        assertEquals(400,invalid.status());assertEquals("INVALID_INPUT",invalid.body().path("code").asText());
        assertTrue(invalid.body().path("message").asText().contains("表情"));
        assertEquals(0,request("GET","/api/v1/children",null,u.access(),null).data().size());
        String c=child(u);assertEquals("小朋友",request("GET","/api/v1/children",null,u.access(),null).data().path(0).path("nickname").asText());
        assertEquals(400,request("POST",base(c)+"/lists",Values.map("title","单词","expectedVersion",0,
                "words",Values.list(Values.map("text","apple","meaning","苹果\uD83D\uDE00"))),u.access(),UUID.randomUUID().toString()).status());
    }
    @Test void legacyAtomicUpdateClaimsEachJobOnceAcrossFourWorkers()throws Exception {
        User u=user();String c=child(u);
        String owner=jdbc.queryForObject("SELECT id FROM parent_account WHERE login_name=?",String.class,u.name());
        for(int i=0;i<12;i++) {
            Job job=new Job();job.ownerId=owner;job.childId=c;job.type="SPEECH";
            job.inputJson=cn.family.learning.common.Json.write(new SpeechInput("word"+i,"en-US"));
            job.requestKey=UUID.randomUUID().toString();job.requestHash=cn.family.learning.auth.Tokens.hash(job.inputJson);
            mapper.jobInsert(job);
        }
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<Job>>> tasks=new ArrayList<>();
            for(int i=0;i<4;i++)tasks.add(()->{
                List<Job> result=new ArrayList<>();
                for(int n=0;n<3;n++){Job job=jobs.claim();assertNotNull(job);result.add(job);}
                return result;
            });
            Set<String> ids=new HashSet<>();
            for(Future<List<Job>> future:pool.invokeAll(tasks))for(Job job:future.get()) {
                assertEquals(owner,job.ownerId);assertTrue(ids.add(job.id),"A job must not be claimed twice");
                jobs.success(job,Values.map("verified",true));
            }
            assertEquals(12,ids.size());
            assertEquals(12,jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE child_id=? AND status='SUCCEEDED'",Integer.class,c));
        } finally {pool.shutdownNow();request("DELETE",base(c),null,u.access(),null);}
    }
    @Test void uuidIdsReachDatabaseAndNumericEndpointIdsAreRejected()throws Exception {
        User u=user();String c=child(u);JsonNode list=list(u,c);JsonNode practice=practice(u,c,list,UUID.randomUUID().toString());
        for(String id:Arrays.asList(c,list.path("id").asText(),practice.path("id").asText(),list.path("words").path(0).path("id").asText()))
            assertEquals(4,UUID.fromString(id).version());
        for(String table:cn.family.learning.data.UuidDataMigrator.TABLES) {
            Map<String,Object> column=jdbc.queryForMap("SELECT DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='id'",table);
            assertEquals("char",column.get("DATA_TYPE"));assertEquals(36,((Number)column.get("CHARACTER_MAXIMUM_LENGTH")).intValue());
            assertFalse(String.valueOf(column.get("EXTRA")).contains("auto_increment"));
        }
        assertEquals(400,request("GET","/api/v1/children/1/lists",null,u.access(),null).status());
        assertEquals(400,request("POST",base(c)+"/practices",Values.map("listId","1","expectedListVersion",1,
                "itemIds",Values.list(list.path("words").path(0).path("id").asText()),"settings",settings()),u.access(),UUID.randomUUID().toString()).status());
    }
}
