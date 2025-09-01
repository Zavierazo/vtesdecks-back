package com.vtesdecks.model.limitedformat;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LimitedFormatCards {
    private Map<String, Boolean> crypt;
    private Map<String, Boolean> library;
}
