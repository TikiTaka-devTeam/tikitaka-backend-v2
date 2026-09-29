package com.tikitaka.search.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.search.dto.response.RecentItemsResponse;
import com.tikitaka.search.dto.response.RecentSearchResponse;
import com.tikitaka.search.dto.response.SearchMessageResponse;
import com.tikitaka.search.service.SearchHistoryService;

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
public class SearchHistoryController {
    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    @Operation(summary = "SCH-002 최근 검색어 조회", description = "본인의 최근 검색어를 최신 검색순으로 최대 10개 조회합니다.")
    @GetMapping("/recent")
    public List<RecentSearchResponse> getRecentSearches(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return searchHistoryService.getRecentSearches(authenticatedUser.userId());
    }

    @Operation(summary = "SCH-003 최근 검색어 개별 삭제", description = "검색 기록 ID에 해당하는 본인의 최근 검색어를 삭제합니다.")
    @DeleteMapping("/recent/{searchId}")
    public SearchMessageResponse deleteRecentSearch(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "삭제할 최근 검색 기록 ID", required = true)
            @PathVariable UUID searchId) {
        searchHistoryService.deleteRecentSearch(authenticatedUser.userId(), searchId);
        return new SearchMessageResponse("검색 기록이 삭제되었습니다.");
    }

    @Operation(summary = "SCH-004 최근 검색어 전체 삭제", description = "본인의 최근 검색 기록을 모두 삭제합니다.")
    @DeleteMapping("/recent")
    public SearchMessageResponse deleteAllRecentSearches(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        searchHistoryService.deleteAllRecentSearches(authenticatedUser.userId());
        return new SearchMessageResponse("검색 기록이 모두 삭제되었습니다.");
    }

    @Operation(summary = "SCH-005 최근 조회 항목 조회", description = "최근 열어본 강의자료와 질문을 최근 조회순으로 각각 최대 3개 조회합니다.")
    @GetMapping("/recent-items")
    public RecentItemsResponse getRecentItems(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return searchHistoryService.getRecentItems(authenticatedUser.userId());
    }
}
