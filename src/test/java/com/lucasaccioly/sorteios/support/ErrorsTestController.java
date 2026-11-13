package com.lucasaccioly.sorteios.support;

import com.lucasaccioly.sorteios.common.BusinessException;
import com.lucasaccioly.sorteios.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Test-only endpoints that raise each error type handled by ApiExceptionHandler. */
@RestController
@RequestMapping("/test/errors")
public class ErrorsTestController {

    public record Body(@NotBlank(message = "Informe o nome") String name) {}

    @GetMapping("/business")
    public void business() {
        throw new BusinessException("Regra violada", Map.of("unavailableNumbers", List.of(4, 9)));
    }

    @GetMapping("/not-found")
    public void notFound() {
        throw new NotFoundException();
    }

    @PostMapping("/body")
    public Body body(@Valid @RequestBody Body body) {
        return body;
    }
}
