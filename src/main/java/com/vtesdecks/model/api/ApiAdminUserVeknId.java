package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiAdminUserVeknId(@JsonProperty(required = true) String veknId) {
}
