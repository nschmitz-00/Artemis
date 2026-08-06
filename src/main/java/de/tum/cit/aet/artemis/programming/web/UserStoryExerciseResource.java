package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.UserStoryExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.UserStoryExerciseService;

/**
 * REST controller for creating, updating, retrieving, and deleting UserStoryExercises belonging to a MilestoneExercise.
 * <p>
 * A UserStoryExercise has no repositories or build plan of its own - see {@link UserStoryExercise} - so, unlike
 * {@link MilestoneExerciseResource}, none of these endpoints touch the version control or CI systems at all.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class UserStoryExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(UserStoryExerciseResource.class);

    private static final String ENTITY_NAME = "userStoryExercise";

    private final AuthorizationCheckService authCheckService;

    private final UserStoryExerciseService userStoryExerciseService;

    private final UserStoryExerciseRepository userStoryExerciseRepository;

    private final MilestoneExerciseRepository milestoneExerciseRepository;

    private final UserRepository userRepository;

    public UserStoryExerciseResource(AuthorizationCheckService authCheckService, UserStoryExerciseService userStoryExerciseService,
            UserStoryExerciseRepository userStoryExerciseRepository, MilestoneExerciseRepository milestoneExerciseRepository, UserRepository userRepository) {
        this.authCheckService = authCheckService;
        this.userStoryExerciseService = userStoryExerciseService;
        this.userStoryExerciseRepository = userStoryExerciseRepository;
        this.milestoneExerciseRepository = milestoneExerciseRepository;
        this.userRepository = userRepository;
    }

    /**
     * POST /milestone-exercises/{milestoneExerciseId}/user-story-exercises : Creates a new UserStoryExercise under a MilestoneExercise.
     *
     * @param milestoneExerciseId the id of the parent MilestoneExercise
     * @param userStoryExercise   the UserStoryExercise to create (title, shortName, maxPoints, problemStatement)
     * @return the ResponseEntity with status 201 (Created) and the new UserStoryExercise
     * @throws URISyntaxException if the location URI could not be built
     */
    @PostMapping("milestone-exercises/{milestoneExerciseId}/user-story-exercises")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<UserStoryExercise> createUserStoryExercise(@PathVariable long milestoneExerciseId, @RequestBody UserStoryExercise userStoryExercise)
            throws URISyntaxException {
        log.debug("REST request to create UserStoryExercise under MilestoneExercise {} : {}", milestoneExerciseId, userStoryExercise);
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findByIdElseThrow(milestoneExerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, milestoneExercise, user);

        UserStoryExercise newUserStoryExercise = userStoryExerciseService.createUserStoryExercise(milestoneExerciseId, userStoryExercise);
        return ResponseEntity.created(new URI("/api/programming/user-story-exercises/" + newUserStoryExercise.getId())).body(newUserStoryExercise);
    }

    /**
     * PUT /user-story-exercises/{exerciseId} : Updates an existing UserStoryExercise's title, short name, max points, and
     * problem statement. Dates and repositories are never part of the payload since both are inherited from the parent Milestone.
     *
     * @param exerciseId        the id of the UserStoryExercise to update
     * @param userStoryExercise the UserStoryExercise carrying the updated fields
     * @return the ResponseEntity with status 200 (OK) and the updated UserStoryExercise
     */
    @PutMapping("user-story-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<UserStoryExercise> updateUserStoryExercise(@PathVariable long exerciseId, @RequestBody UserStoryExercise userStoryExercise) {
        log.debug("REST request to update UserStoryExercise : {}", exerciseId);
        UserStoryExercise existingUserStoryExercise = userStoryExerciseRepository.findWithMilestoneExerciseByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, existingUserStoryExercise, user);
        if (userStoryExercise.getId() != null && !userStoryExercise.getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id in the body", ENTITY_NAME, "idMismatch");
        }

        UserStoryExercise updatedUserStoryExercise = userStoryExerciseService.updateUserStoryExercise(exerciseId, userStoryExercise);
        return ResponseEntity.ok(updatedUserStoryExercise);
    }

    /**
     * GET /user-story-exercises/{exerciseId} : Gets a UserStoryExercise, with its parent MilestoneExercise eagerly loaded.
     *
     * @param exerciseId the id of the UserStoryExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and the UserStoryExercise
     */
    @GetMapping("user-story-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<UserStoryExercise> getUserStoryExercise(@PathVariable long exerciseId) {
        log.debug("REST request to get UserStoryExercise : {}", exerciseId);
        UserStoryExercise userStoryExercise = userStoryExerciseRepository.findWithMilestoneExerciseByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, userStoryExercise, user);
        return ResponseEntity.ok(userStoryExercise);
    }

    /**
     * DELETE /user-story-exercises/{exerciseId} : Deletes a UserStoryExercise (a plain DB-row delete - a UserStoryExercise owns
     * no repositories or build plan of its own, see {@link UserStoryExercise}) and recalculates the parent Milestone's total max points.
     *
     * @param exerciseId the id of the UserStoryExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("user-story-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteUserStoryExercise(@PathVariable long exerciseId) {
        log.debug("REST request to delete UserStoryExercise : {}", exerciseId);
        UserStoryExercise userStoryExercise = userStoryExerciseRepository.findWithMilestoneExerciseByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, userStoryExercise, user);

        userStoryExerciseService.deleteUserStoryExercise(exerciseId);
        return ResponseEntity.ok().build();
    }
}
