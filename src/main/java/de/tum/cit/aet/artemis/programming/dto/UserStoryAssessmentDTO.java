package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * Everything a tutor needs to assess one user story of a Milestone submission: what the user story is worth, which
 * participation of the student it is graded on, and the latest result it already has.
 * <p>
 * The tutor assesses the Milestone submission once and distributes the feedback over its user stories, so the client fetches
 * one of these per user story instead of opening a separate assessment page for each.
 *
 * @param userStoryExerciseId the id of the {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise}
 * @param title               the title of the user story, shown as the section header in the assessment view
 * @param shortName           the short name of the user story
 * @param maxPoints           the points reachable for this user story
 * @param bonusPoints         the bonus points reachable for this user story
 * @param participationId     the id of the student's participation in this user story (a sibling of the Milestone participation)
 * @param latestResult        the latest result of that participation (manual if one exists, otherwise the automatic one), or null if there is none yet
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryAssessmentDTO(long userStoryExerciseId, String title, String shortName, Double maxPoints, Double bonusPoints, long participationId,
        @Nullable Result latestResult) {
}
