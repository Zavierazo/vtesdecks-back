package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaLibrarySection {
    private String type;
    private Integer count;
    private List<TwdaCard> cards = new ArrayList<>();
}
