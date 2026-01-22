package com.dornach.user.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for RFC 7807 (Problem Details) responses.
 *
 * TODO (Step 1 - Exercise 4): Implement error handling
 *
 * 1. Handle MethodArgumentNotValidException (validation errors)
 *    - Return 400 Bad Request with RFC 7807 format
 *    - Include field-level errors in the response
 *
 * 2. Handle UserNotFoundException
 *    - Return 404 Not Found with RFC 7807 format
 *
 * Example RFC 7807 response:
 * {
 *   "type": "https://api.dornach.com/errors/validation-error",
 *   "title": "Validation Error",
 *   "status": 400,
 *   "detail": "One or more fields failed validation",
 *   "instance": "/users",
 *   "errors": {
 *     "email": "Email must be valid",
 *     "firstName": "First name is required"
 *   }
 * }
 *
 * Hint: Use ProblemDetail class from Spring Framework
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // TODO: Add @ExceptionHandler for MethodArgumentNotValidException
    // public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
    //     ...
    // }

    // TODO: Add @ExceptionHandler for UserNotFoundException
    // public ProblemDetail handleUserNotFoundException(UserNotFoundException ex) {
    //     ...
    // }
}
