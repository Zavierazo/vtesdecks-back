package com.vtesdecks.csv.entity;


import lombok.Data;

@Data
public class LibraryI18nCsv {
    private Integer id;
    private String locale;
    private String name;
    private String text;
    private String image;
}
