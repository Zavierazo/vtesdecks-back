package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbComment extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer user;
    private Integer parent;
    private String pageIdentifier;
    private String content;
    private boolean deleted;
}
