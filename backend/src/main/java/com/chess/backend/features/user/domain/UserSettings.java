package com.chess.backend.features.user.domain;

import com.chess.backend.features.user.domain.enums.BoardOrientation;
import com.chess.backend.features.user.domain.enums.BoardPalette;
import com.chess.backend.features.user.domain.enums.MotionPreference;
import com.chess.backend.features.user.domain.enums.ThemePreference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "board_palette", nullable = false, columnDefinition = "board_palette")
    private BoardPalette boardPalette = BoardPalette.FOREST;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "board_orientation", nullable = false, columnDefinition = "board_orientation")
    private BoardOrientation boardOrientation = BoardOrientation.AUTO;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "motion_preference", nullable = false, columnDefinition = "motion_preference")
    private MotionPreference motionPreference = MotionPreference.SYSTEM;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "theme_preference", nullable = false, columnDefinition = "theme_preference")
    private ThemePreference themePreference = ThemePreference.DARK;
    @Column(name = "show_coordinates", nullable = false)
    private boolean showCoordinates = true;
    @Column(name = "show_legal_move_hints", nullable = false)
    private boolean showLegalMoveHints = true;
    @Column(name = "premoves_enabled", nullable = false)
    private boolean premovesEnabled = true;
    @Column(name = "move_confirmation", nullable = false)
    private boolean moveConfirmation;
    @Column(name = "game_sounds_enabled", nullable = false)
    private boolean gameSoundsEnabled = true;
    @Column(name = "turn_indicator_enabled", nullable = false)
    private boolean turnIndicatorEnabled = true;
    @Column(name = "turn_sound_enabled", nullable = false)
    private boolean turnSoundEnabled = true;
    @Column(name = "in_app_notifications_enabled", nullable = false)
    private boolean inAppNotificationsEnabled = true;
    @Column(name = "notification_sounds_enabled", nullable = false)
    private boolean notificationSoundsEnabled;

    protected UserSettings() { }

    public UserSettings(User user) { this.user = user; }
    public BoardPalette getBoardPalette() { return boardPalette; }
    public BoardOrientation getBoardOrientation() { return boardOrientation; }
    public MotionPreference getMotionPreference() { return motionPreference; }
    public ThemePreference getThemePreference() { return themePreference; }
    public boolean isShowCoordinates() { return showCoordinates; }
    public boolean isShowLegalMoveHints() { return showLegalMoveHints; }
    public boolean isPremovesEnabled() { return premovesEnabled; }
    public boolean isMoveConfirmation() { return moveConfirmation; }
    public boolean isGameSoundsEnabled() { return gameSoundsEnabled; }
    public boolean isTurnIndicatorEnabled() { return turnIndicatorEnabled; }
    public boolean isTurnSoundEnabled() { return turnSoundEnabled; }
    public boolean isInAppNotificationsEnabled() { return inAppNotificationsEnabled; }
    public boolean isNotificationSoundsEnabled() { return notificationSoundsEnabled; }

    public void update(BoardPalette palette, BoardOrientation orientation, MotionPreference motion, ThemePreference theme,
                       boolean coordinates, boolean legalMoveHints, boolean premoves, boolean confirmMoves,
                       boolean gameSounds, boolean turnIndicator, boolean turnSound,
                       boolean inAppNotifications, boolean notificationSounds) {
        boardPalette = palette;
        boardOrientation = orientation;
        motionPreference = motion;
        themePreference = theme;
        showCoordinates = coordinates;
        showLegalMoveHints = legalMoveHints;
        premovesEnabled = premoves;
        moveConfirmation = confirmMoves;
        gameSoundsEnabled = gameSounds;
        turnIndicatorEnabled = turnIndicator;
        turnSoundEnabled = turnSound;
        inAppNotificationsEnabled = inAppNotifications;
        notificationSoundsEnabled = notificationSounds;
    }
}
