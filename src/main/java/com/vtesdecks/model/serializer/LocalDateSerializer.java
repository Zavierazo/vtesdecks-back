package com.vtesdecks.model.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.vtesdecks.util.Constants;

import java.io.IOException;
import java.time.LocalDate;

public class LocalDateSerializer extends JsonSerializer<LocalDate> {
    @Override
    public void serialize(final LocalDate date, final JsonGenerator generator, final SerializerProvider provider)
            throws IOException {
        final String dateString = date.format(Constants.DATE_FORMAT);
        generator.writeString(dateString);
    }
}
