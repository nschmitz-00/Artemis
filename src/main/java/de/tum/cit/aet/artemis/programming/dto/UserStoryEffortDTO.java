package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The effort a participant has reported for a user story exercise, in hours - summed from their task board rather
 * than entered by hand (see {@code UserStoryTaskRepository#sumEffortByParticipationId}).
 *
 * @param estimatedEffort hours the participant's tasks estimate the story to take; {@code 0} for a board with no
 *                            tasks yet, never {@code null}
 * @param actualEffort    hours the tasks report actually spent so far, {@code null} until at least one task has any
 *                            logged
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryEffortDTO(Double estimatedEffort, @Nullable Double actualEffort) {
}
