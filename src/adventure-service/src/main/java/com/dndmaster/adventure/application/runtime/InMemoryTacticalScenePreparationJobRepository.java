package com.dndmaster.adventure.application.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryTacticalScenePreparationJobRepository implements TacticalScenePreparationJobRepository {
    private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();

    @Override public synchronized Job createOrGet(UUID sessionId, UUID ownerId, int stagePosition, String stageName, boolean mapRequired) {
        return jobs.values().stream().filter(job -> job.sessionId().equals(sessionId) && job.stagePosition() == stagePosition)
                .findFirst().orElseGet(() -> {
                    Job job = new Job(UUID.randomUUID(), sessionId, ownerId, stagePosition, stageName, Status.QUEUED,
                            0, 0, mapRequired, "대기 중", null, Instant.now());
                    jobs.put(job.jobId(), job);
                    return job;
                });
    }
    @Override public Optional<Job> find(UUID sessionId, int stagePosition) { return jobs.values().stream().filter(job -> job.sessionId().equals(sessionId) && job.stagePosition() == stagePosition).findFirst(); }
    @Override public List<Job> findUnfinished() { return new ArrayList<>(jobs.values().stream().filter(job -> job.status() == Status.QUEUED || job.status() == Status.RUNNING).toList()); }
    @Override public synchronized boolean claim(UUID jobId) {
        Job job = jobs.get(jobId);
        if (job == null || job.status() != Status.QUEUED) return false;
        jobs.put(jobId, copy(job, Status.RUNNING, job.progress(), job.attempts(), "전술 장면 준비 중", job.failureReason()));
        return true;
    }
    @Override public void update(UUID jobId, Status status, int progress, int attempts, String message, String failureReason) { jobs.computeIfPresent(jobId, (id, job) -> copy(job, status, progress, attempts, message, failureReason)); }
    @Override public void resetForRetry(UUID jobId) { jobs.computeIfPresent(jobId, (id, job) -> copy(job, Status.QUEUED, 0, 0, "대기 중", null)); }
    private static Job copy(Job job, Status status, int progress, int attempts, String message, String reason) { return new Job(job.jobId(), job.sessionId(), job.ownerId(), job.stagePosition(), job.stageName(), status, progress, attempts, job.mapRequired(), message, reason, Instant.now()); }
}
