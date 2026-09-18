package com.tikitaka.question.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.question.ai.AnswerAiClient;
import com.tikitaka.question.ai.dto.AnswerTranscribeResponse;
import com.tikitaka.question.dto.response.VoiceAnswerResponse;
import com.tikitaka.question.entity.Answer;
import com.tikitaka.question.entity.AnswerType;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.exception.QuestionErrorCode;
import com.tikitaka.question.repository.AnswerRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceAnswerService {

    private static final long MAX_AUDIO_SIZE =
            20L * 1024L * 1024L;

    private static final Set<String>
            ALLOWED_EXTENSIONS =
            Set.of(
                    "mp3",
                    "wav",
                    "m4a",
                    "webm"
            );

    private static final Set<String>
            ALLOWED_CONTENT_TYPES =
            Set.of(
                    "audio/mpeg",
                    "audio/mp3",
                    "audio/wav",
                    "audio/x-wav",
                    "audio/mp4",
                    "audio/x-m4a",
                    "audio/webm",
                    "video/webm"
            );

    private final QuestionRepository
            questionRepository;

    private final AnswerRepository
            answerRepository;

    private final SpaceMemberRepository
            spaceMemberRepository;

    private final SpaceMemberPermissionRepository
            permissionRepository;

    private final AnswerAiClient
            answerAiClient;

    private final DocumentStorage
            storage;

    private final TransactionTemplate
            transactionTemplate;

    @Transactional(
            propagation = Propagation.NOT_SUPPORTED
    )
    public VoiceAnswerResponse createVoiceAnswer(
            UUID questionId,
            MultipartFile file,
            User user
    ) {
        validateAudioFile(
                file
        );

        validateAccess(
                questionId,
                user.getId()
        );

        byte[] audioBytes =
                readBytes(
                        file
                );

        String filename =
                normalizeFilename(
                        file.getOriginalFilename()
                );

        String contentType =
                normalizeContentType(
                        file.getContentType()
                );

        String audioKey =
                createAudioKey(
                        questionId,
                        filename
                );

        boolean uploaded = false;
        boolean saved = false;

        try {
            storage.put(
                    audioKey,
                    audioBytes,
                    contentType
            );

            uploaded = true;

            AnswerTranscribeResponse
                    aiResponse =
                    answerAiClient
                            .transcribe(
                                    audioBytes,
                                    filename,
                                    contentType
                            );

            String audioUrl =
                    storage
                            .presignedGetUrl(
                                    audioKey
                            );

            VoiceAnswerSnapshot snapshot =
                    transactionTemplate
                            .execute(
                                    status ->
                                            saveVoiceAnswer(
                                                    questionId,
                                                    user.getId(),
                                                    aiResponse,
                                                    audioKey
                                            )
                            );

            if (snapshot == null) {

                throw new IllegalStateException(
                        "Voice answer transaction returned null."
                );
            }

            saved = true;

            return new VoiceAnswerResponse(
                    snapshot.answerId(),
                    snapshot.questionId(),
                    AnswerType.VOICE,
                    snapshot.content(),
                    snapshot.transcript(),
                    audioUrl,
                    snapshot.createdAt()
            );

        } catch (RuntimeException exception) {

            if (uploaded && !saved) {

                cleanupAudio(
                        audioKey,
                        exception
                );
            }

            throw exception;
        }
    }

    private void validateAccess(
            UUID questionId,
            UUID userId
    ) {
        transactionTemplate
                .executeWithoutResult(
                        status -> {

                            Question question =
                                    getQuestion(
                                            questionId
                                    );

                            requireManager(
                                    question
                                            .getDocument()
                                            .getSpace()
                                            .getId(),
                                    userId
                            );
                        }
                );
    }

    private VoiceAnswerSnapshot
    saveVoiceAnswer(
            UUID questionId,
            UUID userId,
            AnswerTranscribeResponse aiResponse,
            String audioKey
    ) {
        Question question =
                getQuestion(
                        questionId
                );

        SpaceMember member =
                requireManager(
                        question
                                .getDocument()
                                .getSpace()
                                .getId(),
                        userId
                );

        Answer answer =
                answerRepository
                        .save(
                                Answer.createVoice(
                                        question,
                                        member.getUser(),
                                        aiResponse
                                                .normalizedContent()
                                                .trim(),
                                        audioKey,
                                        aiResponse
                                                .transcript()
                                                .trim()
                                )
                        );

        question.markAnswered();

        return new VoiceAnswerSnapshot(
                answer.getId(),
                question.getId(),
                answer.getContent(),
                answer.getTranscript(),
                answer.getCreatedAt()
        );
    }

    private Question getQuestion(
            UUID questionId
    ) {
        return questionRepository
                .findById(
                        questionId
                )
                .filter(
                        question ->
                                !question
                                        .isDeleted()
                )
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        QuestionErrorCode
                                                .QUESTION_NOT_FOUND
                                )
                );
    }

    private SpaceMember requireManager(
            UUID spaceId,
            UUID userId
    ) {
        SpaceMember member =
                spaceMemberRepository
                        .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                                spaceId,
                                userId,
                                SpaceMemberStatus
                                        .APPROVED
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                QuestionErrorCode
                                                        .SPACE_MEMBER_REQUIRED
                                        )
                        );

        if (member.getRole()
                == SpaceMemberRole.PROFESSOR) {

            return member;
        }

        if (member.getRole()
                == SpaceMemberRole.ASSISTANT
                && permissionRepository
                .existsBySpaceMemberIdAndPermission(
                        member.getId(),
                        PermissionType
                                .QUESTION_MANAGE
                )) {

            return member;
        }

        throw new BusinessException(
                QuestionErrorCode
                        .QUESTION_MANAGE_FORBIDDEN
        );
    }

    private void validateAudioFile(
            MultipartFile file
    ) {
        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "Audio file must not be empty."
            );
        }

        if (file.getSize()
                > MAX_AUDIO_SIZE) {

            throw new IllegalArgumentException(
                    "Audio file size must not exceed 20MB."
            );
        }

        String filename =
                normalizeFilename(
                        file.getOriginalFilename()
                );

        String extension =
                extension(
                        filename
                );

        if (!ALLOWED_EXTENSIONS
                .contains(
                        extension
                )) {

            throw new IllegalArgumentException(
                    "Unsupported audio file extension: "
                            + extension
            );
        }

        String contentType =
                normalizeContentType(
                        file.getContentType()
                );

        if (!ALLOWED_CONTENT_TYPES
                .contains(
                        contentType
                )) {

            throw new IllegalArgumentException(
                    "Unsupported audio content type: "
                            + contentType
            );
        }
    }

    private byte[] readBytes(
            MultipartFile file
    ) {
        try {
            return file.getBytes();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read audio file.",
                    exception
            );
        }
    }

    private String createAudioKey(
            UUID questionId,
            String filename
    ) {
        String extension =
                extension(
                        filename
                );

        return "answers/audio/"
                + questionId
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;
    }

    private String normalizeFilename(
            String filename
    ) {
        if (filename == null
                || filename.isBlank()) {

            throw new IllegalArgumentException(
                    "Audio filename must not be empty."
            );
        }

        String normalized =
                filename
                        .replace(
                                "\\",
                                "/"
                        );

        int slash =
                normalized
                        .lastIndexOf(
                                '/'
                        );

        if (slash >= 0) {

            normalized =
                    normalized
                            .substring(
                                    slash + 1
                            );
        }

        if (normalized.isBlank()) {

            throw new IllegalArgumentException(
                    "Audio filename must not be empty."
            );
        }

        return normalized;
    }

    private String normalizeContentType(
            String contentType
    ) {
        if (contentType == null
                || contentType.isBlank()) {

            throw new IllegalArgumentException(
                    "Audio content type must not be empty."
            );
        }

        return contentType
                .toLowerCase(
                        Locale.ROOT
                )
                .trim();
    }

    private String extension(
            String filename
    ) {
        int dot =
                filename
                        .lastIndexOf(
                                '.'
                        );

        if (dot < 0
                || dot
                == filename.length() - 1) {

            throw new IllegalArgumentException(
                    "Audio file extension is required."
            );
        }

        return filename
                .substring(
                        dot + 1
                )
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private void cleanupAudio(
            String audioKey,
            RuntimeException original
    ) {
        try {
            storage.delete(
                    audioKey
            );

        } catch (RuntimeException
                 cleanupFailure) {

            original
                    .addSuppressed(
                            cleanupFailure
                    );
        }
    }

    private record VoiceAnswerSnapshot(
            UUID answerId,
            UUID questionId,
            String content,
            String transcript,
            java.time.Instant createdAt
    ) {
    }
}