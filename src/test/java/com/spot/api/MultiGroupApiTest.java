package com.spot.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.auth.jwt.JwtService;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MultiGroupApiTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void joinRequestsAndLeaveAreScopedByGroupId() throws Exception {
        String creator = newUserToken("크리에이터");
        String memberA = newUserToken("멤버A");
        String memberB = newUserToken("멤버B");

        long groupA = createGroup(creator, "스터디 A");
        long groupB = createGroup(creator, "스터디 B");

        joinGroup(memberA, inviteCode(creator, groupA));
        joinGroup(memberB, inviteCode(creator, groupB));

        mockMvc.perform(asUser(get("/groups/me/join-requests").param("groupId", String.valueOf(groupA)), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].groupId", is((int) groupA)));

        mockMvc.perform(asUser(get("/groups/me/join-requests").param("groupId", String.valueOf(groupB)), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].groupId", is((int) groupB)));

        mockMvc.perform(asUser(get("/groups/me/join-requests"), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)));

        MvcResult allRequests = mockMvc.perform(asUser(get("/groups/me/join-requests"), creator))
            .andExpect(status().isOk())
            .andReturn();
        long memberIdA = findMemberIdForGroup(allRequests, groupA);

        mockMvc.perform(asUser(
                post("/groups/me/join-requests/" + memberIdA + "/approve").param("groupId", String.valueOf(groupB)),
                creator))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("MEMBER_NOT_FOUND")));

        mockMvc.perform(asUser(
                post("/groups/me/join-requests/" + memberIdA + "/approve").param("groupId", String.valueOf(groupA)),
                creator))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/groups/join"), memberA)
                .content("{\"inviteCode\":\"" + inviteCode(creator, groupB) + "\"}"))
            .andExpect(status().isOk());

        MvcResult pendingB = mockMvc.perform(asUser(get("/groups/me/join-requests").param("groupId", String.valueOf(groupB)), creator))
            .andExpect(status().isOk())
            .andReturn();
        long memberIdAInB = findMemberIdForUser(pendingB, memberAUserId(memberA));

        mockMvc.perform(asUser(
                post("/groups/me/join-requests/" + memberIdAInB + "/approve").param("groupId", String.valueOf(groupB)),
                creator))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/groups/me/leave"), memberA)
                .content("{\"groupId\":" + groupA + "}"))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/groups/me"), memberA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].groupId", is((int) groupB)));
    }

    private long createGroup(String creatorToken, String name) throws Exception {
        MvcResult created = mockMvc.perform(asUser(post("/groups"), creatorToken)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return dataNode(created).get("groupId").asLong();
    }

    private String inviteCode(String creatorToken, long groupId) throws Exception {
        MvcResult groups = mockMvc.perform(asUser(get("/groups/me"), creatorToken))
            .andExpect(status().isOk())
            .andReturn();
        for (JsonNode group : dataNode(groups)) {
            if (group.get("groupId").asLong() == groupId) {
                return group.get("inviteCode").asText();
            }
        }
        throw new IllegalStateException("group not found: " + groupId);
    }

    private void joinGroup(String memberToken, String inviteCode) throws Exception {
        mockMvc.perform(asUser(post("/groups/join"), memberToken)
                .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
            .andExpect(status().isOk());
    }

    private long findMemberIdForGroup(MvcResult requests, long groupId) throws Exception {
        for (JsonNode item : dataNode(requests)) {
            if (item.get("groupId").asLong() == groupId) {
                return item.get("memberId").asLong();
            }
        }
        throw new IllegalStateException("request not found for group: " + groupId);
    }

    private long findMemberIdForUser(MvcResult requests, long userId) throws Exception {
        for (JsonNode item : dataNode(requests)) {
            if (item.get("userId").asLong() == userId) {
                return item.get("memberId").asLong();
            }
        }
        throw new IllegalStateException("request not found for user: " + userId);
    }

    private long memberAUserId(String memberAToken) throws Exception {
        MvcResult me = mockMvc.perform(asUser(get("/me"), memberAToken))
            .andExpect(status().isOk())
            .andReturn();
        return dataNode(me).get("userId").asLong();
    }

    private String newUserToken(String nickname) {
        String providerId = "multi-" + SEQ.incrementAndGet();
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, providerId, null, nickname, 60));
        return jwtService.generateToken(user.getId());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode dataNode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
