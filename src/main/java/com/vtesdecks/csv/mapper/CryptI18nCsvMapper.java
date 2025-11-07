package com.vtesdecks.csv.mapper;

import com.vtesdecks.csv.entity.CryptI18nCsv;
import com.vtesdecks.jpa.entity.CryptI18nEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface CryptI18nCsvMapper {
    CryptI18nCsvMapper INSTANCE = Mappers.getMapper(CryptI18nCsvMapper.class);

    List<CryptI18nEntity> toEntities(List<CryptI18nCsv> csv);

    @Mapping(target = "id.cardId", source = "id")
    @Mapping(target = "id.locale", source = "locale")
    CryptI18nEntity toEntity(CryptI18nCsv csv);

}
