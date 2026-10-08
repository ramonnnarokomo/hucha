package com.ramonnnarokomo.hucha.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.ramonnnarokomo.hucha.support.IntegrationTest;

class NotificationStreamTest extends IntegrationTest {

    @Test
    void pushesNotificationsInRealTimeOverServerSentEvents() throws Exception {
        Session ana = register("Ana Stream");
        Session luis = register("Luis Stream");
        addPayee(ana, "Luis", mainAccount(luis).getIban());

        MvcResult stream = mvc.perform(get("/api/notifications/stream").param("access_token", luis.accessToken()))
                .andExpect(request().asyncStarted())
                .andReturn();

        transfer(ana, "k-stream", mainAccount(ana).getId(), mainAccount(luis).getIban(), "Luis", "25")
                .andExpect(status().isCreated());
        outboxRelay.publishPending();

        String events = stream.getResponse().getContentAsString();
        assertThat(events).contains("event:connected").contains("event:notification").contains("Has recibido 25,00");
    }

    @Test
    void theQueryParameterTokenOnlyWorksForTheStream() throws Exception {
        Session ana = register("Ana Query");
        mvc.perform(get("/api/accounts").param("access_token", ana.accessToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void readAndUnreadCounters() throws Exception {
        Session ana = register("Ana Leídas");
        mvc.perform(as(ana, get("/api/notifications/unread-count"))).andExpect(jsonPath("$.count").value(1));
        mvc.perform(as(ana, post("/api/notifications/read-all"))).andExpect(status().isNoContent());
        mvc.perform(as(ana, get("/api/notifications/unread-count"))).andExpect(jsonPath("$.count").value(0));
        mvc.perform(as(ana, get("/api/notifications"))).andExpect(jsonPath("$.items[0].read").value(true));
    }
}
