package com.vtesdecks.api.mapper;

import com.vtesdecks.db.model.DbUserNotification;
import com.vtesdecks.model.api.ApiUserNotification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = ApiCommonMapper.class)
public abstract class ApiUserNotificationMapper {

    public abstract ApiUserNotification map(DbUserNotification entity);

    public abstract List<ApiUserNotification> map(List<DbUserNotification> entity);

}
