package com.vtesdecks.api.mapper;

import com.vtesdecks.jpa.entity.FeatureFlagEntity;
import com.vtesdecks.model.api.ApiFeatureFlag;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ApiFeatureFlagMapper {

    ApiFeatureFlag map(FeatureFlagEntity entity);

    List<ApiFeatureFlag> map(List<FeatureFlagEntity> entities);
}
