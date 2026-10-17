package com.chess.backend.features.user.dto;

import com.chess.backend.features.user.domain.enums.BoardOrientation;
import com.chess.backend.features.user.domain.enums.BoardPalette;
import com.chess.backend.features.user.domain.enums.MotionPreference;
import com.chess.backend.features.user.domain.enums.ThemePreference;
import jakarta.validation.constraints.NotNull;

public record UserSettingsView(@NotNull BoardPalette boardPalette, @NotNull BoardOrientation boardOrientation,
        @NotNull MotionPreference motionPreference, @NotNull ThemePreference themePreference,
        boolean showCoordinates, boolean showLegalMoveHints,
        boolean premovesEnabled, boolean moveConfirmation, boolean gameSoundsEnabled,
        boolean inAppNotificationsEnabled, boolean notificationSoundsEnabled) { }
