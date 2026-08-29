package com.tikitaka.space.dto.response;

public record JoinRequestActionResponse(
        Integer approvedCount,
        Integer deniedCount
) {
    public static JoinRequestActionResponse approved(int count) {
        return new JoinRequestActionResponse(count, null);
    }

    public static JoinRequestActionResponse denied(int count) {
        return new JoinRequestActionResponse(null, count);
    }
}
