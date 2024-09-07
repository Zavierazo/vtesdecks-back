package com.vtesdecks.db.converter.csv;

import com.opencsv.bean.AbstractBeanField;

public class GroupConverter extends AbstractBeanField<Integer> {

    @Override
    protected Integer convert(String value) {
        if ("ANY".equals(value)) {
            return -1;
        } else {
            return Integer.parseInt(value);
        }
    }
}
