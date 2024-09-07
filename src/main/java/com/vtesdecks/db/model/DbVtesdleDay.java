package com.vtesdecks.db.model;


import java.time.LocalDate;

import lombok.Data;

@Data
public class DbVtesdleDay {
    private static final long serialVersionUID = 1L;
    private LocalDate day;
    private Integer cardId;
}
