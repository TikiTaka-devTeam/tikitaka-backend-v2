package com.tikitaka.dashboard.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.dashboard.dto.DashboardAssignmentsResponse;
import com.tikitaka.dashboard.dto.DashboardScheduleItem;
import com.tikitaka.dashboard.dto.DashboardTimetableItem;
import com.tikitaka.dashboard.dto.DashboardTimetableRow;
import com.tikitaka.dashboard.repository.DashboardAssignmentRepository;
import com.tikitaka.dashboard.repository.DashboardTimetableRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardAssignmentRepository dashboardAssignmentRepository;
    private final DashboardTimetableRepository dashboardTimetableRepository;

    public List<DashboardTimetableItem> getTimetable(UUID userId) {
        Map<SpaceKey, List<DashboardTimetableRow>> rowsBySpace =
                dashboardTimetableRepository.findTimetable(userId)
                        .stream()
                        .collect(Collectors.groupingBy(
                                row -> new SpaceKey(row.spaceId(), row.spaceName()),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        return rowsBySpace.entrySet().stream()
                .map(entry -> new DashboardTimetableItem(
                        entry.getKey().spaceId(),
                        entry.getKey().spaceName(),
                        entry.getValue().stream()
                                .sorted(Comparator
                                        .comparing(DashboardTimetableRow::day)
                                        .thenComparing(DashboardTimetableRow::startTime)
                                        .thenComparing(DashboardTimetableRow::endTime))
                                .map(row -> new DashboardScheduleItem(
                                        row.day(),
                                        row.startTime(),
                                        row.endTime(),
                                        row.classroom()
                                ))
                                .toList()
                ))
                .toList();
    }

    public DashboardAssignmentsResponse getAssignments(UUID userId) {
        return new DashboardAssignmentsResponse(
                dashboardAssignmentRepository.findOpenAssignments(userId)
        );
    }

    private record SpaceKey(UUID spaceId, String spaceName) {
    }
}
