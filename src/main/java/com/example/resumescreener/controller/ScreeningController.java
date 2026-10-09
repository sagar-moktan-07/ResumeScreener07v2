package com.example.resumescreener.controller;

import com.example.resumescreener.service.ScreeningService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @RequestMapping(value = "/screening/run", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> runScreening(@RequestParam Long vacancyId) {
        return screeningService.runForVacancy(vacancyId);
    }
}
