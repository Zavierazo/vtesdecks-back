package com.vtesdecks.db.model;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbLibraryI18n extends DbBase {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String locale;
    private String name;
    private String text;
    private String image;
}
