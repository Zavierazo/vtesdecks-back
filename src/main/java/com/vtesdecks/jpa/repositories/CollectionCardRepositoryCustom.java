package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entities.CollectionCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface CollectionCardRepositoryCustom {
    Page<CollectionCard> findByDynamicFiltersGroupBy(Integer collectionId, Map<String, String> filters, String groupBy, Pageable pageable);

    Page<CollectionCard> findByDynamicFilters(Integer collectionId, Map<String, String> filters, Pageable pageable);
}