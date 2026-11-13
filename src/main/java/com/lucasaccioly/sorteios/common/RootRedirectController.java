package com.lucasaccioly.sorteios.common;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The demo has no front-end: the API root opens the Swagger UI. */
@Hidden
@Controller
class RootRedirectController {

    @GetMapping("/")
    String root() {
        return "redirect:/swagger-ui.html";
    }
}
