package com.spot.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.auth.jwt.JwtService;
import com.spot.auth.oauth.NaverOAuthClient;
import com.spot.auth.oauth.NaverProfile;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
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
class DisplayNameApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NaverOAuthClient naverOAuthClient;

    @Test
    void newUserNeedsDisplayNameSetupAndCanSetDisplayName() throws Exception {
        when(naverOAuthClient.fetchProfile(any(), any()))
            .thenReturn(new NaverProfile("naver-display", "user@example.com", "네이버민지"));

        MvcResult login = mockMvc.perform(post("/auth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code\",\"state\":\"state\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("네이버민지")))
            .andExpect(jsonPath("$.data.needsDisplayNameSetup", is(true)))
            .andReturn();

        String token = extractToken(login);

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("네이버민지")))
            .andExpect(jsonPath("$.data.displayName").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.naverNickname", is("네이버민지")))
            .andExpect(jsonPath("$.data.needsDisplayNameSetup", is(true)));

        mockMvc.perform(put("/me/display-name")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"스터디민지\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("스터디민지")))
            .andExpect(jsonPath("$.data.displayName", is("스터디민지")))
            .andExpect(jsonPath("$.data.needsDisplayNameSetup", is(false)));

        when(naverOAuthClient.fetchProfile(any(), any()))
            .thenReturn(new NaverProfile("naver-display", "user@example.com", "네이버변경"));

        mockMvc.perform(post("/auth/naver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code2\",\"state\":\"state2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("스터디민지")))
            .andExpect(jsonPath("$.data.needsDisplayNameSetup", is(false)));

        User user = userRepository.findByProviderAndProviderId(AuthProvider.NAVER, "naver-display").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getNaverNickname()).isEqualTo("네이버변경");
        org.assertj.core.api.Assertions.assertThat(user.getDisplayName()).isEqualTo("스터디민지");
    }

    @Test
    void rejectsInvalidDisplayName() throws Exception {
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, "invalid-name", null, "네이버", 60));
        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(put("/me/display-name")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    private String extractToken(MvcResult login) throws Exception {
        return objectMapper.readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("token")
            .asText();
    }
}
