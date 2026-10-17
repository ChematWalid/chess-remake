package com.chess.backend.features.user.dto;

import com.chess.backend.features.user.domain.enums.BoardOrientation;
import com.chess.backend.features.user.domain.enums.BoardPalette;
import com.chess.backend.features.user.domain.enums.MotionPreference;
import jakarta.validation.constraints.NotNull;

public record UserSettingsView(@NotNull BoardPalette boardPalette, @NotNull BoardOrientation boardOrientation,
        @NotNull MotionPreference motionPreference, boolean showCoordinates, boolean showLegalMoveHints,
        boolean premovesEnabled, boolean moveConfirmation, boolean gameSoundsEnabled,
        boolean inAppNotificationsEnabled, boolean notificationSoundsEnabled) { }
