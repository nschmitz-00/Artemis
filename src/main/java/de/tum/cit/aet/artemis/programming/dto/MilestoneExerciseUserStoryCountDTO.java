package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The number of {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise}s belonging to a
 * {@link de.tum.cit.aet.artemis.programming.domain.MilestoneExercise}, counted in the database so the course exercise list
 * can show the number without fetching (and serializing) the children themselves.
 *
 * @param milestoneExerciseId the id of the MilestoneExercise
 * @param count               how many UserStoryExercises belong to it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneExerciseUserStoryCountDTO(long milestoneExerciseId, long count) {
}
