package com.spot.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CoreApiTest {

    // 2026-06-27 09:00 KST → study day 2026-06-27, 11:00 마감 이전
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
    void groupCreateJoinApproveAndDashboardFlow() throws Exception {
        String creator = newUserToken("방장");
        String member = newUserToken("멤버");

        // 그룹 생성
        MvcResult created = mockMvc.perform(asUser(post("/groups"), creator)
                .content("{\"name\":\"백엔드 스터디\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.inviteCode").exists())
            .andReturn();
        String inviteCode = dataNode(created).get("inviteCode").asText();
        long groupId = dataNode(created).get("groupId").asLong();

        // 같은 사용자가 다른 그룹도 생성 가능
        mockMvc.perform(asUser(post("/groups"), creator)
                .content("{\"name\":\"두번째 스터디\"}"))
            .andExpect(status().isCreated());

        // 멤버 가입 요청
        mockMvc.perform(asUser(post("/groups/join"), member)
                .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberStatus", is("PENDING")));

        // 방장이 가입 요청 목록 조회
        MvcResult requests = mockMvc.perform(asUser(get("/groups/me/join-requests"), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status", is("PENDING")))
            .andExpect(jsonPath("$.data[0].nickname").exists())
            .andReturn();
        long memberId = dataNode(requests).get(0).get("memberId").asLong();

        // 승인
        mockMvc.perform(asUser(post("/groups/me/join-requests/" + memberId + "/approve"), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        // 대시보드에 두 멤버 노출
        mockMvc.perform(asUser(get("/groups/me/dashboard?groupId=" + groupId), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.members.length()", is(2)));

        // 구버전 프론트: groupId 생략 시 첫 ACTIVE 그룹
        mockMvc.perform(asUser(get("/groups/me/dashboard"), creator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.members.length()", is(2)));
    }

    @Test
    void memberCanBelongToMultipleGroups() throws Exception {
        String creatorA = newUserToken("방장A");
        String creatorB = newUserToken("방장B");
        String member = newUserToken("멤버");

        MvcResult groupA = mockMvc.perform(asUser(post("/groups"), creatorA)
                .content("{\"name\":\"A팀\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String codeA = dataNode(groupA).get("inviteCode").asText();

        MvcResult groupB = mockMvc.perform(asUser(post("/groups"), creatorB)
                .content("{\"name\":\"B팀\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String codeB = dataNode(groupB).get("inviteCode").asText();

        mockMvc.perform(asUser(post("/groups/join"), member)
                .content("{\"inviteCode\":\"" + codeA + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/groups/join"), member)
                .content("{\"inviteCode\":\"" + codeB + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/groups/me"), member))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(2)));
    }

    @Test
    void dailyGoalAllowsZeroButDefaultGoalRequiresAtLeastOneHour() throws Exception {
        String user = newUserToken("휴식");

        // 일일 목표 0분 허용 (쉬는 날)
        mockMvc.perform(asUser(put("/goals/today"), user)
                .content("{\"goalMinutes\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(0)));

        // 기본 목표는 60분 미만이면 거부
        mockMvc.perform(asUser(put("/me/default-goal"), user)
                .content("{\"goalMinutes\":30}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));

        // 기본 목표 60분 이상은 허용
        mockMvc.perform(asUser(put("/me/default-goal"), user)
                .content("{\"goalMinutes\":90}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.defaultGoalMinutes", is(90)));
    }

    @Test
    void goalAndSessionFlow() throws Exception {
        String user = newUserToken("학생");

        // 오늘 목표 설정 (11:00 이전이므로 허용)
        mockMvc.perform(asUser(put("/goals/today"), user)
                .content("{\"goalMinutes\":120}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(120)))
            .andExpect(jsonPath("$.data.source", is("USER_SET")));

        // 타이머 시작
        MvcResult started = mockMvc.perform(asUser(post("/sessions/start"), user)
                .content("{\"title\":\"Spring\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status", is("OPEN")))
            .andExpect(jsonPath("$.data.title", is("Spring")))
            .andExpect(jsonPath("$.data.todoId").exists())
            .andReturn();
        long sessionId = dataNode(started).get("sessionId").asLong();

        // 이미 OPEN 세션 있는데 또 시작 → 409
        mockMvc.perform(asUser(post("/sessions/start"), user)
                .content("{\"title\":\"JPA\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("SESSION_ALREADY_OPEN")));

        // 종료
        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/end"), user))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CLOSED")));

        // 수동 세션 등록 (과거, 겹치지 않음)
        mockMvc.perform(asUser(post("/sessions/manual"), user)
                .content("{\"title\":\"알고리즘\",\"startedAt\":\"2026-06-26T22:00:00Z\",\"endedAt\":\"2026-06-26T23:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.durationMinutes", is(60)))
            .andExpect(jsonPath("$.data.source", is("MANUAL")));

        // 겹치는 수동 세션 → 409
        mockMvc.perform(asUser(post("/sessions/manual"), user)
                .content("{\"title\":\"중복\",\"startedAt\":\"2026-06-26T22:30:00Z\",\"endedAt\":\"2026-06-26T23:30:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("SESSION_OVERLAP")));

        // 미래 종료 시각 → 400
        mockMvc.perform(asUser(post("/sessions/manual"), user)
                .content("{\"title\":\"미래\",\"startedAt\":\"2026-06-27T05:00:00Z\",\"endedAt\":\"2026-06-27T06:00:00Z\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("FUTURE_TIME")));

        // 미래 시작 시각 → 400
        mockMvc.perform(asUser(post("/sessions/manual"), user)
                .content("{\"title\":\"미래시작\",\"startedAt\":\"2026-06-27T01:00:00Z\",\"endedAt\":\"2026-06-27T01:30:00Z\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("FUTURE_TIME")));

        // 오늘 세션 목록 (타이머 + 수동)
        mockMvc.perform(asUser(get("/sessions/today"), user))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(2)));

        // 타이머 세션도 삭제 가능 (수정은 불가)
        mockMvc.perform(asUser(delete("/sessions/" + sessionId), user))
            .andExpect(status().isOk());
    }

    private String newUserToken(String nickname) {
        String providerId = "user-" + SEQ.incrementAndGet();
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, providerId, null, nickname, 60));
        return jwtService.generateToken(user.getId());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode dataNode(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data");
    }
}
