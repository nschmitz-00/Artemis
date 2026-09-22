package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Spring Data JPA repository for the {@link ExerciseVariantGroup} entity.
 * <p>
 * The {@code Course → ExerciseVariantGroup} relationship is unidirectional (the {@code course_id} foreign key lives on
 * the {@code exercise_variant_group} table but is owned by the {@code Course} collection), so the group itself has no
 * {@code course} attribute. Course-scoped lookups therefore navigate the collection from {@link de.tum.cit.aet.artemis.course.domain.Course}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseVariantGroupRepository extends ArtemisJpaRepository<ExerciseVariantGroup, Long> {

    // Milestone groups are excluded here and served by MilestoneExerciseGroupRepository instead. Fetching a
    // MilestoneExerciseGroup's milestoneExercise from this base-type query would need
    // TREAT(evg AS MilestoneExerciseGroup).milestoneExercise, which restricts the whole query to that subtype rather than
    // only the join - even inside a LEFT JOIN FETCH - so every other group silently vanished from the result. Returning
    // milestone groups here without that fetch is no better: their timeline getters delegate to the unfetched anchor and
    // would read as "no dates".
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
            WHERE c.id = :courseId
                AND TYPE(evg) <> MilestoneExerciseGroup
            """)
    List<ExerciseVariantGroup> findAllByCourseId(@Param("courseId") Long courseId);

    // Milestone groups are excluded - see the comment on findAllByCourseId.
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
            WHERE c.id = :courseId
                AND evg.id = :groupId
                AND TYPE(evg) <> MilestoneExerciseGroup
            """)
    Optional<ExerciseVariantGroup> findByIdAndCourseId(@Param("groupId") Long groupId, @Param("courseId") Long courseId);

    default ExerciseVariantGroup findByIdAndCourseIdElseThrow(Long groupId, Long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdAndCourseId(groupId, courseId), groupId);
    }

    /**
     * Loads the group <em>without</em> its member exercises. Used for deletion: pulling the members into the persistence
     * context would make Hibernate's flush fail with a {@code TransientPropertyValueException} (the managed exercises
     * would still reference the removed group). With the members left unloaded, the {@code ON DELETE SET NULL} foreign
     * key on {@code exercise.exercise_variant_group_id} cleanly ungroups them.
     *
     * @param groupId  the id of the exercise variant group to load
     * @param courseId the id of the course the group must belong to
     * @return the matching group without its exercises, or empty if none matches
     */
    @Query("""
            SELECT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
            WHERE c.id = :courseId
                AND evg.id = :groupId
            """)
    Optional<ExerciseVariantGroup> findByIdAndCourseIdWithoutExercises(@Param("groupId") Long groupId, @Param("courseId") Long courseId);

    default ExerciseVariantGroup findByIdAndCourseIdWithoutExercisesElseThrow(Long groupId, Long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdAndCourseIdWithoutExercises(groupId, courseId), groupId);
    }

    /**
     * Claims an exercise for a group, but only while it still belongs to none. Two variant jobs generated from the
     * same source race here: both can read the source as ungrouped and then assign it, so the later write would take
     * it out of the group the earlier one created and leave that group without the original it promised. Letting the
     * database decide who wins makes the loser observable — a caller that does not update a row knows another job
     * claimed the exercise first. Native for the same reason as {@link #attachToCourse}: this is a plain conditional
     * FK write, and JPQL bulk updates on the polymorphic {@code Exercise} hierarchy are not.
     *
     * @param exerciseId the exercise to claim
     * @param groupId    the group to claim it for
     * @return 1 when the exercise was claimed, 0 when it already belonged to a group
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query(value = """
            UPDATE exercise
            SET exercise_variant_group_id = :groupId
            WHERE id = :exerciseId
                AND exercise_variant_group_id IS NULL
            """, nativeQuery = true)
    int claimExerciseIfUngrouped(@Param("exerciseId") long exerciseId, @Param("groupId") long groupId);

    /**
     * Gives up a claim made by {@link #claimExerciseIfUngrouped} when the assignment that followed it was rejected
     * before anything else was written. Scoped to the claiming group, so it can never release a membership somebody
     * else established in the meantime.
     *
     * @param exerciseId the exercise to release
     * @param groupId    the group it was claimed for
     * @return 1 when the claim was released, 0 when the exercise no longer belonged to that group
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query(value = """
            UPDATE exercise
            SET exercise_variant_group_id = NULL
            WHERE id = :exerciseId
                AND exercise_variant_group_id = :groupId
            """, nativeQuery = true)
    int releaseExerciseFromGroup(@Param("exerciseId") long exerciseId, @Param("groupId") long groupId);

    /**
     * Resolves the group owning the given exercise, or empty if the exercise is not a variant.
     * <p>
     * Navigating from {@code Exercise} in JPQL rather than reading {@link de.tum.cit.aet.artemis.exercise.domain.Exercise#getExerciseVariantGroup()}
     * is deliberate: that association is {@code LAZY} and {@code spring.jpa.open-in-view} is disabled, so the exercise is
     * already detached by the time an update resource needs the owning group's timeline.
     *
     * @param exerciseId the id of the (potential) member exercise
     * @return the owning group, or empty if the exercise has none
     */
    @Query("""
            SELECT e.exerciseVariantGroup
            FROM Exercise e
            WHERE e.id = :exerciseId
            """)
    Optional<ExerciseVariantGroup> findByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Like {@link #findByExerciseId}, but also eagerly fetches the group's {@code exercises} - needed by the
     * milestone-test-suite-changed fan-out ({@code UserStoryExerciseService.syncAllMembersTestCases}), which iterates
     * every member, run from {@code ProgrammingExerciseGradingService} outside of any request-scoped transaction.
     *
     * @param exerciseId the id of the (potential) member exercise, typically a {@code MilestoneExercise}
     * @return the owning group with its exercises initialized, or empty if the exercise has none
     */
    @Query("""
            SELECT DISTINCT g
            FROM Exercise e
                JOIN e.exerciseVariantGroup g
                LEFT JOIN FETCH g.exercises
            WHERE e.id = :exerciseId
            """)
    Optional<ExerciseVariantGroup> findByExerciseIdWithExercises(@Param("exerciseId") long exerciseId);

    /**
     * Counts the group's {@code exercises} members without loading them, used by the "cannot delete a non-empty
     * {@code MilestoneExerciseGroup}" guard. The group's own {@code milestoneExercise} is never itself a member of that
     * collection (see {@link de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup}), so no exclusion is needed here.
     *
     * @param groupId the id of the group whose members to count
     * @return the number of exercises currently assigned to the group
     */
    @Query("""
            SELECT COUNT(e)
            FROM Exercise e
            WHERE e.exerciseVariantGroup.id = :groupId
            """)
    long countExercisesByGroupId(@Param("groupId") Long groupId);
}
