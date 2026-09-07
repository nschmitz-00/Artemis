package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;

/**
 * Links a {@link MilestoneExerciseGroup} to the {@code MilestoneExercise} that anchors it.
 * <p>
 * A scalar projection, because the only reason to resolve this link is to attribute the anchor's points to its group:
 * the anchor has no {@code exerciseVariantGroup} of its own (the group points at it instead), so a score calculation
 * working from exercises alone cannot tell which group an anchor belongs to.
 *
 * @param milestoneExerciseId the id of the group's anchor milestone exercise
 * @param groupId             the id of the milestone group it anchors
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneGroupAnchorDTO(long milestoneExerciseId, long groupId) {
}
