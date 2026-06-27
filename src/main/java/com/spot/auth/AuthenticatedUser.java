package com.spot.auth;

public record AuthenticatedUser(Long userId, String nickname) {

    public static final String REQUEST_ATTRIBUTE = "spot.authenticatedUser";
}
