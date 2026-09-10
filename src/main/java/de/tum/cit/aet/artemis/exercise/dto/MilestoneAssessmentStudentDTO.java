package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One student's standing across a whole milestone group, as one row of the milestone assessment dashboard.
 * <p>
 * The unit a tutor picks is the student rather than a submission: a group's stories share one repository and one build,
 * so grading them one story at a time across different students means reading the same codebase over and over. The
 * dashboard therefore names the student explicitly - a tutor must never be in doubt whose work they are about to open.
 *
 * @param studentLogin             the student's login, which is what the assessment page is addressed by
 * @param studentName              the student's display name
 * @param milestoneParticipationId the student's participation in the group's anchor milestone, which owns the shared
 *                                     repository; {@code null} is impossible here, since a student only appears once they
 *                                     have started the milestone
 * @param stories                  the group's user stories for this student, in the same stable order the assessment
 *                                     page renders its tabs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneAssessmentStudentDTO(String studentLogin, @Nullable String studentName, long milestoneParticipationId, List<MilestoneAssessmentStoryDTO> stories) {
}
