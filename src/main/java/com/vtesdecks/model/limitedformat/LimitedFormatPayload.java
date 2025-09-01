package com.vtesdecks.model.limitedformat;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LimitedFormatPayload {
    private Integer id;
    private String name;
    private String tag;
    private Integer minLibrary;
    private Integer maxLibrary;
    private Integer minCrypt;
    private Integer maxCrypt;
    private Map<String, Boolean> sets;
    private LimitedFormatCards allowed;
    private LimitedFormatCards banned;

}
