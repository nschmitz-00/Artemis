package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The minimal identification of a {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise} needed to name it in the
 * Milestone's test case coverage warnings.
 *
 * @param id    the id of the UserStoryExercise
 * @param title the title of the UserStoryExercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryReferenceDTO(long id, String title) {
}
