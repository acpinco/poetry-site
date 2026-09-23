package com.thinkordrinkpoetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Optional legacy redirect for temporarily making Swagger the site root.
 */
@Controller
@ConditionalOnProperty(prefix = "app.swagger-root-redirect", name = "enabled", havingValue = "true")
class SwaggerIndexController {

    @GetMapping("/")
    String index() {
        return "redirect:/swagger-ui.html";
    }
}
