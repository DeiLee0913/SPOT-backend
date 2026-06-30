package com.spot.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevAuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void devTokenWithEmptyBodyThenMe() throws Exception {
        String token = issueToken("{}");
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void devTokenWithKeyAndNicknameThenMe() throws Exception {
        String token = issueToken("""
            {"key":"minji","nickname":"Minji"}
            """);

        mockMvc.perform(put("/me/display-name")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Minji\"}"))
            .andExpect(status().isOk());

        String tokenAgain = issueToken("""
            {"key":"minji","nickname":"Minji"}
            """);

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + tokenAgain))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.nickname", is("Minji")))
            .andExpect(jsonPath("$.data.needsDisplayNameSetup", is(false)));
    }

    @Test
    void devTokenRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/auth/dev/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{key:minji}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("INVALID_JSON")));
    }

    private String issueToken(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/dev/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("token").asText();
    }
}
