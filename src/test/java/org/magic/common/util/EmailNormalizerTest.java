package org.magic.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void shouldCollapseGmailDotVariants() {
        assertEquals("joe@gmail.com", EmailNormalizer.normalize("j.o.e@gmail.com"));
        assertEquals("joe@gmail.com", EmailNormalizer.normalize("J.O.E@GMAIL.COM"));
        assertEquals("joe@gmail.com", EmailNormalizer.normalize("joe@gmail.com"));
    }

    @Test
    void shouldStripGmailPlusTags() {
        assertEquals("joe@gmail.com", EmailNormalizer.normalize("joe+spam@gmail.com"));
        assertEquals("joe@gmail.com", EmailNormalizer.normalize("j.o.e+newsletter+x@googlemail.com"));
    }

    @Test
    void shouldTreatGooglemailAsGmail() {
        assertEquals(EmailNormalizer.normalize("joe@gmail.com"),
                EmailNormalizer.normalize("joe@googlemail.com"));
    }

    @Test
    void shouldOnlyLowercaseOtherProviders() {
        assertEquals("j.o.e@proton.me", EmailNormalizer.normalize("J.O.E@Proton.Me"));
        assertEquals("j.o.e+x@proton.me", EmailNormalizer.normalize("j.o.e+x@proton.me"));
    }

    @Test
    void shouldHandleUnparseableInput() {
        assertNull(EmailNormalizer.normalize(null));
        assertEquals("", EmailNormalizer.normalize("   "));
        assertEquals("not-an-email", EmailNormalizer.normalize("not-an-email"));
        assertEquals("@domain.com", EmailNormalizer.normalize("@domain.com"));
        assertEquals("user@", EmailNormalizer.normalize("user@"));
    }

    @Test
    void shouldTrimWhitespace() {
        assertEquals("joe@gmail.com", EmailNormalizer.normalize("  joe@gmail.com  "));
    }
}
