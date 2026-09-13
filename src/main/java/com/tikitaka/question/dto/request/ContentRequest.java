package com.tikitaka.question.dto.request;
import jakarta.validation.constraints.NotBlank;
public record ContentRequest(@NotBlank String content) {}
