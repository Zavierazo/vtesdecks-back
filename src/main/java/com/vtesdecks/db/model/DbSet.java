package com.vtesdecks.db.model;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.db.converter.csv.DateConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbSet extends DbBase {
    private static final long serialVersionUID = 1L;

    @CsvBindByName(required = true)
    private Integer Id;
    @CsvBindByName(required = true)
    private String Abbrev;
    @CsvCustomBindByName(column = "Release Date", converter = DateConverter.class, required = true)
    private LocalDate ReleaseDate;
    @CsvBindByName(column = "Full Name", required = true)
    private String FullName;
    @CsvBindByName(required = true)
    private String Company;
}
