package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMemberRole;

import java.util.List;
import java.util.UUID;

public record RolePermissionsResponse(

        @JsonProperty("member_id")
        UUID memberId,

        @JsonProperty("role")
        SpaceMemberRole role,

        @JsonProperty("permissions")
        List<PermissionType> permissions

) {
}