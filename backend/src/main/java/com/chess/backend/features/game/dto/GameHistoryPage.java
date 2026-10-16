package com.chess.backend.features.game.dto;

import java.util.List;

public record GameHistoryPage<T>(List<T> items, int page, int size, long totalElements) {
}
