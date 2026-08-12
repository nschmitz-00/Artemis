package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.dto.MilestoneExerciseUserStoryCountDTO;
import de.tum.cit.aet.artemis.programming.dto.MilestoneProgressDTO;
import de.tum.cit.aet.artemis.programming.dto.MilestoneTestCaseCoverageDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.MilestoneExerciseService;
import de.tum.cit.aet.artemis.programming.service.MilestoneProgressService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseUpdateDtoService;
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

    private final MilestoneProgressService milestoneProgressService;

    private final MilestoneExerciseRepository milestoneExerciseRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ProgrammingExerciseUpdateDtoService programmingExerciseUpdateDtoService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public MilestoneExerciseResource(CourseService courseService, AuthorizationCheckService authCheckService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, MilestoneExerciseService milestoneExerciseService,
            MilestoneProgressService milestoneProgressService, MilestoneExerciseRepository milestoneExerciseRepository, CourseRepository courseRepository,
            UserRepository userRepository, ProgrammingExerciseUpdateDtoService programmingExerciseUpdateDtoService, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.milestoneExerciseService = milestoneExerciseService;
        this.milestoneProgressService = milestoneProgressService;
        this.milestoneExerciseRepository = milestoneExerciseRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.programmingExerciseUpdateDtoService = programmingExerciseUpdateDtoService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    /**
     * POST /milestone-exercises/setup : Sets up a new MilestoneExercise, including its three repositories and build plan.
     * <p>
     * With {@code repositorySourceMilestoneId} the new Milestone works on the repositories of that existing Milestone instead
     * of getting any of its own - the way a course runs several Milestones over one continuously growing codebase. That choice
     * exists only here, at creation: it decides where the repositories students push to live, which cannot be moved afterwards.
     *
     * @param milestoneExercise           the MilestoneExercise to set up
     * @param repositorySourceMilestoneId the id of the Milestone whose repositories should be reused, or null to create new ones
     * @return the ResponseEntity with status 201 (Created) and the new MilestoneExercise, or 500 if repository/build-plan setup failed
     */
    @PostMapping("milestone-exercises/setup")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<MilestoneExercise> createMilestoneExercise(@RequestBody MilestoneExercise milestoneExercise,
            @RequestParam(value = "repositorySourceMilestoneId", required = false) Long repositorySourceMilestoneId) {
        log.debug("REST request to setup MilestoneExercise : {} (reusing the repositories of {})", milestoneExercise, repositorySourceMilestoneId);
        milestoneExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(milestoneExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(milestoneExercise, course);

        // Never taken from the request body: the link is a real association, so a client-deserialized MilestoneExercise in it
        // would be a detached entity that the creation path would try to persist. It is identified by id and loaded here.
        milestoneExercise.setRepositorySourceMilestone(null);
        MilestoneExercise repositorySource = repositorySourceMilestoneId == null ? null : findRepositorySourceElseThrow(repositorySourceMilestoneId, course);

        try {
            MilestoneExercise newMilestoneExercise = milestoneExerciseService.createMilestoneExercise(milestoneExercise, repositorySource);
            return ResponseEntity.created(new URI("/api/programming/milestone-exercises/" + newMilestoneExercise.getId())).body(newMilestoneExercise);
        }
        catch (IOException | URISyntaxException | GitAPIException | ContinuousIntegrationException e) {
            log.error("Error while setting up MilestoneExercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorMilestoneExercise")).body(null);
        }
    }

    /**
     * Loads the Milestone whose repositories a new Milestone should work on, with everything the creation path reads off it
     * (its participations carry the repository URIs, its build config the settings the new Milestone takes over).
     *
     * @param repositorySourceMilestoneId the id of the Milestone to reuse
     * @param course                      the course the new Milestone is created in
     * @return the source MilestoneExercise
     */
    private MilestoneExercise findRepositorySourceElseThrow(long repositorySourceMilestoneId, Course course) {
        MilestoneExercise repositorySource = milestoneExerciseRepository
                .findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(repositorySourceMilestoneId)
                .orElseThrow(() -> new BadRequestAlertException("The milestone exercise whose repositories should be reused does not exist", ENTITY_NAME,
                        "repositorySourceMilestoneNotFound"));
        // Same course only: the repositories are governed by that course's staff, and a cross-course link would let a Milestone
        // hand access to another course's code to its own students
        if (!course.getId().equals(repositorySource.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new BadRequestAlertException("The milestone exercise whose repositories should be reused belongs to a different course", ENTITY_NAME,
                    "repositorySourceMilestoneOfOtherCourse");
        }
        return repositorySource;
    }

    /**
     * PUT /milestone-exercises/{exerciseId} : Updates an existing MilestoneExercise's non-derived fields (dates, channel,
     * categories, problem statement, build configuration, ...). maxPoints is always derived from the UserStoryExercise
     * children and cannot be set through this endpoint.
     * <p>
     * The body is a DTO rather than a MilestoneExercise on purpose. ProgrammingExercise and its superclass declare 16
     * associations with {@code orphanRemoval = true} - student participations, teams, attachments, test cases, tasks,
     * grading criteria, the UserStoryExercise children, ... - so persisting a client-deserialized entity would delete
     * every collection the payload happens to omit. (A student participation with a VCS access token even turned that
     * into a 500 rather than silent data loss, because the token's foreign key is ON DELETE RESTRICT.) Applying the DTO
     * onto the loaded entity leaves all of them untouched, which is exactly what the ProgrammingExercise update endpoint
     * does.
     *
     * @param exerciseId the id of the MilestoneExercise to update
     * @param updateDTO  the DTO carrying the updated fields
     * @return the ResponseEntity with status 200 (OK) and the updated MilestoneExercise
     * @throws JsonProcessingException if the build plan configuration could not be serialized
     */
    @PutMapping("milestone-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<MilestoneExercise> updateMilestoneExercise(@PathVariable long exerciseId, @RequestBody UpdateProgrammingExerciseDTO updateDTO)
            throws JsonProcessingException {
        log.debug("REST request to update MilestoneExercise : {}", exerciseId);
        if (updateDTO.id() != null && !updateDTO.id().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id in the body", ENTITY_NAME, "idMismatch");
        }

        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findForUpdateByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, milestoneExercise, user);

        // Read before applying the DTO, which overwrites both on the same (L1-cached) entity
        Set<Long> originalCompetencyIds = milestoneExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());
        Double derivedMaxPoints = milestoneExercise.getMaxPoints();
        Double derivedBonusPoints = milestoneExercise.getBonusPoints();
        MilestoneExercise repositorySourceMilestone = milestoneExercise.getRepositorySourceMilestone();

        programmingExerciseUpdateDtoService.applyTo(updateDTO, milestoneExercise);
        // Both point totals are derived from the UserStoryExercise children and must never be taken from the client.
        milestoneExercise.setMaxPoints(derivedMaxPoints);
        milestoneExercise.setBonusPoints(derivedBonusPoints);
        // Which repositories this Milestone works on is fixed at creation: students have already pushed to them, so it cannot
        // move afterwards. Restored here in case the DTO application cleared it.
        milestoneExercise.setRepositorySourceMilestone(repositorySourceMilestone);

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
        // Problem statements are persisted with test ids, but the editor works with test names - for the children too, since the
        // edit page feeds their statements to the instruction status bar to decide which test cases are still unused.
        programmingExerciseTaskService.replaceTestIdsWithNames(milestoneExercise);
        milestoneExercise.getUserStoryExercises().forEach(programmingExerciseTaskService::replaceTestIdsWithNames);
        return ResponseEntity.ok(milestoneExercise);
    }

    /**
     * GET /milestone-exercises/{exerciseId}/test-case-coverage : Reports the Milestone's active test cases that its
     * UserStoryExercises do not claim exactly once - orphans (claimed by none, so unreachable for students) and duplicates
     * (claimed by several, so paid out several times).
     * <p>
     * Purely informational: the editor is expected to pass through both states while adding UserStories one at a time, so this
     * never blocks a save.
     *
     * @param exerciseId the id of the MilestoneExercise to check
     * @return the ResponseEntity with status 200 (OK) and the orphan and duplicate test cases
     */
    @GetMapping("milestone-exercises/{exerciseId}/test-case-coverage")
    @EnforceAtLeastEditor
    public ResponseEntity<MilestoneTestCaseCoverageDTO> getTestCaseCoverage(@PathVariable long exerciseId) {
        log.debug("REST request to get the test case coverage of MilestoneExercise : {}", exerciseId);
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, milestoneExercise, user);
        return ResponseEntity.ok(milestoneExerciseService.analyseTestCaseCoverage(exerciseId));
    }

    /**
     * GET /milestone-exercises/{exerciseId}/progress : Gets the requesting student's story-by-story progress on a Milestone -
     * per user story its status and the points earned on it, plus the aggregate over all of them.
     * <p>
     * Student-level, and always reports on the requesting user: a Milestone carries no points of its own, so this is the only
     * place a student can see what their pushes have paid out per user story (see {@link MilestoneProgressService}). Tutors and
     * above get their own progress here as well - the tutor-facing view of a student's user stories is
     * {@link MilestoneAssessmentResource#getUserStoryAssessments}.
     *
     * @param exerciseId the id of the MilestoneExercise to report on
     * @return the ResponseEntity with status 200 (OK) and the requesting student's progress on the Milestone
     */
    @GetMapping("milestone-exercises/{exerciseId}/progress")
    @EnforceAtLeastStudent
    public ResponseEntity<MilestoneProgressDTO> getMilestoneProgress(@PathVariable long exerciseId) {
        log.debug("REST request to get the progress of the current user on MilestoneExercise : {}", exerciseId);
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, milestoneExercise, user);
        // Students must not learn anything about an exercise that has not been released to them yet; tutors and above legitimately see it early
        if (!milestoneExercise.isVisibleToStudents() && !authCheckService.isAtLeastTeachingAssistantForExercise(milestoneExercise, user)) {
            throw new AccessForbiddenException("The milestone exercise has not been released yet");
        }
        return ResponseEntity.ok(milestoneProgressService.getProgressForStudent(milestoneExercise, user.getId()));
    }

    /**
     * GET /courses/{courseId}/milestone-exercises : Gets all MilestoneExercises of a course (for the course management exercise list).
     * <p>
     * Tutor-level like {@code ProgrammingExerciseRetrievalResource#getProgrammingExercisesForCourse}, not editor-level: this backs
     * one section of the course management exercise list, which tutors can open. Requiring EDITOR here made that page fail with a
     * 403 for them while every other exercise type still listed. The read-only nature is enforced in the list itself, where the
     * edit/create/delete actions are gated on the per-exercise editor and instructor rights.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of MilestoneExercises of the course
     */
    @GetMapping("courses/{courseId}/milestone-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MilestoneExercise>> getMilestoneExercisesForCourse(@PathVariable long courseId) {
        log.debug("REST request to get all MilestoneExercises for course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        List<MilestoneExercise> milestoneExercises = milestoneExerciseRepository.findAllByCourseId(courseId);
        // Counted separately instead of fetching the children, which would serialize every UserStoryExercise in full just so
        // the exercise list can show how many there are.
        Map<Long, Long> userStoryExerciseCounts = milestoneExerciseRepository.countUserStoryExercisesByCourseId(courseId).stream()
                .collect(Collectors.toMap(MilestoneExerciseUserStoryCountDTO::milestoneExerciseId, MilestoneExerciseUserStoryCountDTO::count));
        milestoneExercises
                .forEach(milestoneExercise -> milestoneExercise.setNumberOfUserStoryExercises(userStoryExerciseCounts.getOrDefault(milestoneExercise.getId(), 0L).intValue()));

        return ResponseEntity.ok(milestoneExercises);
    }
}
