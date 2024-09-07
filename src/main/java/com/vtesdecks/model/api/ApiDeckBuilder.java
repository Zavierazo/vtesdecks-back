package com.vtesdecks.model.api;

import java.util.List;

import lombok.Data;

@Data
public class ApiDeckBuilder {
    private String id;
    private String name;
    private String description;
    private boolean published;
    private List<ApiCard> cards;
}
