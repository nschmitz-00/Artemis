package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One test case of a MilestoneExercise that its UserStoryExercises do not claim exactly once, together with the UserStories that
 * do claim it (empty for an orphan, two or more entries for a duplicate).
 *
 * @param testCaseId             the id of the test case
 * @param testName               the name of the test case, as it appears in the test repository
 * @param referencingUserStories the UserStoryExercises whose problem statements reference this test case
 */
// Not NON_EMPTY: an orphan's referencingUserStories is empty by definition, and dropping the field would make an orphan entry
// indistinguishable from a malformed one.
@JsonInclude()
public record MilestoneTestCaseIssueDTO(long testCaseId, String testName, List<UserStoryReferenceDTO> referencingUserStories) {
}
