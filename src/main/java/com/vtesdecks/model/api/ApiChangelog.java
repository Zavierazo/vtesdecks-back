package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiChangelog {
    private String version;
    private String date;
    private List<String> changes;
    private Boolean showDialog;
}
