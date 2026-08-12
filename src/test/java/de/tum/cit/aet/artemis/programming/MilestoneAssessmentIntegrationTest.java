package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UserStoryAssessmentDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryManualResultDTO;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.UserStoryExerciseService;

/**
 * Tests assessing a MilestoneExercise submission across its user stories: the tutor assesses the one submission and each
 * piece of feedback ends up on the result of the user story it was written for (see MilestoneAssessmentResource).
 */
class MilestoneAssessmentIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "milestoneassessment";

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Autowired
    private MilestoneExerciseRepository milestoneExerciseRepository;

    @Autowired
    private UserStoryExerciseService userStoryExerciseService;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    private MilestoneExercise milestoneExercise;

    private ProgrammingExerciseStudentParticipation milestoneParticipation;

    private final List<UserStoryExercise> userStoryExercises = new ArrayList<>();

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = courseRepository.save(CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor"));

        MilestoneExercise newMilestoneExercise = new MilestoneExercise();
        newMilestoneExercise.setCourse(course);
        newMilestoneExercise.setTitle("Milestone");
        newMilestoneExercise.setShortName("MSASSESS");
        newMilestoneExercise.setMaxPoints(0.0);
        newMilestoneExercise.setBonusPoints(0.0);
        newMilestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        newMilestoneExercise.setPackageName("de.tum.in.www1");
        // Manual assessment is only allowed once the due date has passed, and it is configured on the Milestone for all of its
        // user stories at once (see UserStoryExercise)
        newMilestoneExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        newMilestoneExercise.setDueDate(PAST_TIMESTAMP);
        newMilestoneExercise.setCategories(new HashSet<>(Set.of("{\"color\":\"#ad5658\",\"category\":\"Blub\"}")));
        newMilestoneExercise.generateAndSetProjectKey();
        newMilestoneExercise.setBuildConfig(buildConfigRepository.save(new ProgrammingExerciseBuildConfig()));
        milestoneExercise = milestoneExerciseRepository.save(newMilestoneExercise);

        userStoryExercises.add(addUserStoryExercise("Story 1", "STORYONE", 10.0));
        userStoryExercises.add(addUserStoryExercise("Story 2", "STORYTWO", 4.0));

        milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, TEST_PREFIX + "student1");
        userStoryExercises.forEach(this::addUserStoryParticipationWithLockedAssessment);
    }

    private UserStoryExercise addUserStoryExercise(String title, String shortName, double maxPoints) {
        UserStoryExercise userStoryExercise = new UserStoryExercise();
        userStoryExercise.setTitle(title);
        userStoryExercise.setShortName(shortName);
        userStoryExercise.setMaxPoints(maxPoints);
        userStoryExercise.setBonusPoints(0.0);
        return userStoryExerciseService.createUserStoryExercise(milestoneExercise.getId(), userStoryExercise);
    }

    /**
     * Sets a user story up the way it looks while the student's Milestone submission is being assessed: a sibling participation
     * on the same repository as the Milestone participation, with the manual result the lock created and the automatic feedback
     * of the build copied into it.
     */
    private void addUserStoryParticipationWithLockedAssessment(UserStoryExercise userStoryExercise) {
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStoryExercise, TEST_PREFIX + "student1");
        // The siblings share the Milestone's repository - that is what identifies them as belonging to the same submission
        participation.setRepositoryUri(milestoneParticipation.getRepositoryUri());
        participation = programmingExerciseStudentParticipationRepository.save(participation);

        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setType(SubmissionType.MANUAL);
        submission.setCommitHash("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        submission.setSubmissionDate(PAST_TIMESTAMP);
        submission = programmingSubmissionRepository.save(submission);

        Result manualResult = new Result();
        manualResult.setSubmission(submission);
        manualResult.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        manualResult.setExerciseId(userStoryExercise.getId());
        manualResult.setRated(true);
        // The automatic feedback of the build, which the lock copied into the manual result
        Feedback automaticFeedback = new Feedback().credits(2.0).type(FeedbackType.AUTOMATIC).detailText("testMethod passed");
        manualResult.setFeedbacks(new ArrayList<>(List.of(automaticFeedback)));
        automaticFeedback.setResult(manualResult);
        resultRepository.save(manualResult);
    }

    private String assessmentUrl() {
        return "/api/programming/milestone-exercises/" + milestoneExercise.getId() + "/participations/" + milestoneParticipation.getId();
    }

    private UserStoryManualResultDTO manualResult(UserStoryExercise userStoryExercise, double score, Feedback... feedbacks) {
        Result result = new Result();
        result.setRated(true);
        result.setScore(score);
        result.setFeedbacks(new ArrayList<>(List.of(feedbacks)));
        return new UserStoryManualResultDTO(userStoryExercise.getId(), result);
    }

    private Feedback manualFeedback(double credits, String detailText) {
        return new Feedback().credits(credits).type(FeedbackType.MANUAL_UNREFERENCED).detailText(detailText);
    }

    private Result latestResultOf(UserStoryExercise userStoryExercise) {
        StudentParticipation participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(userStoryExercise.getId(), TEST_PREFIX + "student1")
                .orElseThrow();
        return resultRepository.findLatestResultWithFeedbacksForParticipation(participation.getId(), false).orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnOneEntryPerUserStoryOfTheSubmission() throws Exception {
        List<UserStoryAssessmentDTO> userStoryAssessments = request.getList(assessmentUrl() + "/user-story-assessments", HttpStatus.OK, UserStoryAssessmentDTO.class);

        assertThat(userStoryAssessments).hasSize(2);
        assertThat(userStoryAssessments).extracting(UserStoryAssessmentDTO::title).containsExactly("Story 1", "Story 2");
        assertThat(userStoryAssessments).extracting(UserStoryAssessmentDTO::maxPoints).containsExactly(10.0, 4.0);
        // The tutor needs the existing result of every user story to continue an assessment where they left off
        assertThat(userStoryAssessments).allSatisfy(userStoryAssessment -> assertThat(userStoryAssessment.latestResult()).isNotNull());
    }

    /**
     * The user stories are the graded units, so the feedback a tutor writes for one of them has to end up on that user story's
     * result - not on the Milestone's, which contributes no points of its own.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldStoreTheFeedbackOfEachUserStoryOnItsOwnResult() throws Exception {
        List<UserStoryManualResultDTO> assessment = List.of(manualResult(userStoryExercises.getFirst(), 50.0, manualFeedback(5.0, "Nicely structured")),
                manualResult(userStoryExercises.get(1), 25.0, manualFeedback(1.0, "Works, but slow")));

        request.putWithResponseBodyList(assessmentUrl() + "/manual-results", assessment, Result.class, HttpStatus.OK);

        Result firstUserStoryResult = latestResultOf(userStoryExercises.getFirst());
        assertThat(firstUserStoryResult.getScore()).isEqualTo(50.0);
        assertThat(firstUserStoryResult.getFeedbacks()).extracting(Feedback::getDetailText).containsExactly("Nicely structured");

        Result secondUserStoryResult = latestResultOf(userStoryExercises.get(1));
        assertThat(secondUserStoryResult.getScore()).isEqualTo(25.0);
        assertThat(secondUserStoryResult.getFeedbacks()).extracting(Feedback::getDetailText).containsExactly("Works, but slow");
    }

    /**
     * Feedback is written against the submission as a whole, so a deduction meant for all of it - or one entered by mistake -
     * must not push a single user story below zero or above what it can pay out.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldClampTheScoreToWhatTheUserStoryCanPayOut() throws Exception {
        List<UserStoryManualResultDTO> assessment = List.of(manualResult(userStoryExercises.getFirst(), 500.0, manualFeedback(50.0, "Far too generous")),
                manualResult(userStoryExercises.get(1), -300.0, manualFeedback(-12.0, "Far too harsh")));

        request.putWithResponseBodyList(assessmentUrl() + "/manual-results", assessment, Result.class, HttpStatus.OK);

        // Neither user story has bonus points, so 100 % is all either of them can reach
        assertThat(latestResultOf(userStoryExercises.getFirst()).getScore()).isEqualTo(100.0);
        assertThat(latestResultOf(userStoryExercises.get(1)).getScore()).isZero();
    }

    /**
     * A tutor assesses the Milestone submission once for all of its user stories, so the user stories must not show up as
     * assessable exercises of their own - the dashboard would otherwise offer the same submission once per user story.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldListTheMilestoneButNotItsUserStoriesOnTheAssessmentDashboard() throws Exception {
        Course course = request.get("/api/core/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
                Course.class);

        assertThat(course.getExercises()).extracting(Exercise::getId).contains(milestoneExercise.getId());
        assertThat(course.getExercises()).extracting(Exercise::getId).doesNotContainAnyElementsOf(userStoryExercises.stream().map(UserStoryExercise::getId).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldRejectAnAssessmentForAUserStoryOfAnotherMilestone() throws Exception {
        List<UserStoryManualResultDTO> assessment = List
                .of(new UserStoryManualResultDTO(milestoneExercise.getId() + 9999, manualResult(userStoryExercises.getFirst(), 50.0).result()));

        request.put(assessmentUrl() + "/manual-results", assessment, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldRejectAnAssessmentWithoutAScore() throws Exception {
        Result resultWithoutScore = new Result();
        resultWithoutScore.setRated(true);
        resultWithoutScore.setFeedbacks(new ArrayList<>(List.of(manualFeedback(1.0, "No score set"))));
        List<UserStoryManualResultDTO> assessment = List.of(new UserStoryManualResultDTO(userStoryExercises.getFirst().getId(), resultWithoutScore));

        request.put(assessmentUrl() + "/manual-results", assessment, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotLetStudentsAssessAMilestone() throws Exception {
        List<UserStoryManualResultDTO> assessment = List.of(manualResult(userStoryExercises.getFirst(), 50.0, manualFeedback(5.0, "Nice")));

        request.put(assessmentUrl() + "/manual-results", assessment, HttpStatus.FORBIDDEN);
        request.getList(assessmentUrl() + "/user-story-assessments", HttpStatus.FORBIDDEN, UserStoryAssessmentDTO.class);
    }

    /**
     * Whether manual results are allowed at all is a Milestone setting inherited by every user story, so switching the
     * Milestone to purely automatic assessment has to close this endpoint for all of them at once.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldRejectTheAssessmentWhenTheMilestoneIsAssessedAutomatically() throws Exception {
        milestoneExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        milestoneExercise.setAllowComplaintsForAutomaticAssessments(false);
        milestoneExerciseRepository.save(milestoneExercise);

        List<UserStoryManualResultDTO> assessment = List.of(manualResult(userStoryExercises.getFirst(), 50.0, manualFeedback(5.0, "Nice")));

        request.put(assessmentUrl() + "/manual-results", assessment, HttpStatus.FORBIDDEN);
    }
}
