package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbDeckCard extends DbBase {
    private static final long serialVersionUID = 1L;
    private String deckId;
    private Integer id;
    private Integer number;
}
