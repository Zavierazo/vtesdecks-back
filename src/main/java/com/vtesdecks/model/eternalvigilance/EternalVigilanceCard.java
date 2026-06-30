package com.vtesdecks.model.eternalvigilance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * A card entry of an eternal-vigilance deck. Each entry usually carries the VTES card {@code id},
 * so it can be resolved directly; when it is missing the remaining fields are used to fall back to
 * name matching. The crypt-only fields ({@code capacity}, {@code disciplines}, {@code title},
 * {@code clan}, {@code grouping}) are only populated for crypt cards. {@code grouping} is kept as
 * a String because it can be a number or the literal {@code ANY}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EternalVigilanceCard {
    private Integer count;
    private Integer id;
    private String name;
    //Crypt only
    private Integer capacity;
    private String disciplines;
    private String title;
    private String clan;
    private String grouping;
}
