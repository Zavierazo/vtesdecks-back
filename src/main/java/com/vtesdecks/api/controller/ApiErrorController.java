package com.vtesdecks.api.controller;

import com.vtesdecks.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api/1.0/error")
@Slf4j
public class ApiErrorController {

    @RequestMapping(method = RequestMethod.POST, produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<Boolean> set(HttpServletRequest httpServletRequest, @RequestBody String message) {
        log.warn("Client side error User-Agent {} Ip {}: {} ", httpServletRequest.getHeader("User-Agent"), Utils.getIp(httpServletRequest), message);
        return new ResponseEntity<>(true, HttpStatus.OK);
    }
}
