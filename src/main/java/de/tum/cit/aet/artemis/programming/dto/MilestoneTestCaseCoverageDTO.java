package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How well the UserStoryExercises of a MilestoneExercise partition the Milestone's active test cases.
 * <p>
 * Grading a UserStoryExercise narrows the Milestone's active test cases down to the ones its problem statement references (see
 * {@code ProgrammingExerciseGradingService#findActiveTestCasesScopedToExercise}), so the partition should be exact: a test case
 * that no UserStory references is unreachable for students, and one that several UserStories reference pays out several times.
 * Both lists are purely informational - they are never enforced, since an instructor authoring UserStories one at a time
 * legitimately passes through states with orphans and duplicates.
 *
 * @param orphanTestCases    active test cases referenced by no UserStoryExercise at all
 * @param duplicateTestCases active test cases referenced by two or more different UserStoryExercises
 */
// Not NON_EMPTY: "no orphans" and "no duplicates" are the whole point of this response, and omitting the empty lists would turn
// the common (healthy) case into an empty object the client has to second-guess.
@JsonInclude()
public record MilestoneTestCaseCoverageDTO(List<MilestoneTestCaseIssueDTO> orphanTestCases, List<MilestoneTestCaseIssueDTO> duplicateTestCases) {
}
