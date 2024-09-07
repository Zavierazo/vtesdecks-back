package com.vtesdecks.db.model;

import com.vtesdecks.enums.UserNotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbUserNotification extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer referenceId;
    private Integer user;
    private Boolean read;
    private UserNotificationType type;
    private String notification;
    private String link;
}
