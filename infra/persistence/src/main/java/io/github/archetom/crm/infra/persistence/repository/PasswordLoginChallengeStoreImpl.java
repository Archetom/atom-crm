package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.application.port.out.authentication.PasswordLoginChallengeStore;
import io.github.archetom.crm.infra.persistence.mysql.mapper.PasswordLoginChallengeMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.PasswordLoginChallengePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** MySQL-backed, hash-only store for atomically consuming signed login challenges once. */
@Repository
@RequiredArgsConstructor
public class PasswordLoginChallengeStoreImpl implements PasswordLoginChallengeStore {

    private static final byte[] PURPOSE = "atom-crm:password-login-challenge:v1\u0000"
            .getBytes(StandardCharsets.US_ASCII);

    private final PasswordLoginChallengeMapper mapper;

    @Override
    public void issue(String challenge, Instant expiresAt) {
        if (challenge == null || challenge.isBlank() || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Password login challenge is invalid");
        }
        Instant now = Instant.now();
        mapper.delete(new LambdaQueryWrapper<PasswordLoginChallengePO>()
                .le(PasswordLoginChallengePO::getExpiresAt, LocalDateTime.ofInstant(now, ZoneOffset.UTC)));

        PasswordLoginChallengePO challengePO = new PasswordLoginChallengePO();
        challengePO.setChallengeDigest(digest(challenge));
        challengePO.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        challengePO.setCreatedTime(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        if (mapper.insert(challengePO) != 1 || challengePO.getId() == null) {
            throw new IllegalStateException("Password login challenge was not persisted");
        }
    }

    @Override
    public boolean consume(String challenge, Instant now) {
        if (challenge == null || challenge.isBlank() || now == null) return false;
        return mapper.delete(new LambdaQueryWrapper<PasswordLoginChallengePO>()
                .eq(PasswordLoginChallengePO::getChallengeDigest, digest(challenge))
                .gt(PasswordLoginChallengePO::getExpiresAt, LocalDateTime.ofInstant(now, ZoneOffset.UTC))) == 1;
    }

    private static String digest(String challenge) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PURPOSE);
            digest.update(challenge.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
