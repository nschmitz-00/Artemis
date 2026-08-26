package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.validation.Valid;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
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
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneStatusDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.programming.service.UserStoryExerciseService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * REST controller for managing {@link ExerciseVariantGroup}s, the course-owned groupings of interchangeable exercise
 * variants.
 * <p>
 * Authorization mirrors the rights for the exercises themselves: editors create, update and read groups (and assign
 * exercises to them), while only instructors may delete a group. Every endpoint additionally verifies that the targeted
 * group (and exercise) belongs to the course in the request path.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ExerciseVariantGroupResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantGroupResource.class);

    private static final String ENTITY_NAME = "exerciseVariantGroup";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseRepository courseRepository;

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseVariantGroupService exerciseVariantGroupService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final ExerciseVersionService exerciseVersionService;

    private final UserStoryExerciseService userStoryExerciseService;

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ChannelService channelService;

    private final ParticipationService participationService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ResultRepository resultRepository;

    public ExerciseVariantGroupResource(CourseRepository courseRepository, ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseRepository exerciseRepository,
            ExerciseVariantGroupService exerciseVariantGroupService, UserRepository userRepository, AuthorizationCheckService authCheckService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            StaticCodeAnalysisService staticCodeAnalysisService, ExerciseVersionService exerciseVersionService, UserStoryExerciseService userStoryExerciseService,
            ProgrammingExerciseDeletionService programmingExerciseDeletionService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ChannelService channelService,
            ParticipationService participationService, ProgrammingExerciseGradingService programmingExerciseGradingService, ResultRepository resultRepository) {
        this.courseRepository = courseRepository;
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseVariantGroupService = exerciseVariantGroupService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.exerciseVersionService = exerciseVersionService;
        this.userStoryExerciseService = userStoryExerciseService;
        this.programmingExerciseDeletionService = programmingExerciseDeletionService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.channelService = channelService;
        this.participationService = participationService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.resultRepository = resultRepository;
    }

    /**
     * POST /courses/:courseId/exercise-variant-groups : Create a new exercise variant group in the given course.
     *
     * @param createDTO the settings of the group to create
     * @param courseId  the id of the course that will own the group
     * @return the ResponseEntity with status 201 (Created) and the created group in the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exercise-variant-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> createExerciseVariantGroup(@Valid @RequestBody CreateExerciseVariantGroupDTO createDTO, @PathVariable Long courseId)
            throws URISyntaxException {
        log.debug("REST request to create ExerciseVariantGroup in course {} : {}", courseId, createDTO);
        ExerciseVariantGroup group = createDTO.toEntity();
        group.validateDates();
        // The course owns the unidirectional collection, so save the group first to get an id, then attach it to write the
        // course_id FK. Not transactional (this codebase avoids service-level @Transactional); a failure between the two
        // saves can only leave an orphan, course-less group that no course-scoped query ever sees.
        group = exerciseVariantGroupRepository.save(group);
        Course course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
        return ResponseEntity.created(new URI("/api/exercise/courses/" + courseId + "/exercise-variant-groups/" + group.getId())).body(new ExerciseVariantGroupDTO(group));
    }

    /**
     * POST /courses/:courseId/milestone-exercise-groups : Create a new milestone exercise group in the given course.
     * <p>
     * Unlike a plain group, a milestone group is never created empty: this provisions a real {@link MilestoneExercise}
     * (repositories, build plan, the works — reusing the same pipeline as {@code POST programming-exercises/setup}) and
     * wires it as the new group's dedicated anchor in one request, matching the "auto-created together with the group"
     * UX — there is no separate "create milestone exercise" endpoint.
     *
     * @param milestoneExercise the settings of the milestone exercise to set up; its {@code course} is overwritten from
     *                              the path
     * @param courseId          the id of the course that will own the group
     * @return the ResponseEntity with status 201 (Created) and the created group (with its milestone exercise wired) in
     *         the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/milestone-exercise-groups")
    @EnforceAtLeastEditorInCourse
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ExerciseVariantGroupDTO> createMilestoneExerciseGroup(@RequestBody MilestoneExercise milestoneExercise, @PathVariable Long courseId)
            throws URISyntaxException {
        log.debug("REST request to create MilestoneExerciseGroup in course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        milestoneExercise.setCourse(course);
        milestoneExercise.setExerciseGroup(null);
        milestoneExercise.setExerciseVariantGroup(null);
        // Milestones aren't Athena-assessed - never included in an overall score to begin with (see MilestoneExercise).
        milestoneExercise.setFeedbackSuggestionModule(null);
        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(milestoneExercise, course);
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(milestoneExercise, ENTITY_NAME);

        MilestoneExercise createdMilestoneExercise;
        try {
            createdMilestoneExercise = (MilestoneExercise) programmingExerciseCreationUpdateService.createProgrammingExercise(milestoneExercise, false);
        }
        catch (IOException | GitAPIException | ContinuousIntegrationException e) {
            log.error("Error while setting up milestone exercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the milestone: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
        if (Boolean.TRUE.equals(milestoneExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(createdMilestoneExercise);
        }
        exerciseVersionService.createExerciseVersion(createdMilestoneExercise);

        MilestoneExerciseGroup group = new MilestoneExerciseGroup();
        group.setTitle(createdMilestoneExercise.getTitle());
        group.setMilestoneExercise(createdMilestoneExercise);
        // Same not-transactional two-step save as createExerciseVariantGroup above; a failure between the two saves
        // leaves an orphan, course-less group (the milestone exercise itself is already fully created and visible).
        group = exerciseVariantGroupRepository.save(group);
        Course courseForGroup = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        courseForGroup.addExerciseVariantGroup(group);
        courseRepository.save(courseForGroup);
        return ResponseEntity.created(new URI("/api/exercise/courses/" + courseId + "/exercise-variant-groups/" + group.getId())).body(new ExerciseVariantGroupDTO(group));
    }

    /**
     * POST /courses/:courseId/exercise-variant-groups/:groupId/user-story-exercises : Create a new user story exercise
     * in the given milestone exercise group.
     * <p>
     * Its Language/Version-Control settings, repositories and timeline are silently overwritten from the group's
     * {@code MilestoneExercise} regardless of what the request body carries for them - a user story is never
     * independently configured on any of these - only its title/short name/problem statement/grading settings and the
     * target group are taken from the request.
     *
     * @param userStoryExercise the settings of the user story exercise to create
     * @param groupId           the id of the milestone exercise group that will own the exercise
     * @param courseId          the id of the course the group belongs to
     * @return the ResponseEntity with status 201 (Created) and the created exercise in the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exercise-variant-groups/{groupId}/user-story-exercises")
    @EnforceAtLeastEditorInCourse
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<UserStoryExercise> createUserStoryExercise(@RequestBody UserStoryExercise userStoryExercise, @PathVariable Long groupId, @PathVariable Long courseId)
            throws URISyntaxException {
        log.debug("REST request to create UserStoryExercise in milestone exercise group {} of course {}", groupId, courseId);
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdWithMilestoneExerciseElseThrow(groupId, courseId);
        if (!(group instanceof MilestoneExerciseGroup milestoneGroup) || milestoneGroup.getMilestoneExercise() == null) {
            throw new BadRequestAlertException("A user story exercise can only be created in a milestone exercise group", ENTITY_NAME, "milestoneGroupRequired");
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        userStoryExercise.setCourse(course);
        userStoryExercise.setExerciseGroup(null);
        userStoryExercise.setExerciseVariantGroup(milestoneGroup);
        userStoryExercise.setFeedbackSuggestionModule(null);
        userStoryExerciseService.applyMilestoneConfig(userStoryExercise, milestoneGroup.getMilestoneExercise());
        userStoryExercise.setReleaseDate(milestoneGroup.getReleaseDate());
        userStoryExercise.setStartDate(milestoneGroup.getStartDate());
        userStoryExercise.setDueDate(milestoneGroup.getDueDate());
        userStoryExercise.setAssessmentDueDate(milestoneGroup.getAssessmentDueDate());
        userStoryExercise.setExampleSolutionPublicationDate(milestoneGroup.getExampleSolutionPublicationDate());

        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(userStoryExercise, course);
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(userStoryExercise, ENTITY_NAME);

        // applyMilestoneConfig above attaches a fresh, still-transient buildConfig (copied from the milestone exercise)
        // and template/solution participations - none of which cascade PERSIST, so they need their own save dance.
        UserStoryExercise created = programmingExerciseCreationUpdateService.saveNewExerciseWithOwnAssociations(userStoryExercise);
        // Unlike the generic programming-exercise creation flow (ProgrammingExerciseCreationUpdateService.createProgrammingExercise),
        // saveNewExerciseWithOwnAssociations above is a narrow "persist this exercise plus its own build config/participations"
        // helper and does not create a communication channel - without this, students saw no channel at all under the
        // exercise's Communication tab.
        channelService.createExerciseChannel(created, Optional.ofNullable(userStoryExercise.getChannelName()));
        // Test cases can only be duplicated onto the new exercise once it has an id (applyMilestoneConfig above
        // skipped this for the same reason); the initial relevance derivation runs against a still-empty problem
        // statement, but re-runs on every later update - see the corresponding TODO on the (not yet implemented)
        // update endpoint.
        userStoryExerciseService.syncTestCasesFromMilestone(created, milestoneGroup.getMilestoneExercise());
        userStoryExerciseService.updateRelevantTestCases(created);
        backfillExistingParticipantsForNewUserStoryExercise(created, milestoneGroup);
        exerciseVersionService.createExerciseVersion(created);
        return ResponseEntity.created(new URI("/api/programming/programming-exercises/" + created.getId())).body(created);
    }

    /**
     * Retroactively provisions a participation - and an initial score derived from their latest existing result - for
     * every student who already shares {@code milestoneGroup}'s repository, so they don't have to start
     * {@code created} themselves for it to show up with a correct score. Only the latest already-graded result per
     * student is backfilled (not the full submission history); the score simply won't reflect this new exercise
     * before that point in time.
     *
     * @param created        the newly created user story exercise
     * @param milestoneGroup the group it was created in
     */
    private void backfillExistingParticipantsForNewUserStoryExercise(UserStoryExercise created, MilestoneExerciseGroup milestoneGroup) {
        long milestoneExerciseId = milestoneGroup.getMilestoneExercise().getId();
        for (ProgrammingExerciseStudentParticipation newParticipation : participationService.provisionParticipationsForNewUserStoryExercise(created)) {
            newParticipation.getStudent()
                    .flatMap(student -> programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(milestoneExerciseId, student.getLogin()))
                    .flatMap(milestoneParticipation -> resultRepository.findLatestResultWithFeedbacksForParticipation(milestoneParticipation.getId(), true))
                    .ifPresent(latestMilestoneResult -> programmingExerciseGradingService.fanOutResultToUserStoryExercise(latestMilestoneResult, created, newParticipation));
        }
    }

    /**
     * PUT /courses/:courseId/exercise-variant-groups/:groupId : Update an existing exercise variant group. The owning
     * course cannot be changed.
     *
     * @param updateDTO the new settings of the group
     * @param groupId   the id of the group to update
     * @param courseId  the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the updated group in the body
     */
    @PutMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> updateExerciseVariantGroup(@Valid @RequestBody UpdateExerciseVariantGroupDTO updateDTO, @PathVariable Long groupId,
            @PathVariable Long courseId) {
        log.debug("REST request to update ExerciseVariantGroup {} in course {} : {}", groupId, courseId, updateDTO);
        if (!Objects.equals(groupId, updateDTO.id())) {
            throw new BadRequestAlertException("The id in the path and the body must match", ENTITY_NAME, "idMismatch");
        }
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        updateDTO.applyTo(group);
        group.validateDates();
        exerciseVariantGroupService.saveWithTimelineAppliedToMembers(group);
        // Respond from the loaded entity (its exercises were fetched); the re-merged save() result has a lazy exercises
        // collection that cannot initialize once the session closes (open-in-view is off).
        return ResponseEntity.ok(new ExerciseVariantGroupDTO(group));
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups : Get all exercise variant groups of a course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of groups in the body
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<ExerciseVariantGroupDTO>> getExerciseVariantGroupsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ExerciseVariantGroups for course {}", courseId);
        List<ExerciseVariantGroupDTO> groups = exerciseVariantGroupRepository.findAllByCourseId(courseId).stream().map(ExerciseVariantGroupDTO::new).toList();
        return ResponseEntity.ok(groups);
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups/:groupId : Get a single exercise variant group.
     *
     * @param groupId  the id of the group to retrieve
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the group in the body
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> getExerciseVariantGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get ExerciseVariantGroup {} in course {}", groupId, courseId);
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        return ResponseEntity.ok(new ExerciseVariantGroupDTO(group));
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups/:groupId/problem-statements : Get the problem statements of a
     * group's member exercises in a single request.
     * <p>
     * This student-facing endpoint exists so the group detail page can render member previews without issuing one
     * heavyweight {@code /exercises/{id}/details} request per member (an unbounded fan-out, since group size is not
     * bounded). Only members the requesting user is allowed to see are returned, mirroring the visibility guard of the
     * details endpoint: exam exercises are excluded and each member is checked with
     * {@link AuthorizationCheckService#isAllowedToSeeCourseExercise}, so an unreleased variant's statement is not leaked
     * to students while teaching assistants and instructors still see it.
     *
     * @param groupId  the id of the group whose member problem statements to retrieve
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the list of {@code {exerciseId, problemStatement}} entries
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups/{groupId}/problem-statements")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<ExerciseProblemStatementDTO>> getExerciseVariantGroupProblemStatements(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get problem statements of ExerciseVariantGroup {} in course {}", groupId, courseId);
        User user = userRepository.getUserWithAuthorities();
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        // The members are fetched with a lazy course and open-in-view is off; set the known path course on each so the
        // visibility check below resolves without a LazyInitializationException and without an extra per-exercise query.
        Course course = courseRepository.findByIdElseThrow(courseId);
        group.getExercises().forEach(exercise -> exercise.setCourse(course));
        List<ExerciseProblemStatementDTO> problemStatements = group.getExercises().stream()
                .filter(exercise -> !exercise.isExamExercise() && authCheckService.isAllowedToSeeCourseExercise(exercise, user) && exercise.getProblemStatement() != null)
                .map(exercise -> new ExerciseProblemStatementDTO(exercise.getId(), exercise.getProblemStatement())).toList();
        return ResponseEntity.ok(problemStatements);
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups/:groupId/milestone-status : Whether the requesting student has
     * started the group's anchor milestone exercise.
     * <p>
     * The milestone exercise is never itself shown to students ({@code MilestoneExercise.isVisibleToStudents} is always
     * {@code false}), so the milestone group view can't just fetch its details like any other exercise to find this out.
     * All the group's {@code UserStoryExercise}s share the milestone's repository once it's started (see
     * {@code ParticipationService.startUserStoryExercise}), so this is what the view uses to decide whether to
     * offer a "Start exercise" action for the milestone itself.
     *
     * @param groupId  the id of the milestone exercise group to check
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the milestone's id, whether the student has started it, and
     *         the milestone's problem statement (which doubles as the group's description in the student group view)
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups/{groupId}/milestone-status")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<MilestoneStatusDTO> getMilestoneStatus(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get milestone status of ExerciseVariantGroup {} in course {}", groupId, courseId);
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdWithoutExercisesElseThrow(groupId, courseId);
        if (!(group instanceof MilestoneExerciseGroup)) {
            throw new BadRequestAlertException("The group is not a milestone exercise group", ENTITY_NAME, "notMilestoneGroup");
        }
        long milestoneExerciseId = exerciseVariantGroupRepository.findMilestoneExerciseIdByGroupId(groupId)
                .orElseThrow(() -> new BadRequestAlertException("The milestone group has no anchor milestone exercise", ENTITY_NAME, "milestoneExerciseMissing"));
        User user = userRepository.getUserWithAuthorities();
        // The milestone's problem statement doubles as the group's description in the student group view - the milestone
        // itself is never rendered, so this endpoint is the only path that can hand it to the group view.
        String problemStatement = exerciseVariantGroupRepository.findMilestoneProblemStatementByGroupId(groupId).orElse(null);
        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(milestoneExerciseId, user.getLogin());
        MilestoneStatusDTO status = participation.map(p -> new MilestoneStatusDTO(milestoneExerciseId, true, p.getId(), p.getRepositoryUri(), problemStatement))
                .orElseGet(() -> new MilestoneStatusDTO(milestoneExerciseId, false, null, null, problemStatement));
        return ResponseEntity.ok(status);
    }

    /**
     * DELETE /courses/:courseId/exercise-variant-groups/:groupId : Delete an exercise variant group. The aggregated
     * exercises survive and simply lose their group membership (the foreign key is set to null).
     *
     * @param groupId  the id of the group to delete
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteExerciseVariantGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to delete ExerciseVariantGroup {} in course {}", groupId, courseId);
        // Load the group without its members so the ON DELETE SET NULL FK (see the Liquibase changelog) ungroups them;
        // loading them would fail Hibernate's flush because managed exercises still reference the removed group. Members survive.
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdWithoutExercisesElseThrow(groupId, courseId);
        Long milestoneExerciseId = null;
        if (group instanceof MilestoneExerciseGroup) {
            if (exerciseVariantGroupRepository.countExercisesByGroupId(groupId) > 0) {
                // Unlike a plain group, a milestone's members share its repositories and grading pool: ungrouping them on
                // delete (as a plain group does) would leave them pointing at a milestone that's about to disappear.
                throw new BadRequestAlertException("A milestone exercise group can only be deleted while it has no exercises", ENTITY_NAME, "milestoneGroupNotEmpty");
            }
            milestoneExerciseId = exerciseVariantGroupRepository.findMilestoneExerciseIdByGroupId(groupId).orElse(null);
        }
        // The group row references the milestone exercise via an ON DELETE RESTRICT foreign key, so the group must be
        // deleted before the exercise itself; the milestoneExercise association has no CascadeType.REMOVE / orphanRemoval
        // (see MilestoneExerciseGroup), so deleting the group here does not also delete the exercise. The exercise is
        // deleted properly afterward through ProgrammingExerciseDeletionService's ordered, VCS/CI-aware cleanup.
        exerciseVariantGroupRepository.delete(group);
        if (milestoneExerciseId != null) {
            programmingExerciseDeletionService.delete(milestoneExerciseId, true);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, group.getTitle())).build();
    }

    /**
     * PUT /courses/:courseId/exercises/:exerciseId/variant-group : Assign an exercise to a variant group, or remove it
     * from its current group. Membership is edited from the exercise side, so moving between groups is a single request.
     * <p>
     * A group may mix exercise types (the same task in different formats). Scoring caps such a group once across all types
     * in the overall course score; only the per-type breakdown on the instructor scores page caps per type bucket.
     *
     * @param assignmentDTO the target group ({@code groupId == null} removes the exercise from its group)
     * @param exerciseId    the id of the exercise to (re-)assign
     * @param courseId      the id of the course the exercise (and the target group) belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/exercises/{exerciseId}/variant-group")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> setExerciseVariantGroup(@RequestBody ExerciseVariantGroupAssignmentDTO assignmentDTO, @PathVariable Long exerciseId, @PathVariable Long courseId) {
        log.debug("REST request to assign exercise {} in course {} to variant group {}", exerciseId, courseId, assignmentDTO.groupId());
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // An exam exercise reports its exam's course, so it would pass the ownership check below and later break group
            // timeline updates. Reject it up front.
            throw new BadRequestAlertException("Exam exercises cannot be assigned to a variant group", ENTITY_NAME, "examExerciseNotAllowed");
        }
        Course exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (exerciseCourse == null || !Objects.equals(exerciseCourse.getId(), courseId)) {
            throw new BadRequestAlertException("The exercise does not belong to the course in the path", ENTITY_NAME, "courseIdMismatch");
        }
        ExerciseVariantGroup group = assignmentDTO.groupId() == null ? null
                : exerciseVariantGroupRepository.findByIdAndCourseIdWithMilestoneExerciseElseThrow(assignmentDTO.groupId(), courseId);
        if (group != null && exercise instanceof QuizExercise quizExercise && quizExercise.getQuizMode() != QuizMode.INDIVIDUAL) {
            // Synchronized/batched quizzes have a single shared run, so only individual-mode quizzes (which support
            // per-student dates) can share a group timeline.
            throw new BadRequestAlertException("Only individual-mode quizzes can be added to an exercise group", ENTITY_NAME, "quizNotIndividual");
        }
        if (exercise instanceof UserStoryExercise && !(group instanceof MilestoneExerciseGroup)) {
            // A UserStory is meaningless without a Milestone (repositories, timeline and shared test cases all come from
            // one) — it may move between Milestone groups, but never leave one or join a plain variant group.
            throw new BadRequestAlertException("A user story exercise must always belong to a milestone exercise group", ENTITY_NAME, "userStoryRequiresMilestoneGroup");
        }
        if (exercise instanceof UserStoryExercise && group instanceof MilestoneExerciseGroup
                && programmingExerciseStudentParticipationRepository.existsByExerciseId(exercise.getId())) {
            // Moving re-syncs the (shared) repository URIs onto the exercise (see ExerciseVariantGroupService.assignToGroup /
            // UserStoryExerciseService.applyMilestoneConfig) - a student who already started at the old repository would be
            // silently left behind, so the move is rejected once any participation exists.
            throw new BadRequestAlertException("This user story exercise cannot be moved because students have already started it", ENTITY_NAME,
                    "userStoryHasStudentParticipations");
        }
        if (group instanceof MilestoneExerciseGroup && (exercise.getClass() == ProgrammingExercise.class || exercise instanceof MilestoneExercise)) {
            // Only user stories (and, implicitly, quiz/text/modeling/file-upload exercises) may join a milestone group —
            // a bare programming exercise has no relevant task/test-case wiring to the milestone, and the milestone
            // exercise itself is wired via its own dedicated field, never as a member of this collection.
            throw new BadRequestAlertException("Only user story exercises may be added to a milestone exercise group", ENTITY_NAME, "onlyUserStoriesInMilestoneGroup");
        }
        exerciseVariantGroupService.assignToGroup(exercise, group);
        return ResponseEntity.ok().build();
    }
}
