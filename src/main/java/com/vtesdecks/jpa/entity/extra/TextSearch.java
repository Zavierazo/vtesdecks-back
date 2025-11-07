package com.vtesdecks.jpa.entity.extra;

import lombok.Data;

@Data
public class TextSearch {
    private Integer id;
    private String name;
    private Double score;

    public TextSearch(Integer id, String name, Double score) {
        this.id = id;
        this.name = name;
        this.score = score;
    }
}
