package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbLoadHistory extends DbBase {
    private static final long serialVersionUID = 1L;
    private String script;
    private String checksum;
    private long executionTime;
}
