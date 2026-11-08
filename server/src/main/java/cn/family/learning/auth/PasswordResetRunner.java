package cn.family.learning.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Available only to an operator with local deployment environment access, never an HTTP endpoint. */
@Component
@ConditionalOnProperty(name="app.role",havingValue="admin")
public class PasswordResetRunner implements ApplicationRunner {
    private final AuthService auth;private final String user,password;
    public PasswordResetRunner(AuthService auth,@Value("${ADMIN_RESET_USER:}")String user,@Value("${ADMIN_RESET_PASSWORD:}")String password){this.auth=auth;this.user=user;this.password=password;}
    @Override public void run(ApplicationArguments args){
        if(user.trim().isEmpty()||password.trim().isEmpty())throw new IllegalArgumentException("Set ADMIN_RESET_USER and ADMIN_RESET_PASSWORD in the local environment");
        auth.resetPassword(user,password);
        System.out.println("Password reset completed; existing sessions were revoked.");
    }
}
