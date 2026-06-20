package com.vtesdecks.model.eternalvigilance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * A card entry of an eternal-vigilance deck. The {@code count} and {@code name} fields are common
 * to both crypt and library cards; the remaining fields are only populated for crypt cards.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EternalVigilanceCard {
    private Integer count;
    private String name;
    //Crypt only
    private Integer capacity;
    private String disciplines;
    private String title;
    private String clan;
    private Integer grouping;
}
