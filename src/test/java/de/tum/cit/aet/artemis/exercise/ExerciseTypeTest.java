package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseTypeTest {

    @Test
    void testGetExerciseTypeFromClass() {
        assertThat(ExerciseType.getExerciseTypeFromClass(TextExercise.class)).isEqualTo(ExerciseType.TEXT);
        assertThat(ExerciseType.getExerciseTypeFromClass(ProgrammingExercise.class)).isEqualTo(ExerciseType.PROGRAMMING);
        assertThat(ExerciseType.getExerciseTypeFromClass(ModelingExercise.class)).isEqualTo(ExerciseType.MODELING);
        assertThat(ExerciseType.getExerciseTypeFromClass(FileUploadExercise.class)).isEqualTo(ExerciseType.FILE_UPLOAD);
        assertThat(ExerciseType.getExerciseTypeFromClass(QuizExercise.class)).isEqualTo(ExerciseType.QUIZ);

        assertThatThrownBy(() -> ExerciseType.getExerciseTypeFromClass(Exercise.class)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Stands in for the class Hibernate generates for a lazy proxy, e.g. {@code MilestoneExercise$HibernateProxy}.
     */
    private abstract static class MilestoneExerciseProxy extends MilestoneExercise implements HibernateProxy {
    }

    @Test
    void testExerciseTypeAndDiscriminatorResolveThroughHibernateProxyClass() {
        assertThat(ExerciseType.getExerciseTypeFromClass(MilestoneExerciseProxy.class)).isEqualTo(ExerciseType.PROGRAMMING);
        assertThat(ExerciseType.getDiscriminatorFromClass(MilestoneExerciseProxy.class)).isEqualTo("milestone");
    }
}
