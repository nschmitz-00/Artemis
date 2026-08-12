package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryProgressStatus;
import de.tum.cit.aet.artemis.programming.dto.MilestoneProgressDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryProgressDTO;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.UserStoryExerciseService;

/**
 * Tests the story-by-story progress overview a student sees on a Milestone page: a Milestone carries no points of its own, so
 * the overview is summed over its user stories (see MilestoneProgressService).
 */
class MilestoneProgressIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "milestoneprogress";

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    /** When the results the tests create were completed - between {@link #PAST_TIMESTAMP} and now. */
    private static final ZonedDateTime RESULT_TIMESTAMP = ZonedDateTime.now().minusHours(12);

    /** After {@link #RESULT_TIMESTAMP}, so a result completed then still counts, but already in the past. */
    private static final ZonedDateTime PASSED_DUE_DATE = ZonedDateTime.now().minusHours(6);

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

    private UserStoryExercise firstUserStory;

    private UserStoryExercise secondUserStory;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        Course course = courseRepository.save(CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor"));

        MilestoneExercise newMilestoneExercise = new MilestoneExercise();
        newMilestoneExercise.setCourse(course);
        newMilestoneExercise.setTitle("Milestone");
        newMilestoneExercise.setShortName("MSPROGRESS");
        newMilestoneExercise.setMaxPoints(0.0);
        newMilestoneExercise.setBonusPoints(0.0);
        newMilestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        newMilestoneExercise.setPackageName("de.tum.in.www1");
        newMilestoneExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        newMilestoneExercise.setReleaseDate(PAST_TIMESTAMP);
        newMilestoneExercise.setDueDate(FUTURE_TIMESTAMP);
        newMilestoneExercise.setCategories(new HashSet<>(Set.of("{\"color\":\"#ad5658\",\"category\":\"Blub\"}")));
        newMilestoneExercise.generateAndSetProjectKey();
        newMilestoneExercise.setBuildConfig(buildConfigRepository.save(new ProgrammingExerciseBuildConfig()));
        milestoneExercise = milestoneExerciseRepository.save(newMilestoneExercise);

        firstUserStory = addUserStoryExercise("Story 1", "STORYONE", 10.0);
        secondUserStory = addUserStoryExercise("Story 2", "STORYTWO", 10.0);
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
     * Gives the student a participation in the user story with one rated result, exactly as the fan-out of a build produces it
     * (see LocalCIResultProcessingService).
     */
    private void addResultForUserStory(UserStoryExercise userStoryExercise, double score, AssessmentType assessmentType) {
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStoryExercise, TEST_PREFIX + "student1");

        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setType(SubmissionType.MANUAL);
        submission.setCommitHash("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        submission.setSubmissionDate(PAST_TIMESTAMP);
        submission = programmingSubmissionRepository.save(submission);

        Result result = new Result();
        result.setSubmission(submission);
        result.setAssessmentType(assessmentType);
        result.setExerciseId(userStoryExercise.getId());
        result.setScore(score);
        result.setRated(true);
        result.setCompletionDate(RESULT_TIMESTAMP);
        if (assessmentType != AssessmentType.AUTOMATIC) {
            result.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        }
        resultRepository.save(result);
    }

    private String progressUrl() {
        return "/api/programming/milestone-exercises/" + milestoneExercise.getId() + "/progress";
    }

    private MilestoneProgressDTO requestProgress() throws Exception {
        return request.get(progressUrl(), HttpStatus.OK, MilestoneProgressDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReportEveryUserStoryAsNotStartedBeforeTheStudentParticipates() throws Exception {
        MilestoneProgressDTO progress = requestProgress();

        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::title).containsExactly("Story 1", "Story 2");
        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::status).containsOnly(UserStoryProgressStatus.NOT_STARTED);
        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::achievedPoints).containsOnly(0.0);
        assertThat(progress.achievedPoints()).isZero();
        assertThat(progress.maxPoints()).isEqualTo(20.0);
        assertThat(progress.completionPercentage()).isZero();
        assertThat(progress.completedUserStories()).isZero();
        assertThat(progress.totalUserStories()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReportPointsAndStatusPerUserStoryAndAggregateOverThem() throws Exception {
        addResultForUserStory(firstUserStory, 100.0, AssessmentType.AUTOMATIC);
        addResultForUserStory(secondUserStory, 40.0, AssessmentType.AUTOMATIC);

        MilestoneProgressDTO progress = requestProgress();

        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::status).containsExactly(UserStoryProgressStatus.COMPLETED, UserStoryProgressStatus.IN_PROGRESS);
        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::achievedPoints).containsExactly(10.0, 4.0);
        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::score).containsExactly(100.0, 40.0);
        assertThat(progress.achievedPoints()).isEqualTo(14.0);
        assertThat(progress.maxPoints()).isEqualTo(20.0);
        assertThat(progress.completionPercentage()).isEqualTo(70.0);
        assertThat(progress.completedUserStories()).isEqualTo(1);
        assertThat(progress.manualAssessmentPending()).isFalse();
    }

    /**
     * A student must not learn their manual assessment before it is published, so it neither shows up as points nor moves the
     * user story out of "not started" while the assessment due date is still in the future.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldHideManualResultsUntilTheAssessmentDueDateHasPassed() throws Exception {
        milestoneExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        milestoneExercise.setAssessmentDueDate(FUTURE_TIMESTAMP);
        milestoneExercise = milestoneExerciseRepository.save(milestoneExercise);
        addResultForUserStory(firstUserStory, 100.0, AssessmentType.SEMI_AUTOMATIC);

        MilestoneProgressDTO progress = requestProgress();

        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::status).containsOnly(UserStoryProgressStatus.NOT_STARTED);
        assertThat(progress.achievedPoints()).isZero();
        assertThat(progress.manualAssessmentPending()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReportManualResultsOnceTheAssessmentDueDateHasPassed() throws Exception {
        milestoneExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        milestoneExercise.setDueDate(PASSED_DUE_DATE);
        milestoneExercise.setAssessmentDueDate(PASSED_DUE_DATE);
        milestoneExercise = milestoneExerciseRepository.save(milestoneExercise);
        addResultForUserStory(firstUserStory, 100.0, AssessmentType.SEMI_AUTOMATIC);

        MilestoneProgressDTO progress = requestProgress();

        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::status).containsExactly(UserStoryProgressStatus.COMPLETED, UserStoryProgressStatus.NOT_STARTED);
        assertThat(progress.achievedPoints()).isEqualTo(10.0);
        assertThat(progress.manualAssessmentPending()).isFalse();
    }

    /**
     * The overview always reports on the requesting user, so one student's results must never leak into another's.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldReportOnlyTheRequestingStudentsResults() throws Exception {
        addResultForUserStory(firstUserStory, 100.0, AssessmentType.AUTOMATIC);

        MilestoneProgressDTO progress = requestProgress();

        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::status).containsOnly(UserStoryProgressStatus.NOT_STARTED);
        assertThat(progress.achievedPoints()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotReportProgressOfAMilestoneThatHasNotBeenReleasedYet() throws Exception {
        milestoneExercise.setReleaseDate(FUTURE_TIMESTAMP);
        milestoneExercise = milestoneExerciseRepository.save(milestoneExercise);

        request.get(progressUrl(), HttpStatus.FORBIDDEN, MilestoneProgressDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "outsider", roles = "USER")
    void shouldNotReportProgressToAStudentOutsideTheCourse() throws Exception {
        userUtilService.addStudent("othercoursestudents", TEST_PREFIX + "outsider");

        request.get(progressUrl(), HttpStatus.FORBIDDEN, MilestoneProgressDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReportAMilestoneWithoutUserStoriesAsEmpty() throws Exception {
        MilestoneExercise emptyMilestone = new MilestoneExercise();
        emptyMilestone.setCourse(milestoneExercise.getCourseViaExerciseGroupOrCourseMember());
        emptyMilestone.setTitle("Empty Milestone");
        emptyMilestone.setShortName("MSEMPTY");
        emptyMilestone.setMaxPoints(0.0);
        emptyMilestone.setBonusPoints(0.0);
        emptyMilestone.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        emptyMilestone.setPackageName("de.tum.in.www1");
        emptyMilestone.setAssessmentType(AssessmentType.AUTOMATIC);
        emptyMilestone.setReleaseDate(PAST_TIMESTAMP);
        emptyMilestone.generateAndSetProjectKey();
        emptyMilestone.setBuildConfig(buildConfigRepository.save(new ProgrammingExerciseBuildConfig()));
        emptyMilestone = milestoneExerciseRepository.save(emptyMilestone);

        MilestoneProgressDTO progress = request.get("/api/programming/milestone-exercises/" + emptyMilestone.getId() + "/progress", HttpStatus.OK, MilestoneProgressDTO.class);

        // Serialized with NON_EMPTY, so an empty user story list does not appear in the response at all
        assertThat(progress.userStories()).isNullOrEmpty();
        assertThat(progress.maxPoints()).isZero();
        assertThat(progress.completionPercentage()).isZero();
        assertThat(progress.totalUserStories()).isZero();
    }

    /**
     * A build after the due date does not count towards the course score, so it must not count here either - otherwise the
     * overview would promise points the student never receives.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIgnoreResultsCompletedAfterTheDueDate() throws Exception {
        milestoneExercise.setDueDate(RESULT_TIMESTAMP.minusHours(1));
        milestoneExercise = milestoneExerciseRepository.save(milestoneExercise);
        addResultForUserStory(firstUserStory, 100.0, AssessmentType.AUTOMATIC);

        MilestoneProgressDTO progress = requestProgress();

        assertThat(progress.userStories()).extracting(UserStoryProgressDTO::status).containsOnly(UserStoryProgressStatus.NOT_STARTED);
        assertThat(progress.achievedPoints()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotReportProgressOfAnExerciseThatIsNotAMilestone() throws Exception {
        request.get("/api/programming/milestone-exercises/" + firstUserStory.getId() + "/progress", HttpStatus.NOT_FOUND, MilestoneProgressDTO.class);
    }
}
