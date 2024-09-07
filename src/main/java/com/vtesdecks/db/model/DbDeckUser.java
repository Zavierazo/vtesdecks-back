package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbDeckUser extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer user;
    private String deckId;
    private Integer rate;
    private boolean favorite;
}
