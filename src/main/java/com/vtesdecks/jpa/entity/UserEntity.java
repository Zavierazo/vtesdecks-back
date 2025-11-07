package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "login_hash", nullable = false)
    private String loginHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "validated", nullable = false)
    private Boolean validated;

    @Column(name = "admin", nullable = false)
    private Boolean admin;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;

    @Column(name = "forgot_password_date")
    private LocalDateTime forgotPasswordDate;
}