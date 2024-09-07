package com.vtesdecks.model.shopify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    private String title;
    private String handle;
    private List<String> tags;
    private List<Variant> variants;
}
