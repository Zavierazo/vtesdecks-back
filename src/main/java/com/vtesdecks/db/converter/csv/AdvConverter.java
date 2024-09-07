package com.vtesdecks.db.converter.csv;

import com.opencsv.bean.AbstractBeanField;

public class AdvConverter extends AbstractBeanField<Boolean> {

    @Override
    protected Boolean convert(String value) {
        if ("Advanced".equals(value)) {
            return true;
        } else {
            return false;
        }
    }
}
