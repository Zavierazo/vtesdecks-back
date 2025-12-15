package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.enums.AsyncAiStatus;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiAiAsyncStatusResponse {
    private AsyncAiStatus status;
    private String message;
    private String error;
}

