package com.vtesdecks.csv.mapper;

import com.vtesdecks.csv.entity.LibraryI18nCsv;
import com.vtesdecks.jpa.entity.LibraryI18nEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface LibraryI18nCsvMapper {
    LibraryI18nCsvMapper INSTANCE = Mappers.getMapper(LibraryI18nCsvMapper.class);

    List<LibraryI18nEntity> toEntities(List<LibraryI18nCsv> csv);

    @Mapping(target = "id.cardId", source = "id")
    @Mapping(target = "id.locale", source = "locale")
    LibraryI18nEntity toEntity(LibraryI18nCsv csv);

}
