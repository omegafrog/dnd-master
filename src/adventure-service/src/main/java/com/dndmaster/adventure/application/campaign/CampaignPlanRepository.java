package com.dndmaster.adventure.application.campaign;

import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.Optional;

public interface CampaignPlanRepository {
    Optional<CampaignPlan> findBySessionId(SessionId sessionId);
    void save(CampaignPlan plan);
}
