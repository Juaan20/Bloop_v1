package com.bloop.authentication;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Login, registro, /me, refresh con rotacion, logout y proteccion de endpoints. */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void loginReturnsJwtWithPermissionsAndRefreshCookie() {
        var tenant = data.createTenantWithAdmin("Auth Test");
        var response = api().post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(tenant.adminEmail(), TestDataFactory.PASSWORD));

        assertThat(response.status()).isEqualTo(200);
        assertThat((String) response.json("$.accessToken")).isNotBlank();
        List<String> permissions = response.json("$.user.permissions");
        assertThat(permissions).contains("EVENT_MANAGE", "USER_MANAGE").doesNotContain("PLATFORM_ADMIN");
        assertThat(response.setCookie()).hasValueSatisfying(cookie -> assertThat(cookie)
                .startsWith("vf_refresh=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        var tenant = data.createTenantWithAdmin("Wrong Pass");
        var response = api().post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"incorrecta\"}".formatted(tenant.adminEmail()));
        assertThat(response.status()).isEqualTo(401);
        assertThat((String) response.json("$.message")).contains("incorrectos");
    }

    @Test
    void protectedEndpointsRequireToken() {
        assertThat(api().get("/api/venues", null).status()).isEqualTo(401);
        assertThat(api().get("/api/auth/me", "token-falso").status()).isEqualTo(401);
        // Los endpoints publicos no requieren login
        assertThat(api().get("/api/public/events", null).status()).isEqualTo(200);
    }

    @Test
    void customerCanRegisterAndSeeOwnProfileButNotBackoffice() {
        String email = TestDataFactory.uniqueEmail("nuevo");
        var register = api().post("/api/auth/register", null, """
                {"email":"%s","password":"Password123!","firstName":"Ana","lastName":"Lopez",
                 "acceptPrivacy":true,"marketingConsent":false}
                """.formatted(email));
        assertThat(register.status()).isEqualTo(201);
        String token = register.json("$.accessToken");

        var me = api().get("/api/auth/me", token);
        assertThat(me.status()).isEqualTo(200);
        assertThat((List<String>) me.json("$.roles")).containsExactly("CUSTOMER");
        assertThat(api().get("/api/venues", token).status()).isEqualTo(403);
    }

    @Test
    void registerRequiresPrivacyAcceptanceAndUniqueEmail() {
        String email = TestDataFactory.uniqueEmail("rgpd");
        var withoutConsent = api().post("/api/auth/register", null, """
                {"email":"%s","password":"Password123!","firstName":"A","lastName":"B","acceptPrivacy":false}
                """.formatted(email));
        assertThat(withoutConsent.status()).isEqualTo(400);
        assertThat((String) withoutConsent.json("$.fieldErrors.acceptPrivacy")).isNotBlank();

        String existing = data.createCustomer();
        var duplicated = api().post("/api/auth/register", null, """
                {"email":"%s","password":"Password123!","firstName":"A","lastName":"B","acceptPrivacy":true}
                """.formatted(existing.toUpperCase()));
        assertThat(duplicated.status()).isEqualTo(409);
    }

    @Test
    void refreshTokenRotatesAndOldTokenCannotBeReused() {
        String email = data.createCustomer();
        var login = api().post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestDataFactory.PASSWORD));
        String firstCookie = cookieValue(login.setCookie().orElseThrow());

        var refreshed = api().postWithHeader("/api/auth/refresh", null, "{}", "Cookie", "vf_refresh=" + firstCookie);
        assertThat(refreshed.status()).isEqualTo(200);
        String secondCookie = cookieValue(refreshed.setCookie().orElseThrow());
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        // Reutilizar el token antiguo se considera robo: se rechaza y se revocan todas las sesiones.
        var reused = api().postWithHeader("/api/auth/refresh", null, "{}", "Cookie", "vf_refresh=" + firstCookie);
        assertThat(reused.status()).isEqualTo(401);
        var afterRevocation = api().postWithHeader("/api/auth/refresh", null, "{}", "Cookie",
                "vf_refresh=" + secondCookie);
        assertThat(afterRevocation.status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesRefreshToken() {
        String email = data.createCustomer();
        var login = api().post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestDataFactory.PASSWORD));
        String cookie = cookieValue(login.setCookie().orElseThrow());

        var logout = api().postWithHeader("/api/auth/logout", null, "{}", "Cookie", "vf_refresh=" + cookie);
        assertThat(logout.status()).isEqualTo(204);
        var refresh = api().postWithHeader("/api/auth/refresh", null, "{}", "Cookie", "vf_refresh=" + cookie);
        assertThat(refresh.status()).isEqualTo(401);
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherEmailExists() {
        assertThat(api().post("/api/auth/forgot-password", null, "{\"email\":\"noexiste@test.local\"}").status())
                .isEqualTo(204);
        assertThat(api().post("/api/auth/forgot-password", null,
                "{\"email\":\"%s\"}".formatted(data.createCustomer())).status()).isEqualTo(204);
        assertThat(api().post("/api/auth/reset-password", null,
                "{\"token\":\"invalido\",\"newPassword\":\"NuevaPass123\"}").status()).isEqualTo(400);
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring("vf_refresh=".length(), setCookie.indexOf(';'));
    }
}
