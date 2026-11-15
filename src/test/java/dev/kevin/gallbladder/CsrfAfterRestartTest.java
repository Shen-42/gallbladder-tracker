package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Its own fresh context because spring-security-test's csrf() helper, used in {@link SecurityTest}, replaces the
 * app's token storage for the rest of a shared context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfAfterRestartTest {

    @Autowired MockMvc mvc;

    @Test
    void loginStillWorksAfterARestartBecauseTheTokenIsInACookie() throws Exception {
        var page = mvc.perform(get("/login")).andReturn();
        String html = page.getResponse().getContentAsString();
        String token = html.replaceAll("(?s).*name=\"_csrf\" value=\"([^\"]+)\".*", "$1");
        var cookie = page.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("token cookie").isNotNull();

        // No session from the first request is sent, as if the app had restarted in between.
        mvc.perform(post("/login").cookie(cookie).param("_csrf", token)
                        .param("username", "me").param("password", "test-password"))
                .andExpect(redirectedUrl("/"));
    }
}
