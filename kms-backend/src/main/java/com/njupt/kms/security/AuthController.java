package com.njupt.kms.security;

import com.njupt.kms.common.Result;
import com.njupt.kms.security.dto.LoginRequest;
import com.njupt.kms.security.dto.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        User user = authService.requireUser(token);

        // Map.of 不接受 null 值，nickname 可能为 null，故用 HashMap
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("user", UserVO.from(user));
        return Result.ok(payload);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = AuthInterceptor.TOKEN_HEADER, required = false) String token) {
        authService.logout(token);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(UserVO.from(CurrentUser.getRequired()));
    }
}
