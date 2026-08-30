package com.tikitaka.dashboard.dto;

import java.util.List;

public record DashboardAssignmentsResponse(
        List<DashboardAssignmentItem> assignments
) {
    public DashboardAssignmentsResponse {
        assignments = List.copyOf(assignments);
    }
}
