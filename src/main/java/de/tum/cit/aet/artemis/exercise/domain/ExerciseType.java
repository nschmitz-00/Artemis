package de.tum.cit.aet.artemis.exercise.domain;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

public enum ExerciseType {

    TEXT("text"), PROGRAMMING("programming"), MODELING("modeling"), FILE_UPLOAD("file-upload"), QUIZ("quiz"), MILESTONE("milestone"), USER_STORY("user-story");

    private final String value;

    ExerciseType(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON serialization value for this exercise type.
     * This matches the type discriminator values used in Exercise entity.
     *
     * @return lowercase string representation (e.g., "text", "programming", "file-upload")
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Used for human-readable string manipulations e.g. for notifications texts
     *
     * @return the exercise type as a lower case String without any special characters (e.g. FILE_UPLOAD -> "file upload")
     */
    public String getExerciseTypeAsReadableString() {
        return this.toString().toLowerCase().replace('_', ' ');
    }

    /**
     * Used to filter the exercise type using the TYPE-operator.
     *
     * @return the class corresponding to the ExerciseType
     */
    public Class<? extends Exercise> getExerciseClass() {
        return switch (this) {
            case TEXT -> TextExercise.class;
            case PROGRAMMING -> ProgrammingExercise.class;
            case MODELING -> ModelingExercise.class;
            case FILE_UPLOAD -> FileUploadExercise.class;
            case QUIZ -> QuizExercise.class;
            case MILESTONE -> MilestoneExercise.class;
            case USER_STORY -> UserStoryExercise.class;
        };
    }

    /**
     * Whether exercises of this type contribute their own max points and achieved points to aggregated scores
     * (course scores, reachable points, per-type breakdowns, CSV exports).
     * <p>
     * A {@link #MILESTONE} is a container: its max points are the sum of its {@code UserStoryExercise} children's max points
     * (see {@code MilestoneExercise#recalculateDerivedPoints}), and a build result is graded once against the Milestone and once
     * against every child. Counting both sides would therefore double every Milestone's contribution, so the children count
     * and the Milestone itself does not.
     *
     * @return true if exercises of this type add their points to aggregated scores
     */
    public boolean contributesPointsToAggregatedScores() {
        return this != MILESTONE;
    }

    /**
     * The exercise type under which scores of this type are reported when scores are grouped by exercise type.
     * <p>
     * A {@link #USER_STORY} is programming work performed in the parent Milestone's repository, so it is reported under
     * {@link #PROGRAMMING}; that keeps the per-type sums adding up to the course total, which they would not if UserStory
     * points were reported in a bucket no client reads.
     *
     * @return the exercise type to aggregate this type's scores under
     */
    public ExerciseType scoreAggregationBucket() {
        return this == USER_STORY ? PROGRAMMING : this;
    }

    /**
     * The exercise types that can appear as a bucket when scores are grouped by exercise type, i.e. the types that both
     * contribute points and are reported under themselves. {@link #MILESTONE} and {@link #USER_STORY} are neither.
     *
     * @return the exercise types to iterate when building a score-per-exercise-type breakdown
     */
    public static List<ExerciseType> scoreAggregationBuckets() {
        return Arrays.stream(values()).filter(type -> type.contributesPointsToAggregatedScores() && type.scoreAggregationBucket() == type).toList();
    }

    /**
     * Get the exercise type based on a class.
     *
     * @param exerciseClass the class for which the ExerciseType should be returned
     * @return the exercise type corresponding to the class
     */
    public static ExerciseType getExerciseTypeFromClass(Class<? extends Exercise> exerciseClass) {
        return switch (exerciseClass.getSimpleName()) {
            case "TextExercise" -> TEXT;
            case "ProgrammingExercise" -> PROGRAMMING;
            case "ModelingExercise" -> MODELING;
            case "FileUploadExercise" -> FILE_UPLOAD;
            case "QuizExercise" -> QUIZ;
            case "MilestoneExercise" -> MILESTONE;
            case "UserStoryExercise" -> USER_STORY;
            default -> throw new IllegalArgumentException("Received unexecpted exercise class name %s".formatted(exerciseClass.getSimpleName()));
        };
    }
}
