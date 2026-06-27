package com.spot.auth;

import com.spot.auth.jwt.JwtService;
import com.spot.auth.oauth.NaverOAuthClient;
import com.spot.auth.oauth.NaverProfile;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final NaverOAuthClient naverOAuthClient;
    private final UserService userService;
    private final JwtService jwtService;
    private final int defaultGoalMinutes;

    public AuthService(
        NaverOAuthClient naverOAuthClient,
        UserService userService,
        JwtService jwtService,
        @Value("${spot.default-goal-minutes:60}") int defaultGoalMinutes
    ) {
        this.naverOAuthClient = naverOAuthClient;
        this.userService = userService;
        this.jwtService = jwtService;
        this.defaultGoalMinutes = defaultGoalMinutes;
    }

    public String naverAuthorizeUrl(String state) {
        return naverOAuthClient.buildAuthorizeUrl(state);
    }

    public AuthResult loginWithNaver(String code, String state) {
        NaverProfile profile = naverOAuthClient.fetchProfile(code, state);
        User user = userService.resolveSocialUser(
            AuthProvider.NAVER,
            profile.id(),
            profile.email(),
            profile.nickname(),
            defaultGoalMinutes
        );
        String token = jwtService.generateToken(user.getId());
        return new AuthResult(
            token,
            user.getId(),
            user.resolvedDisplayName(),
            user.needsDisplayNameSetup()
        );
    }

    public record AuthResult(String token, Long userId, String nickname, boolean needsDisplayNameSetup) {
    }
}
