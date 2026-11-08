package cn.family.learning.common;

import java.util.UUID;

/** Canonical lowercase UUIDs are generated before INSERT, never by an identity column. */
public final class Ids {
    public static final String PATTERN="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private Ids() {}
    public static String next() { return UUID.randomUUID().toString(); }
    public static String require(String value) {
        if(value==null || !value.matches(PATTERN))throw ApiException.invalid("资源编号必须为标准UUID");
        return value;
    }
}
