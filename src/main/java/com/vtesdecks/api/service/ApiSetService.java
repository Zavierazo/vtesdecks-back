package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiSetMapper;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.model.api.ApiSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiSetService {
    @Autowired
    private SetCache setCache;
    @Autowired
    private ApiSetMapper apiSetMapper;

    public ApiSet getSet(Integer id) {
        Set set = setCache.get(id);
        return apiSetMapper.map(set);
    }

    public ApiSet getSet(String abbrev) {
        Set set = setCache.get(abbrev);
        return apiSetMapper.map(set);
    }

    public ApiSet getLastUpdate() {
        Set set = setCache.selectLastUpdated();
        return apiSetMapper.map(set);
    }

    public List<ApiSet> getSets() {
        return setCache.selectAll().stream()
                .map(apiSetMapper::map)
                .collect(Collectors.toList());
    }
}
