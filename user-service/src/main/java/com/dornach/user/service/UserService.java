package com.dornach.user.service;

import com.dornach.user.domain.User;
import com.dornach.user.domain.UserStatus;
import com.dornach.user.dto.CreateUserRequest;
import com.dornach.user.dto.UpdateUserRequest;
import com.dornach.user.exception.EmailAlreadyExistsException;
import com.dornach.user.exception.UserNotFoundException;
import com.dornach.user.mapper.UserMapper;
import com.dornach.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public User createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = userMapper.toEntity(request);
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        log.info("User created with id: {}", saved.getId());

        return saved;
    }

    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        log.debug("Fetching user by id: {}", id);

        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(null));
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        log.debug("Fetching all users");

        return userRepository.findAll();
    }

    public User updateUser(UUID id, UpdateUserRequest request) {
        log.info("Updating user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email());
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }

        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }

        if (request.role() != null) {
            user.setRole(request.role());
        }

        if (request.status() != null) {
            user.setStatus(request.status());
        }

        return userRepository.save(user);
    }

    public void deleteUser(UUID id) {
        log.info("Deleting user: {}", id);

        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userRepository.deleteById(id);
    }
}
