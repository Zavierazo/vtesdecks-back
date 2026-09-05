package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.enums.UserNotificationType;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiUserNotification {
    private Integer id;
    private Boolean read;
    private UserNotificationType type;
    private String notification;
    private String link;
    private JsonNode data;
    private ZonedDateTime creationDate;
}
