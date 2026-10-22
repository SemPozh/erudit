package com.erudit.user;

import com.erudit.user.service.BcryptPasswordHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {
    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

    @Test
    void hashesWithRandomSaltAndVerifiesExistingBcryptFormat() {
        String first = hasher.encode("correct horse battery staple");
        String second = hasher.encode("correct horse battery staple");

        assertThat(first).startsWith("$2a$").isNotEqualTo(second);
        assertThat(hasher.matches("correct horse battery staple", first)).isTrue();
        assertThat(hasher.matches("wrong password", first)).isFalse();
        assertThat(hasher.matches("correct horse battery staple", "malformed")).isFalse();
    }
}
