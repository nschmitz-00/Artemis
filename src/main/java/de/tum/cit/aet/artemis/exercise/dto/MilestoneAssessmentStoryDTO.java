package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One {@code UserStoryExercise} of a milestone group as it stands for a single student, which is both a tab on the
 * milestone assessment page and a cell on the milestone assessment dashboard.
 * <p>
 * Every story of a group shares the milestone's repository and its build, so what differs between these entries is not
 * the code but the grading: each story has its own participation, its own submission row for the same commit, and its
 * own result.
 *
 * @param exerciseId      the user story exercise's id
 * @param title           the user story's title, used as the tab label
 * @param maxPoints       the points the story is worth
 * @param participationId the student's participation in this story, or {@code null} if they never started it
 * @param submissionId    the latest submission of that participation, or {@code null} if there is none yet - this is
 *                            what the assessment editor is opened on
 * @param latestScore     the score of the latest result, or {@code null} if the story has none yet
 * @param assessorLogin   the login of the tutor who assessed it, or {@code null} if nobody has
 * @param assessed        whether a manual assessment has been submitted for this story
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneAssessmentStoryDTO(long exerciseId, String title, @Nullable Double maxPoints, @Nullable Long participationId, @Nullable Long submissionId,
        @Nullable Double latestScore, @Nullable String assessorLogin, boolean assessed) {
}
