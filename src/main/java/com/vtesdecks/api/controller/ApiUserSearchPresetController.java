package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiSearchPresetService;
import com.vtesdecks.model.api.ApiSearchPreset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/user/search-presets")
@RequiredArgsConstructor
@Slf4j
public class ApiUserSearchPresetController {
    private final ApiSearchPresetService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiSearchPreset> getPresets() throws Exception {
        return service.getPresets();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiSearchPreset createPreset(@RequestBody ApiSearchPreset preset) throws Exception {
        return service.createPreset(preset);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiSearchPreset updatePreset(@PathVariable Integer id, @RequestBody ApiSearchPreset preset) throws Exception {
        return service.updatePreset(id, preset);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Boolean deletePreset(@PathVariable Integer id) throws Exception {
        return service.deletePreset(id);
    }

    @PostMapping(value = "/merge", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiSearchPreset> mergePresets(@RequestBody List<ApiSearchPreset> presets) throws Exception {
        return service.mergePresets(presets);
    }
}
