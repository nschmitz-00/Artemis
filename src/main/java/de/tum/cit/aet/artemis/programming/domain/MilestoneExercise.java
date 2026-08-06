package de.tum.cit.aet.artemis.programming.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A MilestoneExercise is a ProgrammingExercise that additionally groups 0..n {@link UserStoryExercise}s.
 * <p>
 * It owns the three repositories (template, solution, tests) exactly like a regular {@link ProgrammingExercise};
 * its {@link UserStoryExercise} children never provision their own repositories and instead reuse the Milestone's.
 */
@Entity
@DiscriminatorValue(value = "MS")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MilestoneExercise extends ProgrammingExercise {

    // Both "milestoneExercise" (the direct back-reference) and "course"/"exerciseGroup" (which would otherwise
    // re-embed this same MilestoneExercise via Course#exercises / ExerciseGroup#exercises) must be cut here, or
    // Jackson recurses forever: userStoryExercises -> course -> exercises -> (this UserStoryExercise) -> course -> ...
    @OneToMany(mappedBy = "milestoneExercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    @JsonIgnoreProperties(value = { "milestoneExercise", "course", "exerciseGroup" }, allowSetters = true)
    private List<UserStoryExercise> userStoryExercises = new ArrayList<>();

    @Override
    public String getType() {
        return "milestone";
    }

    @Override
    public ExerciseType getExerciseType() {
        return ExerciseType.MILESTONE;
    }

    public List<UserStoryExercise> getUserStoryExercises() {
        return userStoryExercises;
    }

    public void setUserStoryExercises(List<UserStoryExercise> userStoryExercises) {
        this.userStoryExercises = userStoryExercises;
    }

    public void addUserStoryExercise(UserStoryExercise userStoryExercise) {
        this.userStoryExercises.add(userStoryExercise);
        userStoryExercise.setMilestoneExercise(this);
    }

    /**
     * Recalculates {@link #getMaxPoints()} as the sum of the max points of all {@link UserStoryExercise} children.
     * Must be called (and the result persisted) whenever a child UserStoryExercise is created, updated, or deleted.
     */
    public void recalculateMaxPoints() {
        double totalPoints = userStoryExercises.stream().mapToDouble(userStoryExercise -> {
            Double points = userStoryExercise.getMaxPoints();
            return points != null ? points : 0.0;
        }).sum();
        setMaxPoints(totalPoints);
    }
}
