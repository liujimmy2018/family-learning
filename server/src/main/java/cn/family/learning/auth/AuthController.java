package cn.family.learning.auth;

import cn.family.learning.common.Values;

import cn.family.learning.common.ApiEnvelope;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) {this.auth=auth;}
    @GetMapping("/health") Object health() {return Values.map("status","UP");}
    @PostMapping("/api/v1/auth/register") Object register(@Valid @RequestBody AuthService.Register r) {return ApiEnvelope.ok(auth.register(r));}
    @PostMapping("/api/v1/auth/login") Object login(@Valid @RequestBody AuthService.Login r) {return ApiEnvelope.ok(auth.login(r));}
    @PostMapping("/api/v1/auth/refresh") Object refresh(@Valid @RequestBody AuthService.Refresh r) {return ApiEnvelope.ok(auth.refresh(r.refreshToken()));}
    @PostMapping("/api/v1/auth/logout") Object logout(@AuthenticationPrincipal SecurityConfig.Identity i) {auth.logout(i.sessionId()); return ApiEnvelope.ok(Values.map("loggedOut",true));}
}
