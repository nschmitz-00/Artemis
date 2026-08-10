package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.SHORT_NAME_PATTERN;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.UserStoryExerciseRepository;

/**
 * Creates, updates, and deletes {@link UserStoryExercise}s belonging to a {@link MilestoneExercise}.
 * <p>
 * Unlike {@link MilestoneExerciseService}, this performs no repository/build-plan provisioning at all - a UserStoryExercise
 * only ever persists its own title, short name, max points, and problem statement; every repository- and date-related field
 * is delegated to the parent Milestone (see {@link UserStoryExercise}).
 */
@Profile(Constants.PROFILE_CORE)
@Lazy
@Service
public class UserStoryExerciseService {

    private static final String ENTITY_NAME = "userStoryExercise";

    private final MilestoneExerciseRepository milestoneExerciseRepository;

    private final UserStoryExerciseRepository userStoryExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public UserStoryExerciseService(MilestoneExerciseRepository milestoneExerciseRepository, UserStoryExerciseRepository userStoryExerciseRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.milestoneExerciseRepository = milestoneExerciseRepository;
        this.userStoryExerciseRepository = userStoryExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    /**
     * Creates a new UserStoryExercise under the given MilestoneExercise, parses its problem statement for test case
     * references against the Milestone's test cases, and recalculates the Milestone's total (derived) max points.
     *
     * @param milestoneExerciseId the id of the parent MilestoneExercise
     * @param userStoryExercise   the UserStoryExercise to create (title, shortName, maxPoints, problemStatement)
     * @return the persisted UserStoryExercise
     */
    public UserStoryExercise createUserStoryExercise(long milestoneExerciseId, UserStoryExercise userStoryExercise) {
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExerciseId);
        validateTitleAndShortName(userStoryExercise);

        userStoryExercise.setId(null);
        userStoryExercise.setMilestoneExercise(milestoneExercise);
        userStoryExercise.setCourse(milestoneExercise.getCourseViaExerciseGroupOrCourseMember());
        // The client sends dummy transient ProgrammingExercise-shaped objects (template/solution participation, build
        // config) for structural consistency with the ProgrammingExercise creation form, exactly like
        // ProgrammingExerciseCreationUpdateService#createProgrammingExercise does - a UserStoryExercise owns none of
        // these (all repo/build state is delegated to the parent Milestone, see UserStoryExercise), so they must be
        // cleared or Hibernate rejects the save with a TransientPropertyValueException on flush.
        clearOwnedProgrammingExerciseAssociations(userStoryExercise);
        // Every ProgrammingExercise row needs a non-null project key (see ProgrammingExercise#generateAndSetProjectKey); this
        // UserStoryExercise's own key is never actually used to look up a repository (all repo access is delegated to the
        // Milestone, see UserStoryExercise#getProjectKey), it merely satisfies the NOT NULL constraint on the shared column.
        userStoryExercise.generateAndSetProjectKey();
        // Store test references as ids rather than names, exactly like ProgrammingExerciseCreationUpdateService does. Students
        // are served the problem statement as stored, and the instruction renderer resolves a task's test status from these ids -
        // with plain names it cannot (test cases are not exposed to students), so every task would render as "not executed".
        programmingExerciseTaskService.replaceTestNamesWithIds(userStoryExercise);

        UserStoryExercise savedUserStoryExercise = userStoryExerciseRepository.save(userStoryExercise);
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedUserStoryExercise);

        milestoneExercise.addUserStoryExercise(savedUserStoryExercise);
        milestoneExercise.recalculateDerivedPoints();
        milestoneExerciseRepository.save(milestoneExercise);

        return savedUserStoryExercise;
    }

    /**
     * Updates the title, short name, max points, and problem statement of an existing UserStoryExercise, re-parses its
     * problem statement, and recalculates the parent Milestone's total max points. Dates and repositories are never part
     * of this update since they are entirely inherited from the parent Milestone.
     *
     * @param exerciseId               the id of the UserStoryExercise to update
     * @param updatedUserStoryExercise the UserStoryExercise carrying the updated title/shortName/maxPoints/problemStatement
     * @return the persisted, updated UserStoryExercise
     */
    public UserStoryExercise updateUserStoryExercise(long exerciseId, UserStoryExercise updatedUserStoryExercise) {
        UserStoryExercise existingUserStoryExercise = userStoryExerciseRepository.findWithMilestoneExerciseByIdElseThrow(exerciseId);
        validateTitleAndShortName(updatedUserStoryExercise);

        existingUserStoryExercise.setTitle(updatedUserStoryExercise.getTitle());
        existingUserStoryExercise.setShortName(updatedUserStoryExercise.getShortName());
        existingUserStoryExercise.setMaxPoints(updatedUserStoryExercise.getMaxPoints());
        existingUserStoryExercise.setBonusPoints(updatedUserStoryExercise.getBonusPoints());
        existingUserStoryExercise.setProblemStatement(updatedUserStoryExercise.getProblemStatement());
        // The editor works with test names; students are served the stored statement and need ids to resolve test status (see
        // createUserStoryExercise). getUserStoryExercise converts back to names when loading the form again.
        programmingExerciseTaskService.replaceTestNamesWithIds(existingUserStoryExercise);

        UserStoryExercise savedUserStoryExercise = userStoryExerciseRepository.save(existingUserStoryExercise);
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedUserStoryExercise);

        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(existingUserStoryExercise.getMilestoneExercise().getId());
        milestoneExercise.recalculateDerivedPoints();
        milestoneExerciseRepository.save(milestoneExercise);

        return savedUserStoryExercise;
    }

    /**
     * Deletes a UserStoryExercise and recalculates the parent Milestone's total max points.
     *
     * @param exerciseId the id of the UserStoryExercise to delete
     */
    public void deleteUserStoryExercise(long exerciseId) {
        UserStoryExercise userStoryExercise = userStoryExerciseRepository.findWithMilestoneExerciseByIdElseThrow(exerciseId);
        long milestoneExerciseId = userStoryExercise.getMilestoneExercise().getId();

        userStoryExerciseRepository.delete(userStoryExercise);

        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExerciseId);
        milestoneExercise.recalculateDerivedPoints();
        milestoneExerciseRepository.save(milestoneExercise);
    }

    private void clearOwnedProgrammingExerciseAssociations(UserStoryExercise userStoryExercise) {
        userStoryExercise.setTemplateParticipation(null);
        userStoryExercise.setSolutionParticipation(null);
        userStoryExercise.setBuildConfig(null);
    }

    private void validateTitleAndShortName(UserStoryExercise userStoryExercise) {
        userStoryExercise.validateTitle();
        if (userStoryExercise.getShortName() == null || !SHORT_NAME_PATTERN.matcher(userStoryExercise.getShortName()).matches()) {
            throw new BadRequestAlertException("The short name is invalid.", ENTITY_NAME, "shortNamePatternInvalid");
        }
    }
}
