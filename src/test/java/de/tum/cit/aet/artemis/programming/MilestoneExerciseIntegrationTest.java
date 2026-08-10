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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
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
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 1, 1);
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

        addUserStoryExercise("Story 1", "STORYONE", 3.0);
        addUserStoryExercise("Story 2", "STORYTWO", 4.0);
        milestoneExercise = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExercise.getId());
        // The real client always round-trips the build config, which the update path saves unconditionally
        milestoneExercise.setBuildConfig(buildConfig);
    }

    private void addUserStoryExercise(String title, String shortName, double maxPoints) {
        UserStoryExercise userStoryExercise = new UserStoryExercise();
        userStoryExercise.setTitle(title);
        userStoryExercise.setShortName(shortName);
        userStoryExercise.setMaxPoints(maxPoints);
        userStoryExercise.setBonusPoints(0.0);
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

        MilestoneExercise updatedMilestoneExercise = update(milestoneExercise);

        assertThat(updatedMilestoneExercise.getMaxPoints()).isEqualTo(7.0);
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
