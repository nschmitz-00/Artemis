package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * Spring Data JPA repository for the UserStoryExercise entity (a specialization of ProgrammingExercise, see {@link UserStoryExercise}).
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserStoryExerciseRepository extends ArtemisJpaRepository<UserStoryExercise, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "milestoneExercise" })
    Optional<UserStoryExercise> findWithMilestoneExerciseById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "milestoneExercise", "milestoneExercise.templateParticipation", "milestoneExercise.solutionParticipation",
            "milestoneExercise.buildConfig" })
    Optional<UserStoryExercise> findWithMilestoneExerciseAndParticipationsById(long exerciseId);

    default UserStoryExercise findWithMilestoneExerciseByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithMilestoneExerciseById(exerciseId), exerciseId);
    }
}
