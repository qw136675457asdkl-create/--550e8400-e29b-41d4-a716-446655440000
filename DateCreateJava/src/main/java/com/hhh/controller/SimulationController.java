package com.hhh.controller;

import com.hhh.service.PythonSimulationService;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
public class SimulationController {

    private final PythonSimulationService pythonSimulationService;

    public SimulationController(PythonSimulationService pythonSimulationService) {
        this.pythonSimulationService = pythonSimulationService;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode health() {
        return pythonSimulationService.getHealth();
    }

    @GetMapping(value = "/api/simulations", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode listSimulations() {
        return pythonSimulationService.listSimulations();
    }

    @PostMapping(
            value = "/api/simulations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public JsonNode createSimulation(@RequestBody JsonNode requestBody) {
        return pythonSimulationService.createSimulation(requestBody);
    }

    @GetMapping(value = "/api/simulations/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getSimulation(@PathVariable String taskId) {
        return pythonSimulationService.getSimulation(taskId);
    }
}
