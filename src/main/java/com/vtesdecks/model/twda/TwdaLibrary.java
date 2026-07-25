package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Library of a TWDA deck; {@code cards} holds one section per card type.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaLibrary {
    private Integer count;
    private List<TwdaLibrarySection> cards = new ArrayList<>();
}
