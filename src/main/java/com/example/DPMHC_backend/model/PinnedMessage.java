package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pinned_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "message_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinnedMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
}
