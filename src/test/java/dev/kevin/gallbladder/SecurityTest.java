package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The test password comes from src/test/resources/application.properties; the username defaults to "me". */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void everyPageNeedsLogin() throws Exception {
        for (String page : new String[]{"/days", "/meals/new", "/summary", "/export.zip", "/foods/usda?q=egg"}) {
            mvc.perform(get(page)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }

    @Test
    void loginPageAndStylesAreOpen() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/app.css")).andExpect(status().isOk());
    }

    @Test
    void rightPasswordLogsInAndCanBeRemembered() throws Exception {
        mvc.perform(formLogin().user("me").password("test-password"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").with(csrf()).param("username", "me").param("password", "test-password")
                        .param("remember", "on"))
                .andExpect(cookie().exists("remember-me"));
    }

    @Test
    void wrongUsernameOrPasswordGivesTheSameMessage() throws Exception {
        String wrongPassword = mvc.perform(formLogin().user("me").password("nope"))
                .andReturn().getResponse().getRedirectedUrl();
        String wrongUser = mvc.perform(formLogin().user("someone").password("test-password"))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(wrongPassword).isEqualTo("/login?error").isEqualTo(wrongUser);
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "match. Check both and try again.")));
    }

    @Test
    void postsWithoutTheFormTokenAreRefused() throws Exception {
        mvc.perform(post("/episodes/quick").with(user("me")).param("severity", "5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void appRefusesToStartWithoutAPassword() {
        assertThatThrownBy(() -> new SecurityConfig("me", " "))
                .hasMessageContaining("APP_PASSWORD");
    }
}
