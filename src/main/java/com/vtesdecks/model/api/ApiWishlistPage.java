package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiWishlistPage<T> {
    private Integer totalPages;
    private Long totalElements;
    private Boolean publicVisibility;
    private List<T> content;
}
