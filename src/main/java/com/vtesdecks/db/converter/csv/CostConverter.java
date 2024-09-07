package com.vtesdecks.db.converter.csv;

import com.opencsv.bean.AbstractBeanField;
import org.apache.commons.lang3.StringUtils;

public class CostConverter extends AbstractBeanField<Integer> {

    @Override
    protected Integer convert(String value) {
        if ("X".equals(value)) {
            return -1;
        } else if (StringUtils.isBlank(value)) {
            return null;
        } else {
            return Integer.parseInt(value);
        }
    }
}
