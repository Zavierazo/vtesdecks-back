package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbDeckView extends DbBase {
    private static final long serialVersionUID = 1L;

    private String id;
    private String deckId;
    private String source;
}
