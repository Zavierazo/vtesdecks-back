package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.vtesdecks.model.CardCondition;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollectionCardCsv {
    public static final List<String> FIELDS_ORDER = List.of("quantity", "name", "set", "binder", "condition", "language", "notes");
    @CsvBindByName(column = "Quantity", required = true)
    private Integer number;
    @CsvBindByName(column = "Name", required = true)
    private String cardName;
    @CsvBindByName
    private String set;
    @CsvBindByName
    private String binder;
    @CsvCustomBindByName(converter = CardConditionConverter.class)
    private CardCondition condition;
    @CsvBindByName
    private String language;
    @CsvBindByName
    private String notes;


    public static class CardConditionConverter extends AbstractBeanField<CardCondition> {
        @Override
        protected Object convert(String value) throws CsvDataTypeMismatchException {
            return CardCondition.fromString(value);
        }
    }
}
