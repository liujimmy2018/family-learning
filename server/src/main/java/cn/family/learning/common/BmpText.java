package cn.family.learning.common;

import java.lang.annotation.*;
import javax.validation.*;

/** Text stored in MySQL 5.1 utf8 VARCHAR columns cannot contain supplementary Unicode characters. */
@Documented
@Constraint(validatedBy=BmpText.Validator.class)
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BmpText {
    String message() default "当前数据库不支持表情或扩展汉字，请使用常用中英文";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BmpText,String> {
        @Override public boolean isValid(String text,ConstraintValidatorContext context) {
            return supported(text);
        }
        public static boolean supported(String text) {
            if(text!=null)for(int i=0;i<text.length();i++)if(Character.isSurrogate(text.charAt(i)))return false;
            return true;
        }
    }
}
