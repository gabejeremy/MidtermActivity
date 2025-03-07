package com.sandiego.googlecontacts.integration.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sandiego.googlecontacts.integration.service.GoogleContactsService;

@RestController
@RequestMapping("/api/contacts")
public class GoogleContactsController {

    private final GoogleContactsService googleContactsService;

    @Autowired
    public GoogleContactsController(GoogleContactsService googleContactsService) {
        if (googleContactsService == null) {
            throw new IllegalArgumentException("GoogleContactsService cannot be null");
        }
        this.googleContactsService = googleContactsService;
    }

    @GetMapping
    public ResponseEntity<?> getAllContacts(@AuthenticationPrincipal OAuth2User principal) {
        try {
            String email = principal.getAttribute("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User email not found in OAuth2 token");
            }
            return ResponseEntity.ok(googleContactsService.getContacts(email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to fetch contacts: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createContact(@AuthenticationPrincipal OAuth2User principal, @RequestBody Map<String, Object> contactData) {
        try {
            String email = principal.getAttribute("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User email not found in OAuth2 token");
            }
            googleContactsService.createContact(email, contactData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to create contact: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateContact(@AuthenticationPrincipal OAuth2User principal, @RequestBody Map<String, Object> updateData) {
        try {
            String email = principal.getAttribute("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User email not found in OAuth2 token");
            }
            
            String resourceName = (String) updateData.get("resourceName");
            String name = (String) updateData.get("name");
            @SuppressWarnings("unchecked")
            List<String> emails = (List<String>) updateData.get("emails");
            @SuppressWarnings("unchecked")
            List<String> phones = (List<String>) updateData.get("phones");

            if (resourceName == null || resourceName.isEmpty()) {
                return ResponseEntity.badRequest().body("Resource name is required");
            }

            if (name == null || name.isEmpty()) {
                return ResponseEntity.badRequest().body("Name is required");
            }

            googleContactsService.updateContact(email, resourceName, name, emails, phones);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to update contact: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteContact(@AuthenticationPrincipal OAuth2User principal, @RequestParam("id") String resourceName) {
        try {
            String email = principal.getAttribute("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User email not found in OAuth2 token");
            }

            // Ensure resourceName has the correct prefix
            String fullResourceName = resourceName.startsWith("people/") ? resourceName : "people/" + resourceName;
            googleContactsService.deleteContact(email, fullResourceName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to delete contact: " + e.getMessage());
        }
    }
}
