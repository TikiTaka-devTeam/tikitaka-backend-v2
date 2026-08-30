package com.tikitaka.dashboard.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.tikitaka.dashboard.dto.DashboardTimetableRow;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DashboardTimetableRepository {

    private final EntityManager entityManager;

    public List<DashboardTimetableRow> findTimetable(
            UUID userId,
            int year,
            String semester
    ) {
        return entityManager.createQuery("""
                        select new com.tikitaka.dashboard.dto.DashboardTimetableRow(
                            space.id,
                            space.spaceName,
                            space.classroom,
                            schedule.day,
                            schedule.startTime,
                            schedule.endTime
                        )
                        from Schedule schedule
                        join schedule.space space
                        join SpaceMember member on member.space = space
                        where member.user.id = :userId
                          and member.status = com.tikitaka.space.entity.SpaceMemberStatus.APPROVED
                          and member.removedAt is null
                          and space.activeStatus = true
                          and space.year = :year
                          and space.semester = :semester
                        order by space.spaceName asc, space.id asc
                        """, DashboardTimetableRow.class)
                .setParameter("userId", userId)
                .setParameter("year", year)
                .setParameter("semester", semester)
                .getResultList();
    }
}
