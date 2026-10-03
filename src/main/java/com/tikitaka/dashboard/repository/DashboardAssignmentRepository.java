package com.tikitaka.dashboard.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.tikitaka.dashboard.dto.DashboardAssignmentItem;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DashboardAssignmentRepository {

    private final EntityManager entityManager;

    public List<DashboardAssignmentItem> findOpenAssignments(UUID userId) {
        return entityManager.createQuery("""
                        select new com.tikitaka.dashboard.dto.DashboardAssignmentItem(
                            assignment.id,
                            space.id,
                            space.spaceName,
                            assignment.title,
                            assignment.dueAt,
                            'OPEN'
                        )
                        from Assignment assignment
                        join assignment.space space
                        join SpaceMember member on member.space = space
                        where member.user.id = :userId
                          and member.status = com.tikitaka.space.entity.SpaceMemberStatus.APPROVED
                          and member.removedAt is null
                          and space.activeStatus = true
                          and assignment.deleted = false
                          and assignment.closedAt is null
                          and (
                              assignment.autoClose = false
                              or assignment.dueAt > current_timestamp
                          )
                        order by assignment.dueAt asc, assignment.id asc
                        """, DashboardAssignmentItem.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
