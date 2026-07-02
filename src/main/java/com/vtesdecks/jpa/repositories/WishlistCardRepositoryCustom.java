package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.WishlistCardEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface WishlistCardRepositoryCustom {
    Page<WishlistCardEntity> findByDynamicFilters(Integer userId, Map<String, String> filters, Pageable pageable);
}
