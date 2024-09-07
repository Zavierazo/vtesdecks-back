package com.vtesdecks.db.model;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.vtesdecks.model.serializer.LocalDateTimeDeserializer;
import com.vtesdecks.model.serializer.LocalDateTimeSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
public class DbUser extends DbBase {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private String username;
    private String email;
    private String password;
    private String loginHash;
    private String displayName;
    private String profileImage;
    private boolean validated;
    private boolean admin;
    private boolean tester;
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime forgotPasswordDate;
}
