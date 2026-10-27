package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.application.port.out.authentication.PasswordResetTokenStore;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.PasswordResetTokenMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.PasswordResetTokenPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** MySQL-backed, hash-only store for one-use password-reset capabilities. */
@Repository
@RequiredArgsConstructor
public class PasswordResetTokenStoreImpl implements PasswordResetTokenStore {

    private static final byte[] PURPOSE = "atom-crm:password-reset:v1\u0000"
            .getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenMapper mapper;

    @Override
    public IssuedPasswordResetToken issue(TenantId sourceTenantId, UserId actorId, Instant expiresAt) {
        if (sourceTenantId == null || actorId == null || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Password reset token subject is invalid");
        }
        String rawToken = randomToken();
        PasswordResetTokenPO po = new PasswordResetTokenPO();
        po.setSourceTenantId(sourceTenantId.getValue());
        po.setActorId(actorId.getValue());
        po.setTokenDigest(digest(rawToken));
        po.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        po.setCreatedTime(LocalDateTime.now(ZoneOffset.UTC));
        if (mapper.insert(po) != 1 || po.getId() == null) {
            throw new IllegalStateException("Password reset token was not persisted");
        }
        return new IssuedPasswordResetToken(rawToken, expiresAt);
    }

    @Override
    public Optional<ActivePasswordResetToken> findActiveForUpdate(String rawToken, Instant now) {
        if (rawToken == null || now == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        PasswordResetTokenPO po = mapper.selectOne(new LambdaQueryWrapper<PasswordResetTokenPO>()
                .eq(PasswordResetTokenPO::getTokenDigest, digest(rawToken))
                .isNull(PasswordResetTokenPO::getConsumedAt)
                .gt(PasswordResetTokenPO::getExpiresAt, LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .last("FOR UPDATE"));
        if (po == null) return Optional.empty();
        return Optional.of(new ActivePasswordResetToken(po.getId(), new TenantId(po.getSourceTenantId()),
                new UserId(po.getActorId())));
    }

    @Override
    public void markConsumed(long tokenId, Instant consumedAt) {
        if (tokenId <= 0 || consumedAt == null) {
            throw new IllegalArgumentException("Password reset token consumption is invalid");
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<PasswordResetTokenPO>()
                .eq(PasswordResetTokenPO::getId, tokenId)
                .isNull(PasswordResetTokenPO::getConsumedAt)
                .set(PasswordResetTokenPO::getConsumedAt, LocalDateTime.ofInstant(consumedAt, ZoneOffset.UTC)));
        if (updated != 1) {
            throw new IllegalStateException("Password reset token could not be consumed");
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PURPOSE);
            digest.update(rawToken.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
