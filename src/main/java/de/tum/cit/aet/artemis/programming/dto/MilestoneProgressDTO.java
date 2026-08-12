package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A student's progress over all user stories of one Milestone, as shown on the Milestone page.
 * <p>
 * A Milestone carries no points of its own - its max points are the sum over its
 * {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise} children, and a build result is graded against each child
 * separately (see {@code MilestoneExercise#recalculateDerivedPoints}). The aggregate below is therefore summed over the user
 * stories rather than read off the Milestone participation.
 *
 * @param milestoneExerciseId     the id of the {@link de.tum.cit.aet.artemis.programming.domain.MilestoneExercise}
 * @param userStories             the progress on each user story of the Milestone, in the order the user stories are defined in;
 *                                    omitted from the response entirely for a Milestone without user stories (NON_EMPTY)
 * @param achievedPoints          the points the student has earned over all user stories
 * @param maxPoints               the points reachable over all user stories, i.e. the Milestone's max points
 * @param completionPercentage    {@code achievedPoints} as a percentage of {@code maxPoints}, capped at 100 because bonus points
 *                                    can push the achieved points past the reachable ones; 0 if the Milestone is worth no points
 * @param completedUserStories    how many user stories the student has completed
 * @param totalUserStories        how many user stories the Milestone has
 * @param manualAssessmentPending whether the Milestone is manually assessed and its assessments are not published yet, so the
 *                                    numbers above can still grow once the assessment due date has passed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneProgressDTO(long milestoneExerciseId, List<UserStoryProgressDTO> userStories, double achievedPoints, double maxPoints, double completionPercentage,
        int completedUserStories, int totalUserStories, boolean manualAssessmentPending) {
}
