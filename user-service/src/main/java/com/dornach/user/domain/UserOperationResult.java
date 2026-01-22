package com.dornach.user.domain;

import java.util.UUID;

/**
 * Sealed interface for type-safe operation results.
 * Demonstrates Java 21 pattern matching with exhaustive switch expressions.
 *
 * Usage example:
 * <pre>
 * return switch (result) {
 *     case Success(var user) -> ResponseEntity.ok(toResponse(user));
 *     case NotFound(var id) -> ResponseEntity.notFound().build();
 *     case ValidationError(var msg) -> ResponseEntity.badRequest().body(msg);
 *     case Conflict(var msg) -> ResponseEntity.status(409).body(msg);
 * };
 * </pre>
 */
public sealed interface UserOperationResult {

    record Success(User user) implements UserOperationResult {}

    record NotFound(UUID userId) implements UserOperationResult {}

    record ValidationError(String message) implements UserOperationResult {}

    record Conflict(String message) implements UserOperationResult {}
}
