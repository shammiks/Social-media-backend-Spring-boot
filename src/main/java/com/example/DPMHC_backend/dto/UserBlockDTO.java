package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.UserBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBlockDTO {
    private Long id;
    private UserDTO blocker;
    private UserDTO blocked;
    private LocalDateTime blockedAt;
    private Boolean isActive;

    public UserBlockDTO(UserBlock userBlock) {
        this.id = userBlock.getId();
        this.blocker = new UserDTO(userBlock.getBlocker());
        this.blocked = new UserDTO(userBlock.getBlocked());
        this.blockedAt = userBlock.getBlockedAt();
        this.isActive = userBlock.getIsActive();
    }

    public static UserBlockDTO fromEntity(UserBlock userBlock) {
        return new UserBlockDTO(userBlock);
    }
}
