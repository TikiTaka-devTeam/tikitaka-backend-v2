package com.tikitaka.space.dto.request;

import java.util.List;

import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMemberRole;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RolePermissionsRequest(

        @NotNull
        @Schema(
                description = "변경할 역할",
                example = "ASSISTANT",
                allowableValues = {
                        "ASSISTANT",
                        "STUDENT"
                }
        )
        SpaceMemberRole role,

        @NotNull
        @Schema(
                description = "조교 세부 권한",
                example = "[\"MEMBER_MANAGE\", \"NOTICE_MANAGE\"]"
        )
        List<PermissionType> permissions
) {
}