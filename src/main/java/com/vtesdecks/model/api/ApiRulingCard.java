package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRulingCard {
    private String text;
    private Integer id;
    private String name;
}
