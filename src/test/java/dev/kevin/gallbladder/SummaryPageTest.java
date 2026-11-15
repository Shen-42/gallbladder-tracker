package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SummaryPageTest {

    @Autowired MockMvc mvc;

    @Test
    void summaryRendersBeforeFiveEpisodes() throws Exception {
        mvc.perform(get("/summary").with(user("me")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Percentages appear once you have logged 5 pain entries")));
    }
}
