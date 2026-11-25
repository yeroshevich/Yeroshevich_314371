package by.technosoft.bonus.service.integration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.config.SecurityProperties;

/**
 * AES-256-GCM: случайный 12-байтовый вектор инициализации на каждое шифрование,
 * в БД хранится {@code enc:gcm:<base64(iv || шифртекст+тег)>}. Ключ — {@code bonus.security.secret-key}.
 */
@Component
public class AesGcmSecretCipher implements SecretCipher {

    static final String PREFIX = "enc:gcm:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(SecurityProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.secretKey());
        if (raw.length != 32) {
            throw new IllegalStateException("bonus.security.secret-key должен содержать 32 байта (base64)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось зашифровать секрет", e);
        }
    }

    @Override
    public Optional<String> decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_LENGTH));
            return Optional.of(new String(cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
