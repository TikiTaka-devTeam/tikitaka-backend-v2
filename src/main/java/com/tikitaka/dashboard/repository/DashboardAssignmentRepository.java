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

    public List<DashboardAssignmentItem> findAssignments(UUID userId) {
        return entityManager.createQuery("""
                        select new com.tikitaka.dashboard.dto.DashboardAssignmentItem(
                            assignment.id,
                            space.id,
                            space.spaceName,
                            assignment.title,
                            assignment.dueAt,
                            case
                                when assignment.closedAt is not null
                                  or (assignment.autoClose = true and assignment.dueAt <= current_timestamp)
                                then 'CLOSED'
                                else 'OPEN'
                            end,
                            case
                                when member.role = com.tikitaka.space.entity.SpaceMemberRole.STUDENT
                                then case
                                    when submission.id is null then 'NOT_SUBMITTED'
                                    when submission.status = com.tikitaka.assignment.entity.SubmissionStatus.SUBMITTED
                                        then 'SUBMITTED'
                                    when submission.status = com.tikitaka.assignment.entity.SubmissionStatus.LATE
                                        then 'LATE'
                                end
                                else null
                            end,
                            case
                                when member.role <> com.tikitaka.space.entity.SpaceMemberRole.STUDENT
                                then case
                                    when assignment.gradingStatus = com.tikitaka.assignment.entity.GradingStatus.DRAFT
                                        then 'DRAFT'
                                    when assignment.gradingStatus = com.tikitaka.assignment.entity.GradingStatus.FINALIZED
                                        then 'FINALIZED'
                                end
                                else null
                            end
                        )
                        from Assignment assignment
                        join assignment.space space
                        join SpaceMember member on member.space = space
                        left join AssignmentSubmission submission
                            on submission.assignment = assignment
                            and submission.student.id = :userId
                        where member.user.id = :userId
                          and member.status = com.tikitaka.space.entity.SpaceMemberStatus.APPROVED
                          and member.removedAt is null
                          and space.activeStatus = true
                          and assignment.deleted = false
                          and (
                              (
                                  member.role = com.tikitaka.space.entity.SpaceMemberRole.STUDENT
                                  and assignment.closedAt is null
                                  and (assignment.autoClose = false or assignment.dueAt > current_timestamp)
                              )
                              or (
                                  member.role <> com.tikitaka.space.entity.SpaceMemberRole.STUDENT
                                  and (
                                      (assignment.closedAt is null
                                       and (assignment.autoClose = false or assignment.dueAt > current_timestamp))
                                      or assignment.gradingStatus = com.tikitaka.assignment.entity.GradingStatus.DRAFT
                                  )
                              )
                          )
                        order by assignment.dueAt asc, assignment.id asc
                        """, DashboardAssignmentItem.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
