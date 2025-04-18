package com.vtesdecks.db.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbUserAiAsk extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private String user;
    private String question;
    private String answer;
}
