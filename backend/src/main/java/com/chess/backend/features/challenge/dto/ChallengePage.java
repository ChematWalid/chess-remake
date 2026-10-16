package com.chess.backend.features.challenge.dto;

import java.util.List;

public record ChallengePage(List<ChallengeSummary> items, int page, int size, long totalElements) {
}
