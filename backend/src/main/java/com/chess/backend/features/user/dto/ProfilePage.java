package com.chess.backend.features.user.dto;

import java.util.List;

public record ProfilePage<T>(List<T> items, int page, int size, long totalElements) {
}
