package com.sandiego.googlecontacts.integration.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.EmailAddress;
import com.google.api.services.people.v1.model.ListConnectionsResponse;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.PhoneNumber;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class GoogleContactsService {

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    private String getAccessToken(String userName) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient("google", userName);
        if (client == null) {
            throw new RuntimeException("Failed to get access token - client not authorized. Please log out and log in again.");
        }
        OAuth2AccessToken accessToken = client.getAccessToken();
        if (accessToken == null || accessToken.getTokenValue() == null) {
            throw new RuntimeException("Failed to get access token - token is null. Please log out and log in again.");
        }
        if (accessToken.getExpiresAt() != null && accessToken.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Access token has expired. Please log out and log in again.");
        }
        return accessToken.getTokenValue();
    }

    private PeopleService getPeopleService(String userName) {
        try {
            String accessToken = getAccessToken(userName);
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            
            Credentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            return new PeopleService.Builder(httpTransport, jsonFactory, requestInitializer)
                    .setApplicationName("Google Contacts Integration")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PeopleService: " + e.getMessage(), e);
        }
    }

    public List<Map<String, String>> getContacts(String userName) {
        try {
            PeopleService peopleService = getPeopleService(userName);
            ListConnectionsResponse response = peopleService.people().connections()
                    .list("people/me")
                    .setPersonFields("names,emailAddresses,phoneNumbers")
                    .setPageSize(100)
                    .execute();

            List<Map<String, String>> contacts = new ArrayList<>();
            List<Person> connections = response.getConnections();
            if (connections != null) {
                for (Person person : connections) {
                    Map<String, String> contact = new HashMap<>();
                    contact.put("resourceName", person.getResourceName());
                    contact.put("etag", person.getEtag());

                    // Get name
                    List<Name> names = person.getNames();
                    if (names != null && !names.isEmpty()) {
                        contact.put("name", names.get(0).getDisplayName());
                    }

                    // Get emails
                    List<EmailAddress> emails = person.getEmailAddresses();
                    if (emails != null && !emails.isEmpty()) {
                        contact.put("emails", emails.stream()
                                .map(EmailAddress::getValue)
                                .collect(Collectors.joining(", ")));
                    }

                    // Get phones
                    List<PhoneNumber> phones = person.getPhoneNumbers();
                    if (phones != null && !phones.isEmpty()) {
                        contact.put("phones", phones.stream()
                                .map(PhoneNumber::getValue)
                                .collect(Collectors.joining(", ")));
                    }

                    contacts.add(contact);
                }
            }
            return contacts;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get contacts: " + e.getMessage(), e);
        }
    }

    public void createContact(String userName, Map<String, Object> contactData) {
        try {
            PeopleService peopleService = getPeopleService(userName);
            
            Person person = new Person();
            
            // Set name
            String name = (String) contactData.get("name");
            if (name != null && !name.isEmpty()) {
                Name personName = new Name()
                    .setGivenName(name)
                    .setDisplayName(name);
                person.setNames(Collections.singletonList(personName));
            }
            
            // Set emails
            @SuppressWarnings("unchecked")
            List<String> emails = (List<String>) contactData.get("emails");
            if (emails != null && !emails.isEmpty()) {
                List<EmailAddress> emailAddresses = emails.stream()
                    .map(email -> new EmailAddress().setValue(email))
                    .collect(Collectors.toList());
                person.setEmailAddresses(emailAddresses);
            }
            
            // Set phones
            @SuppressWarnings("unchecked")
            List<String> phones = (List<String>) contactData.get("phones");
            if (phones != null && !phones.isEmpty()) {
                List<PhoneNumber> phoneNumbers = phones.stream()
                    .map(phone -> new PhoneNumber().setValue(phone))
                    .collect(Collectors.toList());
                person.setPhoneNumbers(phoneNumbers);
            }
            
            peopleService.people()
                .createContact(person)
                .setPersonFields("names,emailAddresses,phoneNumbers")
                .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create contact: " + e.getMessage(), e);
        }
    }

    public void updateContact(String userName, String resourceName, String name, List<String> emails, List<String> phones) {
        try {
            PeopleService peopleService = getPeopleService(userName);
            
            // First get the existing contact to get the etag
            Person existingContact = peopleService.people().get(resourceName)
                .setPersonFields("names,emailAddresses,phoneNumbers")
                .execute();
            
            Person person = new Person();
            person.setEtag(existingContact.getEtag());
            
            // Set name
            if (name != null && !name.isEmpty()) {
                Name personName = new Name()
                    .setGivenName(name)
                    .setDisplayName(name);
                person.setNames(Collections.singletonList(personName));
            }
            
            // Set emails
            if (emails != null && !emails.isEmpty()) {
                List<EmailAddress> emailAddresses = emails.stream()
                    .map(email -> new EmailAddress().setValue(email))
                    .collect(Collectors.toList());
                person.setEmailAddresses(emailAddresses);
            }
            
            // Set phones
            if (phones != null && !phones.isEmpty()) {
                List<PhoneNumber> phoneNumbers = phones.stream()
                    .map(phone -> new PhoneNumber().setValue(phone))
                    .collect(Collectors.toList());
                person.setPhoneNumbers(phoneNumbers);
            }
            
            // Ensure resourceName has the correct prefix
            String fullResourceName = resourceName.startsWith("people/") ? resourceName : "people/" + resourceName;
            
            peopleService.people()
                .updateContact(fullResourceName, person)
                .setUpdatePersonFields("names,emailAddresses,phoneNumbers")
                .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update contact: " + e.getMessage(), e);
        }
    }

    public void deleteContact(String userName, String resourceName) {
        try {
            PeopleService peopleService = getPeopleService(userName);
            peopleService.people().deleteContact(resourceName).execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete contact: " + e.getMessage(), e);
        }
    }
}
