package com.tikitaka.note.dto.response;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.note.entity.Fixer;
public record FixerResponse(UUID fixerId, Double xRatio, Double yRatio, String content,
        @JsonProperty("is_checked") boolean checked) {
    public static FixerResponse of(Fixer f) {
        return new FixerResponse(f.getId(), f.getXRatio(), f.getYRatio(), f.getContent(), f.isChecked());
    }
    public record Created(UUID fixerId, UUID slideId, Double xRatio, Double yRatio, String content,
            @JsonProperty("is_checked") boolean checked) {
        public static Created of(Fixer f) {
            return new Created(f.getId(), f.getSlide().getId(), f.getXRatio(), f.getYRatio(), f.getContent(), f.isChecked());
        }
    }
    public record Checked(UUID fixerId, @JsonProperty("is_checked") boolean checked) {}
}