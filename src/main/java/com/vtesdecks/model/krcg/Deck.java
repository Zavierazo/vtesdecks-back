package com.vtesdecks.model.krcg;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deck {
    private String id;
    private String name;
    private String comments;
    private String author;
    private LocalDate date;
    private Wrapper crypt;
    private Wrapper library;
}
