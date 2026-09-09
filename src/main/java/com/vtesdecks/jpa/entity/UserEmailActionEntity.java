package com.vtesdecks.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_email_action")
@Getter
@Setter
public class UserEmailActionEntity {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(nullable = false, length = 32)
    private String purpose;
    @Column(name = "target_email", nullable = false, length = 320)
    private String targetEmail;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
