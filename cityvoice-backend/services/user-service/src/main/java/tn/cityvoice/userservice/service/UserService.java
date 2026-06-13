package tn.cityvoice.userservice.service;

import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.AgentStatus;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User register(User user, String rawPassword);
    User findById(UUID id);
    List<User> findByRole(String role);  // ← AJOUTER CETTE LIGNE

    User findByEmail(String email);
    List<User> findAll();
    User update(UUID id, User updated);
    void delete(UUID id);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
    void updatePhoto(UUID userId, String base64Photo);
    String getStatut(User user);
    int getCivicIndex(User user);
    void updateAgentStatus(UUID userId, AgentStatus status);
}