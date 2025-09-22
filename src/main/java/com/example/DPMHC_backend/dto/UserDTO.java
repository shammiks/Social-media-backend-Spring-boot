package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String profileImageUrl;
    private Boolean isOnline;
    private LocalDateTime lastSeen;
    private Date createdAt;
    private String email;
    private String avatar;
    private String bio;
    
    @JsonProperty("isAdmin")  // Ensure JSON field is "isAdmin" not "admin"
    private boolean isAdmin;
    
    // Explicit getter to ensure correct JSON property name
    @JsonProperty("isAdmin")
    public boolean isAdmin() {
        return this.isAdmin;
    }
    
    // Explicit setter to ensure correct JSON property name  
    @JsonProperty("isAdmin")
    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.profileImageUrl = user.getProfileImageUrl();
        this.avatar = user.getAvatar();
        this.bio = user.getBio();
        this.createdAt = user.getCreatedAt();
        this.isAdmin = user.isAdmin();  // Fix: Set isAdmin from user entity
        // isOnline and lastSeen would be set by the service layer
    }

    public static UserDTO fromEntity(User user) {
        return new UserDTO(user);
    }

    // Constructor for basic user info (for reactions, messages, etc.)
    public UserDTO(Long id, String username, String profileImageUrl) {
        this.id = id;
        this.username = username;
        this.profileImageUrl = profileImageUrl;
    }
}
