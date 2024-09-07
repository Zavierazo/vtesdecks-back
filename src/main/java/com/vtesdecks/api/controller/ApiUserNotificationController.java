package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiUserNotificationService;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.model.api.ApiUserNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/api/1.0/user/notifications")
@Slf4j
public class ApiUserNotificationController {
    @Autowired
    private ApiUserNotificationService userNotificationService;

    @GetMapping(value = "/unreadCount", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Integer> notificationUnreadCount() {
        return new ResponseEntity<>(userNotificationService.notificationUnreadCount(ApiUtils.extractUserId()), HttpStatus.OK);

    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<ApiUserNotification>> notifications() {
        return new ResponseEntity<>(userNotificationService.getUserNotifications(), HttpStatus.OK);

    }

    @PostMapping(value = "/{id}/markAsRead", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> markAsRead(@PathVariable Integer id) {
        userNotificationService.markAsRead(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/markAsRead", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> markAsRead() {
        userNotificationService.markAllAsRead();
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
