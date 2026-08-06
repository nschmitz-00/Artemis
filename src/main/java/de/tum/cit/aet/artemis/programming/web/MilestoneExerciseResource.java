package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.MilestoneExerciseService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

/**
 * REST controller for creating, updating, retrieving, and listing MilestoneExercises.
 * <p>
 * A MilestoneExercise is a lightly specialized ProgrammingExercise (see {@link MilestoneExercise}) that additionally groups
 * 0..n UserStoryExercises (see {@link UserStoryExerciseResource}); deletion is handled by the existing, unmodified
 * {@code DELETE /api/programming/programming-exercises/{exerciseId}} endpoint since it requires no different logic for the
 * repository/build-plan cleanup that a MilestoneExercise, as a real ProgrammingExercise, still owns.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class MilestoneExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(MilestoneExerciseResource.class);

    private static final String ENTITY_NAME = "milestoneExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final MilestoneExerciseService milestoneExerciseService;

    private final MilestoneExerciseRepository milestoneExerciseRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public MilestoneExerciseResource(CourseService courseService, AuthorizationCheckService authCheckService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, MilestoneExerciseService milestoneExerciseService,
            MilestoneExerciseRepository milestoneExerciseRepository, CourseRepository courseRepository, UserRepository userRepository) {
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.milestoneExerciseService = milestoneExerciseService;
        this.milestoneExerciseRepository = milestoneExerciseRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * POST /milestone-exercises/setup : Sets up a new MilestoneExercise, including its three repositories and build plan.
     *
     * @param milestoneExercise the MilestoneExercise to set up
     * @return the ResponseEntity with status 201 (Created) and the new MilestoneExercise, or 500 if repository/build-plan setup failed
     */
    @PostMapping("milestone-exercises/setup")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<MilestoneExercise> createMilestoneExercise(@RequestBody MilestoneExercise milestoneExercise) {
        log.debug("REST request to setup MilestoneExercise : {}", milestoneExercise);
        milestoneExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(milestoneExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(milestoneExercise, course);

        try {
            MilestoneExercise newMilestoneExercise = milestoneExerciseService.createMilestoneExercise(milestoneExercise);
            return ResponseEntity.created(new URI("/api/programming/milestone-exercises/" + newMilestoneExercise.getId())).body(newMilestoneExercise);
        }
        catch (IOException | URISyntaxException | GitAPIException | ContinuousIntegrationException e) {
            log.error("Error while setting up MilestoneExercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorMilestoneExercise")).body(null);
        }
    }

    /**
     * PUT /milestone-exercises/{exerciseId} : Updates an existing MilestoneExercise's non-derived fields (dates, channel,
     * categories, build configuration, ...). maxPoints is always derived from the UserStoryExercise children and cannot be
     * set through this endpoint.
     *
     * @param exerciseId        the id of the MilestoneExercise to update
     * @param milestoneExercise the MilestoneExercise carrying the updated fields
     * @return the ResponseEntity with status 200 (OK) and the updated MilestoneExercise
     * @throws JsonProcessingException if the build plan configuration could not be serialized
     */
    @PutMapping("milestone-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<MilestoneExercise> updateMilestoneExercise(@PathVariable long exerciseId, @RequestBody MilestoneExercise milestoneExercise)
            throws JsonProcessingException {
        log.debug("REST request to update MilestoneExercise : {}", exerciseId);
        MilestoneExercise existingMilestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, existingMilestoneExercise, user);
        if (milestoneExercise.getId() != null && !milestoneExercise.getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id in the body", ENTITY_NAME, "idMismatch");
        }

        milestoneExercise.setId(exerciseId);
        // maxPoints is derived from the UserStoryExercise children and must never be overwritten by the client.
        milestoneExercise.setMaxPoints(existingMilestoneExercise.getMaxPoints());
        Set<Long> originalCompetencyIds = existingMilestoneExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

        MilestoneExercise updatedMilestoneExercise = milestoneExerciseService.updateMilestoneExercise(milestoneExercise, null, originalCompetencyIds);
        return ResponseEntity.ok(updatedMilestoneExercise);
    }

    /**
     * GET /milestone-exercises/{exerciseId} : Gets a MilestoneExercise including its UserStoryExercise children in a single
     * query (avoiding a separate round trip for the editor/student exercise list).
     *
     * @param exerciseId the id of the MilestoneExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and the MilestoneExercise, including its userStoryExercises
     */
    @GetMapping("milestone-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<MilestoneExercise> getMilestoneExercise(@PathVariable long exerciseId) {
        log.debug("REST request to get MilestoneExercise : {}", exerciseId);
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(exerciseId)
                .orElseThrow(() -> new BadRequestAlertException("MilestoneExercise not found", ENTITY_NAME, "milestoneExerciseNotFound"));
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, milestoneExercise, user);
        return ResponseEntity.ok(milestoneExercise);
    }

    /**
     * GET /courses/{courseId}/milestone-exercises : Gets all MilestoneExercises of a course (for the course management exercise list).
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of MilestoneExercises of the course
     */
    @GetMapping("courses/{courseId}/milestone-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<List<MilestoneExercise>> getMilestoneExercisesForCourse(@PathVariable long courseId) {
        log.debug("REST request to get all MilestoneExercises for course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        return ResponseEntity.ok(milestoneExerciseRepository.findAllByCourseId(courseId));
    }
}
