package com.vtesdecks.api.mapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ApiCommonMapper {

    public static ZonedDateTime map(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault());
    }

}
