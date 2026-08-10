package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.dto.UserStoryTestCaseReferenceDTO;

/**
 * Spring Data repository for the ProgrammingExerciseTask entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ProgrammingExerciseTaskRepository extends ArtemisJpaRepository<ProgrammingExerciseTask, Long> {

    /**
     * Gets all tasks with its test cases and solution entries of the test case for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     * @throws EntityNotFoundException If the exercise with exerciseId does not exist
     */
    @NonNull
    default List<ProgrammingExerciseTask> findByExerciseIdWithTestCaseElseThrow(long exerciseId) throws EntityNotFoundException {
        return getArbitraryValueElseThrow(findByExerciseIdWithTestCase(exerciseId), Long.toString(exerciseId));
    }

    /**
     * Gets all tasks with its test cases and solution entries of the test case for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
                LEFT JOIN FETCH t.testCases tc
            WHERE t.exercise.id = :exerciseId
                AND tc.exercise.id = :exerciseId
            """)
    Optional<List<ProgrammingExerciseTask>> findByExerciseIdWithTestCase(@Param("exerciseId") long exerciseId);

    /**
     * Gets all tasks with its test cases for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
                LEFT JOIN FETCH t.testCases tc
            WHERE t.exercise.id = :exerciseId
            """)
    Set<ProgrammingExerciseTask> findByExerciseIdWithTestCases(@Param("exerciseId") Long exerciseId);

    /**
     * Gets all tasks (with their test cases) that were parsed from the problem statement of a specific {@code UserStoryExercise}.
     * The tasks themselves are persisted against the owning MilestoneExercise (see {@link ProgrammingExerciseTask#getExercise()}),
     * but are tagged with the UserStoryExercise that referenced them so grading can scope to just that subset.
     *
     * @param userStoryExerciseId of the UserStoryExercise
     * @return all tasks (with test cases) referenced by that UserStoryExercise's problem statement
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
                LEFT JOIN FETCH t.testCases tc
            WHERE t.referencingUserStoryExercise.id = :userStoryExerciseId
            """)
    Set<ProgrammingExerciseTask> findByReferencingUserStoryExerciseIdWithTestCases(@Param("userStoryExerciseId") long userStoryExerciseId);

    /**
     * Gets the ids of all test cases referenced by any task belonging to a specific {@code UserStoryExercise}'s problem statement.
     * Used to scope grading of a UserStory's participation to only the test cases relevant to it, out of the full set of test cases
     * belonging to the shared Milestone test repository.
     *
     * @param userStoryExerciseId of the UserStoryExercise
     * @return the ids of the test cases relevant to that UserStoryExercise
     */
    @Query("""
            SELECT tc.id
            FROM ProgrammingExerciseTask t
                JOIN t.testCases tc
            WHERE t.referencingUserStoryExercise.id = :userStoryExerciseId
            """)
    Set<Long> findTestCaseIdsByReferencingUserStoryExerciseId(@Param("userStoryExerciseId") long userStoryExerciseId);

    /**
     * Gets every "UserStoryExercise references test case" link of a MilestoneExercise, one row per distinct pair.
     * <p>
     * Both joins are inner joins, so tasks of the Milestone's own problem statement (which have no referencing UserStory) are
     * excluded - only the UserStories' claims on the shared test repository are of interest here. The result being a {@code Set}
     * collapses the case where several tasks of the <i>same</i> UserStory reference the same test case, which is not a duplicate
     * claim: grading deduplicates the test case ids per UserStory anyway.
     *
     * @param milestoneExerciseId of the MilestoneExercise owning the tasks and test cases
     * @return one entry per distinct (test case, referencing UserStoryExercise) pair
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.UserStoryTestCaseReferenceDTO(tc.id, userStory.id, userStory.title)
            FROM ProgrammingExerciseTask t
                JOIN t.testCases tc
                JOIN t.referencingUserStoryExercise userStory
            WHERE t.exercise.id = :milestoneExerciseId
            """)
    Set<UserStoryTestCaseReferenceDTO> findUserStoryTestCaseReferencesByMilestoneExerciseId(@Param("milestoneExerciseId") long milestoneExerciseId);
}
