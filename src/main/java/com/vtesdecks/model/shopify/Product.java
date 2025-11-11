package com.vtesdecks.model.shopify;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("product_type")
    private String productType;
    private List<String> tags;
    private List<Variant> variants;
}
