package com.tikitaka.search.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.search.dto.response.SearchResponse;
import com.tikitaka.search.service.SearchService;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.AuthenticatedUser;
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

    @Operation(summary = "SCH-001 통합 검색", description = "APPROVED 멤버로 참여 중인 Space의 강의자료, 공지사항, 질문을 통합 검색합니다. 입력 중 실시간 검색에도 사용하며 최근 검색어는 저장하지 않습니다.")
    @GetMapping
    public SearchResponse search(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "검색어", required = true, example = "스케줄링")
            @RequestParam String keyword) {
        return searchService.search(authenticatedUser.userId(), keyword);
    }
}
