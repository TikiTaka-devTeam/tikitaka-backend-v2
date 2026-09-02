package com.tikitaka.space.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.space.dto.request.ScheduleRequest;
import com.tikitaka.space.dto.request.SpaceCreateRequest;
import com.tikitaka.space.dto.request.SpaceJoinRequest;
import com.tikitaka.space.dto.request.SpaceUpdateRequest;
import com.tikitaka.space.dto.response.PendingSpaceResponse;
import com.tikitaka.space.dto.response.ScheduleResponse;
import com.tikitaka.space.dto.response.SpaceCreateResponse;
import com.tikitaka.space.dto.response.SpaceJoinResponse;
import com.tikitaka.space.dto.response.SpaceListResponse;
import com.tikitaka.space.dto.response.SpaceQueryResponse;
import com.tikitaka.space.dto.response.SpaceStatusResponse;
import com.tikitaka.space.dto.response.SpaceUpdateResponse;
import com.tikitaka.space.entity.Schedule;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceColorKey;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.exception.SpaceErrorCode;
import com.tikitaka.space.repository.ScheduleRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.space.repository.SpaceRepository;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceService {

    private static final ZoneId KST =
            ZoneId.of("Asia/Seoul");

    private static final String SPACE_CODE_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int SPACE_CODE_LENGTH = 8;

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public SpaceCreateResponse createSpace(
            User currentUser,
            SpaceCreateRequest request
    ) {
        requireProfessorAccount(currentUser);

        /*
         * 요청 내부 시간 검증
         */
        validateSchedules(
                request.schedules()
        );

        /*
         * 현재 교수가 이미 가지고 있는
         * 다른 활성 Space 수업과 시간 충돌 검증
         */
        validateProfessorScheduleConflict(
                currentUser.getId(),
                request.schedules(),
                null
        );

        ZonedDateTime nowKst =
                ZonedDateTime.now(KST);

        Instant now =
                nowKst.toInstant();

        int year =
                nowKst.getYear();

        String semester =
                resolveSemester(nowKst);

        String spaceCode =
                generateUniqueSpaceCode();

        Space space =
                Space.create(
                        currentUser,
                        request.spaceName().trim(),
                        year,
                        semester,
                        normalizeNullable(
                                request.classroom()
                        ),
                        spaceCode
                );

        spaceRepository.save(space);

        SpaceColorKey colorKey =
                nextColorKey(
                        currentUser.getId()
                );

        SpaceMember professorMember =
                SpaceMember.professor(
                        space,
                        currentUser,
                        colorKey,
                        now
                );

        spaceMemberRepository.save(
                professorMember
        );

        List<Schedule> schedules =
                request.schedules()
                        .stream()
                        .map(item ->
                                Schedule.create(
                                        space,
                                        item.day(),
                                        item.startTime(),
                                        item.endTime()
                                )
                        )
                        .toList();

        scheduleRepository.saveAll(
                schedules
        );

        return new SpaceCreateResponse(
                space.getId(),
                space.getSpaceName(),
                space.getYear(),
                space.getSemester(),
                space.getClassroom(),
                schedules.stream()
                        .map(ScheduleResponse::from)
                        .toList(),
                colorKey,
                space.getSpaceCode(),
                "ACTIVE"
        );
    }

    public SpaceQueryResponse getSpaces(
            User currentUser,
            String status
    ) {
        if (!"ACTIVE".equalsIgnoreCase(status)
                && !"ARCHIVED".equalsIgnoreCase(status)) {

            throw new BusinessException(
                    SpaceErrorCode.INVALID_SPACE_FILTER
            );
        }

        boolean archived =
                "ARCHIVED".equalsIgnoreCase(status);

        List<SpaceListResponse> spaces =
                getApprovedSpaces(
                        currentUser,
                        archived
                );

        List<PendingSpaceResponse> pendingSpaces =
                !archived
                        && currentUser.getAccountType()
                        == AccountType.STUDENT
                        ? getPendingSpaces(currentUser)
                        : List.of();

        return new SpaceQueryResponse(
                spaces,
                pendingSpaces
        );
    }

    private List<SpaceListResponse> getApprovedSpaces(
            User currentUser,
            boolean archived
    ) {
        String responseStatus =
                archived
                        ? "ARCHIVED"
                        : "ACTIVE";

        return spaceMemberRepository
                .findAllByUserIdAndStatusAndRemovedAtIsNull(
                        currentUser.getId(),
                        SpaceMemberStatus.APPROVED
                )
                .stream()
                .filter(member ->
                        member.getSpace()
                                .isActiveStatus() != archived
                )
                .sorted(
                        Comparator
                                .comparing(
                                        (SpaceMember member) ->
                                                member.getSpace()
                                                        .getCreatedAt()
                                )
                                .reversed()
                )
                .map(member ->
                        toSpaceListResponse(
                                member,
                                responseStatus
                        )
                )
                .toList();
    }

    private List<PendingSpaceResponse> getPendingSpaces(
            User currentUser
    ) {
        return spaceMemberRepository
                .findAllByUserIdAndStatusAndRemovedAtIsNull(
                        currentUser.getId(),
                        SpaceMemberStatus.PENDING
                )
                .stream()
                .filter(member ->
                        member.getSpace()
                                .isActiveStatus()
                )
                .sorted(
                        Comparator
                                .comparing(
                                        SpaceMember::getRequestedAt
                                )
                                .reversed()
                )
                .map(member -> {

                    Space space =
                            member.getSpace();

                    List<ScheduleResponse> schedules =
                            scheduleRepository
                                    .findAllBySpaceId(
                                            space.getId()
                                    )
                                    .stream()
                                    .sorted(
                                            scheduleComparator()
                                    )
                                    .map(
                                            ScheduleResponse::from
                                    )
                                    .toList();

                    return new PendingSpaceResponse(
                            member.getId(),
                            space.getId(),
                            space.getSpaceName(),
                            space.getProfessor().getName(),
                            space.getYear(),
                            space.getSemester(),
                            space.getClassroom(),
                            schedules,
                            member.getColorKey(),
                            member.getStatus(),
                            member.getRequestedAt()
                                    .atZone(KST)
                                    .toOffsetDateTime()
                    );
                })
                .toList();
    }

    @Transactional
    public SpaceJoinResponse joinSpace(
            User currentUser,
            SpaceJoinRequest request
    ) {
        requireStudentAccount(
                currentUser
        );

        Space space =
                spaceRepository
                        .findBySpaceCode(
                                request.spaceCode()
                                        .toUpperCase()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        SpaceErrorCode.SPACE_CODE_NOT_FOUND
                                )
                        );

        if (!space.isActiveStatus()) {
            throw new BusinessException(
                    SpaceErrorCode.SPACE_CODE_NOT_FOUND
            );
        }

        spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        space.getId(),
                        currentUser.getId(),
                        SpaceMemberStatus.APPROVED
                )
                .ifPresent(member -> {
                    throw new BusinessException(
                            SpaceErrorCode.ALREADY_JOINED
                    );
                });

        spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        space.getId(),
                        currentUser.getId(),
                        SpaceMemberStatus.PENDING
                )
                .ifPresent(member -> {
                    throw new BusinessException(
                            SpaceErrorCode
                                    .JOIN_REQUEST_ALREADY_PENDING
                    );
                });

        Instant now =
                Instant.now();

        SpaceColorKey colorKey =
                nextColorKey(
                        currentUser.getId()
                );

        SpaceMember member =
                spaceMemberRepository
                        .findFirstBySpaceIdAndUserIdAndRemovedAtIsNotNullOrderByRemovedAtDesc(
                                space.getId(),
                                currentUser.getId()
                        )
                        .map(removedMember -> {

                            removedMember.rejoin(
                                    colorKey,
                                    space.isAutoApprove(),
                                    now
                            );

                            return removedMember;
                        })
                        .orElseGet(() ->
                                SpaceMember.student(
                                        space,
                                        currentUser,
                                        colorKey,
                                        space.isAutoApprove(),
                                        now
                                )
                        );

        spaceMemberRepository.save(
                member
        );

        return new SpaceJoinResponse(
                member.getId(),
                space.getId(),
                member.getStatus(),
                member.getStatus()
                        == SpaceMemberStatus.APPROVED
                        ? member.getApprovedAt()
                        : null
        );
    }

    @Transactional
    public SpaceUpdateResponse updateSpace(
            User currentUser,
            UUID spaceId,
            SpaceUpdateRequest request
    ) {
        Space space =
                getSpace(
                        spaceId
                );

        requireSpaceProfessor(
                currentUser,
                space
        );

        if (!space.isActiveStatus()) {
            throw new BusinessException(
                    SpaceErrorCode
                            .ARCHIVED_SPACE_CANNOT_BE_MODIFIED
            );
        }

        if (request.schedules() != null) {

            /*
             * 새로 전달된 시간표 내부 충돌 검사
             */
            validateSchedules(
                    request.schedules()
            );

            /*
             * 다른 활성 Space와 충돌 검사
             *
             * 현재 수정 중인 Space의 기존 시간표는 제외
             */
            validateProfessorScheduleConflict(
                    currentUser.getId(),
                    request.schedules(),
                    spaceId
            );
        }

        if (request.spaceName() != null) {

            String updatedName =
                    request.spaceName()
                            .trim();

            if (updatedName.isEmpty()) {
                throw new BusinessException(
                        SpaceErrorCode.INVALID_SPACE_NAME
                );
            }

            space.updateSpaceName(
                    updatedName
            );
        }

        if (request.classroom() != null) {
            space.updateClassroom(
                    normalizeNullable(
                            request.classroom()
                    )
            );
        }

        if (request.schedules() != null) {

            scheduleRepository.deleteAllBySpaceId(
                    spaceId
            );

            scheduleRepository.flush();

            List<Schedule> newSchedules =
                    request.schedules()
                            .stream()
                            .map(item ->
                                    Schedule.create(
                                            space,
                                            item.day(),
                                            item.startTime(),
                                            item.endTime()
                                    )
                            )
                            .toList();

            scheduleRepository.saveAll(
                    newSchedules
            );
        }

        List<ScheduleResponse> schedules =
                scheduleRepository
                        .findAllBySpaceId(
                                spaceId
                        )
                        .stream()
                        .sorted(
                                scheduleComparator()
                        )
                        .map(
                                ScheduleResponse::from
                        )
                        .toList();

        return new SpaceUpdateResponse(
                space.getId(),
                space.getSpaceName(),
                space.getClassroom(),
                schedules,
                Instant.now()
        );
    }

    @Transactional
    public SpaceStatusResponse archiveSpace(
            User currentUser,
            UUID spaceId
    ) {
        Space space =
                getSpace(
                        spaceId
                );

        requireSpaceProfessor(
                currentUser,
                space
        );

        if (!space.isActiveStatus()) {
            throw new BusinessException(
                    SpaceErrorCode.SPACE_ALREADY_ARCHIVED
            );
        }

        space.archive(
                Instant.now()
        );

        return new SpaceStatusResponse(
                space.getId(),
                "ARCHIVED"
        );
    }

    @Transactional
    public SpaceStatusResponse restoreSpace(
            User currentUser,
            UUID spaceId
    ) {
        Space space =
                getSpace(
                        spaceId
                );

        requireSpaceProfessor(
                currentUser,
                space
        );

        if (space.isActiveStatus()) {
            throw new BusinessException(
                    SpaceErrorCode.SPACE_ALREADY_ACTIVE
            );
        }

        space.restore();

        return new SpaceStatusResponse(
                space.getId(),
                "ACTIVE"
        );
    }

    @Transactional
    public void deleteSpace(
            User currentUser,
            UUID spaceId
    ) {
        Space space =
                getSpace(
                        spaceId
                );

        requireSpaceProfessor(
                currentUser,
                space
        );

        if (space.isActiveStatus()) {
            throw new BusinessException(
                    SpaceErrorCode
                            .ACTIVE_SPACE_CANNOT_BE_DELETED
            );
        }

        spaceRepository.delete(
                space
        );
    }

    private Space getSpace(
            UUID spaceId
    ) {
        return spaceRepository
                .findById(
                        spaceId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                SpaceErrorCode.SPACE_NOT_FOUND
                        )
                );
    }

    private void requireProfessorAccount(
            User user
    ) {
        if (user.getAccountType()
                != AccountType.PROFESSOR) {

            throw new BusinessException(
                    SpaceErrorCode.PROFESSOR_ONLY
            );
        }
    }

    private void requireStudentAccount(
            User user
    ) {
        if (user.getAccountType()
                != AccountType.STUDENT) {

            throw new BusinessException(
                    SpaceErrorCode.STUDENT_ONLY
            );
        }
    }

    private void requireSpaceProfessor(
            User user,
            Space space
    ) {
        if (!space.getProfessor()
                .getId()
                .equals(user.getId())) {

            throw new BusinessException(
                    SpaceErrorCode.SPACE_ACCESS_DENIED
            );
        }
    }

    /**
     * 전달받은 시간표 자체 검증
     *
     * 1. 시작 시간 < 종료 시간
     * 2. 요청 내부에서 같은 요일 시간 중복 금지
     */
    private void validateSchedules(
            List<ScheduleRequest> schedules
    ) {
        if (schedules == null
                || schedules.isEmpty()) {
            return;
        }

        /*
         * 시작 시간 / 종료 시간 검증
         */
        for (ScheduleRequest schedule : schedules) {

            if (!schedule.startTime()
                    .isBefore(
                            schedule.endTime()
                    )) {

                throw new BusinessException(
                        SpaceErrorCode.INVALID_SCHEDULE
                );
            }
        }

        /*
         * 같은 요청 내부의 시간 중복 검사
         */
        for (int i = 0;
             i < schedules.size();
             i++) {

            ScheduleRequest current =
                    schedules.get(i);

            for (int j = i + 1;
                 j < schedules.size();
                 j++) {

                ScheduleRequest other =
                        schedules.get(j);

                /*
                 * 다른 요일이면 허용
                 */
                if (current.day()
                        != other.day()) {

                    continue;
                }

                boolean overlaps =
                        current.startTime()
                                .isBefore(
                                        other.endTime()
                                )
                        &&
                        other.startTime()
                                .isBefore(
                                        current.endTime()
                                );

                if (overlaps) {
                    throw new BusinessException(
                            SpaceErrorCode.DUPLICATE_SCHEDULE
                    );
                }
            }
        }
    }

    /**
     * 해당 교수가 이미 가지고 있는 활성 Space의 수업들과
     * 새로 등록하려는 수업의 시간 중복 검사
     */
    private void validateProfessorScheduleConflict(
            UUID professorId,
            List<ScheduleRequest> newSchedules,
            UUID excludeSpaceId
    ) {
        if (newSchedules == null
                || newSchedules.isEmpty()) {
            return;
        }

        List<Schedule> existingSchedules =
                scheduleRepository
                        .findAllBySpaceProfessorIdAndSpaceActiveStatusTrue(
                                professorId
                        );

        for (ScheduleRequest newSchedule : newSchedules) {

            for (Schedule existing : existingSchedules) {

                /*
                 * Space 수정 시
                 * 수정 대상 Space 자기 자신의 기존 일정은 제외
                 */
                if (excludeSpaceId != null
                        && existing.getSpace()
                                .getId()
                                .equals(excludeSpaceId)) {

                    continue;
                }

                /*
                 * 요일이 다르면 충돌 아님
                 */
                if (newSchedule.day()
                        != existing.getDay()) {

                    continue;
                }

                /*
                 * 시간 중복 조건
                 *
                 * 새 시작 < 기존 종료
                 * &&
                 * 기존 시작 < 새 종료
                 *
                 * 예:
                 *
                 * 기존 10:30 ~ 12:00
                 * 신규 11:00 ~ 13:00
                 * -> 중복
                 *
                 * 기존 10:30 ~ 12:00
                 * 신규 12:00 ~ 14:00
                 * -> 허용
                 */
                boolean overlaps =
                        newSchedule.startTime()
                                .isBefore(
                                        existing.getEndTime()
                                )
                        &&
                        existing.getStartTime()
                                .isBefore(
                                        newSchedule.endTime()
                                );

                if (overlaps) {
                    throw new BusinessException(
                            SpaceErrorCode.DUPLICATE_SCHEDULE
                    );
                }
            }
        }
    }

    private SpaceListResponse toSpaceListResponse(
            SpaceMember member,
            String status
    ) {
        Space space =
                member.getSpace();

        String spaceCode =
                member.getRole()
                        == SpaceMemberRole.PROFESSOR
                        ? space.getSpaceCode()
                        : null;

        return new SpaceListResponse(
                space.getId(),
                space.getSpaceName(),
                space.getYear(),
                space.getSemester(),
                space.getClassroom(),
                getScheduleResponses(space.getId()),
                member.getColorKey(),
                spaceCode,
                status
        );
    }

    private List<ScheduleResponse> getScheduleResponses(UUID spaceId) {
        return scheduleRepository
                .findAllBySpaceId(spaceId)
                .stream()
                .sorted(scheduleComparator())
                .map(ScheduleResponse::from)
                .toList();
    }

    private SpaceColorKey nextColorKey(
            UUID userId
    ) {
        SpaceColorKey[] values =
                SpaceColorKey.values();

        long currentCount =
                spaceMemberRepository
                        .countByUserIdAndRemovedAtIsNull(
                                userId
                        );

        return values[
                (int) (
                        currentCount
                                % values.length
                )
                ];
    }

    private String generateUniqueSpaceCode() {

        for (int attempt = 0;
             attempt < 100;
             attempt++) {

            StringBuilder builder =
                    new StringBuilder(
                            SPACE_CODE_LENGTH
                    );

            for (int i = 0;
                 i < SPACE_CODE_LENGTH;
                 i++) {

                builder.append(
                        SPACE_CODE_CHARS.charAt(
                                RANDOM.nextInt(
                                        SPACE_CODE_CHARS
                                                .length()
                                )
                        )
                );
            }

            String code =
                    builder.toString();

            if (!spaceRepository
                    .existsBySpaceCode(
                            code
                    )) {

                return code;
            }
        }

        throw new IllegalStateException(
                "Space code generation failed"
        );
    }

    private String resolveSemester(
            ZonedDateTime now
    ) {
        int month =
                now.getMonthValue();

        if (month >= 1
                && month <= 6) {

            return "1";
        }

        return "2";
    }

    private String normalizeNullable(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private Comparator<Schedule> scheduleComparator() {
        return Comparator
                .comparing(
                        Schedule::getDay
                )
                .thenComparing(
                        Schedule::getStartTime
                );
    }
}
