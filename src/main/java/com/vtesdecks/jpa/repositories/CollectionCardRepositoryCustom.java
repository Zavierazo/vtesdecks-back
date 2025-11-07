package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionCardEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface CollectionCardRepositoryCustom {
    Page<CollectionCardEntity> findByDynamicFiltersGroupBy(Integer collectionId, Map<String, String> filters, String groupBy, Pageable pageable);

    Page<CollectionCardEntity> findByDynamicFilters(Integer collectionId, Map<String, String> filters, Pageable pageable);
}