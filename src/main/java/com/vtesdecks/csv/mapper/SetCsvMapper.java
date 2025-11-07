package com.vtesdecks.csv.mapper;

import com.vtesdecks.csv.entity.SetCsv;
import com.vtesdecks.jpa.entity.SetEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface SetCsvMapper {
    SetCsvMapper INSTANCE = Mappers.getMapper(SetCsvMapper.class);

    List<SetEntity> toEntities(List<SetCsv> csv);

    SetEntity toEntity(SetCsv csv);

}
