package com.vtesdecks.db.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DbCardShop extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer cardId;
    private String platform;
    private String set;
    private String link;
    private BigDecimal price;
}

