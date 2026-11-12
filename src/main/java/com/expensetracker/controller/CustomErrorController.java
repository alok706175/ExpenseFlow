package com.expensetracker.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        String errorMessage = "An unexpected error occurred.";
        if (message != null && !message.toString().isEmpty()) {
            errorMessage = message.toString();
        } else if (exception != null) {
            errorMessage = ((Throwable) exception).getMessage();
        }

        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException ignored) {}
        }

        // Check if request is from browser UI (HTML) or API (JSON)
        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/html")) {
            // Friendly redirect to the frontend with error parameter
            response.sendRedirect("/index.html?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8));
            return null;
        }

        // Return JSON error response for API callers
        Map<String, Object> body = new HashMap<>();
        body.put("status", statusCode);
        body.put("message", errorMessage);
        return new ResponseEntity<>(body, HttpStatus.valueOf(statusCode));
    }
}
