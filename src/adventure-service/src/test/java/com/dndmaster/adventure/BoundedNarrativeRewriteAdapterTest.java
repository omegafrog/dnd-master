package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.BoundedNarrativeRewriteAdapter;
import com.dndmaster.adventure.application.runtime.RewriteContext;
import com.dndmaster.adventure.application.runtime.VerificationSeverity;
import com.dndmaster.adventure.application.runtime.VerificationViolation;
import com.dndmaster.adventure.application.runtime.VerificationViolationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedNarrativeRewriteAdapterTest {
    @Test
    void removes_violation_evidence_case_insensitively_without_regex_interpretation() {
        var context = new RewriteContext("The Dragon [secret] escaped; dragon [secret] remains.", List.of(
                new VerificationViolation(VerificationViolationType.SECRET_LEAK, VerificationSeverity.ERROR,
                        "dragon [secret]", "draft", "remove it")), "fingerprint", 0);

        assertEquals("The  escaped;  remains.", new BoundedNarrativeRewriteAdapter().rewrite(context).prose());
    }
}
