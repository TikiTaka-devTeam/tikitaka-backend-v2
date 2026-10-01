package com.tikitaka.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.dashboard.dto.DashboardAssignmentItem;
import com.tikitaka.dashboard.dto.DashboardAssignmentsResponse;
import com.tikitaka.dashboard.dto.DashboardTimetableItem;
import com.tikitaka.dashboard.dto.DashboardTimetableRow;
import com.tikitaka.dashboard.repository.DashboardAssignmentRepository;
import com.tikitaka.dashboard.repository.DashboardTimetableRepository;
import com.tikitaka.dashboard.service.DashboardService;
import com.tikitaka.space.entity.DayOfWeek;
import com.tikitaka.space.entity.SpaceColorKey;

class DashboardServiceTests {

    private final DashboardAssignmentRepository repository =
            mock(DashboardAssignmentRepository.class);
    private final DashboardTimetableRepository timetableRepository =
            mock(DashboardTimetableRepository.class);
    private final DashboardService dashboardService =
            new DashboardService(repository, timetableRepository);

    @Test
    void groupsAndSortsTimetableBySpace() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(timetableRepository.findTimetable(userId)).thenReturn(List.of(
                new DashboardTimetableRow(
                        spaceId, "운영체제", SpaceColorKey.COLOR_3,
                        "SW101", DayOfWeek.WEDNESDAY,
                        LocalTime.of(13, 0), LocalTime.of(14, 30)
                ),
                new DashboardTimetableRow(
                        spaceId, "운영체제", SpaceColorKey.COLOR_3,
                        "SW101", DayOfWeek.MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(10, 30)
                )
        ));

        List<DashboardTimetableItem> response =
                dashboardService.getTimetable(userId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).spaceId()).isEqualTo(spaceId);
        assertThat(response.get(0).colorKey()).isEqualTo(SpaceColorKey.COLOR_3);
        assertThat(response.get(0).schedules())
                .extracting(item -> item.day())
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        verify(timetableRepository).findTimetable(userId);
    }

    @Test
    void returnsEmptyTimetableWhenNoScheduleExists() {
        UUID userId = UUID.randomUUID();
        when(timetableRepository.findTimetable(userId)).thenReturn(List.of());

        assertThat(dashboardService.getTimetable(userId)).isEmpty();
    }

    @Test
    void returnsOpenAssignmentsProvidedByDashboardRepository() {
        UUID userId = UUID.randomUUID();
        DashboardAssignmentItem item = new DashboardAssignmentItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "운영체제",
                "프로세스 과제",
                Instant.parse("2026-09-01T14:59:59Z")
        );
        when(repository.findOpenAssignments(userId)).thenReturn(List.of(item));

        DashboardAssignmentsResponse response = dashboardService.getAssignments(userId);

        assertThat(response.assignments()).containsExactly(item);
        verify(repository).findOpenAssignments(userId);
    }

    @Test
    void returnsEmptyAssignmentsInsteadOfNull() {
        UUID userId = UUID.randomUUID();
        when(repository.findOpenAssignments(userId)).thenReturn(List.of());

        DashboardAssignmentsResponse response = dashboardService.getAssignments(userId);

        assertThat(response.assignments()).isEmpty();
    }
}
