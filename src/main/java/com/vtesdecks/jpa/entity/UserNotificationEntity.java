package com.vtesdecks.jpa.entity;

import com.vtesdecks.enums.UserNotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification")
@Data
@EqualsAndHashCode(exclude = {"creationDate"})
public class UserNotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "reference_id")
    private Integer referenceId;

    @Column(name = "user")
    private Integer user;

    @Column(name = "`read`")
    private Boolean read;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private UserNotificationType type;

    @Column(name = "notification")
    private String notification;

    @Column(name = "link")
    private String link;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
}