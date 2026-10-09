package com.vtesdecks.jpa.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.jpa.entity.converter.JsonNodeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "archon_user")
public class ArchonUserEntity {
    @Id
    @Column(name = "vekn_id", length = 64)
    private String veknId;
    @Column(name = "archon_user_uid", nullable = false, length = 36)
    private String archonUserUid;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "alias")
    private String alias;
    @Column(name = "country", length = 2)
    private String country;
    @Column(name = "city")
    private String city;
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "roles", nullable = false, columnDefinition = "json")
    private JsonNode roles;
    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false)
    private LocalDateTime modificationDate;
}
