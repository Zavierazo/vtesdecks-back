package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiSearchResponse {
    private List<Object> cards;
    private List<ApiDeck> decks;
    private List<ApiPublicUser> users;
}

