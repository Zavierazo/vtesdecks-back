package com.vtesdecks.csv.mapper;

import com.vtesdecks.csv.entity.CryptCsv;
import com.vtesdecks.jpa.entity.CryptEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface CryptCsvMapper {
    CryptCsvMapper INSTANCE = Mappers.getMapper(CryptCsvMapper.class);

    List<CryptEntity> toEntities(List<CryptCsv> csv);

    CryptEntity toEntity(CryptCsv csv);

}
