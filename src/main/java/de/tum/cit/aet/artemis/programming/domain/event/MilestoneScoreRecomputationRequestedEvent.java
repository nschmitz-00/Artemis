package de.tum.cit.aet.artemis.programming.domain.event;

/**
 * Published when every student's score on a milestone has become stale and has to be recomputed, for example because the
 * milestone's points or its Definition of Done changed.
 * <p>
 * An event rather than a direct call, because the services that notice the change sit on the grading path, which the
 * cluster messaging that schedules the recomputation itself depends on.
 *
 * @param milestoneExerciseId the id of the milestone exercise whose scores are stale
 */
public record MilestoneScoreRecomputationRequestedEvent(long milestoneExerciseId) {
}
