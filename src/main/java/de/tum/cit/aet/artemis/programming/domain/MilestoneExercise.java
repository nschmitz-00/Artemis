package de.tum.cit.aet.artemis.programming.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    //
    // READ_ONLY: the children are owned exclusively by UserStoryExerciseResource, never by the Milestone endpoints. The
    // mapping below is orphanRemoval, so accepting this collection from a request body would let any MilestoneExercise
    // payload silently delete every UserStoryExercise just by omitting them. Deserializing it as empty and merging is
    // exactly as destructive, hence read-only rather than merely "trusted"; MilestoneExerciseResource#updateMilestoneExercise
    // restores the persisted children before saving.
    @OneToMany(mappedBy = "milestoneExercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonIgnoreProperties(value = { "milestoneExercise", "course", "exerciseGroup" }, allowSetters = true)
    private List<UserStoryExercise> userStoryExercises = new ArrayList<>();

    /**
     * How many UserStoryExercises belong to this Milestone. Derived and never persisted; it exists so the course exercise
     * list can show the number without fetching every child (see MilestoneExerciseResource#getMilestoneExercisesForCourse).
     * Only populated by that list endpoint - endpoints that fetch the children themselves leave it null, since
     * {@link #getUserStoryExercises()} already carries the answer there.
     */
    @Transient
    private Integer numberOfUserStoryExercisesTransient;

    @Override
    public String getType() {
        return "milestone";
    }

    public Integer getNumberOfUserStoryExercises() {
        return numberOfUserStoryExercisesTransient;
    }

    public void setNumberOfUserStoryExercises(Integer numberOfUserStoryExercises) {
        this.numberOfUserStoryExercisesTransient = numberOfUserStoryExercises;
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
     * Recalculates {@link #getMaxPoints()} and {@link #getBonusPoints()} as the sums over all {@link UserStoryExercise} children.
     * Must be called (and the result persisted) whenever a child UserStoryExercise is created, updated, or deleted.
     * <p>
     * Both are derived rather than client-settable: the UserStories are the graded units (a build result is scored against each
     * of them separately, see {@code ProgrammingExerciseGradingService#findActiveTestCasesScopedToExercise}), so a Milestone
     * total that disagreed with its children's would be unreachable in either direction.
     */
    public void recalculateDerivedPoints() {
        setMaxPoints(sumOverChildren(UserStoryExercise::getMaxPoints));
        setBonusPoints(sumOverChildren(UserStoryExercise::getBonusPoints));
    }

    private double sumOverChildren(Function<UserStoryExercise, Double> pointsGetter) {
        return userStoryExercises.stream().map(pointsGetter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }
}
