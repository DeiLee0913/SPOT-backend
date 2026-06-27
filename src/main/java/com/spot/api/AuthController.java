package com.spot.api;

import com.spot.auth.AuthService;
import com.spot.auth.AuthService.AuthResult;
import com.spot.common.ApiResponse;
import com.spot.common.BadRequestException;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 프론트가 네이버 로그인으로 보낼 authorize URL을 생성한다.
     * 반환된 state는 프론트가 보관 후 콜백에서 그대로 돌려준다(CSRF 방지).
     */
    @GetMapping("/naver/authorize-url")
    public ApiResponse<Map<String, String>> naverAuthorizeUrl() {
        String state = UUID.randomUUID().toString();
        String url = authService.naverAuthorizeUrl(state);
        return ApiResponse.ok(Map.of("authorizeUrl", url, "state", state));
    }

    /**
     * 프론트가 네이버 콜백에서 받은 code/state를 전달하면, 검증 후 SPOT JWT를 발급한다.
     */
    @PostMapping("/naver")
    public ApiResponse<LoginResponse> loginWithNaver(@RequestBody NaverLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.code()) || !StringUtils.hasText(request.state())) {
            throw new BadRequestException("INVALID_OAUTH_PARAM", "code와 state가 필요합니다.");
        }
        AuthResult result = authService.loginWithNaver(request.code(), request.state());
        return ApiResponse.ok(new LoginResponse(
            result.token(),
            result.userId(),
            result.nickname(),
            result.needsDisplayNameSetup()
        ));
    }

    public record NaverLoginRequest(@NotBlank String code, @NotBlank String state) {
    }

    public record LoginResponse(
        String token,
        Long userId,
        String nickname,
        boolean needsDisplayNameSetup
    ) {
    }
}
