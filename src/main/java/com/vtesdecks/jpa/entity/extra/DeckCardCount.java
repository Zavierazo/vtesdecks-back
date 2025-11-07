package com.vtesdecks.jpa.entity.extra;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeckCardCount {
    private Integer id;
    private Object number;

    public Long getNumberAsLong() {
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).longValue();
        } else if (number instanceof Long) {
            return (Long) number;
        }
        return 0L;
    }
}