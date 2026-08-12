package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.UserStoryProgressStatus;

/**
 * A student's progress on one user story of a Milestone.
 * <p>
 * The user stories are the graded units of a Milestone (see {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise}),
 * so this is what the student's progress overview is built from: what the user story is worth, what the student earned on it so
 * far, and how far along it is.
 *
 * @param userStoryExerciseId        the id of the {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise}
 * @param title                      the title of the user story
 * @param shortName                  the short name of the user story
 * @param maxPoints                  the points reachable for this user story
 * @param bonusPoints                the bonus points reachable for this user story
 * @param achievedPoints             the points the student has earned so far, rounded with the course's score accuracy; 0 while there is no counting result
 * @param score                      the percentage of the user story the student reached, or null while there is no counting result
 * @param status                     how far the student has come with this user story
 * @param participationId            the id of the student's participation in this user story, or null if they have not started it
 * @param latestResultCompletionDate the completion date of the counting result, or null if there is none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryProgressDTO(long userStoryExerciseId, String title, String shortName, double maxPoints, double bonusPoints, double achievedPoints, @Nullable Double score,
        UserStoryProgressStatus status, @Nullable Long participationId, @Nullable ZonedDateTime latestResultCompletionDate) {
}
