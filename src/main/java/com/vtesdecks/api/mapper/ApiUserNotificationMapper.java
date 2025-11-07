package com.vtesdecks.api.mapper;

import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.model.api.ApiUserNotification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = ApiCommonMapper.class)
public abstract class ApiUserNotificationMapper {

    public abstract ApiUserNotification map(UserNotificationEntity entity);

    public abstract List<ApiUserNotification> map(List<UserNotificationEntity> entity);

}
