package cn.family.learning.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {
    // JSON text stays ASCII so MySQL 5.1 utf8 can also retain supplementary characters in opaque JSON values.
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
    private Json() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("JSON serialization failed", e); }
    }
    public static <T> T read(String value, Class<T> type) {
        try { return MAPPER.readValue(value, type); } catch (Exception e) { throw new IllegalArgumentException("JSON data is invalid", e); }
    }
    public static <T> T read(String value, TypeReference<T> type) {
        try { return MAPPER.readValue(value, type); } catch (Exception e) { throw new IllegalArgumentException("JSON data is invalid", e); }
    }
}
