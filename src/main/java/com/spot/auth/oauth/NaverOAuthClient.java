package com.spot.auth.oauth;

/**
 * 네이버 OAuth 연동. authorization code를 access token으로 교환하고 프로필을 조회한다.
 * 테스트에서는 이 인터페이스를 대체(mock)한다.
 */
public interface NaverOAuthClient {

    String buildAuthorizeUrl(String state);

    NaverProfile fetchProfile(String code, String state);
}
