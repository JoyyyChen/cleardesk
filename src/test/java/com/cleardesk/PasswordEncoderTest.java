package com.cleardesk;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BCrypt 密码编码测试，不依赖 Spring 容器。
 */
class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void shouldMatchRawPassword() {
        String encoded = passwordEncoder.encode("12345678");
        assertTrue(passwordEncoder.matches("12345678", encoded));
        assertFalse(passwordEncoder.matches("wrong-password", encoded));
    }

    @Test
    void shouldGenerateDifferentHashEachTime() {
        String first = passwordEncoder.encode("12345678");
        String second = passwordEncoder.encode("12345678");
        assertNotEquals(first, second);
        assertTrue(passwordEncoder.matches("12345678", first));
        assertTrue(passwordEncoder.matches("12345678", second));
    }
}
