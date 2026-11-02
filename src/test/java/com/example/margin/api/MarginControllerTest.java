package com.example.margin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.margin.domain.AssetClass;
import com.example.margin.domain.MarginResult;
import com.example.margin.service.MarginService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
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
        when(marginService.evaluate(any())).thenReturn(new MarginResult("ACC-1",
                new BigDecimal("5000.00"), new BigDecimal("750.00"), new BigDecimal("562.50"),
                new BigDecimal("500.00"), new BigDecimal("-250.00"), true, new BigDecimal("250.00")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": 100, "price": 50}
                          ],
                          "collateral": [ {"type": "CASH", "marketValue": 500} ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1"))
                .andExpect(jsonPath("$.initialMargin").value(750.00))
                .andExpect(jsonPath("$.marginCallRequired").value(true))
                .andExpect(jsonPath("$.marginCallAmount").value(250.00));

        verify(marginService).evaluate(argThat(p ->
                p.accountId().equals("ACC-1")
                        && p.positions().size() == 1
                        && p.positions().getFirst().assetClass() == AssetClass.EQUITY));
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
                          "accountId": "ACC-1",
                          "positions": [
                            {"instrumentId": "AAPL", "assetClass": "EQUITY", "quantity": 1, "price": -1}
                          ],
                          "collateral": []
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownAssetClass() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "accountId": "ACC-1",
                          "positions": [
                            {"instrumentId": "X", "assetClass": "CRYPTO", "quantity": 1, "price": 1}
                          ],
                          "collateral": []
                        }
                        """))
                .andExpect(status().isBadRequest());
    }
}
