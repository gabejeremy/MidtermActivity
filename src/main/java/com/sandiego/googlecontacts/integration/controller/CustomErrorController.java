package com.sandiego.googlecontacts.integration.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CustomErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(value = {"/error", "/error/**"})
    public String handleError(HttpServletRequest request, Model model) {
        // Get the error status
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR; // Default to 500
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.valueOf(statusCode);
        }

        // Get detailed error attributes
        WebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> errorAttributes = this.errorAttributes.getErrorAttributes(webRequest, 
            ErrorAttributeOptions.of(
                ErrorAttributeOptions.Include.MESSAGE,
                ErrorAttributeOptions.Include.BINDING_ERRORS,
                ErrorAttributeOptions.Include.EXCEPTION
            ));

        // Get the original error message
        String errorMessage = (String) errorAttributes.get("message");
        String path = (String) errorAttributes.get("path");

        // Add user-friendly error message based on status code
        String userFriendlyMessage = switch (httpStatus) {
            case NOT_FOUND -> {
                if (errorMessage != null && errorMessage.contains("No static resource")) {
                    yield "The page '" + path + "' could not be found.";
                }
                yield "The page you're looking for could not be found.";
            }
            case FORBIDDEN -> "You don't have permission to access this resource.";
            case UNAUTHORIZED -> "Please log in to access this resource.";
            case BAD_REQUEST -> "The request could not be understood by the server.";
            case GATEWAY_TIMEOUT -> "The server took too long to respond. Please try again.";
            case SERVICE_UNAVAILABLE -> "The service is temporarily unavailable. Please try again later.";
            default -> "An unexpected error occurred. Please try again later.";
        };

        // Add formatted timestamp
        LocalDateTime timestamp = LocalDateTime.now();
        String formattedTimestamp = timestamp.format(formatter);

        // Add all necessary attributes to the model
        model.addAttribute("timestamp", formattedTimestamp);
        model.addAttribute("status", httpStatus.value());
        model.addAttribute("error", userFriendlyMessage);
        model.addAttribute("path", path);
        model.addAttribute("message", errorMessage); // Add the original error message for debugging

        return "error";
    }
}
