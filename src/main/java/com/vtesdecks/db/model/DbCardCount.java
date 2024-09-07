package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbCardCount extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Long number;
}
