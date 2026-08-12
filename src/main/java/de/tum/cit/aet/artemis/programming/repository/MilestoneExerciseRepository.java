package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.dto.MilestoneExerciseUserStoryCountDTO;

/**
 * Spring Data JPA repository for the MilestoneExercise entity (a specialization of ProgrammingExercise, see {@link MilestoneExercise}).
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface MilestoneExerciseRepository extends ArtemisJpaRepository<MilestoneExercise, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "userStoryExercises" })
    Optional<MilestoneExercise> findWithUserStoryExercisesById(long exerciseId);

    /**
     * Finds the MilestoneExercises that were created to work on the repositories of the given Milestone.
     * <p>
     * They hold no repositories of their own, so the Milestone they point at can neither be deleted nor have its repositories
     * removed while they exist.
     *
     * @param repositorySourceMilestoneId the id of the MilestoneExercise that owns the repositories
     * @return the MilestoneExercises using those repositories, empty if the Milestone shares them with nobody
     */
    List<MilestoneExercise> findAllByRepositorySourceMilestoneId(long repositorySourceMilestoneId);

    /**
     * Loads everything {@link de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseUpdateDtoService} touches, so the
     * update endpoint can apply the incoming DTO onto a fully populated entity. Mirrors
     * ProgrammingExerciseRepository#findForUpdateById, plus the Milestone's own children.
     *
     * @param exerciseId the id of the MilestoneExercise to load
     * @return the MilestoneExercise with all associations the update path reads, if it exists
     */
    @EntityGraph(type = LOAD, attributePaths = { "userStoryExercises", "competencyLinks", "competencyLinks.competency", "templateParticipation", "solutionParticipation",
            "auxiliaryRepositories", "buildConfig", "categories", "plagiarismDetectionConfig", "gradingCriteria", "gradingCriteria.structuredGradingInstructions" })
    Optional<MilestoneExercise> findForUpdateById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "userStoryExercises", "templateParticipation", "solutionParticipation", "buildConfig", "categories" })
    Optional<MilestoneExercise> findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(long exerciseId);

    // Exercise#categories is a LAZY element collection, so it has to be fetched explicitly or it is silently missing from the
    // serialized response (Jackson omits uninitialized collections rather than failing), and the exercise appears uncategorized.
    // templateParticipation is LAZY for the same reason and is what the course exercise list's "Edit in editor" action links to
    // (it routes to the code editor for the template repository, which is addressed by the participation id).
    @EntityGraph(type = LOAD, attributePaths = { "categories", "templateParticipation" })
    List<MilestoneExercise> findAllByCourseId(long courseId);

    /**
     * Counts the UserStoryExercise children of every MilestoneExercise in the given course. Counting in the database keeps the
     * course exercise list from having to fetch and serialize the children just to show how many there are.
     *
     * @param courseId the id of the course
     * @return one entry per MilestoneExercise of the course, including those without any children (count 0)
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.MilestoneExerciseUserStoryCountDTO(milestoneExercise.id, COUNT(userStoryExercise))
            FROM MilestoneExercise milestoneExercise
                LEFT JOIN milestoneExercise.userStoryExercises userStoryExercise
            WHERE milestoneExercise.course.id = :courseId
            GROUP BY milestoneExercise.id
            """)
    List<MilestoneExerciseUserStoryCountDTO> countUserStoryExercisesByCourseId(@Param("courseId") long courseId);

    default MilestoneExercise findWithUserStoryExercisesByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithUserStoryExercisesById(exerciseId), exerciseId);
    }

    default MilestoneExercise findForUpdateByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findForUpdateById(exerciseId), exerciseId);
    }
}
