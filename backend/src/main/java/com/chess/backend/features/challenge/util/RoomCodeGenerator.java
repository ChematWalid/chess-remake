package com.chess.backend.features.challenge.util;

import com.chess.backend.features.challenge.exception.RoomCodeAllocationException;
import com.chess.backend.features.challenge.repository.GameChallengeRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RoomCodeGenerator {
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;
    private final SecureRandom random = new SecureRandom();
    private final GameChallengeRepository challenges;

    public RoomCodeGenerator(GameChallengeRepository challenges) {
        this.challenges = challenges;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = nextCode();
            if (!challenges.existsByRoomCode(code)) {
                return code;
            }
        }
        throw new RoomCodeAllocationException("Could not allocate a unique game room code");
    }

    public boolean randomBoolean() {
        return random.nextBoolean();
    }

    private String nextCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
}
