package com.spot.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void saveTodoWithFrontendPayload() throws Exception {
        String token = newUserToken();

        MvcResult category = mockMvc.perform(asUser(post("/todos/categories"), token)
                .content("{\"name\":\"Spring\",\"color\":\"#3B82F6\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long categoryId = dataNode(category).get("categoryId").asLong();

        MvcResult tag1 = mockMvc.perform(asUser(post("/todos/tags"), token)
                .content("{\"name\":\"work\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long tagId1 = dataNode(tag1).get("tagId").asLong();

        MvcResult tag2 = mockMvc.perform(asUser(post("/todos/tags"), token)
                .content("{\"name\":\"study\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long tagId2 = dataNode(tag2).get("tagId").asLong();

        MvcResult created = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {"title":"Draft","categoryId":%d,"tagIds":[%d],"priority":1,"dueStudyDay":"2026-06-27"}
                    """.formatted(categoryId, tagId1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andReturn();
        long todoId = dataNode(created).get("todoId").asLong();

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("""
                    {
                      "title": "API spec",
                      "categoryId": %d,
                      "clearCategory": false,
                      "tagIds": [%d, %d],
                      "priority": 2,
                      "dueStudyDay": "2026-06-30",
                      "clearDue": false
                    }
                    """.formatted(categoryId, tagId1, tagId2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.title", is("API spec")))
            .andExpect(jsonPath("$.data.priority", is(2)))
            .andExpect(jsonPath("$.data.dueStudyDay", is("2026-06-30")))
            .andExpect(jsonPath("$.data.tags.length()", is(2)));

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("""
                    {
                      "title": "Undated",
                      "categoryId": null,
                      "clearCategory": true,
                      "tagIds": [],
                      "priority": null,
                      "dueStudyDay": null,
                      "clearDue": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.title", is("Undated")))
            .andExpect(jsonPath("$.data.category").value(nullValue()))
            .andExpect(jsonPath("$.data.priority").value(nullValue()))
            .andExpect(jsonPath("$.data.dueStudyDay").value(nullValue()))
            .andExpect(jsonPath("$.data.tags", hasSize(0)));
    }

    @Test
    void manageCategoriesAndTags() throws Exception {
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

        MvcResult todo = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {"title":"Tagged","categoryId":%d,"tagIds":[%d],"dueStudyDay":"2026-06-27"}
                    """.formatted(categoryId, tagId)))
            .andExpect(status().isCreated())
            .andReturn();
        long todoId = dataNode(todo).get("todoId").asLong();

        mockMvc.perform(asUser(patch("/todos/categories/" + categoryId), token)
                .content("{\"name\":\"Backend\",\"color\":\"#111827\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", is("Backend")));

        mockMvc.perform(asUser(patch("/todos/tags/" + tagId), token)
                .content("{\"name\":\"focus\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", is("focus")));

        mockMvc.perform(asUser(delete("/todos/categories/" + categoryId), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(asUser(get("/todos"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.today[0].todoId", is((int) todoId)))
            .andExpect(jsonPath("$.data.today[0].category").value(nullValue()));

        mockMvc.perform(asUser(delete("/todos/tags/" + tagId), token))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/todos/tags"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(0)));
    }

    @Test
    void deleteTodoUnlinksSessions() throws Exception {
        String token = newUserToken();

        MvcResult todo = mockMvc.perform(asUser(post("/todos/quick"), token)
                .content("{\"title\":\"To remove\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long todoId = dataNode(todo).get("todoId").asLong();

        MvcResult session = mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{\"todoId\":" + todoId + "}"))
            .andExpect(status().isCreated())
            .andReturn();
        long sessionId = dataNode(session).get("sessionId").asLong();

        mockMvc.perform(asUser(delete("/todos/" + todoId), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(asUser(get("/todos"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.today", hasSize(0)));

        mockMvc.perform(asUser(get("/sessions/open"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId", is((int) sessionId)))
            .andExpect(jsonPath("$.data.todoId").value(nullValue()))
            .andExpect(jsonPath("$.data.title").value(nullValue()));
    }

    @Test
    void duplicateTodoCreatesOpenCopy() throws Exception {
        String token = newUserToken();

        MvcResult category = mockMvc.perform(asUser(post("/todos/categories"), token)
                .content("{\"name\":\"Meetings\",\"color\":\"#3B82F6\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long categoryId = dataNode(category).get("categoryId").asLong();

        MvcResult tag = mockMvc.perform(asUser(post("/todos/tags"), token)
                .content("{\"name\":\"sync\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long tagId = dataNode(tag).get("tagId").asLong();

        MvcResult created = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Standup",
                      "categoryId": %d,
                      "tagIds": [%d],
                      "priority": 1,
                      "dueStudyDay": "2026-06-27",
                      "startTime": "10:00",
                      "endTime": "10:30"
                    }
                    """.formatted(categoryId, tagId)))
            .andExpect(status().isCreated())
            .andReturn();
        long todoId = dataNode(created).get("todoId").asLong();

        mockMvc.perform(asUser(post("/todos/" + todoId + "/complete"), token))
            .andExpect(status().isOk());

        MvcResult duplicated = mockMvc.perform(asUser(post("/todos/" + todoId + "/duplicate"), token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title", is("Standup_copy")))
            .andExpect(jsonPath("$.data.status", is("OPEN")))
            .andExpect(jsonPath("$.data.priority", is(1)))
            .andExpect(jsonPath("$.data.dueStudyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.startTime", is("10:00:00")))
            .andExpect(jsonPath("$.data.endTime", is("10:30:00")))
            .andExpect(jsonPath("$.data.category.categoryId", is((int) categoryId)))
            .andExpect(jsonPath("$.data.tags[0].tagId", is((int) tagId)))
            .andExpect(jsonPath("$.data.doneAt").value(nullValue()))
            .andReturn();
        long copyId = dataNode(duplicated).get("todoId").asLong();

        mockMvc.perform(asUser(get("/todos"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.today.length()", is(1)))
            .andExpect(jsonPath("$.data.today[0].todoId", is((int) copyId)))
            .andExpect(jsonPath("$.data.done.length()", is(1)))
            .andExpect(jsonPath("$.data.done[0].todoId", is((int) todoId)));
    }

    @Test
    void createAndUpdateTodoWithDescription() throws Exception {
        String token = newUserToken();

        MvcResult created = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Project plan",
                      "description": "Prep notes\\n- [ ] Draft outline\\n- [x] Review slides",
                      "dueStudyDay": "2026-06-27"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.description", is("Prep notes\n- [ ] Draft outline\n- [x] Review slides")))
            .andReturn();
        long todoId = dataNode(created).get("todoId").asLong();

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("""
                    {
                      "description": "Updated body"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.description", is("Updated body")));

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("""
                    {
                      "clearDescription": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.description").value(nullValue()));
    }

    @Test
    void createAndUpdateTodoWithOptionalTime() throws Exception {
        String token = newUserToken();

        MvcResult created = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Team sync",
                      "dueStudyDay": "2026-06-27",
                      "startTime": "14:00",
                      "endTime": "15:30"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.startTime", is("14:00:00")))
            .andExpect(jsonPath("$.data.endTime", is("15:30:00")))
            .andReturn();
        long todoId = dataNode(created).get("todoId").asLong();

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("""
                    {
                      "startTime": "10:00",
                      "clearEndTime": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.startTime", is("10:00:00")))
            .andExpect(jsonPath("$.data.endTime").value(nullValue()))
            .andExpect(jsonPath("$.data.endStudyDay").value(nullValue()));

        mockMvc.perform(asUser(patch("/todos/" + todoId), token)
                .content("""
                    {
                      "startTime": "16:00",
                      "endTime": "15:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("INVALID_TIME_RANGE")));
    }

    @Test
    void todoScheduleAllowsEndDateOnly() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Multi-day block",
                      "dueStudyDay": "2026-06-27",
                      "endStudyDay": "2026-06-29"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.dueStudyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.endStudyDay", is("2026-06-29")))
            .andExpect(jsonPath("$.data.startTime").value(nullValue()))
            .andExpect(jsonPath("$.data.endTime").value(nullValue()));
    }

    @Test
    void todoScheduleAllowsEndTimeOnlyOnStartDay() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Deadline",
                      "dueStudyDay": "2026-06-27",
                      "endTime": "18:00"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.endStudyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.endTime", is("18:00:00")));
    }

    @Test
    void todoScheduleRequiresStartDateWhenTimeSet() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "No start date",
                      "startTime": "10:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("START_DATE_REQUIRED")));
    }

    @Test
    void todoScheduleAllowsMultiDayEnd() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Overnight workshop",
                      "dueStudyDay": "2026-06-27",
                      "startTime": "22:00",
                      "endStudyDay": "2026-06-28",
                      "endTime": "06:00"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.dueStudyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.startTime", is("22:00:00")))
            .andExpect(jsonPath("$.data.endStudyDay", is("2026-06-28")))
            .andExpect(jsonPath("$.data.endTime", is("06:00:00")));
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

    @Test
    void searchTodosWithFiltersAndPagination() throws Exception {
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

        MvcResult datedOpen = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Spring API draft",
                      "description": "endpoint checklist",
                      "categoryId": %d,
                      "tagIds": [%d],
                      "priority": 1,
                      "dueStudyDay": "2026-06-27"
                    }
                    """.formatted(categoryId, tagId)))
            .andExpect(status().isCreated())
            .andReturn();
        long datedOpenId = dataNode(datedOpen).get("todoId").asLong();

        mockMvc.perform(asUser(post("/todos"), token)
                .content("{\"title\":\"Backlog item\"}"))
            .andExpect(status().isCreated());

        MvcResult done = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {"title":"Done task","categoryId":%d,"dueStudyDay":"2026-06-26"}
                    """.formatted(categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
        long doneId = dataNode(done).get("todoId").asLong();
        mockMvc.perform(asUser(post("/todos/" + doneId + "/complete"), token))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/todos/search"), token)
                .param("q", "spring")
                .param("status", "OPEN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.items.length()", is(1)))
            .andExpect(jsonPath("$.data.items[0].title", is("Spring API draft")));

        mockMvc.perform(asUser(get("/todos/search"), token)
                .param("status", "DONE")
                .param("categoryId", String.valueOf(categoryId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.items[0].todoId", is((int) doneId)));

        mockMvc.perform(asUser(get("/todos/search"), token)
                .param("tagId", String.valueOf(tagId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.items[0].tags[0].tagId", is((int) tagId)));

        mockMvc.perform(asUser(get("/todos/search"), token)
                .param("dueFrom", "2026-06-27")
                .param("dueTo", "2026-06-27"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.items[0].todoId", is((int) datedOpenId)))
            .andExpect(jsonPath("$.data.items[0].title", is("Spring API draft")));

        MvcResult firstPage = mockMvc.perform(asUser(get("/todos/search"), token)
                .param("status", "ALL")
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(3)))
            .andExpect(jsonPath("$.data.items.length()", is(1)))
            .andExpect(jsonPath("$.data.nextCursor").isNumber())
            .andReturn();
        long cursor = dataNode(firstPage).get("nextCursor").asLong();

        mockMvc.perform(asUser(get("/todos/search"), token)
                .param("status", "ALL")
                .param("limit", "1")
                .param("cursor", String.valueOf(cursor)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()", is(1)));
    }

    @Test
    void boardReturnsWeeklyStatsWithBreakdown() throws Exception {
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

        MvcResult todo = mockMvc.perform(asUser(post("/todos"), token)
                .content("""
                    {
                      "title": "Board task",
                      "categoryId": %d,
                      "tagIds": [%d],
                      "dueStudyDay": "2026-06-27"
                    }
                    """.formatted(categoryId, tagId)))
            .andExpect(status().isCreated())
            .andReturn();
        long todoId = dataNode(todo).get("todoId").asLong();

        mockMvc.perform(asUser(post("/todos/" + todoId + "/complete"), token))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/sessions/manual"), token)
                .content("""
                    {
                      "todoId": %d,
                      "startedAt": "2026-06-26T22:00:00Z",
                      "endedAt": "2026-06-26T23:00:00Z"
                    }
                    """.formatted(todoId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.durationMinutes", is(60)));

        mockMvc.perform(asUser(get("/todos/board"), token)
                .param("from", "2026-06-23")
                .param("to", "2026-06-27"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from", is("2026-06-23")))
            .andExpect(jsonPath("$.data.to", is("2026-06-27")))
            .andExpect(jsonPath("$.data.summary.completedCount", is(1)))
            .andExpect(jsonPath("$.data.summary.studyMinutes", is(60)))
            .andExpect(jsonPath("$.data.summary.studyMinutesFromSessions", is(60)))
            .andExpect(jsonPath("$.data.days.length()", is(1)))
            .andExpect(jsonPath("$.data.days[0].studyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.days[0].completedCount", is(1)))
            .andExpect(jsonPath("$.data.days[0].studyMinutes", is(60)))
            .andExpect(jsonPath("$.data.days[0].byCategory[0].categoryId", is((int) categoryId)))
            .andExpect(jsonPath("$.data.days[0].byTag[0].tagId", is((int) tagId)));

        mockMvc.perform(asUser(get("/todos/board"), token)
                .param("from", "2026-06-23")
                .param("to", "2026-06-27")
                .param("categoryId", String.valueOf(categoryId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.completedCount", is(1)))
            .andExpect(jsonPath("$.data.summary.studyMinutes", is(60)));
    }

    @Test
    void boardRejectsInvalidDateRange() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(get("/todos/board"), token)
                .param("from", "2026-06-27")
                .param("to", "2026-06-20"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("INVALID_DATE_RANGE")));
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
