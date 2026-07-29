package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.InetSocketAddressValidator;

class InetSocketAddressValidatorTest {

    @Test
    void shouldAllowCorrectAddress() {
        assertThat(InetSocketAddressValidator.getValidAddress("localhost:8081")).isPresent();
        assertThat(InetSocketAddressValidator.getValidAddress("127.0.0.1:8081")).isPresent();
        assertThat(InetSocketAddressValidator.getValidAddress("[::1]:8081")).isPresent();
        assertThat(InetSocketAddressValidator.getValidAddress("artemis.cit.tum.de:8081")).isPresent();
    }

    @Test
    void shouldDenyIncorrectAddress() {
        assertThat(InetSocketAddressValidator.getValidAddress("localhost:8081A")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("A127.0.0.1:8081")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("A[::1]:8081")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("artemis.cit.tum.de:8081A")).isEmpty();

        assertThat(InetSocketAddressValidator.getValidAddress("localhost")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("127.0.0.1")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("[::1]")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("artemis.cit.tum.de")).isEmpty();
    }
}
