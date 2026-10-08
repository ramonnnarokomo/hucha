package com.ramonnnarokomo.hucha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.ramonnnarokomo.hucha.support.IntegrationTest;

class AuthFlowTest extends IntegrationTest {

    @Test
    void registrationOpensAnAccountWithIbanGiftAndCard() throws Exception {
        Session ana = register("Ana Torres");

        mvc.perform(as(ana, get("/api/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("CHECKING"))
                .andExpect(jsonPath("$[0].iban").value(org.hamcrest.Matchers.startsWith("ES")))
                .andExpect(jsonPath("$[0].balance").value(1000.00));
        mvc.perform(as(ana, get("/api/cards")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].holderName").value("ANA TORRES"));
        mvc.perform(as(ana, get("/api/notifications/unread-count")))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void refreshTokenTravelsInAnHttpOnlyCookie() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Eva", "email": "eva-cookie@test.dev", "password": "Clave1234"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly(RefreshCookie.NAME, true))
                .andExpect(cookie().path(RefreshCookie.NAME, "/api/auth"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").isNotEmpty());
    }

    @Test
    void rejectsDuplicateEmailsAndWeakPasswords() throws Exception {
        Session ana = register("Ana Duplicada");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Otra", "email": "%s", "password": "Clave1234"}
                                """.formatted(ana.email().toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Débil", "email": "debil@test.dev", "password": "solotexto"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void locksLoginAfterFiveWrongPasswords() throws Exception {
        Session ana = register("Ana Bloqueo");
        for (int i = 0; i < 5; i++) {
            loginWith(ana.email(), "Equivocada1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        // Ni con la contraseña buena mientras dura el bloqueo.
        loginWith(ana.email(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_LOCKED"));

        clock.advance(Duration.ofMinutes(16));
        loginWith(ana.email(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailGetsTheSameErrorAsAWrongPassword() throws Exception {
        loginWith("nadie@test.dev", "Clave1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
        Session ana = register("Ana Refresco");

        MvcResult first = mvc.perform(post("/api/auth/refresh").cookie(ana.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotated = first.getResponse().getCookie(RefreshCookie.NAME);
        assertThat(rotated.getValue()).isNotEqualTo(ana.refreshToken());

        // Reutilizar el token viejo se trata como un robo: se cierra la sesión entera...
        mvc.perform(post("/api/auth/refresh").cookie(ana.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        // ...así que el token nuevo tampoco sirve ya.
        mvc.perform(post("/api/auth/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        Session ana = register("Ana Salida");
        mvc.perform(post("/api/auth/logout").cookie(ana.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(RefreshCookie.NAME, 0));
        mvc.perform(post("/api/auth/refresh").cookie(ana.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized());

        Session ana = register("Ana Caducada");
        clock.advance(Duration.ofMinutes(20));
        mvc.perform(as(ana, get("/api/accounts"))).andExpect(status().isUnauthorized());
        // Con el access token caducado se puede refrescar igualmente (en /api/auth no se lee el token).
        mvc.perform(post("/api/auth/refresh").cookie(ana.refreshCookie())
                        .header("Authorization", "Bearer " + ana.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void changingThePasswordClosesTheOtherSessions() throws Exception {
        Session phone = register("Ana Sesiones");
        Session laptop = login(phone.email());
        MvcResult sessions = mvc.perform(as(laptop, get("/api/me/sessions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        assertThat(read(sessions).findValues("current")).hasSize(2);

        String laptopSession = read(mvc.perform(post("/api/auth/refresh").cookie(laptop.refreshCookie()))
                .andReturn()).get("sessionId").asText();
        mvc.perform(as(laptop, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/me/password"))
                        .header("X-Session-Id", laptopSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "Nueva12345"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").cookie(phone.refreshCookie())).andExpect(status().isUnauthorized());
        loginWith(phone.email(), "Nueva12345").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions loginWith(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }
}
