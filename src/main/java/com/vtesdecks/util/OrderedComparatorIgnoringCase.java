package com.vtesdecks.util;

import java.util.Comparator;
import java.util.List;

public class OrderedComparatorIgnoringCase implements Comparator<String> {
    private List<String> predefinedOrder;

    public OrderedComparatorIgnoringCase(List<String> predefinedOrder) {
        this.predefinedOrder = predefinedOrder;
    }

    @Override
    public int compare(String o1, String o2) {
        return predefinedOrder.indexOf(o1.toLowerCase()) - predefinedOrder.indexOf(o2.toLowerCase());
    }
}