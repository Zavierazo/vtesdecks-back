package com.vtesdecks.model.archon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchonUser {
    private String uid;
    private String veknId;
    private String name;
    private String nickname;
    private String country;
    private String city;
    private List<String> roles;
    private String deletedAt;
}
