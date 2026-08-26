package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/**
 * A {@code MilestoneExercise} is the anchor of a {@code MilestoneExerciseGroup}: a full {@link ProgrammingExercise} that
 * genuinely owns the shared template/solution/test repositories, build plan, and test cases the group's
 * {@link UserStoryExercise}s work against and are graded from.
 * <p>
 * It is created and deleted together with its {@code MilestoneExerciseGroup} (see the group's {@code milestoneExercise}
 * field), is never rendered to students, and never contributes to grading itself.
 */
@Entity
@DiscriminatorValue("MS")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MilestoneExercise extends ProgrammingExercise {

    @Override
    public String getType() {
        return "milestone";
    }

    /** A milestone never counts towards a student's score; only its {@link UserStoryExercise}s do. */
    @Override
    public IncludedInOverallScore getIncludedInOverallScore() {
        return IncludedInOverallScore.NOT_INCLUDED;
    }

    /**
     * A milestone exercise is never rendered to students (see the class-level javadoc) - only its {@link UserStoryExercise}
     * members are. Always {@code false} regardless of release date, which is the single authoritative gate every
     * student-facing exercise access goes through ({@code AuthorizationCheckService.isAllowedToSeeCourseExercise} and
     * {@code ExerciseService.filterExercisesForCourse}).
     */
    @Override
    public boolean isVisibleToStudents() {
        return false;
    }
}
