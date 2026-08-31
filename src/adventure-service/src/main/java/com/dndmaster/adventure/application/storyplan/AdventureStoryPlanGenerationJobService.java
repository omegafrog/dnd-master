package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public final class AdventureStoryPlanGenerationJobService implements AutoCloseable {
    private final AdventureStoryPlanApplicationService plans;
    private final AdventureSessionRepository sessions;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> activeBySession = new ConcurrentHashMap<>();

    public AdventureStoryPlanGenerationJobService(AdventureStoryPlanApplicationService plans, AdventureSessionRepository sessions) {
        this.plans = plans;
        this.sessions = sessions;
    }

    public JobView start(SessionId sessionId, OwnerPlayerId owner, AdventurePlanConfiguration configuration) {
        sessions.findById(sessionId).filter(session -> session.ownerPlayerId().equals(owner))
                .orElseThrow(() -> new SecurityException("adventure session access denied"));
        UUID existingId = activeBySession.get(sessionId.value());
        if (existingId != null) {
            Job existing = jobs.get(existingId);
            if (existing != null && (existing.status == Status.QUEUED || existing.status == Status.RUNNING)) return existing.view();
        }
        Job job = new Job(UUID.randomUUID(), sessionId.value(), owner.value());
        jobs.put(job.jobId, job);
        activeBySession.put(sessionId.value(), job.jobId);
        executor.submit(() -> run(job, sessionId, owner, configuration));
        return job.view();
    }

    public JobView read(UUID jobId, SessionId sessionId, OwnerPlayerId owner) {
        Job job = jobs.get(jobId);
        if (job == null) throw new IllegalArgumentException("story plan generation job not found");
        if (!job.sessionId.equals(sessionId.value())) throw new SecurityException("adventure session access denied");
        if (!job.ownerId.equals(owner.value().toString())) throw new SecurityException("adventure session access denied");
        return job.view();
    }

    private void run(Job job, SessionId sessionId, OwnerPlayerId owner, AdventurePlanConfiguration configuration) {
        job.update(Status.RUNNING, 10, "요청 검증", null);
        try {
            AdventureStoryPlan plan = plans.generate(sessionId, owner, configuration, (progress, stage) -> job.update(Status.RUNNING, progress, stage, null));
            job.update(plan.status().name().equals("READY") ? Status.COMPLETE : Status.FAILED, 100,
                    plan.status().name().equals("READY") ? "플레이 준비 완료" : "계획 검증 실패", plan.failureReason());
        } catch (Throwable failure) {
            job.update(Status.FAILED, 100, "계획 생성 실패", rootMessage(failure));
        } finally {
            activeBySession.remove(sessionId.value(), job.jobId);
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override public void close() { executor.close(); }

    public enum Status { QUEUED, RUNNING, COMPLETE, FAILED }

    public record JobView(UUID jobId, UUID sessionId, Status status, int progress, String stage, String message, Instant updatedAt) {}

    private static final class Job {
        private final UUID jobId;
        private final UUID sessionId;
        private final String ownerId;
        private volatile Status status = Status.QUEUED;
        private volatile int progress;
        private volatile String stage = "대기 중";
        private volatile String message;
        private volatile Instant updatedAt = Instant.now();
        private Job(UUID jobId, UUID sessionId, UUID ownerId) { this.jobId = jobId; this.sessionId = sessionId; this.ownerId = ownerId.toString(); }
        private synchronized void update(Status status, int progress, String stage, String message) {
            if (this.status == Status.COMPLETE || this.status == Status.FAILED) return;
            this.status = status;
            this.progress = progress;
            this.stage = stage;
            this.message = message;
            this.updatedAt = Instant.now();
        }
        private JobView view() { return new JobView(jobId, sessionId, status, progress, stage, message, updatedAt); }
    }
}
