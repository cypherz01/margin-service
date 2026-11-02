package com.example.margin.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.margin.marketdata.QuoteProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The UI's JS and CSS must be revalidated on every load; otherwise browsers heuristically cache them
 * and pair a new index.html with stale scripts and styles after a redeploy.
 */
@WebMvcTest(QuoteController.class)
class StaticResourceCachingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteProvider quoteProvider;

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/app.js", "/styles.css"})
    void uiAssetsMustBeRevalidated(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }
}
