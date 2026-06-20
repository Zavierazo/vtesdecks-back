package com.vtesdecks.model.eternalvigilance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EternalVigilanceLibrarySection {
    private String name;
    private Integer count;
    private List<EternalVigilanceCard> cards;
}
