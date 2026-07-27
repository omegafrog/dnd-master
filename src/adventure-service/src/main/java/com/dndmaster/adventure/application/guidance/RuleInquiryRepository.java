package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.inquiry.InquiryId;
import com.dndmaster.adventure.domain.inquiry.RuleInquiry;
import java.util.Optional;

public interface RuleInquiryRepository {
    Optional<RuleInquiry> findById(InquiryId inquiryId);
    void save(RuleInquiry inquiry);
}
