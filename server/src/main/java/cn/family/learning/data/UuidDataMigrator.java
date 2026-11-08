package cn.family.learning.data;

import cn.family.learning.common.Ids;
import cn.family.learning.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.sql.*;
import java.util.*;

/** Copies the numeric-ID schema into an empty UUID schema; it never updates the source. */
public final class UuidDataMigrator {
    public static final List<String> TABLES=Collections.unmodifiableList(Arrays.asList(
            "parent_account","invite_code","auth_session","child_profile","media_asset","learning_material",
            "ai_job","learning_list","list_version","list_item","practice_session","play_event"));

    public static final class Report {
        public final Map<String,Map<String,String>> ids=new LinkedHashMap<>();
        public final Map<String,Integer> counts=new LinkedHashMap<>();
        public int expiredMediaReferences;
    }
    private static String quote(String name) { return "`"+name.replace("`","``")+"`"; }
    private static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }

    public Report copy(Connection source,Connection target)throws Exception {
        if(!source.getAutoCommit()||!target.getAutoCommit())throw new IllegalArgumentException("Use dedicated connections without an active transaction");
        if(source.getCatalog().equalsIgnoreCase(target.getCatalog()))throw new IllegalArgumentException("Source and target must use different database names");
        verifySchema(source,target);
        boolean sourceReadOnly=source.isReadOnly();
        source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        source.setReadOnly(true);source.setAutoCommit(false);target.setAutoCommit(false);
        try {
            // Prevent two migration processes from both accepting the same empty target.
            try(Statement statement=target.createStatement()) {
                for(String table:TABLES)try(ResultSet rows=statement.executeQuery("SELECT id FROM "+quote(table)+" FOR UPDATE")) {
                    if(rows.next())throw new IllegalStateException("Target business tables must be empty: "+table);
                }
            }
            Report report=new Report();
            for(String table:TABLES) {
                Map<String,String> ids=new LinkedHashMap<>();report.ids.put(table,ids);
                try(Statement statement=source.createStatement();ResultSet rows=statement.executeQuery("SELECT "+(table.equals("invite_code")?"code_hash":"id")+" FROM "+quote(table))) {
                    while(rows.next())ids.put(rows.getString(1),Ids.next());
                }
            }
            for(String table:TABLES)copyTable(source,target,table,report);
            for(String table:TABLES)try(Statement statement=target.createStatement();ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM "+quote(table))) {
                rows.next();if(rows.getInt(1)!=report.counts.get(table))throw new IllegalStateException("Row count check failed: "+table);
            }
            target.commit();source.rollback();return report;
        } catch(Exception e) { target.rollback();source.rollback();throw e; }
        finally {target.setAutoCommit(true);source.setAutoCommit(true);source.setReadOnly(sourceReadOnly);}
    }

    private void verifySchema(Connection source,Connection target)throws SQLException {
        for(String table:TABLES) {
            String sourceId=table.equals("invite_code")?"code_hash":"id";
            try(ResultSet column=source.getMetaData().getColumns(source.getCatalog(),null,table,sourceId)) {
                if(!column.next()||(!table.equals("invite_code")&&column.getInt("DATA_TYPE")!=Types.BIGINT))
                    throw new IllegalStateException("Source must be the original numeric-ID schema: "+table);
            }
            try(ResultSet column=target.getMetaData().getColumns(target.getCatalog(),null,table,"id")) {
                if(!column.next()||column.getInt("DATA_TYPE")!=Types.CHAR||column.getInt("COLUMN_SIZE")!=36)
                    throw new IllegalStateException("Target must be the new UUID schema: "+table);
            }
        }
    }

    private void copyTable(Connection source,Connection target,String table,Report report)throws Exception {
        int count=0;
        try(Statement statement=source.createStatement();ResultSet rows=statement.executeQuery("SELECT * FROM "+quote(table))) {
            ResultSetMetaData metadata=rows.getMetaData();List<String> columns=new ArrayList<>();
            if(table.equals("invite_code"))columns.add("id");
            for(int n=1;n<=metadata.getColumnCount();n++)columns.add(metadata.getColumnName(n));
            List<String> quoted=new ArrayList<>();for(String column:columns)quoted.add(quote(column));
            String sql="INSERT INTO "+quote(table)+" ("+String.join(",",quoted)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
            try(PreparedStatement insert=target.prepareStatement(sql)) {
                while(rows.next()) {
                    Map<String,Object> values=new LinkedHashMap<>();
                    for(int n=1;n<=metadata.getColumnCount();n++) {
                        String column=metadata.getColumnName(n);
                        values.put(column,metadata.getColumnType(n)==Types.TIMESTAMP?rows.getTimestamp(n,utc()):rows.getObject(n));
                    }
                    values.put("id",mapped(report,table,String.valueOf(values.get(table.equals("invite_code")?"code_hash":"id")),false));
                    for(String column:columns) {
                        String reference=reference(table,column);
                        if(reference!=null&&values.get(column)!=null)values.put(column,mapped(report,reference,String.valueOf(values.get(column)),false));
                    }
                    rewriteJson(table,values,report);
                    if(table.equals("auth_session"))values.put("revoked",true);
                    if(table.equals("ai_job")&&(Objects.equals(values.get("status"),"QUEUED")||Objects.equals(values.get("status"),"RUNNING"))) {
                        values.put("status","CANCELLED");values.put("claim_token",null);values.put("lease_until",null);
                        values.put("error_code","ID_MIGRATION");values.put("error_message","记录已迁移，请重新发起任务");
                    }
                    for(int n=0;n<columns.size();n++) {
                        Object value=values.get(columns.get(n));
                        if(value instanceof Timestamp)insert.setTimestamp(n+1,(Timestamp)value,utc());else insert.setObject(n+1,value);
                    }
                    insert.executeUpdate();count++;
                }
            }
        }
        report.counts.put(table,count);
    }

    private String reference(String table,String column) {
        if(column.equals("owner_id"))return "parent_account";
        if(column.equals("child_id"))return "child_profile";
        if(column.equals("material_id"))return "learning_material";
        if(column.equals("recognition_job_id"))return "ai_job";
        if(column.equals("list_id"))return "learning_list";
        if(column.equals("version_id"))return "list_version";
        if(column.equals("practice_id"))return "practice_session";
        return null;
    }
    private String mapped(Report report,String table,String old,boolean allowExpiredMedia) {
        String id=report.ids.get(table).get(old);
        if(id==null&&allowExpiredMedia&&table.equals("media_asset")) {
            id=Ids.next();report.ids.get(table).put(old,id);report.expiredMediaReferences++;
        }
        if(id==null)throw new IllegalStateException("Unresolved legacy reference in "+table);
        return id;
    }
    private void rewriteJson(String table,Map<String,Object> values,Report report)throws Exception {
        if(table.equals("learning_material")) {
            ArrayNode assets=(ArrayNode)Json.MAPPER.readTree((String)values.get("asset_ids"));
            rewriteAssets(assets,report);values.put("asset_ids",Json.write(assets));
        }
        if(table.equals("practice_session")) {
            ArrayNode words=(ArrayNode)Json.MAPPER.readTree((String)values.get("snapshot_json"));
            for(JsonNode word:words)((ObjectNode)word).put("id",mapped(report,"list_item",word.path("id").asText(),false));
            values.put("snapshot_json",Json.write(words));
        }
        if(table.equals("ai_job")) {
            ObjectNode input=(ObjectNode)Json.MAPPER.readTree((String)values.get("input_json"));
            if(input.has("assetIds"))rewriteAssets((ArrayNode)input.get("assetIds"),report);
            values.put("input_json",Json.write(input));
            if(values.get("result_json")!=null) {
                JsonNode result=Json.MAPPER.readTree((String)values.get("result_json"));
                if(result.has("assetId"))((ObjectNode)result).put("assetId",mapped(report,"media_asset",result.path("assetId").asText(),true));
                values.put("result_json",Json.write(result));
            }
        }
    }
    private void rewriteAssets(ArrayNode assets,Report report) {
        for(int n=0;n<assets.size();n++)assets.set(n,TextNode.valueOf(mapped(report,"media_asset",assets.get(n).asText(),true)));
    }
}
