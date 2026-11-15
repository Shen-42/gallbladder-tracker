package dev.kevin.gallbladder;

import dev.kevin.gallbladder.UsdaService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

@DataJpaTest
class UsdaServiceTest {

    private static final String FRIED_EGG = """
            {"totalHits": 3, "foods": [
              {"fdcId": 173423, "description": "Egg, whole, cooked, fried", "dataType": "SR Legacy",
               "foodNutrients": [{"nutrientId": 1003, "value": 13.6},
                                 {"nutrientId": 1004, "nutrientName": "Total lipid (fat)", "value": 14.8, "unitName": "G"}]},
              {"fdcId": 1, "description": "No fat listed", "foodNutrients": [{"nutrientId": 1003, "value": 2}]},
              {"fdcId": 2707158, "description": "Egg, whole, fried with oil",
               "foodNutrients": [{"nutrientId": 1004, "value": 15.81}]}
            ]}""";

    @Autowired FoodRepository foods;
    MockRestServiceServer server;
    UsdaService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(UsdaService.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new UsdaService(builder, "test-key", foods);
    }

    @Test
    void returnsGenericFoodsWithTheirFatAndSkipsThoseWithout() {
        server.expect(requestTo(startsWith(UsdaService.BASE_URL + "/foods/search")))
                .andExpect(queryParam("api_key", "test-key"))
                .andExpect(queryParam("query", "fried%20egg"))
                .andExpect(queryParam("dataType", "Foundation", "SR%20Legacy", "Survey%20(FNDDS)"))
                .andRespond(withSuccess(FRIED_EGG, MediaType.APPLICATION_JSON));

        assertThat(service.search("Fried egg ")).containsExactly(
                new Result(173423, "Egg, whole, cooked, fried", new BigDecimal("14.8")),
                new Result(2707158, "Egg, whole, fried with oil", new BigDecimal("15.81")));
        server.verify();
    }

    @Test
    void plainMatchesComeFirst() {
        server.expect(requestTo(startsWith(UsdaService.BASE_URL + "/foods/search"))).andRespond(withSuccess("""
                {"foods": [
                  {"fdcId": 1, "description": "Salad, with avocado", "foodNutrients": [{"nutrientId": 1004, "value": 9}]},
                  {"fdcId": 2, "description": "Oil, avocado", "foodNutrients": [{"nutrientId": 1004, "value": 100}]},
                  {"fdcId": 3, "description": "Avocados, raw, all commercial varieties",
                   "foodNutrients": [{"nutrientId": 1004, "value": 14.7}]},
                  {"fdcId": 4, "description": "Avocado, raw", "foodNutrients": [{"nutrientId": 1004, "value": 14.66}]}
                ]}""", MediaType.APPLICATION_JSON));

        assertThat(service.search("Avocado")).extracting(Result::fdcId).containsExactly(4L, 1L, 2L, 3L);
    }

    @Test
    void repeatedSearchesAreCached() {
        server.expect(once(), requestTo(startsWith(UsdaService.BASE_URL)))
                .andRespond(withSuccess(FRIED_EGG, MediaType.APPLICATION_JSON));

        service.search("fried egg");
        assertThat(service.search("FRIED EGG")).hasSize(2);
        server.verify();
    }

    @Test
    void failuresSayWhatHappenedAndAreNotCached() {
        server.expect(requestTo(startsWith(UsdaService.BASE_URL))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(startsWith(UsdaService.BASE_URL))).andRespond(withServerError());

        assertThatThrownBy(() -> service.search("egg"))
                .isInstanceOf(UsdaService.UnavailableException.class).hasMessageContaining("limit");
        assertThatThrownBy(() -> service.search("egg"))
                .isInstanceOf(UsdaService.UnavailableException.class).hasMessageContaining("reachable");
        server.verify();
    }

    @Test
    void savingAPickMakesALocalUsdaFoodOnce() {
        Result picked = new Result(173423, "Egg, whole, cooked, fried", new BigDecimal("14.8"));

        Food food = service.save(picked);
        Food again = service.save(picked);

        assertThat(again.getId()).isEqualTo(food.getId());
        assertThat(foods.count()).isEqualTo(1);
        assertThat(food.getSource()).isEqualTo(FoodSource.USDA);
        assertThat(food.getFdcId()).isEqualTo(173423);
        assertThat(food.getFatPer100g()).isEqualByComparingTo("14.8");
    }
}
