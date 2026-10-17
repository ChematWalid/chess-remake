package com.chess.backend.features.user.service;

import com.chess.backend.features.user.domain.UserSettings;
import com.chess.backend.features.user.dto.UserSettingsView;
import com.chess.backend.features.user.exception.UserSettingsNotFoundException;
import com.chess.backend.features.user.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingsService {
    private final UserSettingsRepository settings;

    public UserSettingsService(UserSettingsRepository settings) { this.settings = settings; }

    @Transactional(readOnly = true)
    public UserSettingsView get(long userId) {
        return view(settings.findByUserId(userId)
                .orElseThrow(() -> new UserSettingsNotFoundException("User settings were not found")));
    }

    @Transactional
    public UserSettingsView update(long userId, UserSettingsView request) {
        UserSettings entity = settings.findByUserId(userId)
                .orElseThrow(() -> new UserSettingsNotFoundException("User settings were not found"));
        entity.update(request.boardPalette(), request.boardOrientation(), request.motionPreference(), request.themePreference(),
                request.showCoordinates(), request.showLegalMoveHints(), request.premovesEnabled(),
                request.moveConfirmation(), request.gameSoundsEnabled(), request.turnIndicatorEnabled(),
                request.turnSoundEnabled(), request.inAppNotificationsEnabled(),
                request.notificationSoundsEnabled());
        return view(entity);
    }

    private UserSettingsView view(UserSettings entity) {
        return new UserSettingsView(entity.getBoardPalette(), entity.getBoardOrientation(), entity.getMotionPreference(), entity.getThemePreference(),
                entity.isShowCoordinates(), entity.isShowLegalMoveHints(), entity.isPremovesEnabled(),
                entity.isMoveConfirmation(), entity.isGameSoundsEnabled(), entity.isTurnIndicatorEnabled(),
                entity.isTurnSoundEnabled(), entity.isInAppNotificationsEnabled(),
                entity.isNotificationSoundsEnabled());
    }
}
