package com.example.margin.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.margin.domain.AssetClass;
import com.example.margin.domain.MarginResult;
import com.example.margin.messaging.MarginCallPublishException;
import com.example.margin.service.MarginService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarginController.class)
class MarginControllerTest {

    private static final String URL = "/api/v1/margin/calculations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarginService marginService;

    @Test
    void returnsCalculatedMargin() throws Exception {
        when(marginService.evaluate(any())).thenReturn(new MarginResult("ACC-1", Currency.getInstance("USD"),
                new BigDecimal("5000.00"), new BigDecimal("750.00"), new BigDecimal("562.50"),
                new BigDecimal("500.00"), new BigDecimal("-250.00"), true, new BigDecimal("250.00")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": 100, "price": 50}
                          ],
                          "collateral": [ {"type": "CASH", "marketValue": 500} ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.initialMargin").value(750.00))
                .andExpect(jsonPath("$.marginCallRequired").value(true))
                .andExpect(jsonPath("$.marginCallAmount").value(250.00));

        verify(marginService).evaluate(argThat(p ->
                p.accountId().equals("ACC-1")
                        && p.positions().size() == 1
                        && p.positions().getFirst().assetClass() == AssetClass.EQUITY
                        && p.positions().getFirst().currency().getCurrencyCode().equals("USD")));
    }

    @Test
    void returns503ProblemWhenMarginCallCannotBePublished() throws Exception {
        when(marginService.evaluate(any()))
                .thenThrow(new MarginCallPublishException("Failed to publish margin call evt-1", null));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": 100, "price": 50}
                          ],
                          "collateral": [ {"type": "CASH", "marketValue": 500} ]
                        }
                        """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void rejectsMissingAccountId() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        { "positions": [], "collateral": [] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marginService);
    }

    @Test
    void rejectsNegativePrice() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": 1, "price": -1}
                          ],
                          "collateral": []
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    /** Huge exponents parse cheaply but cost seconds of CPU to multiply and round, so they must never reach the model. */
    @ParameterizedTest(name = "quantity {0}")
    @ValueSource(strings = {"1e5000000", "1e-5000000", "1234567890123", "0.1234567"})
    void rejectsQuantityOutsideDigitBounds(String quantity) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": %s, "price": 50}
                          ],
                          "collateral": []
                        }
                        """.formatted(quantity)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(marginService);
    }

    @Test
    void rejectsCollateralValueOutsideDigitBounds() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        { "accountId": "ACC-1", "currency": "USD", "positions": [],
                          "collateral": [ {"type": "CASH", "marketValue": 1e400} ] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marginService);
    }

    @Test
    void rejectsTooManyPositions() throws Exception {
        String position = """
                {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": 1, "price": 1}""";
        String positions = String.join(",", Collections.nCopies(MarginRequest.MAX_POSITIONS + 1, position));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        { "accountId": "ACC-1", "currency": "USD", "positions": [%s], "collateral": [] }
                        """.formatted(positions)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marginService);
    }

    @ParameterizedTest(name = "accountId \"{0}\"")
    @ValueSource(strings = {"ACC 1", "ACC-1\\nINJECTED", "<script>", "an-account-id-that-is-far-too-long-to-be-a-real-identifier-0123456789"})
    void rejectsMalformedAccountId(String accountId) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        { "accountId": "%s", "currency": "USD", "positions": [], "collateral": [] }
                        """.formatted(accountId)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marginService);
    }

    @Test
    void acceptsValuesAtTheBoundary() throws Exception {
        when(marginService.evaluate(any())).thenReturn(new MarginResult("ACC-1", Currency.getInstance("USD"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                BigDecimal.ZERO));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY",
                             "quantity": -999999999999.999999, "price": 999999999999.99999999}
                          ],
                          "collateral": [ {"type": "CASH", "marketValue": 999999999999999.99} ]
                        }
                        """))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingCurrency() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        { "accountId": "ACC-1", "positions": [], "collateral": [] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marginService);
    }

    @ParameterizedTest(name = "currency \"{0}\"")
    @ValueSource(strings = {"XYZ", "usd", "DOLLARS", ""})
    void rejectsCurrencyThatIsNotAnIso4217Code(String currency) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        { "accountId": "ACC-1", "currency": "%s", "positions": [], "collateral": [] }
                        """.formatted(currency)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marginService);
    }

    @Test
    void rejectsPositionInADifferentCurrencyFromThePortfolio() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "VOD.L", "assetClass": "EQUITY", "quantity": 100, "price": 0.7,
                             "currency": "GBP"}
                          ],
                          "collateral": []
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("mixed-currency")));

        verifyNoInteractions(marginService);
    }

    @Test
    void rejectsUnknownAssetClass() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1", "currency": "USD",
                          "positions": [
                            {"instrumentId": "X", "assetClass": "CRYPTO", "quantity": 1, "price": 1}
                          ],
                          "collateral": []
                        }
                        """))
                .andExpect(status().isBadRequest());
    }
}
