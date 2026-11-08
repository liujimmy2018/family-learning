package cn.family.learning;

import cn.family.learning.common.*;
import cn.family.learning.data.UuidDataMigrator;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_LEGACY_DB_URL",matches=".+")
class UuidMigrationIntegrationTest {
    static Connection connection(String key)throws Exception {
        Connection c=DriverManager.getConnection(System.getenv(key),System.getenv().getOrDefault("TEST_DB_USER","family_test"),System.getenv("TEST_DB_PASSWORD"));
        try(Statement statement=c.createStatement()){statement.execute("SET time_zone = '+00:00'");}
        return c;
    }
    static void reset(Connection c,String suffix,String path)throws Exception {
        if(!c.getCatalog().endsWith(suffix))throw new IllegalArgumentException("Migration fixture database name must end with "+suffix);
        List<String> tables=new ArrayList<>(UuidDataMigrator.TABLES);Collections.reverse(tables);
        try(Statement statement=c.createStatement()){for(String table:tables)statement.execute("DROP TABLE IF EXISTS `"+table+"`");}
        new ResourceDatabasePopulator(new ClassPathResource(path+"/V1__phase_one.sql"),new ClassPathResource(path+"/V2__idempotent_list_revisions.sql")).populate(c);
    }
    static void insert(Connection c,String sql,Object... values)throws Exception {
        try(PreparedStatement statement=c.prepareStatement(sql)){for(int n=0;n<values.length;n++)statement.setObject(n+1,values[n]);statement.executeUpdate();}
    }
    static String scalar(Connection c,String sql)throws Exception {
        try(Statement statement=c.createStatement();ResultSet rows=statement.executeQuery(sql)){assertTrue(rows.next());return rows.getString(1);}
    }
    @Test void migrationRewritesAssociationsAndSnapshotsAndRollsBackInvalidData()throws Exception {
        try(Connection source=connection("TEST_LEGACY_DB_URL");Connection target=connection("TEST_MIGRATION_DB_URL")) {
            reset(source,"_uuid_legacy_test","legacy/mysql51");reset(target,"_uuid_copy_test","db/mysql51-uuid");
            insert(source,"INSERT INTO parent_account(id,login_name,password_hash) VALUES(1,'legacy_parent','unchanged-bcrypt-hash')");
            insert(source,"INSERT INTO invite_code VALUES(?,7,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 30 DAY))",TokensHash());
            insert(source,"INSERT INTO auth_session(id,owner_id,access_hash,refresh_hash,access_expires,refresh_expires) VALUES(1,1,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 30 DAY))",cn.family.learning.auth.Tokens.hash("old-access"),cn.family.learning.auth.Tokens.hash("old-refresh"));
            insert(source,"INSERT INTO child_profile(id,owner_id,nickname,grade,textbook) VALUES(1,1,'孩子',3,'教材')");
            insert(source,"INSERT INTO media_asset(id,owner_id,child_id,object_key,content_type,size_bytes,width,height) VALUES(1,1,1,'keep-file.jpg','image/jpeg',100,10,10)");
            insert(source,"INSERT INTO learning_material(id,owner_id,child_id,title,type,asset_ids) VALUES(1,1,1,'教材','WORD_TABLE','[\"1\",\"99\"]')");
            insert(source,"INSERT INTO ai_job(id,owner_id,child_id,material_id,type,status,input_json,result_json,request_key,request_hash,next_run_at) VALUES(1,1,1,1,'SPEECH','SUCCEEDED','{\"text\":\"apple\",\"language\":\"en-US\"}','{\"assetId\":\"1\",\"contentType\":\"audio/mpeg\"}','audio',?,UTC_TIMESTAMP())",cn.family.learning.auth.Tokens.hash("audio"));
            insert(source,"INSERT INTO ai_job(id,owner_id,child_id,material_id,type,status,input_json,request_key,request_hash,next_run_at,claim_token,lease_until) VALUES(2,1,1,1,'RECOGNITION','RUNNING','{\"type\":\"WORD_TABLE\",\"assetIds\":[\"1\",\"99\"],\"grade\":3}','photo',?,UTC_TIMESTAMP(),?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 60 SECOND))",cn.family.learning.auth.Tokens.hash("photo"),Ids.next());
            insert(source,"INSERT INTO learning_list(id,owner_id,child_id,title,material_id,recognition_job_id,request_key,request_hash) VALUES(1,1,1,'词表',1,2,'list',?)",cn.family.learning.auth.Tokens.hash("list"));
            insert(source,"INSERT INTO list_version(id,list_id,version,title,request_key,request_hash) VALUES(1,1,1,'词表','revision',?)",cn.family.learning.auth.Tokens.hash("revision"));
            insert(source,"INSERT INTO list_item(id,version_id,ordinal,text,meaning) VALUES(1,1,0,'apple','苹果')");
            String validSnapshot=Json.write(Values.list(Values.map("id","1","ordinal",0,"text","apple","meaning","苹果")));
            insert(source,"INSERT INTO practice_session(id,owner_id,child_id,list_id,title,snapshot_json,settings_json,progress_json,request_key,request_hash) VALUES(1,1,1,1,'听写',?,'{}','{\"round\":0,\"itemIndex\":0}','practice',?)",validSnapshot,cn.family.learning.auth.Tokens.hash("practice"));
            insert(source,"INSERT INTO play_event(id,practice_id,event_id,payload_json) VALUES(1,1,?,'{\"round\":0}')",Ids.next());
            UuidDataMigrator migrator=new UuidDataMigrator();
            insert(source,"UPDATE practice_session SET snapshot_json='[{\"id\":\"999\"}]' WHERE id=1");
            assertThrows(IllegalStateException.class,()->migrator.copy(source,target));
            for(String table:UuidDataMigrator.TABLES)assertEquals("0",scalar(target,"SELECT COUNT(*) FROM "+table),"Failed copy must roll back every target row");
            insert(source,"UPDATE practice_session SET snapshot_json=? WHERE id=1",validSnapshot);
            UuidDataMigrator.Report report=migrator.copy(source,target);
            for(String table:UuidDataMigrator.TABLES) {
                assertEquals(scalar(source,"SELECT COUNT(*) FROM "+table),scalar(target,"SELECT COUNT(*) FROM "+table));
                assertNotNull(Ids.require(scalar(target,"SELECT id FROM "+table+" LIMIT 1")));
            }
            assertEquals("1",scalar(source,"SELECT id FROM parent_account"));assertEquals("0",scalar(source,"SELECT revoked FROM auth_session"));
            assertEquals("1",scalar(target,"SELECT revoked FROM auth_session"));assertEquals("CANCELLED",scalar(target,"SELECT status FROM ai_job WHERE type='RECOGNITION'"));
            assertEquals("RUNNING",scalar(source,"SELECT status FROM ai_job WHERE type='RECOGNITION'"));
            assertEquals(report.ids.get("parent_account").get("1"),scalar(target,"SELECT owner_id FROM child_profile"));
            assertEquals(report.ids.get("child_profile").get("1"),scalar(target,"SELECT child_id FROM learning_list"));
            assertEquals(report.ids.get("ai_job").get("2"),scalar(target,"SELECT recognition_job_id FROM learning_list"));
            assertEquals(report.ids.get("list_item").get("1"),Json.MAPPER.readTree(scalar(target,"SELECT snapshot_json FROM practice_session")).path(0).path("id").asText());
            assertEquals("苹果",Json.MAPPER.readTree(scalar(target,"SELECT snapshot_json FROM practice_session")).path(0).path("meaning").asText());
            assertEquals(report.ids.get("media_asset").get("1"),Json.MAPPER.readTree(scalar(target,"SELECT result_json FROM ai_job WHERE type='SPEECH'")).path("assetId").asText());
            assertEquals(report.ids.get("media_asset").get("99"),Json.MAPPER.readTree(scalar(target,"SELECT input_json FROM ai_job WHERE type='RECOGNITION'")).path("assetIds").path(1).asText());
            assertEquals(1,report.expiredMediaReferences);assertEquals("keep-file.jpg",scalar(target,"SELECT object_key FROM media_asset"));
            assertEquals(scalar(source,"SELECT UNIX_TIMESTAMP(access_expires) FROM auth_session"),scalar(target,"SELECT UNIX_TIMESTAMP(access_expires) FROM auth_session"));
            assertThrows(IllegalStateException.class,()->migrator.copy(source,target));
            assertEquals("1",scalar(target,"SELECT COUNT(*) FROM parent_account"));
        }
    }
    private static String TokensHash(){return cn.family.learning.auth.Tokens.hash("legacy-invite");}
}
