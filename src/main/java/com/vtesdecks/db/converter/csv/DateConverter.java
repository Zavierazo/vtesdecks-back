package com.vtesdecks.db.converter.csv;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;

import java.time.LocalDate;

public class DateConverter extends AbstractBeanField<LocalDate> {

    @Override
    protected Object convert(String s) throws CsvDataTypeMismatchException, CsvConstraintViolationException {
        int year = Integer.parseInt(s.substring(0, 4));
        int month = Integer.parseInt(s.substring(4, 6));
        int day = Integer.parseInt(s.substring(6, 8));

        return LocalDate.of(year, month, day);
    }
}
