package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiAdminUserPatreonId(@JsonProperty(required = true) String patreonId) {
}
