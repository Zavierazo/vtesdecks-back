package com.vtesdecks.model.currencyexchange;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyResponse {
    private LocalDate date;
    private Map<String, Map<String, Double>> currencies = new HashMap<>();

    @JsonAnySetter
    public void addCurrency(String name, Object value) {
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<String, Double> mapped = new HashMap<>();
            raw.forEach((k, v) -> {
                if (k != null && v instanceof Number) {
                    mapped.put(k.toString(), ((Number) v).doubleValue());
                }
            });
            currencies.put(name, mapped);
        }
    }

    public Map<String, Double> getRatesFor(String currency) {
        return currencies.get(currency);
    }
}
