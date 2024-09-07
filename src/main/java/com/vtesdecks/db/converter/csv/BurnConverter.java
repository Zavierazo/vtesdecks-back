package com.vtesdecks.db.converter.csv;

import com.opencsv.bean.AbstractBeanField;

public class BurnConverter extends AbstractBeanField<Boolean> {
    private static final String YES = "YES";
    private static final String Y = "Y";

    @Override
    protected Boolean convert(String value) {
        return value != null && (value.equalsIgnoreCase(YES) || value.equalsIgnoreCase(Y));
    }
}
