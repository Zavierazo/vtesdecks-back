package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCardService;
import com.vtesdecks.db.VtesdleDayMapper;
import com.vtesdecks.db.model.DbVtesdleDay;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiTodayCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;

@Controller
@RequestMapping("/api/1.0/vtesdle")
@Slf4j
public class ApiVtesdleController {
    @Autowired
    private ApiCardService apiCardService;
    @Autowired
    private VtesdleDayMapper vtesdleDayMapper;

    @RequestMapping(method = RequestMethod.GET, value = "/todayCard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiTodayCard> todayCard() throws Exception {
        LocalDate day = LocalDate.now();
        DbVtesdleDay vtesdleDay = vtesdleDayMapper.selectByDay(day);
        if (vtesdleDay == null) {
            day = day.minusDays(1);
            vtesdleDay = vtesdleDayMapper.selectByDay(day);
        }
        ApiCrypt apiCrypt = apiCardService.getCrypt(vtesdleDay.getCardId(), null);
        apiCrypt.setName("Nice try ;)");
        apiCrypt.setAka(null);
        apiCrypt.setImage(null);
        apiCrypt.setCropImage(null);

        ApiTodayCard apiTodayCard = new ApiTodayCard();
        apiTodayCard.setDay(day);
        apiTodayCard.setCard(apiCrypt);
        return new ResponseEntity<>(apiTodayCard, HttpStatus.OK);
    }
}
