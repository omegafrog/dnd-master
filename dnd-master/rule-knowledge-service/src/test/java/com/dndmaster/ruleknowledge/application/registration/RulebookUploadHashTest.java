package com.dndmaster.ruleknowledge.application.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RulebookUploadHashTest {
    @Test
    void hashesRawBytesOnly() {
        byte[] content = "shared bytes".getBytes(StandardCharsets.UTF_8);

        String first = RulebookUploadHash.sha256(content);
        String second = RulebookUploadHash.sha256(content.clone());

        assertEquals(first, second);
        assertEquals("98aa966a36056043cbb7e279cadf62728507e9101f2db4797e4461345fda7a88", first);
    }

    @Test
    void differentBytesProduceDifferentHash() {
        String first = RulebookUploadHash.sha256("alpha".getBytes(StandardCharsets.UTF_8));
        String second = RulebookUploadHash.sha256("beta".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(first, second);
    }
}
