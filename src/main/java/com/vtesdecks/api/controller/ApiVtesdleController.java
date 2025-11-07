package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCardService;
import com.vtesdecks.jpa.entity.VtesdleDayEntity;
import com.vtesdecks.jpa.repositories.VtesdleDayRepository;
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
    private VtesdleDayRepository vtesdleDayRepository;

    @RequestMapping(method = RequestMethod.GET, value = "/todayCard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiTodayCard> todayCard() throws Exception {
        LocalDate day = LocalDate.now();
        VtesdleDayEntity vtesdleDay = vtesdleDayRepository.findById(day).orElse(null);
        if (vtesdleDay == null) {
            day = day.minusDays(1);
            vtesdleDay = vtesdleDayRepository.findById(day).orElse(null);
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
