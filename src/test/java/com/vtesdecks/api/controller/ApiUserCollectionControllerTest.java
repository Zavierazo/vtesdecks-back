package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCollectionService;
import com.vtesdecks.api.service.ApiCollectionStatsService;
import com.vtesdecks.api.service.ApiDeckService;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.model.api.ApiCardSearchRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ApiUserCollectionControllerTest {

    @Mock
    private ApiCollectionService collectionService;
    @Mock
    private ApiCollectionStatsService apiCollectionStatsService;
    @Mock
    private ApiDeckService apiDeckService;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private LibraryCache libraryCache;
    @Mock
    private HttpServletRequest httpServletRequest;
    @InjectMocks
    private ApiUserCollectionController controller;

    @Test
    public void shouldMapSearchRequestOntoExistingFilterHandling() throws Exception {
        ApiCardSearchRequest search = new ApiCardSearchRequest();
        search.setPage(1);
        search.setSize(50);
        search.setSortBy("cardName");
        search.setSortDirection("desc");
        Map<String, Object> filters = new HashMap<>();
        filters.put("binderId", 3);
        filters.put("cardName", "gov");
        filters.put("groupBy", "cardId");
        filters.put("unknownFutureKey", "ignored");
        search.setFilters(filters);
        search.setCardIds(List.of(200011, 200013));

        controller.searchCards(httpServletRequest, search);

        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(collectionService).searchCards(eq(1), eq(50), eq("cardId"), eq("cardName"), eq("desc"),
                filtersCaptor.capture(), eq(List.of(200011, 200013)), any());
        assertEquals("3", filtersCaptor.getValue().get("binderId"));
        assertEquals("gov", filtersCaptor.getValue().get("cardName"));
        assertFalse(filtersCaptor.getValue().containsKey("groupBy"));
        assertFalse(filtersCaptor.getValue().containsKey("unknownFutureKey"));
    }

    @Test
    public void shouldSearchWithoutFiltersAndWithoutGroupBy() throws Exception {
        ApiCardSearchRequest search = new ApiCardSearchRequest();
        search.setCardIds(List.of(200011));

        controller.searchCards(httpServletRequest, search);

        verify(collectionService).searchCards(eq(0), eq(20), isNull(), isNull(), isNull(),
                eq(new HashMap<>()), eq(List.of(200011)), any());
    }
}
