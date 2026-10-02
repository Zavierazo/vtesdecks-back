package com.vtesdecks.model.api;

/** Advisory similarity match, without card requirements or a minimum score. */
public record ApiNearestArchetype(Integer id, String name, double similarity) {
}
