package com.spot.auth.oauth;

/**
 * 네이버 프로필 조회 결과 중 우리가 사용하는 필드.
 */
public record NaverProfile(String id, String email, String nickname) {
}
