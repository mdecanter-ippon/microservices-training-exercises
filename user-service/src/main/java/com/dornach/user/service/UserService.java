package com.dornach.user.service;

import com.dornach.user.domain.User;
import com.dornach.user.dto.CreateUserRequest;
import com.dornach.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(UUID id) {
        // TODO (Step 1 - Exercise 3):
        // Return the user if found, otherwise throw UserNotFoundException
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public User createUser(CreateUserRequest request) {
        // TODO (Step 1 - Exercise 3):
        // 1. Check if email already exists (throw exception if so)
        // 2. Create a new User entity from the request
        // 3. Save and return the user

        User user = new User(
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getRole()
        );

        return userRepository.save(user);
    }

    public User updateUser(UUID id, CreateUserRequest request) {
        User user = getUserById(id);
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());
        return userRepository.save(user);
    }

    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }
}
