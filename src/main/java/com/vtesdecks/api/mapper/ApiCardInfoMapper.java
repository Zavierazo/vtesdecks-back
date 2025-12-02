package com.vtesdecks.api.mapper;

import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.krcg.Ruling;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ApiCardInfoMapper {

    public abstract List<ApiRuling> mapRulings(List<Ruling> rulings);


}
