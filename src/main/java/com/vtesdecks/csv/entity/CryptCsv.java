package com.vtesdecks.csv.entity;


import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.vtesdecks.csv.converter.AdvConverter;
import com.vtesdecks.csv.converter.GroupConverter;
import lombok.Data;

@Data
public class CryptCsv {
    @CsvBindByName(required = true)
    private Integer Id;
    @CsvBindByName(required = true)
    private String Name;
    @CsvBindByName
    private String Aka;
    @CsvBindByName(required = true)
    private String Type;
    @CsvBindByName(required = true)
    private String Clan;
    @CsvBindByName
    private String Path;
    @CsvCustomBindByName(converter = AdvConverter.class)
    private Boolean Adv;
    @CsvCustomBindByName(required = true, converter = GroupConverter.class)
    private Integer Group;
    @CsvBindByName(required = true)
    private Integer Capacity;
    @CsvBindByName(required = true)
    private String Disciplines;
    @CsvBindByName(column = "Card Text", required = true)
    private String Text;
    @CsvBindByName(required = true)
    private String Set;
    @CsvBindByName
    private String Title;
    @CsvBindByName
    private String Banned;
    @CsvBindByName(required = true)
    private String Artist;
}
