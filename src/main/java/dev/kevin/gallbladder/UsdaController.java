package dev.kevin.gallbladder;

import dev.kevin.gallbladder.UsdaService.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** JSON endpoints for the food search on the meal form. */
@RestController
public class UsdaController {

    private final UsdaService usda;

    public UsdaController(UsdaService usda) {
        this.usda = usda;
    }

    @GetMapping("/foods/usda")
    List<Result> search(@RequestParam String q) {
        return q.isBlank() ? List.of() : usda.search(q);
    }

    /** Saves the picked result and answers with the food's local name and fat, for the form to fill in. */
    @PostMapping("/foods/usda")
    Map<String, Object> pick(@RequestParam long fdcId, @RequestParam String name, @RequestParam BigDecimal fatPer100g) {
        if (name.isBlank() || fatPer100g.signum() < 0 || fatPer100g.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Food food = usda.save(new Result(fdcId, name.trim(), fatPer100g));
        return Map.of("name", food.getName(), "fatPer100g", food.getFatPer100g());
    }

    @ExceptionHandler(UsdaService.UnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(UsdaService.UnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }
}
