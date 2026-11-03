package com.example.margin.api;

import com.example.margin.domain.MarginResult;
import com.example.margin.service.MarginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/margin")
@Tag(name = "Margin")
public class MarginController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final MarginService marginService;

    public MarginController(MarginService marginService) {
        this.marginService = marginService;
    }

    /**
     * Calculates initial/maintenance margin and collateral value for a portfolio.
     * If collateral is below maintenance margin, a margin-call event is also published to Kafka.
     */
    @Operation(
            summary = "Calculate margin for a portfolio",
            description = "Returns initial and maintenance margin and haircut collateral value. If collateral is "
                    + "below maintenance margin, a margin-call event is published to Kafka and acknowledged by "
                    + "the broker before this returns.")
    @ApiResponse(responseCode = "200", description = "Calculated; `marginCallRequired` says whether a call was raised")
    @ApiResponse(responseCode = "400", description = "Invalid request: missing or out-of-range fields, unknown "
            + "asset class or currency, or lines in a different currency from the portfolio",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "No margin rate or haircut configured for a requested class",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "A margin call was required but could not be published; "
            + "no call has been raised, so retry",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/calculations")
    public MarginResult calculate(@Valid @RequestBody MarginRequest request) {
        return marginService.evaluate(request.toPortfolio());
    }
}
