package cn.family.learning.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import cn.family.learning.common.ApiException;
import cn.family.learning.common.Ids;
import cn.family.learning.data.LearningMapper;
import cn.family.learning.data.Rows.*;
import javax.validation.constraints.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AuthService implements ApplicationRunner {
    public static final class Login {
        public final @Pattern(regexp="[A-Za-z0-9_]{4,40}") @NotNull String loginName;
        public final @Size(min=8,max=72) @NotNull String password;
        @JsonCreator
        public Login(@JsonProperty("loginName") String loginName, @JsonProperty("password") String password) {
            this.loginName = loginName;
            this.password = password;
        }
        public String loginName() { return loginName; }
        public String password() { return password; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Login value = (Login) other;
            return java.util.Objects.equals(loginName, value.loginName) && java.util.Objects.equals(password, value.password);
        }
        @Override public int hashCode() { return java.util.Objects.hash(loginName, password); }
    }
    public static final class Register {
        public final @Pattern(regexp="[A-Za-z0-9_]{4,40}") @NotNull String loginName;
        public final @Size(min=8,max=72) @NotNull String password;
        public final @NotBlank @Size(max=128) String inviteCode;
        @JsonCreator
        public Register(@JsonProperty("loginName") String loginName, @JsonProperty("password") String password, @JsonProperty("inviteCode") String inviteCode) {
            this.loginName = loginName;
            this.password = password;
            this.inviteCode = inviteCode;
        }
        public String loginName() { return loginName; }
        public String password() { return password; }
        public String inviteCode() { return inviteCode; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Register value = (Register) other;
            return java.util.Objects.equals(loginName, value.loginName) && java.util.Objects.equals(password, value.password) && java.util.Objects.equals(inviteCode, value.inviteCode);
        }
        @Override public int hashCode() { return java.util.Objects.hash(loginName, password, inviteCode); }
    }
    public static final class Refresh {
        public final @NotBlank @Size(max=128) String refreshToken;
        @JsonCreator
        public Refresh(@JsonProperty("refreshToken") String refreshToken) {
            this.refreshToken = refreshToken;
        }
        public String refreshToken() { return refreshToken; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Refresh value = (Refresh) other;
            return java.util.Objects.equals(refreshToken, value.refreshToken);
        }
        @Override public int hashCode() { return java.util.Objects.hash(refreshToken); }
    }
    public static final class Credentials {
        public final String accessToken;
        public final String refreshToken;
        public final int expiresIn;
        public final String accountId;
        @JsonCreator
        public Credentials(@JsonProperty("accessToken") String accessToken, @JsonProperty("refreshToken") String refreshToken, @JsonProperty("expiresIn") int expiresIn, @JsonProperty("accountId") String accountId) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.accountId = accountId;
        }
        public String accessToken() { return accessToken; }
        public String refreshToken() { return refreshToken; }
        public int expiresIn() { return expiresIn; }
        public String accountId() { return accountId; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Credentials value = (Credentials) other;
            return java.util.Objects.equals(accessToken, value.accessToken) && java.util.Objects.equals(refreshToken, value.refreshToken) && java.util.Objects.equals(expiresIn, value.expiresIn) && java.util.Objects.equals(accountId, value.accountId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(accessToken, refreshToken, expiresIn, accountId); }
    }
    private final LearningMapper db;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String invite;
    private final String role;
    private final String dummyHash = passwords.encode(Tokens.random());
    public AuthService(LearningMapper db,@Value("${app.invite-code}") String invite,@Value("${app.role}") String role) { this.db=db; this.invite=invite; this.role=role; }
    @Override public void run(ApplicationArguments args) { if (!"migrate".equals(role)&&!invite.trim().isEmpty()) db.seedInvite(Ids.next(),Tokens.hash(invite)); }
    @Transactional public Credentials register(Register r) {
        checkPassword(r.password());
        Invite code=db.invite(Tokens.hash(r.inviteCode()));
        if(code==null || code.remainingUses<1 || code.expiresAt.isBefore(now())) throw new ApiException(400,"INVITE_INVALID","邀请码无效、已过期或已用完");
        if(db.account(r.loginName().toLowerCase(java.util.Locale.ROOT))!=null) throw new ApiException(409,"ACCOUNT_EXISTS","账号已存在");
        Account a=new Account(); a.loginName=r.loginName().toLowerCase(java.util.Locale.ROOT); a.passwordHash=passwords.encode(r.password());
        db.accountInsert(a); db.consumeInvite(code.codeHash); return createSession(a.id);
    }
    @Transactional public Credentials login(Login r) {
        checkPassword(r.password());
        Account a=db.account(r.loginName().toLowerCase(java.util.Locale.ROOT));
        boolean matched=passwords.matches(r.password(),a==null?dummyHash:a.passwordHash);
        if(a==null || !matched) throw new ApiException(401,"BAD_CREDENTIALS","账号或密码不正确");
        return createSession(a.id);
    }
    @Transactional public Credentials refresh(String raw) {
        Session s=db.byRefresh(Tokens.hash(raw));
        if(s==null) throw new ApiException(401,"SESSION_EXPIRED","登录已失效，请重新登录");
        return rotate(s,false);
    }
    public void logout(String session) { db.revokeSession(session); }
    @Transactional public void resetPassword(String name,String password){
        if(password==null||password.length()<8)throw ApiException.invalid("新密码至少8位");checkPassword(password);
        Account a=db.account(name.toLowerCase(java.util.Locale.ROOT));if(a==null)throw ApiException.missing();
        a.passwordHash=passwords.encode(password);db.passwordUpdate(a);db.revokeAccount(a.id);
    }
    private Credentials createSession(String owner) { Session s=new Session(); s.ownerId=owner; return rotate(s,true); }
    private Credentials rotate(Session s,boolean insert) {
        String access=Tokens.random(), refresh=Tokens.random();
        s.accessHash=Tokens.hash(access); s.refreshHash=Tokens.hash(refresh); s.accessExpires=now().plusMinutes(15); s.refreshExpires=now().plusDays(30);
        if(insert) db.sessionInsert(s); else db.rotateSession(s);
        return new Credentials(access,refresh,900,s.ownerId.toString());
    }
    private void checkPassword(String p) { if(p.getBytes(StandardCharsets.UTF_8).length>72) throw ApiException.invalid("密码UTF-8长度不能超过72字节"); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
