package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_follower")
@Data
@EqualsAndHashCode(exclude = {"creationDate"})
public class UserFollowerEntity {

    @EmbeddedId
    private UserFollowerId id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private UserEntity user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_id", referencedColumnName = "id", insertable = false, updatable = false)
    private UserEntity followed;


    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserFollowerId implements Serializable {

        @Column(name = "user_id", nullable = false)
        private Integer userId;

        @Column(name = "followed_id", nullable = false)
        private Integer followedId;
    }
}

