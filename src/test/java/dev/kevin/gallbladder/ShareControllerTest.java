package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShareControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ShareLinkService shareLinks;

    @Test
    void activeLinkOpensReadOnlyPagesWithoutLogin() throws Exception {
        String token = shareLinks.create("Dr. Wang", 30).token();

        String overview = mvc.perform(get("/share/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andReturn().getResponse().getContentAsString();
        assertThat(overview).contains("Shared read-only by the patient", "with Dr. Wang", "Fat per meal")
                .doesNotContainIgnoringCase("method=\"post\"")
                .doesNotContain("href=\"/meals", "href=\"/episodes", "href=\"/settings", "Log out");

        String day = mvc.perform(get("/share/" + token + "/days/" + LocalDate.now()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(day).doesNotContainIgnoringCase("method=\"post\"").doesNotContain("/edit");

        mvc.perform(get("/share/" + token + "/report.pdf")
                        .param("from", LocalDate.now().minusDays(6).toString()).param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void deadLinksAllLookTheSame() throws Exception {
        ShareLinkService.Created revoked = shareLinks.create(null, 30);
        shareLinks.revoke(revoked.link().getId());

        for (String token : new String[]{revoked.token(), "made-up-token"}) {
            mvc.perform(get("/share/" + token)).andExpect(status().isNotFound())
                    .andExpect(content().string(containsString("This link is no longer active")));
            mvc.perform(get("/share/" + token + "/days/2026-11-15")).andExpect(status().isNotFound());
            mvc.perform(get("/share/" + token + "/report.pdf").param("from", "2026-11-01").param("to", "2026-11-15"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void aShareLinkDoesNotUnlockTheOwnersPages() throws Exception {
        String token = shareLinks.create(null, 30).token();
        mvc.perform(get("/share/" + token)).andExpect(status().isOk());

        mvc.perform(get("/settings")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/share/" + token + "/../settings")).andExpect(status().is4xxClientError());
    }

    @Test
    void ownerCreatesAndRevokesLinksFromSettings() throws Exception {
        var created = mvc.perform(post("/settings/share").with(user("me")).with(csrf())
                        .param("label", "Dr. Li").param("days", "7"))
                .andExpect(redirectedUrl("/settings#share"))
                .andExpect(flash().attributeExists("newShareUrl"))
                .andReturn();
        String url = (String) created.getFlashMap().get("newShareUrl");
        assertThat(url).startsWith("http://localhost/share/");
        String token = url.substring(url.lastIndexOf('/') + 1);
        mvc.perform(get("/share/" + token)).andExpect(status().isOk());

        mvc.perform(get("/settings").with(user("me")))
                .andExpect(content().string(containsString("Dr. Li")));
        long id = ((ShareLink) created.getFlashMap().get("newShare")).getId();
        mvc.perform(post("/settings/share/" + id + "/revoke").with(user("me")).with(csrf()))
                .andExpect(redirectedUrl("/settings#share"));

        mvc.perform(get("/share/" + token)).andExpect(status().isNotFound());
    }
}
