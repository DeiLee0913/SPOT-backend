package com.spot.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class TodoApiTest {

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
    void quickCreateAndStartTimerWithoutCategory() throws Exception {
        String token = newUserToken();

        MvcResult quick = mockMvc.perform(asUser(post("/todos/quick"), token)
                .content("{\"title\":\"Review ch.3\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title", is("Review ch.3")))
            .andExpect(jsonPath("$.data.category").value(nullValue()))
            .andExpect(jsonPath("$.data.dueStudyDay", is("2026-06-27")))
            .andReturn();
        long todoId = dataNode(quick).get("todoId").asLong();

        mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{\"todoId\":" + todoId + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.todoId", is((int) todoId)))
            .andExpect(jsonPath("$.data.title", is("Review ch.3")));

        mockMvc.perform(asUser(get("/todos"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.today", hasSize(1)));
    }

    @Test
    void startWithTitleCreatesTodayTodoAndSession() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{\"title\":\"Algorithm drill\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title", is("Algorithm drill")));

        mockMvc.perform(asUser(get("/todos/picker"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(1)));
    }

    @Test
    void startWithoutTodoOrTitleAllowed() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.todoId").value(nullValue()))
            .andExpect(jsonPath("$.data.title").value(nullValue()));
    }

    @Test
    void assignCategoryTagsAndComplete() throws Exception {
        String token = newUserToken();

        MvcResult category = mockMvc.perform(asUser(post("/todos/categories"), token)
                .content("{\"name\":\"Spring\",\"color\":\"#3B82F6\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long categoryId = dataNode(category).get("categoryId").asLong();

        MvcResult tag = mockMvc.perform(asUser(post("/todos/tags"), token)
                .content("{\"name\":\"work\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long tagId = dataNode(tag).get("tagId").asLong();

        MvcResult created = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {"title":"API spec","categoryId":%d,"tagIds":[%d],"priority":1,"dueStudyDay":"2026-06-27"}
                    """.formatted(categoryId, tagId)))
            .andExpect(status().isCreated())
            .andReturn();
        long todoId = dataNode(created).get("todoId").asLong();

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("{\"priority\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.priority", is(2)));

        mockMvc.perform(asUser(post("/todos/" + todoId + "/complete"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("DONE")));

        mockMvc.perform(asUser(post("/todos/" + todoId + "/reopen"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("OPEN")));
    }

    @Test
    void linkTodoToSession() throws Exception {
        String token = newUserToken();

        MvcResult todo = mockMvc.perform(asUser(post("/todos/quick"), token)
                .content("{\"title\":\"Late link\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long todoId = dataNode(todo).get("todoId").asLong();

        MvcResult session = mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
        long sessionId = dataNode(session).get("sessionId").asLong();

        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/link-todo"), token)
                .content("{\"todoId\":" + todoId + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.todoId", is((int) todoId)))
            .andExpect(jsonPath("$.data.title", is("Late link")));
    }

    private String newUserToken() {
        User user = userRepository.save(User.ofSocial(
            AuthProvider.NAVER,
            "todo-" + SEQ.incrementAndGet(),
            null,
            "학습자",
            60
        ));
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
