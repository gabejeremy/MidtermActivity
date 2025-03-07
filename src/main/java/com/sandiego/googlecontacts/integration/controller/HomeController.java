package com.sandiego.googlecontacts.integration.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/error")
    public String error() {
        return "Something happened.";
    }
    
    @GetMapping("/")
    public String home(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            return "redirect:/contacts";
        }
        return "login";
    }

    @GetMapping("/contacts")
    public String contacts(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return "redirect:/";
        }
        model.addAttribute("userName", principal.getAttribute("name"));
        return "contacts"; 
    }
}
