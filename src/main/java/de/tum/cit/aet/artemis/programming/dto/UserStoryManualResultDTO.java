package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * One user story's share of a Milestone assessment: the manual {@link Result} - and with it the feedbacks - a tutor authored
 * for that user story while assessing the Milestone submission as a whole.
 * <p>
 * A Milestone's user stories are all graded from one and the same submission, so a tutor assesses them in a single session and
 * the client sends one of these per user story it touched (see MilestoneAssessmentResource#saveMilestoneAssessment).
 *
 * @param userStoryExerciseId the id of the {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise} this result belongs to
 * @param result              the manual result with the feedbacks the tutor authored for that user story
 */
// TODO: the result should be a DTO as well; it is passed as the entity here to stay consistent with the existing
// ProgrammingAssessmentResource#saveProgrammingAssessment endpoint, which the client shares its serialization code with.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryManualResultDTO(long userStoryExerciseId, Result result) {
}
