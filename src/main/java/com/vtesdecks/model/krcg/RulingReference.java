package com.vtesdecks.model.krcg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RulingReference {
    private String text;
    private String label;
    private String url;
}
