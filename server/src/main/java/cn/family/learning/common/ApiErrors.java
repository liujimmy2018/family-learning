package cn.family.learning.common;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    @org.springframework.web.bind.annotation.InitBinder
    void bindFields(org.springframework.web.bind.WebDataBinder binder) {
        // Request DTOs expose fields rather than JavaBean setters/getters. Nested
        // validation must read those fields when producing an HTTP 400 response.
        binder.initDirectFieldAccess();
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> business(ApiException e) { return ResponseEntity.status(e.status).body(ApiEnvelope.error(e.code, e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, javax.validation.ConstraintViolationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> validation(Exception e) {
        String message="请检查输入内容与参数范围";
        if(e instanceof MethodArgumentNotValidException && ((MethodArgumentNotValidException)e).getBindingResult()
                .getFieldErrors().stream().anyMatch(error->"BmpText".equals(error.getCode())))
            message="当前数据库不支持表情或扩展汉字，请使用常用中英文";
        return ResponseEntity.badRequest().body(ApiEnvelope.error("INVALID_INPUT",message));
    }
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    ResponseEntity<?> notFound(Exception e){return ResponseEntity.status(404).body(ApiEnvelope.error("NOT_FOUND","接口不存在"));}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> large(Exception e) { return ResponseEntity.status(413).body(ApiEnvelope.error("FILE_TOO_LARGE", "单张图片不能超过15MB")); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate(Exception e) { return ResponseEntity.status(409).body(ApiEnvelope.error("DUPLICATE", "内容已存在，请刷新后重试")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        // Log the exception class only: provider and SQL messages can contain private content.
        LoggerFactory.getLogger(ApiErrors.class).error("Unhandled request failure: {}", e.getClass().getName());
        return ResponseEntity.internalServerError().body(ApiEnvelope.error("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
