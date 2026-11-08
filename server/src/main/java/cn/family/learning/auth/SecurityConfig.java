package cn.family.learning.auth;

import cn.family.learning.common.Values;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import cn.family.learning.common.ApiEnvelope;
import cn.family.learning.common.Json;
import cn.family.learning.data.LearningMapper;
import cn.family.learning.data.Rows.Session;
import javax.servlet.*;
import javax.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    public static final class Identity {
        public final String ownerId;
        public final String sessionId;
        @JsonCreator
        public Identity(@JsonProperty("ownerId") String ownerId, @JsonProperty("sessionId") String sessionId) {
            this.ownerId = ownerId;
            this.sessionId = sessionId;
        }
        public String ownerId() { return ownerId; }
        public String sessionId() { return sessionId; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Identity value = (Identity) other;
            return java.util.Objects.equals(ownerId, value.ownerId) && java.util.Objects.equals(sessionId, value.sessionId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(ownerId, sessionId); }
    }
    @Bean org.springframework.security.core.userdetails.UserDetailsService noDefaultUsers(){
        return username->{throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Use the account API");};
    }
    @Bean SecurityFilterChain chain(HttpSecurity http,LearningMapper db) throws Exception {
        http.csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeRequests(c->c.antMatchers("/","/web","/web/**","/health","/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/refresh").permitAll().anyRequest().authenticated())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->write(res,401,"UNAUTHORIZED","请先登录")))
            .addFilterBefore(new AccessFilter(db),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    static void write(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.write(ApiEnvelope.error(code,message)));
    }
    static class AccessFilter extends OncePerRequestFilter {
        private final LearningMapper db;
        private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
        static final class Window {
        public final long start;
        public final int count;
        @JsonCreator
        public Window(@JsonProperty("start") long start, @JsonProperty("count") int count) {
            this.start = start;
            this.count = count;
        }
        public long start() { return start; }
        public int count() { return count; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Window value = (Window) other;
            return java.util.Objects.equals(start, value.start) && java.util.Objects.equals(count, value.count);
        }
        @Override public int hashCode() { return java.util.Objects.hash(start, count); }
    }
        AccessFilter(LearningMapper db) { this.db=db; }
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            String requestId=UUID.randomUUID().toString(); MDC.put("requestId",requestId); res.setHeader("X-Request-Id",requestId);
            try {
                if(req.getRequestURI().startsWith("/api/v1/auth/") && !req.getRequestURI().endsWith("/logout")) {
                    long now=System.currentTimeMillis();
                    if(attempts.size()>10000) attempts.entrySet().removeIf(x->now-x.getValue().start()>300000);
                    Window w=attempts.compute(req.getRemoteAddr(),(k,old)->old==null||now-old.start()>300000?new Window(now,1):new Window(old.start(),old.count()+1));
                    if(w.count()>30) {write(res,429,"RATE_LIMITED","操作太频繁，请5分钟后重试");return;}
                }
                String header=req.getHeader("Authorization");
                if(header!=null && header.startsWith("Bearer ") && header.length()<150) {
                    Session session=db.byAccess(Tokens.hash(header.substring(7)));
                    if(session!=null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Identity(session.ownerId,session.id),null,Values.list()));
                }
                chain.doFilter(req,res);
            } finally { MDC.remove("requestId"); }
        }
    }
}
