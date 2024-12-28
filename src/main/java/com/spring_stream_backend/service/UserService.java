package com.spring_stream_backend.service;

import com.spring_stream_backend.entity.User;
import com.spring_stream_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUserByName(String name) {
        System.out.println("Getting user by name");
        return userRepository.findByName(name);
    }

    public void createUser(User user) {
        System.out.println("Creating user");
        userRepository.save(user);
    }

    public void updateUser(Long id, User newUserDetails) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setName(newUserDetails.getName());
        userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        userRepository.delete(user);
    }
}