package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;

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

    // Also fetches a MilestoneExerciseGroup's milestoneExercise (LAZY @OneToOne): ExerciseVariantGroupDTO reads the
    // group's dates and milestoneExercise id for every group in the list, and spring.jpa.open-in-view is disabled, so
    // an unfetched proxy throws LazyInitializationException once the session closes (see findByIdAndCourseIdWithMilestoneExercise).
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
                LEFT JOIN FETCH TREAT(evg AS MilestoneExerciseGroup).milestoneExercise
            WHERE c.id = :courseId
            """)
    List<ExerciseVariantGroup> findAllByCourseId(@Param("courseId") Long courseId);

    // Also fetches a MilestoneExerciseGroup's milestoneExercise - see the comment on findAllByCourseId. Its callers
    // (get, update) both read the group's delegated timeline getters/setters, which dereference that proxy.
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
                LEFT JOIN FETCH TREAT(evg AS MilestoneExerciseGroup).milestoneExercise
            WHERE c.id = :courseId
                AND evg.id = :groupId
            """)
    Optional<ExerciseVariantGroup> findByIdAndCourseId(@Param("groupId") Long groupId, @Param("courseId") Long courseId);

    default ExerciseVariantGroup findByIdAndCourseIdElseThrow(Long groupId, Long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdAndCourseId(groupId, courseId), groupId);
    }

    /**
     * Like {@link #findByIdAndCourseId}, but also eagerly fetches a {@code MilestoneExerciseGroup}'s
     * {@code milestoneExercise} (a {@code LAZY @OneToOne}, so left unfetched by every other query here) together with the
     * milestone exercise's own {@code buildConfig}, {@code templateParticipation} and {@code solutionParticipation} (each
     * a {@code LAZY @OneToOne} in turn) - needed by the few call sites (creating/moving a user story exercise, via
     * {@code UserStoryExerciseService.applyMilestoneConfig}) that copy the milestone exercise's build config and
     * repository URIs onto the user story, since {@code spring.jpa.open-in-view} is disabled and everything here would
     * otherwise be an uninitialized proxy by the time that code runs. A no-op (plain left join) for a group that isn't a
     * milestone group. {@code exercises} is fetched too - {@code ExerciseVariantGroupService.assignToGroup} reads it
     * (via {@code adoptMissingDatesFromExercise}) on the very group this query loads.
     *
     * @param groupId  the id of the exercise variant group to load
     * @param courseId the id of the course the group must belong to
     * @return the matching group with its milestone exercise (if any) and its exercises initialized, or empty if none matches
     */
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
                LEFT JOIN FETCH TREAT(evg AS MilestoneExerciseGroup).milestoneExercise me
                LEFT JOIN FETCH me.buildConfig
                LEFT JOIN FETCH me.templateParticipation
                LEFT JOIN FETCH me.solutionParticipation
            WHERE c.id = :courseId
                AND evg.id = :groupId
            """)
    Optional<ExerciseVariantGroup> findByIdAndCourseIdWithMilestoneExercise(@Param("groupId") Long groupId, @Param("courseId") Long courseId);

    default ExerciseVariantGroup findByIdAndCourseIdWithMilestoneExerciseElseThrow(Long groupId, Long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdAndCourseIdWithMilestoneExercise(groupId, courseId), groupId);
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
     * Resolves a {@code MilestoneExerciseGroup}'s anchor {@code milestoneExercise} id without loading any entity - a
     * scalar projection, so it carries none of the lazy-proxy/session-lifetime risk eager-fetching the full group or
     * exercise would. Used by {@code ParticipationService} to find the milestone's own participation when a
     * {@code UserStoryExercise} in the group is started (it should share that participation's repository in preference
     * to any other sibling's, since the milestone's is the group's canonical one once it exists).
     *
     * @param groupId the id of the group to resolve the anchor milestone exercise id for
     * @return the milestone exercise id, or empty if the group isn't a milestone group (or doesn't exist)
     */
    @Query("""
            SELECT g.milestoneExercise.id
            FROM MilestoneExerciseGroup g
            WHERE g.id = :groupId
            """)
    Optional<Long> findMilestoneExerciseIdByGroupId(@Param("groupId") Long groupId);

    /**
     * Resolves a {@code MilestoneExerciseGroup}'s anchor {@code milestoneExercise}, fully hydrated (its
     * {@code buildConfig}, {@code templateParticipation} and {@code solutionParticipation}, each a further {@code LAZY}
     * association). Used to hydrate an already-loaded {@code UserStoryExercise}'s {@code exerciseVariantGroup} before it
     * is serialized (e.g. {@code ProgrammingExerciseRetrievalResource.getProgrammingExercise}): {@code MilestoneExerciseGroup}'s
     * timeline getters ({@code getReleaseDate()}, {@code getDueDate()}, etc. - see {@link MilestoneExerciseGroup}) all
     * delegate to {@code milestoneExercise}, so those getters throw {@code LazyInitializationException} once the loading
     * session has closed (open-in-view is disabled) unless it was fetched - regardless of whether {@code milestoneExercise}
     * itself is ever serialized as its own JSON property.
     *
     * @param groupId the id of the group to resolve the hydrated anchor milestone exercise for
     * @return the milestone exercise, or empty if the group isn't a milestone group (or doesn't exist)
     */
    @Query("""
            SELECT me
            FROM MilestoneExerciseGroup g
                JOIN g.milestoneExercise me
                LEFT JOIN FETCH me.buildConfig
                LEFT JOIN FETCH me.templateParticipation
                LEFT JOIN FETCH me.solutionParticipation
            WHERE g.id = :groupId
            """)
    Optional<MilestoneExercise> findMilestoneExerciseByGroupId(@Param("groupId") Long groupId);

    /**
     * Resolves a {@code MilestoneExerciseGroup} from its anchor {@code milestoneExercise}'s id, with the group's
     * {@code exercises} eagerly fetched. The milestone exercise itself is never a member of that collection and never
     * has its own {@code exerciseVariantGroup} set (only member {@code UserStoryExercise}s do - see
     * {@link de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup}), so {@link #findByExerciseId} /
     * {@link #findByExerciseIdWithExercises} - which both resolve via {@code Exercise.exerciseVariantGroup} - always
     * return empty for a milestone exercise id; this query instead goes through the group's own
     * {@code milestoneExercise} reference, the only FK that actually links a milestone exercise to its group.
     *
     * @param milestoneExerciseId the id of the milestone exercise to resolve the owning group for
     * @return the group with its exercises initialized, or empty if the exercise isn't a milestone exercise (or doesn't exist)
     */
    @Query("""
            SELECT DISTINCT g
            FROM MilestoneExerciseGroup g
                LEFT JOIN FETCH g.exercises
            WHERE g.milestoneExercise.id = :milestoneExerciseId
            """)
    Optional<MilestoneExerciseGroup> findByMilestoneExerciseIdWithExercises(@Param("milestoneExerciseId") long milestoneExerciseId);

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
