package com.vtesdecks.model.eternalvigilance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EternalVigilanceDeckList {
    private String name;
    private String description;
    private Integer cryptCount;
    private Integer cryptMin;
    private Integer cryptMax;
    private Double cryptAvg;
    private List<EternalVigilanceCard> crypt;
    private Integer libraryCount;
    private List<EternalVigilanceLibrarySection> librarySections;
}
