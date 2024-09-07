package com.vtesdecks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mail {
    private String from;
    private String to;
    private String subject;
    private String content;
    private String contentType;
}
