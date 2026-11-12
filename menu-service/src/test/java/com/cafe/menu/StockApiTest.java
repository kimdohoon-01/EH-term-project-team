package com.cafe.menu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:menu-test;MODE=MySQL")
@AutoConfigureMockMvc
class StockApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void reserveAndCompensateExactlyOnce() throws Exception {
        long menuId = createMenu(5);
        String reservation = "/api/stock/reservations/demo-failure";
        String items = items(menuId, 2);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put(reservation).contentType(MediaType.APPLICATION_JSON).content(items))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESERVED"))
                    .andExpect(jsonPath("$.totalAmount").value(8000));
        }
        expectStock(menuId, 3);
        mvc.perform(put(reservation).contentType(MediaType.APPLICATION_JSON).content(items(menuId, 1)))
                .andExpect(status().isConflict());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(reservation + "/release"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RELEASED"));
        }
        mvc.perform(put(reservation).contentType(MediaType.APPLICATION_JSON).content(items))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        expectStock(menuId, 5);
    }

    @Test
    void insufficientSecondItemRollsBackFirstItem() throws Exception {
        long first = createMenu(3);
        long second = createMenu(1);
        String reservation = "/api/stock/reservations/insufficient";
        String payload = """
                {"items":[{"menuId":%d,"quantity":2},{"menuId":%d,"quantity":2}]}
                """.formatted(first, second);
        mvc.perform(put(reservation).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
        expectStock(first, 3);
        expectStock(second, 1);
        mvc.perform(get(reservation)).andExpect(status().isNotFound());
    }

    @Test
    void menuManagementAndStockValidation() throws Exception {
        long menuId = createMenu(2);
        String menu = "/api/menus/" + menuId;
        mvc.perform(post(menu + "/stock-adjustments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-3}"))
                .andExpect(status().isConflict());
        mvc.perform(post(menu + "/stock-adjustments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(5));
        mvc.perform(put(menu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Latte\",\"price\":4500,\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/stock/reservations/disabled").contentType(MediaType.APPLICATION_JSON)
                        .content(items(menuId, 1)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/stock/reservations/invalid").contentType(MediaType.APPLICATION_JSON)
                        .content(items(menuId, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/stock/reservations/INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    private long createMenu(int stock) throws Exception {
        String location = mvc.perform(post("/api/menus").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coffee\",\"price\":4000,\"stock\":" + stock + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(location, "$.id")).longValue();
    }

    private void expectStock(long menuId, int stock) throws Exception {
        mvc.perform(get("/api/menus/" + menuId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(stock));
    }

    private String items(long menuId, int quantity) {
        return """
                {"items":[{"menuId":%d,"quantity":%d}]}
                """.formatted(menuId, quantity);
    }
}
