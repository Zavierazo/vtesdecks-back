package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCommentService;
import com.vtesdecks.model.api.ApiComment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/api/1.0/comments")
@Slf4j
public class ApiCommentController {
    @Autowired
    private ApiCommentService commentService;

    @RequestMapping(method = RequestMethod.GET, value = "/decks/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiComment>> comments(@PathVariable String id) {
        List<ApiComment> comments = commentService.getComments(id);
        return new ResponseEntity<>(comments, HttpStatus.OK);

    }
}
