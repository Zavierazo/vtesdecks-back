package com.vtesdecks.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.vtesdecks.model.api.ApiContact;
import com.vtesdecks.service.MailService;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/api/1.0/contact")
@Slf4j
public class ApiContactController {
    @Autowired
    private MailService mailService;

    @RequestMapping(method = RequestMethod.POST, produces = {
        MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<Boolean> contact(@RequestBody ApiContact apiContact) throws Exception {
        log.info("Contact request received with {}", apiContact);
        mailService.sendContactMail(
            apiContact.getName(),
            apiContact.getEmail(),
            apiContact.getSubject(),
            apiContact.getMessage());
        return new ResponseEntity<>(true, HttpStatus.OK);

    }
}
