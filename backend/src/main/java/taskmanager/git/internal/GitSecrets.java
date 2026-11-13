package taskmanager.git.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts the secrets of the applications and the tokens of the connections with the key of the operation (TASK-111), as
 * the helpdesk does with the mailbox passwords: AES-256-GCM with a random nonce of 12 bytes in front of the cipher text. The
 * associated data names what the value belongs to (an application secret of one provider, an access or refresh token of one
 * person at one provider), so a cipher text copied to another row does not open. Without a valid key nothing is encrypted
 * or decrypted.
 */
@Component
class GitSecrets {
    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    GitSecrets(GitProperties properties) {
        this.key = parse(properties.secretKey());
    }

    /** No key is set. */
    static final class KeyMissing extends RuntimeException {
        private static final long serialVersionUID = 1L;

        KeyMissing() {
            super("TASK_MANAGER_SECRET_KEY is not set to 32 bytes in Base64");
        }
    }

    /** The cipher text does not open with this key and purpose: another key, or a damaged value. */
    static final class Unreadable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unreadable(Throwable cause) {
            super("A stored secret could not be decrypted", cause);
        }
    }

    boolean available() {
        return key != null;
    }

    byte[] seal(String purpose, String secret) {
        if (key == null) {
            throw new KeyMissing();
        }
        byte[] nonce = new byte[NONCE];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associated(purpose));
            byte[] sealed = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(NONCE + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM is not available", failure);
        }
    }

    String open(String purpose, byte[] sealed) {
        if (key == null) {
            throw new KeyMissing();
        }
        if (sealed == null || sealed.length <= NONCE) {
            throw new Unreadable(null);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE));
            cipher.updateAAD(associated(purpose));
            return new String(cipher.doFinal(sealed, NONCE, sealed.length - NONCE), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new Unreadable(failure);
        }
    }

    private static byte[] associated(String purpose) {
        return ("git:" + purpose).getBytes(StandardCharsets.US_ASCII);
    }

    private static SecretKeySpec parse(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.strip());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("TASK_MANAGER_SECRET_KEY must be Base64");
        }
        if (bytes.length != 32) {
            throw new IllegalArgumentException("TASK_MANAGER_SECRET_KEY must hold exactly 32 bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
