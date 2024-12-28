package com.spring_stream_backend.service;

import com.spring_stream_backend.entity.User;
import com.spring_stream_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    /**
     * A mock repository is a simulated version of a repository (data access layer) that is used for testing purposes.
     * It mimics the behavior of a real repository but without interacting with an actual database.
     * Mock repositories allow you to test your application logic in isolation by providing controlled and
     * predictable responses.
     *
     * Using a mock repository, you can effectively test the service layer of your application without
     * requiring an actual database, thus making your tests faster, more reliable, and easier to manage.
     */
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        System.out.println("Setting up user in beforeEach");
        user = new User(1L, "John Doe");
    }

    @Test
    void getUserByName_ShouldReturnUser() {
        System.out.println("Testing getUserByName");
        when(userRepository.findByName("John Doe")).thenReturn(user);

        User foundUser = userService.getUserByName("John Doe");

        assertNotNull(foundUser);
        assertEquals("John Doe", foundUser.getName());
    }

    @Test
    void createUser_ShouldInvokeSaveMethod() {
        System.out.println("Testing createUser");
        when(userRepository.save(user)).thenReturn(user);
        userService.createUser(user);

        verify(userRepository, times(1)).save(user);
    }

    @Test
    void deleteUser_ShouldThrowException_WhenUserNotFound() {
        Long userId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.deleteUser(userId);
        });

        String expectedMessage = "User not found";
        String actualMessage = exception.getMessage();

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void updateUser_ShouldUpdateUserDetails() {
        Long userId = 1L;
        User existingUser = new User(userId, "John Doe");
        User newUserDetails = new User(userId, "Jane Doe");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        userService.updateUser(userId, newUserDetails);

        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);

        assertEquals("Jane Doe", existingUser.getName());
    }

    @Test
    void deleteUser_ShouldCallFindByIdAndDelete_InOrder() {
        Long userId = 1L;
        User user = new User(userId, "John Doe");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).findById(userId);
        inOrder.verify(userRepository).delete(user);
    }

    @Test
    void getUserByName_ShouldNotInteractWithRepository_WhenNameIsNull() {
        userService.getUserByName(null);

        verify(userRepository, never()).findByName(anyString());
    }
}