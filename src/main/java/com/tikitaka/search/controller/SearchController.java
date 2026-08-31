package com.tikitaka.search.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.search.dto.SearchResponse;
import com.tikitaka.search.service.SearchService;

import com.tikitaka.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Search", description = "통합 검색 및 검색 기록 API")
@RestController
@RequestMapping("/api/v1/search")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "SCH-001 통합 검색", description = "APPROVED 멤버로 참여 중인 Space의 강의자료, 공지사항, 질문을 통합 검색하고 최근 검색어를 갱신합니다.")
    @GetMapping
    public SearchResponse search(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "검색어", required = true, example = "스케줄링")
            @RequestParam String keyword) {
        return searchService.search(userId, keyword);
    }
}
