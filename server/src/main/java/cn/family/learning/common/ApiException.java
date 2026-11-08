package cn.family.learning.common;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "内容不存在或无权访问"); }
    public static ApiException invalid(String message) { return new ApiException(400, "INVALID_INPUT", message); }
    public static ApiException conflict() { return new ApiException(409, "VERSION_CONFLICT", "内容已更新，请刷新后重试"); }
}
