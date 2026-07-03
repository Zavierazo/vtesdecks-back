package com.vtesdecks.model.api;

import lombok.Data;

import java.util.List;

@Data
public class ApiShoppingOptimizationRequest {
    private List<ApiCard> cards;
}
