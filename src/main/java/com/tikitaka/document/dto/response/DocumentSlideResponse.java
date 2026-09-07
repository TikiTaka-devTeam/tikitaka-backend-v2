package com.tikitaka.document.dto.response;
import java.util.UUID;
import com.tikitaka.document.entity.SlideStatus;
public record DocumentSlideResponse(UUID slideId, Integer pageNumber, SlideStatus status, String thumbnailUrl) {}
