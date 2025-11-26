package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiShopInfo {
    private String name;
    private String fullName;
    private String baseUrl;
    private boolean showButton;
}
