package com.dndmaster.adventure.evidence;
import java.util.List;
import java.util.UUID;
@FunctionalInterface public interface EvidenceRerankerPort { List<UUID> rerank(EvidenceRerankRequest request); }
