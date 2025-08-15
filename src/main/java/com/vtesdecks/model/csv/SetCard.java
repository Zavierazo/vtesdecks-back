package com.vtesdecks.model.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class SetCard {
    @CsvBindByName(required = true)
    private Integer id;
    @CsvBindByName
    private String name;


}
