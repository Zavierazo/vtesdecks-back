package com.vtesdecks.csv.converter;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@Slf4j
public class DateConverter extends AbstractBeanField<LocalDate> {

    @Override
    protected Object convert(String value) throws CsvDataTypeMismatchException, CsvConstraintViolationException {
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            int year = Integer.parseInt(value.substring(0, 4));
            int month = Integer.parseInt(value.substring(4, 6));
            int day = Integer.parseInt(value.substring(6, 8));

            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            log.warn("Failed to convert date string '{}': {}", value, e.getMessage());
            return null;
        }
    }
}
