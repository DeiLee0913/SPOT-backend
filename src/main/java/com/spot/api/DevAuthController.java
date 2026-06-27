package com.spot.api;

import com.spot.auth.jwt.JwtService;
import com.spot.common.ApiResponse;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 전용 토큰 발급. {@code dev} 프로파일에서만 활성화된다.
 * 네이버/프론트 연동 없이 Postman으로 인증이 필요한 API를 테스트하기 위한 용도.
 * 실행: SPRING_PROFILES_ACTIVE=dev
 */
@RestController
@RequestMapping("/auth/dev")
@Profile("dev")
public class DevAuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final int defaultGoalMinutes;

    public DevAuthController(
        UserService userService,
        JwtService jwtService,
        @Value("${spot.default-goal-minutes:60}") int defaultGoalMinutes
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.defaultGoalMinutes = defaultGoalMinutes;
    }

    @PostMapping("/token")
    public ApiResponse<TokenResponse> issueToken(@RequestBody(required = false) DevTokenRequest request) {
        String key = (request != null && StringUtils.hasText(request.key())) ? request.key().trim() : "dev-user";
        String naverNickname = (request != null && StringUtils.hasText(request.nickname()))
            ? request.nickname().trim()
            : "개발자-" + key;

        User user = userService.resolveSocialUser(
            AuthProvider.NAVER,
            "dev-" + key,
            null,
            naverNickname,
            defaultGoalMinutes
        );
        String token = jwtService.generateToken(user.getId());
        return ApiResponse.ok(new TokenResponse(
            token,
            user.getId(),
            user.resolvedDisplayName(),
            user.needsDisplayNameSetup()
        ));
    }

    public record DevTokenRequest(String key, String nickname) {
    }

    public record TokenResponse(
        String token,
        Long userId,
        String nickname,
        boolean needsDisplayNameSetup
    ) {
    }
}
