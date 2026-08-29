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
import com.tikitaka.space.dto.response.SpaceStatusResponse;
import com.tikitaka.space.dto.response.SpaceUpdateResponse;
import com.tikitaka.space.entity.Schedule;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceColorKey;
import com.tikitaka.space.entity.SpaceMember;
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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String SPACE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SPACE_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public SpaceCreateResponse createSpace(User currentUser, SpaceCreateRequest request) {
        requireProfessorAccount(currentUser);
        validateSchedules(request.schedules());

        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        Instant now = nowKst.toInstant();
        int year = nowKst.getYear();
        String semester = resolveSemester(nowKst);
        String spaceCode = generateUniqueSpaceCode();

        Space space = Space.create(
                currentUser,
                request.spaceName().trim(),
                year,
                semester,
                normalizeNullable(request.classroom()),
                spaceCode);
        spaceRepository.save(space);

        SpaceColorKey colorKey = nextColorKey(currentUser.getId());
        SpaceMember professorMember = SpaceMember.professor(space, currentUser, colorKey, now);
        spaceMemberRepository.save(professorMember);

        List<Schedule> schedules = request.schedules().stream()
                .map(item -> Schedule.create(space, item.day(), item.startTime(), item.endTime()))
                .toList();
        scheduleRepository.saveAll(schedules);

        return new SpaceCreateResponse(
                space.getId(),
                space.getSpaceName(),
                space.getYear(),
                space.getSemester(),
                space.getClassroom(),
                schedules.stream().map(ScheduleResponse::from).toList(),
                colorKey,
                space.getSpaceCode(),
                "ACTIVE");
    }

    public List<SpaceListResponse> getSpaces(User currentUser, String status) {
        boolean archived = "ARCHIVED".equalsIgnoreCase(status);

        return spaceMemberRepository.findAllByUserIdAndStatus(currentUser.getId(), SpaceMemberStatus.APPROVED)
                .stream()
                .filter(member -> member.getSpace().isActiveStatus() != archived)
                .sorted(Comparator.comparing((SpaceMember m) -> m.getSpace().getCreatedAt()).reversed())
                .map(member -> toSpaceListResponse(member, archived ? "ARCHIVED" : "ACTIVE"))
                .toList();
    }

    public List<PendingSpaceResponse> getPendingSpaces(User currentUser) {
        requireStudentAccount(currentUser);

        return spaceMemberRepository.findAllByUserIdAndStatus(currentUser.getId(), SpaceMemberStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(SpaceMember::getRequestedAt).reversed())
                .map(member -> {
                    Space space = member.getSpace();
                    List<ScheduleResponse> schedules = scheduleRepository.findAllBySpaceId(space.getId()).stream()
                            .sorted(scheduleComparator())
                            .map(ScheduleResponse::from)
                            .toList();
                    return new PendingSpaceResponse(
                            member.getId(),
                            space.getId(),
                            space.getSpaceName(),
                            space.getProfessor().getName(),
                            space.getClassroom(),
                            schedules,
                            member.getColorKey(),
                            member.getStatus(),
                            member.getRequestedAt());
                })
                .toList();
    }

    @Transactional
    public SpaceJoinResponse joinSpace(User currentUser, SpaceJoinRequest request) {
        requireStudentAccount(currentUser);

        Space space = spaceRepository.findBySpaceCode(request.spaceCode().toUpperCase())
                .orElseThrow(() -> new BusinessException(SpaceErrorCode.SPACE_CODE_NOT_FOUND));

        if (!space.isActiveStatus()) {
            throw new BusinessException(SpaceErrorCode.SPACE_CODE_NOT_FOUND);
        }

        spaceMemberRepository.findBySpaceIdAndUserIdAndStatus(
                        space.getId(), currentUser.getId(), SpaceMemberStatus.APPROVED)
                .ifPresent(member -> {
                    throw new BusinessException(SpaceErrorCode.ALREADY_JOINED);
                });

        spaceMemberRepository.findBySpaceIdAndUserIdAndStatus(
                        space.getId(), currentUser.getId(), SpaceMemberStatus.PENDING)
                .ifPresent(member -> {
                    throw new BusinessException(SpaceErrorCode.JOIN_REQUEST_ALREADY_PENDING);
                });

        Instant now = Instant.now();
        SpaceColorKey colorKey = nextColorKey(currentUser.getId());
        SpaceMember member = SpaceMember.student(space, currentUser, colorKey, space.isAutoApprove(), now);
        spaceMemberRepository.save(member);

        return new SpaceJoinResponse(
                member.getId(),
                space.getId(),
                member.getStatus(),
                member.getStatus() == SpaceMemberStatus.APPROVED ? member.getApprovedAt() : null);
    }

    @Transactional
    public SpaceUpdateResponse updateSpace(User currentUser, UUID spaceId, SpaceUpdateRequest request) {
        Space space = getSpace(spaceId);
        requireSpaceProfessor(currentUser, space);

        if (request.schedules() != null) {
            validateSchedules(request.schedules());
        }

        if (request.spaceName() != null) {
            String updatedName = request.spaceName().trim();
            if (updatedName.isEmpty()) {
                throw new BusinessException(SpaceErrorCode.INVALID_SPACE_NAME);
            }
            space.updateSpaceName(updatedName);
        }
        if (request.classroom() != null) {
            space.updateClassroom(normalizeNullable(request.classroom()));
        }

        if (request.schedules() != null) {
            scheduleRepository.deleteAllBySpaceId(spaceId);
            scheduleRepository.flush();
            List<Schedule> newSchedules = request.schedules().stream()
                    .map(item -> Schedule.create(space, item.day(), item.startTime(), item.endTime()))
                    .toList();
            scheduleRepository.saveAll(newSchedules);
        }

        List<ScheduleResponse> schedules = scheduleRepository.findAllBySpaceId(spaceId).stream()
                .sorted(scheduleComparator())
                .map(ScheduleResponse::from)
                .toList();

        return new SpaceUpdateResponse(
                space.getId(),
                space.getSpaceName(),
                space.getClassroom(),
                schedules,
                Instant.now());
    }

    @Transactional
    public SpaceStatusResponse archiveSpace(User currentUser, UUID spaceId) {
        Space space = getSpace(spaceId);
        requireSpaceProfessor(currentUser, space);
        if (!space.isActiveStatus()) {
            throw new BusinessException(SpaceErrorCode.SPACE_ALREADY_ARCHIVED);
        }
        space.archive(Instant.now());
        return new SpaceStatusResponse(space.getId(), "ARCHIVED");
    }

    @Transactional
    public SpaceStatusResponse restoreSpace(User currentUser, UUID spaceId) {
        Space space = getSpace(spaceId);
        requireSpaceProfessor(currentUser, space);
        if (space.isActiveStatus()) {
            throw new BusinessException(SpaceErrorCode.SPACE_ALREADY_ACTIVE);
        }
        space.restore();
        return new SpaceStatusResponse(space.getId(), "ACTIVE");
    }

    @Transactional
    public void deleteSpace(User currentUser, UUID spaceId) {
        Space space = getSpace(spaceId);
        requireSpaceProfessor(currentUser, space);
        spaceRepository.delete(space);
    }

    private Space getSpace(UUID spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(SpaceErrorCode.SPACE_NOT_FOUND));
    }

    private void requireProfessorAccount(User user) {
        if (user.getAccountType() != AccountType.PROFESSOR) {
            throw new BusinessException(SpaceErrorCode.PROFESSOR_ONLY);
        }
    }

    private void requireStudentAccount(User user) {
        if (user.getAccountType() != AccountType.STUDENT) {
            throw new BusinessException(SpaceErrorCode.STUDENT_ONLY);
        }
    }

    private void requireSpaceProfessor(User user, Space space) {
        if (!space.getProfessor().getId().equals(user.getId())) {
            throw new BusinessException(SpaceErrorCode.SPACE_ACCESS_DENIED);
        }
    }

    private void validateSchedules(List<ScheduleRequest> schedules) {
        for (ScheduleRequest schedule : schedules) {
            if (!schedule.startTime().isBefore(schedule.endTime())) {
                throw new BusinessException(SpaceErrorCode.INVALID_SCHEDULE);
            }
        }
    }

    private SpaceListResponse toSpaceListResponse(SpaceMember member, String status) {
        Space space = member.getSpace();
        return new SpaceListResponse(
                space.getId(),
                space.getSpaceName(),
                space.getYear(),
                space.getSemester(),
                member.getColorKey(),
                status);
    }

    private SpaceColorKey nextColorKey(UUID userId) {
        SpaceColorKey[] values = SpaceColorKey.values();
        long currentCount = spaceMemberRepository.countByUserId(userId);
        return values[(int) (currentCount % values.length)];
    }

    private String generateUniqueSpaceCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder builder = new StringBuilder(SPACE_CODE_LENGTH);
            for (int i = 0; i < SPACE_CODE_LENGTH; i++) {
                builder.append(SPACE_CODE_CHARS.charAt(RANDOM.nextInt(SPACE_CODE_CHARS.length())));
            }
            String code = builder.toString();
            if (!spaceRepository.existsBySpaceCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Space code generation failed");
    }

    private String resolveSemester(ZonedDateTime now) {
        int month = now.getMonthValue();

        if (month >= 3 && month <= 6) {
            return "1";
        }
        if (month >= 9 && month <= 12) {
            return "2";
        }

        throw new BusinessException(SpaceErrorCode.SPACE_CREATION_OUTSIDE_SEMESTER);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Comparator<Schedule> scheduleComparator() {
        return Comparator.comparing(Schedule::getDay)
                .thenComparing(Schedule::getStartTime);
    }
}
