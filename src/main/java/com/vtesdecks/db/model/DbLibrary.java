package com.vtesdecks.db.model;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.vtesdecks.db.converter.csv.BurnConverter;
import com.vtesdecks.db.converter.csv.CostConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbLibrary extends DbBase {
    private static final long serialVersionUID = 1L;

    @CsvBindByName(required = true)
    private Integer Id;
    @CsvBindByName(required = true)
    private String Name;
    @CsvBindByName
    private String Aka;
    @CsvBindByName(required = true)
    private String Type;
    @CsvBindByName
    private String Clan;
    @CsvBindByName
    private String Discipline;
    @CsvCustomBindByName(column = "Pool Cost", converter = CostConverter.class)
    private Integer PoolCost;
    @CsvCustomBindByName(column = "Blood Cost", converter = CostConverter.class)
    private Integer BloodCost;
    @CsvCustomBindByName(column = "Conviction Cost", converter = CostConverter.class)
    private Integer ConvictionCost;
    @CsvCustomBindByName(column = "Burn Option", converter = BurnConverter.class)
    private Boolean Burn;
    @CsvBindByName(column = "Card Text", required = true)
    private String Text;
    @CsvBindByName(column = "Flavor Text")
    private String Flavor;
    @CsvBindByName
    private String Set;
    @CsvBindByName
    private String Requirement;
    @CsvBindByName
    private String Banned;
    @CsvBindByName
    private String Artist;
    @CsvBindByName
    private String Capacity;

}
