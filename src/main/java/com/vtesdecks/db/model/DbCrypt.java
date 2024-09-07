package com.vtesdecks.db.model;


import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.vtesdecks.db.converter.csv.AdvConverter;
import com.vtesdecks.db.converter.csv.GroupConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbCrypt extends DbBase {
    private static final long serialVersionUID = 1L;

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
