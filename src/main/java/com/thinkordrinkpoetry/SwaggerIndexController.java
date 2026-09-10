package com.thinkordrinkpoetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Provides a useful temporary landing page while Swagger is intentionally public.
 */
@Controller
@ConditionalOnProperty(prefix = "springdoc.swagger-ui", name = "enabled", havingValue = "true")
class SwaggerIndexController {

    @GetMapping("/")
    String index() {
        return "redirect:/swagger-ui.html";
    }
}
