package com.vtesdecks.model.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class ApiDeckBuilder {
    private String id;
    private String name;
    private String description;
    private boolean published;
    private boolean collection;
    private List<ApiCard> cards;
    private JsonNode extra;
}
