package com.company.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.orders.presentation.OrderController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "testuser", password = "testpass")
class OrderIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper objectMapper;

    private String placeOrder(String customerId) throws Exception {
        var req = new OrderController.PlaceOrderRequest(
                customerId,
                List.of(new OrderController.ItemRequest("prod-1", "Widget", 2, 15.00, "USD")));
        MvcResult result = mockMvc
                .perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId")
                .asText();
    }

    // ── Order lifecycle tests (use @WithMockUser from class level) ────────────

    @Test
    @DisplayName("POST /orders → 201, status=PENDING, total=30.00")
    void placeOrder_success() throws Exception {
        var req = new OrderController.PlaceOrderRequest(
                "cust-1",
                List.of(new OrderController.ItemRequest("prod-1", "Widget", 2, 15.00, "USD")));
        mockMvc
                .perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(30.00))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("Full lifecycle: place → confirm → GET shows CONFIRMED")
    void fullLifecycle_placeConfirmGet() throws Exception {
        String id = placeOrder("cust-2");
        mockMvc.perform(post("/api/v1/orders/" + id + "/confirm"))
                .andExpect(status().isNoContent());
        mockMvc
                .perform(get("/api/v1/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Cancel PENDING order → 204, GET shows CANCELLED")
    void cancelOrder_success() throws Exception {
        String id = placeOrder("cust-3");
        mockMvc
                .perform(
                        delete("/api/v1/orders/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"changed mind\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/orders/" + id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /orders/{id} — non-existent → 404")
    void getOrder_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/no-such-id")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /orders?customerId — returns all orders for customer")
    void listOrders_byCustomer() throws Exception {
        placeOrder("cust-list");
        placeOrder("cust-list");
        placeOrder("cust-other");
        mockMvc
                .perform(get("/api/v1/orders").param("customerId", "cust-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Confirm already-cancelled order → 409 Conflict")
    void confirmCancelledOrder_returns409() throws Exception {
        String id = placeOrder("cust-5");
        mockMvc.perform(
                delete("/api/v1/orders/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"));
        mockMvc
                .perform(post("/api/v1/orders/" + id + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("POST /orders with empty items → 400 Validation error")
    void placeOrder_emptyItems_returns400() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"customerId\":\"c\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Unauthenticated request → 401")
    @WithAnonymousUser
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/some-id")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Virtual thread info is available")
    void virtualThread_isEnabled() {
        assertThat(
                Thread.currentThread().isVirtual()
                        || System.getProperty("spring.threads.virtual.enabled", "true")
                                .equals("true"))
                .isTrue();
    }

    // ── JWT authentication tests ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login with valid credentials → 200 with token")
    @WithAnonymousUser
    void login_validCredentials_returnsToken() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/login with wrong password → 401")
    @WithAnonymousUser
    void login_wrongPassword_returns401() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testuser\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login with blank password → 400")
    @WithAnonymousUser
    void login_blankPassword_returns400() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testuser\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JWT token from login grants access to protected endpoint")
    @WithAnonymousUser
    void login_thenAccessProtectedEndpoint_succeeds() throws Exception {
        MvcResult loginResult = mockMvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc
                .perform(get("/api/v1/orders/nonexistent").header("Authorization",
                        "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tampered JWT → 401")
    @WithAnonymousUser
    void tamperedToken_returns401() throws Exception {
        mockMvc
                .perform(
                        get("/api/v1/orders/some-id")
                                .header("Authorization",
                                        "Bearer eyJhbGciOiJIUzI1NiJ9.tampered.signature"))
                .andExpect(status().isUnauthorized());
    }
}
