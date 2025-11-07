package com.vtesdecks.csv.entity;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.vtesdecks.csv.converter.DateConverter;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SetCsv {
    @CsvBindByName(required = true)
    private Integer Id;
    @CsvBindByName(required = true)
    private String Abbrev;
    @CsvCustomBindByName(column = "Release Date", converter = DateConverter.class)
    private LocalDate ReleaseDate;
    @CsvBindByName(column = "Full Name", required = true)
    private String FullName;
    @CsvBindByName(required = true)
    private String Company;
}
