package cn.family.learning.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.MDC;

public final class ApiEnvelope<T> {
        public final String code;
        public final T data;
        public final String message;
        public final String requestId;
        @JsonCreator
        public ApiEnvelope(@JsonProperty("code") String code, @JsonProperty("data") T data, @JsonProperty("message") String message, @JsonProperty("requestId") String requestId) {
            this.code = code;
            this.data = data;
            this.message = message;
            this.requestId = requestId;
        }
        public String code() { return code; }
        public T data() { return data; }
        public String message() { return message; }
        public String requestId() { return requestId; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ApiEnvelope<?> value = (ApiEnvelope<?> ) other;
            return java.util.Objects.equals(code, value.code) && java.util.Objects.equals(data, value.data) && java.util.Objects.equals(message, value.message) && java.util.Objects.equals(requestId, value.requestId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(code, data, message, requestId); }
        public static <T> ApiEnvelope<T> ok(T value) { return new ApiEnvelope<>("OK", value, null, MDC.get("requestId")); }
    public static ApiEnvelope<Void> error(String code, String message) { return new ApiEnvelope<>(code, null, message, MDC.get("requestId")); }
    }
