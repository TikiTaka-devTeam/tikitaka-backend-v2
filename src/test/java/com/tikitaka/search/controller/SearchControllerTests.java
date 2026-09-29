package com.tikitaka.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.global.security.AuthenticatedUser;
import com.tikitaka.search.dto.response.SearchResponse;
import com.tikitaka.search.service.SearchHistoryService;
import com.tikitaka.search.service.SearchService;

class SearchControllerTests {

    @Test
    void passesAuthenticatedUserIdToIntegratedSearch() {
        SearchService searchService = mock(SearchService.class);
        SearchController controller = new SearchController(searchService);
        UUID userId = UUID.randomUUID();
        SearchResponse expected = new SearchResponse(List.of(), List.of(), List.of());
        when(searchService.search(userId, "scheduling")).thenReturn(expected);

        SearchResponse result = controller.search(
                new AuthenticatedUser(userId),
                "scheduling"
        );

        assertThat(result).isSameAs(expected);
        verify(searchService).search(userId, "scheduling");
    }

    @Test
    void passesAuthenticatedUserIdToEverySearchHistoryOperation() {
        SearchHistoryService searchHistoryService = mock(SearchHistoryService.class);
        SearchHistoryController controller = new SearchHistoryController(searchHistoryService);
        UUID userId = UUID.randomUUID();
        UUID searchId = UUID.randomUUID();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId);

        controller.getRecentSearches(authenticatedUser);
        controller.deleteRecentSearch(authenticatedUser, searchId);
        controller.deleteAllRecentSearches(authenticatedUser);
        controller.getRecentItems(authenticatedUser);

        verify(searchHistoryService).getRecentSearches(userId);
        verify(searchHistoryService).deleteRecentSearch(userId, searchId);
        verify(searchHistoryService).deleteAllRecentSearches(userId);
        verify(searchHistoryService).getRecentItems(userId);
    }
}
