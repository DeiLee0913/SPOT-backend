package com.spot.api;

import com.spot.api.dto.DashboardDtos.DashboardResponse;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.domain.dashboard.DashboardService;
import com.spot.domain.group.GroupService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequestMapping("/groups/me/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final GroupService groupService;

    public DashboardController(DashboardService dashboardService, GroupService groupService) {
        this.dashboardService = dashboardService;
        this.groupService = groupService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) Long groupId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDay
    ) {
        Long resolvedGroupId = groupService.resolveDashboardGroupId(currentUser.userId(), groupId);
        return ApiResponse.ok(dashboardService.getDashboard(currentUser.userId(), resolvedGroupId, studyDay));
    }
}
