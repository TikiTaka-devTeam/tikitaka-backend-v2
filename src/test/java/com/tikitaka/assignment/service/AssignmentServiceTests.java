package com.tikitaka.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tikitaka.assignment.dto.response.AssignmentSubmissionListItemResponse;
import com.tikitaka.assignment.entity.Assignment;
import com.tikitaka.assignment.entity.AssignmentSubmission;
import com.tikitaka.assignment.entity.GradingStatus;
import com.tikitaka.assignment.entity.SubmissionStatus;
import com.tikitaka.assignment.repository.AssignmentGradeRepository;
import com.tikitaka.assignment.repository.AssignmentRepository;
import com.tikitaka.assignment.repository.AssignmentSubmissionRepository;
import com.tikitaka.assignment.repository.SubmissionFileRepository;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTests {

    @Mock AssignmentRepository assignmentRepository;
    @Mock AssignmentSubmissionRepository submissionRepository;
    @Mock AssignmentGradeRepository gradeRepository;
    @Mock SubmissionFileRepository fileRepository;
    @Mock SpaceMemberRepository memberRepository;
    @InjectMocks AssignmentService service;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();
    private User professor;

    @BeforeEach
    void setUp() {
        Assignment assignment = mock(Assignment.class);
        Space space = mock(Space.class);
        professor = mock(User.class);
        UUID professorId = UUID.randomUUID();
        SpaceMember actor = mock(SpaceMember.class);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignment.getSpace()).thenReturn(space);
        when(space.getId()).thenReturn(spaceId);
        when(professor.getId()).thenReturn(professorId);
        when(memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                spaceId, professorId, SpaceMemberStatus.APPROVED)).thenReturn(Optional.of(actor));
        when(actor.getRole()).thenReturn(SpaceMemberRole.PROFESSOR);
        when(assignment.getGradingStatus()).thenReturn(GradingStatus.DRAFT);
    }

    @Test
    void listsSubmittedStudentWithoutStudentNumberAfterNumberedStudents() {
        SpaceMember unnumbered = student("송현석", null);
        SpaceMember later = student("김민수", "20260002");
        SpaceMember earlier = student("임정민", "20260001");
        when(memberRepository.findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                spaceId, SpaceMemberStatus.APPROVED))
                .thenReturn(List.of(unnumbered, later, earlier));

        AssignmentSubmission submission = mock(AssignmentSubmission.class);
        Instant submittedAt = Instant.parse("2026-10-07T04:19:53Z");
        User submittedStudent = unnumbered.getUser();
        when(submission.getStudent()).thenReturn(submittedStudent);
        when(submission.getStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(submission.getSubmittedAt()).thenReturn(submittedAt);
        when(submissionRepository.findAllByAssignmentId(assignmentId)).thenReturn(List.of(submission));

        var response = service.getSubmissions(assignmentId, professor);

        assertThat(response.submissions()).extracting(AssignmentSubmissionListItemResponse::name)
                .containsExactly("임정민", "김민수", "송현석");
        var item = response.submissions().get(2);
        assertThat(item.studentNumber()).isNull();
        assertThat(item.status()).isEqualTo("SUBMITTED");
        assertThat(item.submittedAt()).isEqualTo(submittedAt);
    }

    @Test
    void sortsStudentsByNameWhenAllStudentNumbersAreMissing() {
        List<SpaceMember> students = List.of(
                student("송현석", null), student("강대운", null), student("김영환", null));
        when(memberRepository.findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                spaceId, SpaceMemberStatus.APPROVED))
                .thenReturn(students);

        var response = service.getSubmissions(assignmentId, professor);

        assertThat(response.submissions()).extracting(AssignmentSubmissionListItemResponse::name)
                .containsExactly("강대운", "김영환", "송현석");
        assertThat(response.submissions()).allSatisfy(item -> {
            assertThat(item.studentNumber()).isNull();
            assertThat(item.status()).isEqualTo("NOT_SUBMITTED");
        });
    }

    private SpaceMember student(String name, String number) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getName()).thenReturn(name);
        when(user.getMemberIdNumber()).thenReturn(number);
        SpaceMember member = mock(SpaceMember.class);
        when(member.getRole()).thenReturn(SpaceMemberRole.STUDENT);
        when(member.getUser()).thenReturn(user);
        return member;
    }
}
