package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiComment {
    private Integer id;
    private String deckId;
    private ZonedDateTime created;
    private ZonedDateTime modified;
    private String content;
    private String fullName;
    private String username;
    private String profileImage;
    private boolean createdByAdmin = false;
    private boolean createdBySupporter = false;
    private boolean createdByCurrentUser = false;
}
