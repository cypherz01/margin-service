package com.example.margin.api;

import com.example.margin.domain.MarginResult;
import com.example.margin.service.MarginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/margin")
public class MarginController {

    private final MarginService marginService;

    public MarginController(MarginService marginService) {
        this.marginService = marginService;
    }

    /**
     * Calculates initial/maintenance margin and collateral value for a portfolio.
     * If collateral is below maintenance margin, a margin-call event is also published to Kafka.
     */
    @PostMapping("/calculations")
    public MarginResult calculate(@Valid @RequestBody MarginRequest request) {
        return marginService.evaluate(request.toPortfolio());
    }
}
