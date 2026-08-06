package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;

/**
 * Spring Data JPA repository for the MilestoneExercise entity (a specialization of ProgrammingExercise, see {@link MilestoneExercise}).
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface MilestoneExerciseRepository extends ArtemisJpaRepository<MilestoneExercise, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "userStoryExercises" })
    Optional<MilestoneExercise> findWithUserStoryExercisesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "userStoryExercises", "templateParticipation", "solutionParticipation", "buildConfig" })
    Optional<MilestoneExercise> findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(long exerciseId);

    List<MilestoneExercise> findAllByCourseId(long courseId);

    default MilestoneExercise findWithUserStoryExercisesByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithUserStoryExercisesById(exerciseId), exerciseId);
    }
}
