package com.tikitaka.space.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMemberRole;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RolePermissionsRequest(

        @JsonProperty("role")
        @NotNull
        SpaceMemberRole role,

        @JsonProperty("permissions")
        @NotNull
        List<PermissionType> permissions

) {
}