package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerPagesTest {

    @Autowired MockMvc mvc;
    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired SettingsRepository settings;

    @Test
    void navMarksTheCurrentPage() throws Exception {
        mvc.perform(get("/chart").with(user("me")))
                .andExpect(content().string(containsString("<a href=\"/chart\" aria-current=\"page\">Chart</a>")))
                .andExpect(content().string(containsString("<a href=\"/days\">Timeline</a>")));
        assertThat(NavAdvice.section("/meals/12")).isEqualTo("timeline");
        assertThat(NavAdvice.section("/meals/new")).isEqualTo("meal");
        assertThat(NavAdvice.section("/summary/foods")).isEqualTo("summary");
    }

    @Test
    void emptyAccountGetsWaysToStartInsteadOfBlankPages() throws Exception {
        // Rolled back after the test, like everything else here.
        meals.deleteAll();
        episodes.deleteAll();
        settings.deleteAll();

        mvc.perform(get("/chart").with(user("me")))
                .andExpect(content().string(containsString("so there is nothing to draw yet")));
        mvc.perform(get("/summary").with(user("me")))
                .andExpect(content().string(containsString("Nothing to summarise yet")));
        mvc.perform(get("/meals/new").with(user("me")))
                .andExpect(content().string(containsString("No fat limit set yet")));
    }

    @Test
    void quickLogOffersAnUndoThatDeletesTheEntry() throws Exception {
        Undo undo = (Undo) mvc.perform(post("/episodes/quick").with(user("me")).with(csrf()).param("severity", "7"))
                .andReturn().getFlashMap().get("undo");

        assertThat(undo.message()).matches("Saved pain 7/10 at \\d\\d:\\d\\d\\.");
        assertThat(undo.restoreUrl()).matches("/episodes/\\d+/delete");
    }
}
