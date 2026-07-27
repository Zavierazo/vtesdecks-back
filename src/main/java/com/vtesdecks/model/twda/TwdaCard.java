package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * A card entry of a TWDA deck. {@code id} is the VEKN card id, shared with the application
 * database (library 1xxxxx, crypt 2xxxxx). {@code comment} is the inline annotation of the
 * decklist line and is not imported.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaCard {
    private Integer id;
    private Integer count;
    private String printedName;
    private String kind;
    private String comment;
}
