package com.vtesdecks.csv.mapper;

import com.vtesdecks.csv.entity.LibraryCsv;
import com.vtesdecks.jpa.entity.LibraryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface LibraryCsvMapper {
    LibraryCsvMapper INSTANCE = Mappers.getMapper(LibraryCsvMapper.class);

    List<LibraryEntity> toEntities(List<LibraryCsv> csv);

    LibraryEntity toEntity(LibraryCsv csv);

}
