package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * Tests the milestone/user-story grading fan-out ({@link de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService#fanOutResultToUserStoryExercise}) and the
 * retroactive backfill of a newly created {@link UserStoryExercise} ({@link ParticipationService#provisionParticipationsForNewUserStoryExercise}).
 * <p>
 * Deliberately avoids the VCS/CI test infrastructure: both mechanisms under test only duplicate already-existing
 * database rows (submission/result/feedback/participation), they never touch a real repository or build plan.
 */
class UserStoryExerciseGradingFanOutTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userstoryfanout";

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    @Autowired
    private ParticipationService participationService;

    private Course course;

    private MilestoneExercise milestoneExercise;

    private MilestoneExerciseGroup group;

    private String studentLogin;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        studentLogin = TEST_PREFIX + "student1";

        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        milestoneExercise = new MilestoneExercise();
        milestoneExercise.setTitle("Milestone");
        milestoneExercise.setShortName("milestone" + TEST_PREFIX);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setCourse(course);
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        group = new MilestoneExerciseGroup();
        group.setTitle("Milestone Group " + TEST_PREFIX);
        group.setMilestoneExercise(milestoneExercise);
        group = (MilestoneExerciseGroup) exerciseVariantGroupRepository.save(group);

        course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
    }

    private UserStoryExercise createUserStoryExercise(String shortNameSuffix) {
        UserStoryExercise exercise = new UserStoryExercise();
        exercise.setTitle("UserStory " + shortNameSuffix);
        exercise.setShortName(shortNameSuffix + TEST_PREFIX);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setCourse(course);
        exercise.setMaxPoints(2.0);
        exercise.setExerciseVariantGroup(group);
        exercise.generateAndSetProjectKey();
        return (UserStoryExercise) programmingExerciseRepository.save(exercise);
    }

    private ProgrammingExerciseTestCase createTestCase(ProgrammingExercise exercise, String name) {
        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase().testName(name).weight(1.0).active(true).exercise(exercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1.0).bonusPoints(0.0);
        return testCaseRepository.save(testCase);
    }

    private ProgrammingExerciseStudentParticipation participationFor(ProgrammingExercise exercise) {
        return participationUtilService.addStudentParticipationForProgrammingExercise(exercise, studentLogin);
    }

    private Result buildSourceResult(ProgrammingExerciseStudentParticipation participation, String commitHash, List<Feedback> feedbacks) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setCommitHash(commitHash);
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setSubmitted(true);
        submission = programmingSubmissionRepository.save(submission);

        Result result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        result.setSuccessful(true);
        result.setExerciseId(participation.getExercise().getId());
        result.setSubmission(submission);
        result.setFeedbacks(feedbacks);
        return resultRepository.save(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutScoresEachSiblingWithOnlyItsOwnTestCases() {
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        // The milestone owns the full test suite; each UserStoryExercise only has the (already-synced, per
        // UserStoryExerciseService) subset of test cases relevant to it.
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        ProgrammingExerciseTestCase milestoneTestB = createTestCase(milestoneExercise, "testB");
        ProgrammingExerciseTestCase milestoneTestC = createTestCase(milestoneExercise, "testC");
        createTestCase(userStory1, "testA");
        createTestCase(userStory1, "testB");
        createTestCase(userStory2, "testB");
        createTestCase(userStory2, "testC");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation1 = participationFor(userStory1);
        ProgrammingExerciseStudentParticipation participation2 = participationFor(userStory2);

        List<Feedback> sourceFeedbacks = List.of(new Feedback().testCase(milestoneTestA).positive(true).type(FeedbackType.AUTOMATIC),
                new Feedback().testCase(milestoneTestB).positive(true).type(FeedbackType.AUTOMATIC),
                new Feedback().testCase(milestoneTestC).positive(false).type(FeedbackType.AUTOMATIC));
        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", sourceFeedbacks);

        Result us1Result = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory1, participation1);
        Result us2Result = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory2, participation2);

        // US1 only knows testA/testB, both positive -> full score; testC feedback isn't even attached (not its test case).
        assertThat(us1Result.getScore()).isEqualTo(100.0);
        assertThat(us1Result.getFeedbacks()).hasSize(2).allSatisfy(feedback -> assertThat(feedback.getTestCase().getExercise().getId()).isEqualTo(userStory1.getId()));
        assertThat(((ProgrammingSubmission) us1Result.getSubmission()).getCommitHash()).isEqualTo("commit-1");
        assertThat(us1Result.getSubmission().getParticipation().getId()).isEqualTo(participation1.getId());
        assertThat(us1Result.getSubmission().getId()).isNotEqualTo(sourceResult.getSubmission().getId());

        // US2 knows testB (positive) and testC (negative) -> half score; testA feedback isn't attached.
        assertThat(us2Result.getScore()).isEqualTo(50.0);
        assertThat(us2Result.getFeedbacks()).hasSize(2).allSatisfy(feedback -> assertThat(feedback.getTestCase().getExercise().getId()).isEqualTo(userStory2.getId()));

        // Both results are actually persisted, not just returned in-memory.
        assertThat(resultRepository.findById(us1Result.getId())).isPresent();
        assertThat(resultRepository.findById(us2Result.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutDoesNotTurnANeverExecutedTestIntoAPass() {
        UserStoryExercise userStory = createUserStoryExercise("us1");
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation = participationFor(userStory);
        createTestCase(userStory, "testA");

        // A dynamic/parameterized test JUnit couldn't even generate against a missing class is reported with
        // positive=null and credits=0 (not the same as an executed-and-failed test, which has positive=false) -
        // feedbackService.copyFeedback's setPositiveViaCredits() fallback (credits >= 0) must not turn that into a pass.
        Feedback neverExecuted = new Feedback().testCase(milestoneTestA).type(FeedbackType.AUTOMATIC);
        neverExecuted.setPositive(null);
        neverExecuted.setCredits(0.0);
        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", List.of(neverExecuted));

        Result fannedOutResult = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory, participation);

        assertThat(fannedOutResult.getScore()).isZero();
        assertThat(fannedOutResult.getFeedbacks()).singleElement().satisfies(feedback -> assertThat(feedback.isPositive()).isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void provisionPendingSubmissionsForUserStoryExercisesCreatesLiveBuildingIndicatorImmediately() {
        UserStoryExercise userStory = createUserStoryExercise("us1");
        createTestCase(milestoneExercise, "testA");
        createTestCase(userStory, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation = participationFor(userStory);

        ProgrammingSubmission milestoneSubmission = new ProgrammingSubmission();
        milestoneSubmission.setParticipation(milestoneParticipation);
        milestoneSubmission.setCommitHash("commit-1");
        milestoneSubmission.setType(SubmissionType.MANUAL);
        milestoneSubmission.setSubmissionDate(ZonedDateTime.now());
        milestoneSubmission.setSubmitted(true);
        milestoneSubmission = programmingSubmissionRepository.save(milestoneSubmission);

        // Called at push time, before any build has run - the point is that the sibling's "building..." indicator
        // must exist immediately, not only once fanOutResultToUserStoryExercise runs after the build completes.
        gradingService.provisionPendingSubmissionsForUserStoryExercises(milestoneSubmission, milestoneExercise, milestoneParticipation);

        List<ProgrammingSubmission> siblingSubmissions = programmingSubmissionRepository
                .findByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), "commit-1");
        assertThat(siblingSubmissions).singleElement().satisfies(submission -> assertThat(submission.getResults()).isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutReusesThePendingSubmissionCreatedAtPushTimeInsteadOfDuplicatingIt() {
        UserStoryExercise userStory = createUserStoryExercise("us1");
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        createTestCase(userStory, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation = participationFor(userStory);

        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", List.of(new Feedback().testCase(milestoneTestA).positive(true).type(FeedbackType.AUTOMATIC)));
        // Simulates the real push-time flow: a pending submission is created for the sibling first, before the
        // build (and therefore the result) exists.
        gradingService.provisionPendingSubmissionsForUserStoryExercises((ProgrammingSubmission) sourceResult.getSubmission(), milestoneExercise, milestoneParticipation);

        Result fannedOutResult = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory, participation);

        List<ProgrammingSubmission> siblingSubmissions = programmingSubmissionRepository
                .findByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), "commit-1");
        assertThat(siblingSubmissions).singleElement().satisfies(submission -> assertThat(submission.getId()).isEqualTo(fannedOutResult.getSubmission().getId()));
        assertThat(fannedOutResult.getScore()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void backfillProvisionsParticipationAndScoreForNewUserStoryExercise() {
        UserStoryExercise existingUserStory = createUserStoryExercise("existing");
        createTestCase(existingUserStory, "testA");

        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        participationFor(existingUserStory);

        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", List.of(new Feedback().testCase(milestoneTestA).positive(true).type(FeedbackType.AUTOMATIC)));

        // A student story added to the group AFTER the student already started it via the milestone.
        UserStoryExercise newUserStory = createUserStoryExercise("new");
        createTestCase(newUserStory, "testA");

        List<ProgrammingExerciseStudentParticipation> created = participationService.provisionParticipationsForNewUserStoryExercise(newUserStory);

        assertThat(created).hasSize(1);
        ProgrammingExerciseStudentParticipation newParticipation = created.get(0);
        assertThat(newParticipation.getExercise().getId()).isEqualTo(newUserStory.getId());
        assertThat(newParticipation.getStudent()).map(User::getLogin).contains(studentLogin);
        assertThat(newParticipation.getRepositoryUri()).isEqualTo(milestoneParticipation.getRepositoryUri());
        assertThat(newParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(newParticipation.getBuildPlanId()).isNull();

        // Re-running the backfill is idempotent: the student already has a participation now, so nothing new is created.
        assertThat(participationService.provisionParticipationsForNewUserStoryExercise(newUserStory)).isEmpty();
        assertThat(programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndStudentLogin(newUserStory.getId(), studentLogin)).hasSize(1);

        // The caller (ExerciseVariantGroupResource) derives the initial score from the student's latest milestone result.
        Result backfilledResult = gradingService.fanOutResultToUserStoryExercise(sourceResult, newUserStory, newParticipation);
        assertThat(backfilledResult.getScore()).isEqualTo(100.0);
        assertThat(backfilledResult.getSubmission().getParticipation().getId()).isEqualTo(newParticipation.getId());
    }
}
