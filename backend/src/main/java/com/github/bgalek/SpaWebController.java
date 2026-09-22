package com.github.bgalek;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaWebController {

    @GetMapping(value = {"/", "/register", "/login", "/leaderboard", "/admin", "/admin/**", "/leaderboard/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
