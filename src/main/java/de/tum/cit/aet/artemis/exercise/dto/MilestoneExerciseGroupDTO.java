package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * DTO returned for a {@link MilestoneExerciseGroup}. The {@code exerciseIds} expose the group's current members so the
 * client can render them without serializing the full {@link Exercise} graph, and {@code milestoneExerciseId} points at
 * the group's anchor exercise.
 * <p>
 * {@code maxPoints} is always the sum of the members' points (a milestone group never stores a cap of its own - see
 * {@link MilestoneExerciseGroup#setMaxPoints}); it is purely a display value for the instructor UI. The timeline fields
 * are read through the group, which delegates them to its anchor exercise.
 * <p>
 * {@code programmingLanguage} and {@code projectType} are the anchor {@link MilestoneExercise}'s, which every member
 * inherits (see {@code UserStoryExerciseService.applyMilestoneConfig}). The client needs them before a
 * {@code UserStoryExercise} exists: the create form hides both fields, so they are the only way it can seed the new
 * story's problem statement from the right readme template - the Gradle and Maven templates spell test names
 * differently ({@code testBubbleSort()} vs {@code testBubbleSort}), and a mismatch leaves every task unlinked.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneExerciseGroupDTO(Long id, String title, @Nullable Long milestoneExerciseId, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        @Nullable ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType, Set<Long> exerciseIds) {

    public MilestoneExerciseGroupDTO(MilestoneExerciseGroup group) {
        this(group.getId(), group.getTitle(), group.getMilestoneExercise() != null ? group.getMilestoneExercise().getId() : null, sumOfMemberPoints(group), group.getReleaseDate(),
                group.getStartDate(), group.getDueDate(), group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(),
                group.getMilestoneExercise() != null ? group.getMilestoneExercise().getProgrammingLanguage() : null,
                group.getMilestoneExercise() != null ? group.getMilestoneExercise().getProjectType() : null,
                group.getExercises().stream().map(DomainObject::getId).collect(Collectors.toSet()));
    }

    private static Double sumOfMemberPoints(MilestoneExerciseGroup group) {
        return group.getExercises().stream().map(Exercise::getMaxPoints).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }
}
