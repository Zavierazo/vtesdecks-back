package com.vtesdecks.api.mapper;

import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.api.ApiRulingSymbol;
import com.vtesdecks.model.krcg.Ruling;
import com.vtesdecks.model.krcg.RulingSymbol;
import com.vtesdecks.util.VtesUtils;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ApiCardInfoMapper {

    public abstract List<ApiRuling> mapRulings(List<Ruling> rulings);


    protected ApiRulingSymbol mapRulingSymbol(RulingSymbol rulingSymbol) {
        if (rulingSymbol == null || rulingSymbol.getText() == null || rulingSymbol.getText().length() < 2) {
            return null;
        }
        String disciplineAlias = rulingSymbol.getText().substring(1, rulingSymbol.getText().length() - 1);

        if (disciplineAlias.equals("MERGED")) {
            return ApiRulingSymbol
                    .builder()
                    .text(disciplineAlias)
                    .symbol("merged")
                    .build();
        }
        String symbol = VtesUtils.getDisciplineIconFromAbbreviation(disciplineAlias);

        if (symbol == null) {
            return null;
        }

        return ApiRulingSymbol
                .builder()
                .text(disciplineAlias)
                .symbol(symbol)
                .build();
    }

}
