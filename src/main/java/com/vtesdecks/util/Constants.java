package com.vtesdecks.util;

import java.time.format.DateTimeFormatter;

public class Constants {
    public static final String SEPARATOR = "_";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final String CARDS_DELETED_HEADER = "X-Card-Deleted";
    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    public static final String USER_COUNTRY_HEADER = "CF-IPCountry";
    public static final String DATE_HEADER = "Date";
    public static final String DEFAULT_CURRENCY = "EUR";
}
