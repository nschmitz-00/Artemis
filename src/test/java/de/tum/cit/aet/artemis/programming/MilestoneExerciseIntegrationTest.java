package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.MilestoneTestCaseCoverageDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryReferenceDTO;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.UserStoryExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.UserStoryExerciseService;

class MilestoneExerciseIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "milestoneexerciseintegration";

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Autowired
    private MilestoneExerciseRepository milestoneExerciseRepository;

    @Autowired
    private UserStoryExerciseRepository userStoryExerciseRepository;

    @Autowired
    private UserStoryExerciseService userStoryExerciseService;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    private MilestoneExercise milestoneExercise;

    private ProgrammingExerciseBuildConfig buildConfig;

    @BeforeEach
    void init() {
        // A tutor is needed because the course exercise list is tutor-level (the whole page is), unlike every other endpoint here
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = courseRepository.save(CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor"));

        MilestoneExercise newMilestoneExercise = new MilestoneExercise();
        newMilestoneExercise.setCourse(course);
        newMilestoneExercise.setTitle("Milestone");
        newMilestoneExercise.setShortName("MSONE");
        newMilestoneExercise.setMaxPoints(0.0);
        newMilestoneExercise.setBonusPoints(0.0);
        newMilestoneExercise.setDifficulty(DifficultyLevel.EASY);
        newMilestoneExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        newMilestoneExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        newMilestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        newMilestoneExercise.setPackageName("de.tum.in.www1");
        newMilestoneExercise.setCategories(new HashSet<>(Set.of("{\"color\":\"#ad5658\",\"category\":\"Blub\"}")));
        newMilestoneExercise.generateAndSetProjectKey();
        buildConfig = buildConfigRepository.save(new ProgrammingExerciseBuildConfig());
        newMilestoneExercise.setBuildConfig(buildConfig);
        milestoneExercise = milestoneExerciseRepository.save(newMilestoneExercise);

        addUserStoryExercise("Story 1", "STORYONE", 3.0, 1.0);
        addUserStoryExercise("Story 2", "STORYTWO", 4.0, 2.0);
        milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExercise.getId());
        // The real client always round-trips the build config, which the update path saves unconditionally
        milestoneExercise.setBuildConfig(buildConfig);
    }

    private void addUserStoryExercise(String title, String shortName, double maxPoints, double bonusPoints) {
        UserStoryExercise userStoryExercise = new UserStoryExercise();
        userStoryExercise.setTitle(title);
        userStoryExercise.setShortName(shortName);
        userStoryExercise.setMaxPoints(maxPoints);
        userStoryExercise.setBonusPoints(bonusPoints);
        userStoryExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        userStoryExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        userStoryExerciseService.createUserStoryExercise(milestoneExercise.getId(), userStoryExercise);
    }

    /** PUTs the exercise the way the client does: as an UpdateProgrammingExerciseDTO, never as the entity. */
    private MilestoneExercise update(MilestoneExercise exercise) throws Exception {
        return request.putWithResponseBody("/api/programming/milestone-exercises/" + exercise.getId(), UpdateProgrammingExerciseDTO.of(exercise), MilestoneExercise.class,
                HttpStatus.OK);
    }

    /**
     * The client never sends the UserStoryExercise children back when saving a Milestone, and the association is mapped
     * with orphanRemoval - so without the server restoring the persisted children, saving a Milestone silently deletes
     * every UserStoryExercise attached to it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldKeepUserStoryExercisesWhenUpdatingTheMilestone() throws Exception {
        List<Long> userStoryExerciseIds = milestoneExercise.getUserStoryExercises().stream().map(UserStoryExercise::getId).toList();
        assertThat(userStoryExerciseIds).hasSize(2);

        milestoneExercise.setTitle("Milestone renamed");

        MilestoneExercise updatedMilestoneExercise = update(milestoneExercise);

        assertThat(updatedMilestoneExercise.getTitle()).isEqualTo("Milestone renamed");
        assertThat(userStoryExerciseRepository.findAllById(userStoryExerciseIds)).hasSize(2);
    }

    /**
     * ProgrammingExercise and its superclass declare 16 associations with orphanRemoval, so persisting a client-supplied
     * exercise entity deletes every collection the payload omits. Student participations are the most damaging case: the
     * update used to wipe them, and only the ON DELETE RESTRICT foreign key from participation_vcs_access_token turned
     * that silent data loss into a 500. Updating a Milestone must leave a student's participation and token alone.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldKeepStudentParticipationsAndTheirVcsAccessTokensWhenUpdatingTheMilestone() throws Exception {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, student.getLogin());
        // addStudentParticipationForProgrammingExercise already provisions the VCS access token whose ON DELETE RESTRICT
        // foreign key surfaced this bug
        assertThat(participationVCSAccessTokenRepository.findByUserIdAndParticipationId(student.getId(), participation.getId())).isPresent();

        milestoneExercise.setTitle("Milestone renamed");
        update(milestoneExercise);

        assertThat(participationRepository.findById(participation.getId())).isPresent();
        assertThat(participationVCSAccessTokenRepository.findByUserIdAndParticipationId(student.getId(), participation.getId())).isPresent();
    }

    /**
     * maxPoints is derived from the children, so a Milestone update must never let the client overwrite it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldNotLetTheClientOverwriteTheDerivedMaxPoints() throws Exception {
        assertThat(milestoneExercise.getMaxPoints()).isEqualTo(7.0);
        milestoneExercise.setMaxPoints(999.0);
        milestoneExercise.setBonusPoints(999.0);

        MilestoneExercise updatedMilestoneExercise = update(milestoneExercise);

        assertThat(updatedMilestoneExercise.getMaxPoints()).isEqualTo(7.0);
        assertThat(updatedMilestoneExercise.getBonusPoints()).isEqualTo(3.0);
    }

    /**
     * Both point totals are sums over the children: the UserStories are the graded units, so a Milestone total that disagreed
     * with them would be unreachable in one direction or the other.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldDeriveBothPointTotalsFromTheUserStories() {
        assertThat(milestoneExercise.getMaxPoints()).isEqualTo(7.0);
        assertThat(milestoneExercise.getBonusPoints()).isEqualTo(3.0);

        UserStoryExercise firstUserStory = milestoneExercise.getUserStoryExercises().getFirst();
        UserStoryExercise update = new UserStoryExercise();
        update.setTitle(firstUserStory.getTitle());
        update.setShortName(firstUserStory.getShortName());
        update.setMaxPoints(10.0);
        update.setBonusPoints(5.0);
        userStoryExerciseService.updateUserStoryExercise(firstUserStory.getId(), update);

        MilestoneExercise reloaded = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExercise.getId());
        assertThat(reloaded.getMaxPoints()).isEqualTo(14.0);
        assertThat(reloaded.getBonusPoints()).isEqualTo(7.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldResetBothPointTotalsWhenTheLastUserStoryIsDeleted() {
        milestoneExercise.getUserStoryExercises().forEach(userStoryExercise -> userStoryExerciseService.deleteUserStoryExercise(userStoryExercise.getId()));

        MilestoneExercise reloaded = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExercise.getId());
        assertThat(reloaded.getMaxPoints()).isZero();
        assertThat(reloaded.getBonusPoints()).isZero();
    }

    /**
     * The children are read-only on MilestoneExercise, so a payload that carries a manipulated child must not reach the
     * database - otherwise the Milestone endpoints would become a second, unvalidated way of editing UserStoryExercises.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldIgnoreUserStoryExercisesSentByTheClient() throws Exception {
        UserStoryExercise manipulated = milestoneExercise.getUserStoryExercises().getFirst();
        Long manipulatedId = manipulated.getId();
        manipulated.setTitle("Injected title");

        update(milestoneExercise);

        assertThat(userStoryExerciseRepository.findByIdElseThrow(manipulatedId).getTitle()).isEqualTo("Story 1");
    }

    /**
     * Exercise#categories is a LAZY element collection, so both read endpoints have to fetch it explicitly - otherwise it is
     * silently absent from the response and the exercise renders as uncategorized in the course management exercise list.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnTheCategoriesForTheCourseExerciseList() throws Exception {
        List<MilestoneExercise> milestoneExercises = request.getList(
                "/api/programming/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/milestone-exercises", HttpStatus.OK, MilestoneExercise.class);

        assertThat(milestoneExercises).hasSize(1);
        assertThat(milestoneExercises.getFirst().getCategories()).containsExactly("{\"color\":\"#ad5658\",\"category\":\"Blub\"}");
    }

    /**
     * The problem statement doubles as the Milestone's description: it is what students are shown when they open the
     * Milestone, so it has to survive the update round trip and be returned by the endpoint the edit page loads.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldPersistTheProblemStatementOfAMilestone() throws Exception {
        milestoneExercise.setProblemStatement("# Milestone description\n\nBuild the thing.");

        MilestoneExercise updatedMilestoneExercise = update(milestoneExercise);

        assertThat(updatedMilestoneExercise.getProblemStatement()).isEqualTo("# Milestone description\n\nBuild the thing.");

        MilestoneExercise fetchedMilestoneExercise = request.get("/api/programming/milestone-exercises/" + milestoneExercise.getId(), HttpStatus.OK, MilestoneExercise.class);
        assertThat(fetchedMilestoneExercise.getProblemStatement()).isEqualTo("# Milestone description\n\nBuild the thing.");
    }

    /**
     * The course exercise list shows how many UserStoryExercises belong to each Milestone. The children are deliberately not
     * fetched there, so the count has to come from the separate count query instead of the (absent) collection.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnTheUserStoryExerciseCountForTheCourseExerciseList() throws Exception {
        List<MilestoneExercise> milestoneExercises = request.getList(
                "/api/programming/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/milestone-exercises", HttpStatus.OK, MilestoneExercise.class);

        assertThat(milestoneExercises).hasSize(1);
        assertThat(milestoneExercises.getFirst().getNumberOfUserStoryExercises()).isEqualTo(2);
        // The children themselves must not be shipped just to show their number
        assertThat(milestoneExercises.getFirst().getUserStoryExercises()).isEmpty();
    }

    /**
     * The course exercise list offers "Edit in editor", which routes to the code editor for the Milestone's template repository
     * and addresses it by the template participation's id. That association is LAZY, so without fetching it explicitly Jackson
     * omits it from the response and the action never renders.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnTheTemplateParticipationForTheCourseExerciseList() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(milestoneExercise);

        List<MilestoneExercise> milestoneExercises = request.getList(
                "/api/programming/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/milestone-exercises", HttpStatus.OK, MilestoneExercise.class);

        assertThat(milestoneExercises).hasSize(1);
        assertThat(milestoneExercises.getFirst().getTemplateParticipation()).isNotNull();
        assertThat(milestoneExercises.getFirst().getTemplateParticipation().getId()).isNotNull();
    }

    /**
     * The course exercise list is one page with a section per exercise type, and tutors can open it. Requiring EDITOR here made
     * that whole page fail with a 403 for them while every other exercise type still listed, so the list is tutor-level; the
     * read-only nature is enforced by the row actions, which are gated on the per-exercise editor and instructor rights.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldLetTutorsReadTheCourseExerciseList() throws Exception {
        List<MilestoneExercise> milestoneExercises = request.getList(
                "/api/programming/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/milestone-exercises", HttpStatus.OK, MilestoneExercise.class);

        assertThat(milestoneExercises).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotLetStudentsReadTheCourseExerciseList() throws Exception {
        request.getList("/api/programming/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/milestone-exercises", HttpStatus.FORBIDDEN,
                MilestoneExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReportZeroUserStoryExercisesForAMilestoneWithoutAny() throws Exception {
        userStoryExerciseRepository.deleteAll(milestoneExercise.getUserStoryExercises());

        List<MilestoneExercise> milestoneExercises = request.getList(
                "/api/programming/courses/" + milestoneExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/milestone-exercises", HttpStatus.OK, MilestoneExercise.class);

        assertThat(milestoneExercises.getFirst().getNumberOfUserStoryExercises()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnTheCategoriesForASingleMilestoneExercise() throws Exception {
        MilestoneExercise fetchedMilestoneExercise = request.get("/api/programming/milestone-exercises/" + milestoneExercise.getId(), HttpStatus.OK, MilestoneExercise.class);

        assertThat(fetchedMilestoneExercise.getCategories()).containsExactly("{\"color\":\"#ad5658\",\"category\":\"Blub\"}");
    }

    // --- Test case coverage: the UserStoryExercises should claim each of the Milestone's active test cases exactly once ---

    /** Points a UserStoryExercise's problem statement at the given test cases, which creates the tagged tasks linking them. */
    private void referenceTestCases(String userStoryTitle, String... testNames) {
        UserStoryExercise userStoryExercise = milestoneExercise.getUserStoryExercises().stream().filter(exercise -> userStoryTitle.equals(exercise.getTitle())).findFirst()
                .orElseThrow();
        UserStoryExercise update = new UserStoryExercise();
        update.setTitle(userStoryExercise.getTitle());
        update.setShortName(userStoryExercise.getShortName());
        update.setMaxPoints(userStoryExercise.getMaxPoints());
        update.setProblemStatement("[task][Do the thing](%s)".formatted(String.join(",", testNames)));
        userStoryExerciseService.updateUserStoryExercise(userStoryExercise.getId(), update);
    }

    private MilestoneTestCaseCoverageDTO getTestCaseCoverage() throws Exception {
        return request.get("/api/programming/milestone-exercises/" + milestoneExercise.getId() + "/test-case-coverage", HttpStatus.OK, MilestoneTestCaseCoverageDTO.class);
    }

    /**
     * Students are served the problem statement exactly as stored, and the instruction renderer resolves each task's test status
     * from the {@code <testid>} references in it - test cases themselves are never exposed to students, so a statement that still
     * carried plain test names would leave every task in the overview stuck at "not executed". The test cases live on the parent
     * Milestone, so the conversion has to look them up there rather than on the (test-case-less) UserStory row.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldStoreTheUserStoryProblemStatementWithTestIds() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testOne");
        referenceTestCases("Story 1", "testOne");

        UserStoryExercise stored = userStoryExerciseRepository.findByIdElseThrow(milestoneExercise.getUserStoryExercises().getFirst().getId());
        assertThat(stored.getProblemStatement()).isEqualTo("[task][Do the thing](<testid>%d</testid>)".formatted(testCase.getId()));
    }

    /**
     * The Milestone edit page feeds the user stories' problem statements to the instruction editor's status bar, which decides
     * from them which of the Milestone's test cases are still unused. That comparison is by test name, so the children have to
     * come back with names rather than the ids they are stored with - otherwise every test case is reported as unused.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnTheUserStoryProblemStatementsWithTestNamesWithTheMilestone() throws Exception {
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testOne");
        referenceTestCases("Story 1", "testOne");

        // Asserted on the raw JSON: MilestoneExercise#userStoryExercises is mapped READ_ONLY, so deserializing the response back
        // into the entity would silently drop exactly the children this is about.
        String response = request.get("/api/programming/milestone-exercises/" + milestoneExercise.getId(), HttpStatus.OK, String.class);

        assertThat(response).contains("[task][Do the thing](testOne)").doesNotContain("<testid>");
    }

    /** The editor authors tasks with test names, so the stored ids have to be converted back when the edit form loads. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnTheUserStoryProblemStatementWithTestNamesToTheEditor() throws Exception {
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testOne");
        referenceTestCases("Story 1", "testOne");
        long userStoryExerciseId = milestoneExercise.getUserStoryExercises().getFirst().getId();

        UserStoryExercise fetched = request.get("/api/programming/user-story-exercises/" + userStoryExerciseId, HttpStatus.OK, UserStoryExercise.class);

        assertThat(fetched.getProblemStatement()).isEqualTo("[task][Do the thing](testOne)");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReportNoTestCaseIssuesWhenEveryTestCaseIsClaimedExactlyOnce() throws Exception {
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testOne");
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testTwo");
        referenceTestCases("Story 1", "testOne");
        referenceTestCases("Story 2", "testTwo");

        MilestoneTestCaseCoverageDTO coverage = getTestCaseCoverage();

        assertThat(coverage.orphanTestCases()).isEmpty();
        assertThat(coverage.duplicateTestCases()).isEmpty();
    }

    /**
     * A test case no UserStory references is never graded (grading scopes each UserStory to its own referenced test cases), so
     * its points are unreachable for students - the editor has to be told.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReportATestCaseNoUserStoryReferencesAsAnOrphan() throws Exception {
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testOne");
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testUnclaimed");
        referenceTestCases("Story 1", "testOne");

        MilestoneTestCaseCoverageDTO coverage = getTestCaseCoverage();

        assertThat(coverage.orphanTestCases()).singleElement().satisfies(orphan -> assertThat(orphan.testName()).isEqualTo("testUnclaimed"),
                orphan -> assertThat(orphan.referencingUserStories()).isEmpty());
        assertThat(coverage.duplicateTestCases()).isEmpty();
    }

    /** A test case referenced by two UserStories is graded once per UserStory, so its points are paid out twice. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReportATestCaseReferencedByTwoUserStoriesAsADuplicate() throws Exception {
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testShared");
        referenceTestCases("Story 1", "testShared");
        referenceTestCases("Story 2", "testShared");

        MilestoneTestCaseCoverageDTO coverage = getTestCaseCoverage();

        assertThat(coverage.orphanTestCases()).isEmpty();
        assertThat(coverage.duplicateTestCases()).singleElement().satisfies(duplicate -> assertThat(duplicate.testName()).isEqualTo("testShared"),
                duplicate -> assertThat(duplicate.referencingUserStories()).extracting(UserStoryReferenceDTO::title).containsExactly("Story 1", "Story 2"));
    }

    /** Inactive test cases are never graded, so an unclaimed one is not a problem the editor needs to act on. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldIgnoreInactiveTestCases() throws Exception {
        ProgrammingExerciseTestCase inactiveTestCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testInactive");
        inactiveTestCase.setActive(false);
        testCaseRepository.save(inactiveTestCase);

        MilestoneTestCaseCoverageDTO coverage = getTestCaseCoverage();

        assertThat(coverage.orphanTestCases()).isEmpty();
        assertThat(coverage.duplicateTestCases()).isEmpty();
    }

    /**
     * Grading collects a UserStory's test case ids into a set, so referencing the same test case from two tasks of the same
     * UserStory grades it once - that is not a duplicate claim.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldNotReportATestCaseReferencedTwiceByTheSameUserStoryAsADuplicate() throws Exception {
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(milestoneExercise, "testOne");
        UserStoryExercise userStoryExercise = milestoneExercise.getUserStoryExercises().getFirst();
        UserStoryExercise update = new UserStoryExercise();
        update.setTitle(userStoryExercise.getTitle());
        update.setShortName(userStoryExercise.getShortName());
        update.setMaxPoints(userStoryExercise.getMaxPoints());
        update.setProblemStatement("[task][First](testOne)\n[task][Second](testOne)");
        userStoryExerciseService.updateUserStoryExercise(userStoryExercise.getId(), update);

        MilestoneTestCaseCoverageDTO coverage = getTestCaseCoverage();

        assertThat(coverage.orphanTestCases()).isEmpty();
        assertThat(coverage.duplicateTestCases()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotLetStudentsReadTheTestCaseCoverage() throws Exception {
        request.get("/api/programming/milestone-exercises/" + milestoneExercise.getId() + "/test-case-coverage", HttpStatus.FORBIDDEN, MilestoneTestCaseCoverageDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldPersistUpdatedCategories() throws Exception {
        milestoneExercise.setCategories(new HashSet<>(Set.of("{\"color\":\"#111111\",\"category\":\"Renamed\"}")));

        MilestoneExercise updatedMilestoneExercise = update(milestoneExercise);

        assertThat(updatedMilestoneExercise.getCategories()).containsExactly("{\"color\":\"#111111\",\"category\":\"Renamed\"}");
        assertThat(milestoneExerciseRepository.findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(milestoneExercise.getId()).orElseThrow()
                .getCategories()).containsExactly("{\"color\":\"#111111\",\"category\":\"Renamed\"}");
    }
}
