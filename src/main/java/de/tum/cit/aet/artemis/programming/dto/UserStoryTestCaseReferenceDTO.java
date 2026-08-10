package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single "this UserStoryExercise references this test case" link, projected straight out of the
 * {@link de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask}s of a MilestoneExercise.
 * <p>
 * The title is selected alongside the ids on purpose: the task's {@code referencingUserStoryExercise} association is LAZY, so
 * reading it after the query would either need an extra fetch join per row or fail with a LazyInitializationException once the
 * session is gone.
 *
 * @param testCaseId             the id of the referenced test case (owned by the MilestoneExercise)
 * @param userStoryExerciseId    the id of the UserStoryExercise whose problem statement references it
 * @param userStoryExerciseTitle the title of that UserStoryExercise, for display in the coverage warnings
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryTestCaseReferenceDTO(long testCaseId, long userStoryExerciseId, String userStoryExerciseTitle) {
}
