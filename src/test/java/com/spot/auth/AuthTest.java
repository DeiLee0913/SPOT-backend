package com.spot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.auth.oauth.NaverOAuthClient;
import com.spot.auth.oauth.NaverProfile;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private NaverOAuthClient naverOAuthClient;

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void naverAuthorizeUrlReturnsUrlAndState() throws Exception {
        when(naverOAuthClient.buildAuthorizeUrl(any()))
            .thenAnswer(inv -> "https://nid.naver.com/oauth2.0/authorize?state=" + inv.getArgument(0));

        mockMvc.perform(get("/auth/naver/authorize-url"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.authorizeUrl").exists())
            .andExpect(jsonPath("$.data.state").exists());
    }

    @Test
    void naverLoginIssuesJwtAndAuthenticatesMe() throws Exception {
        when(naverOAuthClient.fetchProfile(any(), any()))
            .thenReturn(new NaverProfile("naver-abc", "minji@example.com", "민지"));

        MvcResult login = mockMvc.perform(post("/auth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"dummy-code\",\"state\":\"dummy-state\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token", notNullValue()))
            .andExpect(jsonPath("$.data.nickname", is("민지")))
            .andExpect(jsonPath("$.data.needsDisplayNameSetup", is(true)))
            .andReturn();

        String token = objectMapper.readTree(login.getResponse().getContentAsString())
            .get("data").get("token").asText();

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("민지")));

        // 같은 네이버 계정으로 재로그인해도 사용자는 1명
        mockMvc.perform(post("/auth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"dummy-code-2\",\"state\":\"dummy-state-2\"}"))
            .andExpect(status().isOk());

        assertThat(userRepository.findByProviderAndProviderId(AuthProvider.NAVER, "naver-abc")).isPresent();
        long count = userRepository.findAll().stream()
            .filter(u -> "naver-abc".equals(u.getProviderId()))
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }
}
