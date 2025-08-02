package com.vtesdecks.model;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import lombok.Getter;

public enum CardCondition {
    MT("Mint"),
    NM("Near Mint"),
    EX("Excellent"),
    GD("Good"),
    LP("Lightly Played"),
    PL("Played"),
    PO("Poor");

    @Getter
    private final String name;

    CardCondition(String name) {
        this.name = name;
    }

    public static CardCondition fromString(String value) throws CsvDataTypeMismatchException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (CardCondition condition : CardCondition.values()) {
            if (condition.name().equalsIgnoreCase(value)) {
                return condition;
            }
        }
        throw new CsvDataTypeMismatchException("Unknown card condition: " + value);
    }
}
