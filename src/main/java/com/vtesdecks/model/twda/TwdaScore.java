package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaScore {
    private BigDecimal finalsVp;
}
