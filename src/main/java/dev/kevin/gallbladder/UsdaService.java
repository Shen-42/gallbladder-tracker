package dev.kevin.gallbladder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Food search against USDA FoodData Central. Only generic foods are searched; branded products are left out. */
@Service
public class UsdaService {

    private static final Logger log = LoggerFactory.getLogger(UsdaService.class);
    static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    /** FoodData Central's id for "Total lipid (fat)", reported per 100 g. */
    static final int FAT_NUTRIENT_ID = 1004;
    /** More are fetched than shown, so the reranking below has plain matches to bring up. */
    private static final int PAGE_SIZE = 25;
    private static final int SHOWN = 10;
    private static final int CACHED_QUERIES = 200;

    /** A search hit with its fat per 100 g. */
    public record Result(long fdcId, String name, BigDecimal fatPer100g) {
    }

    /** The search could not be done; the message says why, in words for the user. */
    public static class UnavailableException extends RuntimeException {
        UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record SearchResponse(List<UsdaFood> foods) {
    }

    record UsdaFood(long fdcId, String description, List<Nutrient> foodNutrients) {
    }

    record Nutrient(Integer nutrientId, BigDecimal value) {
    }

    private final RestClient client;
    private final String apiKey;
    private final FoodRepository foods;
    /** Recent searches by normalized query, oldest dropped first. */
    private final Map<String, List<Result>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Result>> eldest) {
                    return size() > CACHED_QUERIES;
                }
            });

    @Autowired
    public UsdaService(@Value("${usda.api-key:DEMO_KEY}") String apiKey, FoodRepository foods) {
        this(RestClient.builder().requestFactory(requestFactory()).baseUrl(BASE_URL), apiKey, foods);
    }

    UsdaService(RestClient.Builder client, String apiKey, FoodRepository foods) {
        this.client = client.build();
        this.apiKey = apiKey == null || apiKey.isBlank() ? "DEMO_KEY" : apiKey;
        this.foods = foods;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        return factory;
    }

    /** Up to {@value #SHOWN} foods matching {@code query}, skipping any without a fat value. Repeated queries are answered from memory. */
    public List<Result> search(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        List<Result> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SearchResponse response;
        try {
            response = client.get()
                    .uri(u -> u.path("/foods/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("query", key)
                            .queryParam("pageSize", PAGE_SIZE)
                            .queryParam("dataType", "Foundation", "SR Legacy", "Survey (FNDDS)")
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("USDA search failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new UnavailableException(e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? "USDA search has hit its limit for now." : "USDA search was refused.", e);
        } catch (RestClientException e) {
            log.warn("USDA search failed", e);
            throw new UnavailableException("USDA search isn't reachable right now.", e);
        }
        List<Result> results = response == null || response.foods() == null ? List.of()
                : response.foods().stream().map(UsdaService::toResult).filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(r -> rank(r.name(), key))).limit(SHOWN).toList();
        cache.put(key, results);
        return results;
    }

    /** Saves a picked result as a local food, or returns the local food that already has its name. */
    @Transactional
    public Food save(Result picked) {
        return foods.findByNameIgnoreCase(picked.name())
                .orElseGet(() -> foods.save(Food.fromUsda(picked.name(), picked.fatPer100g(), picked.fdcId())));
    }

    /**
     * USDA names run from general to specific ("Egg, whole, raw"), so a name that starts with the query as its own
     * term is the plainest match; next, the query as a later term ("Oil, avocado"); then the query as the start of a
     * longer word. USDA's order is kept within each rank.
     */
    static int rank(String name, String query) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals(query) || n.startsWith(query + ",")) return 0;
        if (n.contains(" " + query + ",") || n.contains("," + query + ",") || n.endsWith(" " + query)) return 1;
        if (n.startsWith(query)) return 2;
        return 3;
    }

    private static Result toResult(UsdaFood food) {
        if (food.foodNutrients() == null || food.description() == null) {
            return null;
        }
        return food.foodNutrients().stream()
                .filter(n -> n.nutrientId() != null && n.nutrientId() == FAT_NUTRIENT_ID && n.value() != null)
                .findFirst()
                .map(n -> new Result(food.fdcId(), food.description(), n.value()))
                .orElse(null);
    }
}
