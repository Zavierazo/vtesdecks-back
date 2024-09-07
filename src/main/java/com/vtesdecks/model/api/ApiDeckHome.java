package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDeckHome {
    private Integer preConstructedTotal;
    private Integer tournamentTotal;
    private Integer communityTotal;
    private Integer userTotal;
    private Integer favoriteTotal;
    private List<ApiDeck> tournamentPopular;
    private List<ApiDeck> tournamentNewest;
    private List<ApiDeck> communityPopular;
    private List<ApiDeck> communityNewest;
}
