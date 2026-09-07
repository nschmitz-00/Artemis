package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseExercisesForOverviewDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.dto.CreateUserStoryExerciseDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneStatusDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateMilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UserStoryExerciseDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * Covers {@link MilestoneExerciseGroup} and its dedicated repository/routes, alongside ordinary
 * {@link ExerciseVariantGroup}s in the same course.
 * <p>
 * The two types share one table under a discriminator, so the point of these tests is that neither type's queries ever
 * see the other's rows, and that a milestone group's anchor exercise is actually fetched - its timeline is read through
 * that anchor, so an unfetched one silently reads as "no dates".
 * <p>
 * The milestone group is built directly through the repository rather than through the create endpoint: provisioning a
 * real {@link MilestoneExercise} would drag in the VCS/CI test infrastructure, and nothing here depends on it.
 */
class MilestoneExerciseGroupIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "milestonegrpinteg";

    @Autowired
    private MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private Course course;

    private MilestoneExercise milestoneExercise;

    private MilestoneExerciseGroup milestoneGroup;

    private ExerciseVariantGroup variantGroup;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        milestoneExercise = new MilestoneExercise();
        milestoneExercise.setTitle("Milestone");
        milestoneExercise.setShortName("milestone" + TEST_PREFIX);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setCourse(course);
        milestoneExercise.setMaxPoints(0.0);
        // Truncated to milliseconds: PostgreSQL stores no finer precision, so an untruncated value would not survive the
        // round-trip the assertions compare against.
        milestoneExercise.setReleaseDate(ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS));
        milestoneExercise.setDueDate(ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS));
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        milestoneGroup = new MilestoneExerciseGroup();
        milestoneGroup.setTitle("Sprint 1");
        milestoneGroup.setMilestoneExercise(milestoneExercise);
        milestoneGroup = milestoneExerciseGroupRepository.save(milestoneGroup);

        variantGroup = new ExerciseVariantGroup();
        variantGroup.setTitle("Loop variants");
        variantGroup.setMaxPoints(100.0);
        variantGroup = exerciseVariantGroupRepository.save(variantGroup);

        course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        course.addExerciseVariantGroup(milestoneGroup);
        course.addExerciseVariantGroup(variantGroup);
        courseRepository.save(course);
    }

    private String variantGroupsUrl() {
        return "/api/exercise/courses/" + course.getId() + "/exercise-variant-groups";
    }

    private String milestoneGroupsUrl() {
        return "/api/exercise/courses/" + course.getId() + "/milestone-exercise-groups";
    }

    @Test
    void milestoneAndVariantGroupsAreStoredUnderTheirOwnDiscriminator() {
        assertThat(milestoneExerciseGroupRepository.findAllByCourseId(course.getId())).extracting(ExerciseVariantGroup::getId).containsExactly(milestoneGroup.getId());
        // Each repository sees only its own type: the two share a table, and the variant query cannot fetch a milestone
        // group's anchor without a TREAT that would drop every other group from the result.
        assertThat(exerciseVariantGroupRepository.findAllByCourseId(course.getId())).extracting(ExerciseVariantGroup::getId).containsExactly(variantGroup.getId());
        assertThat(exerciseVariantGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), course.getId())).isEmpty();
    }

    @Test
    void milestoneGroupLookupsRejectAVariantGroup() {
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), course.getId())).isPresent();
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(variantGroup.getId(), course.getId())).isEmpty();
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseIdWithoutExercises(variantGroup.getId(), course.getId())).isEmpty();
    }

    @Test
    void milestoneGroupLookupsAreScopedToTheCourse() {
        Course otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX + "other");
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), otherCourse.getId())).isEmpty();
        assertThat(milestoneExerciseGroupRepository.findAllByCourseId(otherCourse.getId())).isEmpty();
    }

    @Test
    void theAnchorExerciseIsFetchedSoTheGroupCanReportItsTimeline() {
        MilestoneExerciseGroup loaded = milestoneExerciseGroupRepository.findByIdAndCourseIdElseThrow(milestoneGroup.getId(), course.getId());

        assertThat(loaded.getMilestoneExercise()).isNotNull();
        assertThat(loaded.getMilestoneExercise().getId()).isEqualTo(milestoneExercise.getId());
        // Read through the group, which delegates to the anchor - null here would mean the anchor was left a proxy.
        assertThat(loaded.getDueDate()).isNotNull();
        assertThat(loaded.getDueDate().toInstant()).isEqualTo(milestoneExercise.getDueDate().toInstant());
    }

    @Test
    void theAnchorExerciseIdIsResolvableWithoutLoadingIt() {
        assertThat(milestoneExerciseGroupRepository.findMilestoneExerciseIdByGroupId(milestoneGroup.getId())).contains(milestoneExercise.getId());
        assertThat(milestoneExerciseGroupRepository.countExercisesByGroupId(milestoneGroup.getId())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void milestoneGroupEndpointReturnsOnlyMilestoneGroupsWithTheirAnchor() throws Exception {
        List<MilestoneExerciseGroupDTO> groups = request.getList(milestoneGroupsUrl(), HttpStatus.OK, MilestoneExerciseGroupDTO.class);

        assertThat(groups).hasSize(1);
        MilestoneExerciseGroupDTO group = groups.getFirst();
        assertThat(group.id()).isEqualTo(milestoneGroup.getId());
        assertThat(group.title()).isEqualTo("Sprint 1");
        assertThat(group.milestoneExerciseId()).isEqualTo(milestoneExercise.getId());
        assertThat(group.dueDate()).isNotNull();
        assertThat(group.dueDate().toInstant()).isEqualTo(milestoneExercise.getDueDate().toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void milestoneGroupEndpointExposesTheAnchorsLanguageAndProjectType() throws Exception {
        // The user story create form hides both fields, so the group DTO is the only place it can learn them - and it
        // needs them to seed a new story's problem statement from the milestone's own readme template: the Gradle and
        // Maven templates spell the example test names differently (testBubbleSort() vs testBubbleSort).
        milestoneExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        programmingExerciseRepository.save(milestoneExercise);

        List<MilestoneExerciseGroupDTO> groups = request.getList(milestoneGroupsUrl(), HttpStatus.OK, MilestoneExerciseGroupDTO.class);

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.programmingLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
            assertThat(group.projectType()).isEqualTo(ProjectType.PLAIN_MAVEN);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void singleGroupEndpointRejectsAVariantGroup() throws Exception {
        request.get(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), HttpStatus.OK, MilestoneExerciseGroupDTO.class);
        request.get(milestoneGroupsUrl() + "/" + variantGroup.getId(), HttpStatus.NOT_FOUND, MilestoneExerciseGroupDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updatingAMilestoneGroupWritesTheTimelineToItsAnchorExercise() throws Exception {
        ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(21).truncatedTo(ChronoUnit.MILLIS);
        UpdateMilestoneExerciseGroupDTO updateDTO = new UpdateMilestoneExerciseGroupDTO(milestoneGroup.getId(), "Sprint 1 renamed", milestoneExercise.getReleaseDate(), null,
                newDueDate, null, null);

        MilestoneExerciseGroupDTO updated = request.putWithResponseBody(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), updateDTO, MilestoneExerciseGroupDTO.class,
                HttpStatus.OK);

        assertThat(updated.title()).isEqualTo("Sprint 1 renamed");
        assertThat(updated.dueDate().toInstant()).isEqualTo(newDueDate.toInstant());
        // The milestone group stores no dates of its own - they live on the anchor exercise.
        assertThat(programmingExerciseRepository.findByIdElseThrow(milestoneExercise.getId()).getDueDate().toInstant()).isEqualTo(newDueDate.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updatingWithAMismatchedIdIsRejected() throws Exception {
        UpdateMilestoneExerciseGroupDTO mismatched = new UpdateMilestoneExerciseGroupDTO(milestoneGroup.getId() + 1, "Renamed", null, null, null, null, null);

        request.putWithResponseBody(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), mismatched, MilestoneExerciseGroupDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void variantGroupEndpointsStillServeVariantGroupsUnchanged() throws Exception {
        ExerciseVariantGroupDTO fetched = request.get(variantGroupsUrl() + "/" + variantGroup.getId(), HttpStatus.OK, ExerciseVariantGroupDTO.class);

        assertThat(fetched.id()).isEqualTo(variantGroup.getId());
        assertThat(fetched.maxPoints()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentMayNotReadMilestoneGroups() throws Exception {
        request.getList(milestoneGroupsUrl(), HttpStatus.FORBIDDEN, MilestoneExerciseGroupDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void anEditorMayNotDeleteAMilestoneGroup() throws Exception {
        request.delete(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), HttpStatus.FORBIDDEN);

        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), course.getId())).isPresent();
    }

    /**
     * The anchor exercise is never part of any exercise listing, so this endpoint is the only way the student group view
     * learns its id - and therefore what the group's "Start exercise" action addresses.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void milestoneStatusNamesTheAnchorExerciseForAStudentWhoHasNotStartedIt() throws Exception {
        MilestoneStatusDTO status = request.get(milestoneGroupsUrl() + "/" + milestoneGroup.getId() + "/milestone-status", HttpStatus.OK, MilestoneStatusDTO.class);

        assertThat(status.milestoneExerciseId()).isEqualTo(milestoneExercise.getId());
        assertThat(status.started()).isFalse();
        assertThat(status.participationId()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void milestoneStatusRejectsAVariantGroup() throws Exception {
        request.get(milestoneGroupsUrl() + "/" + variantGroup.getId() + "/milestone-status", HttpStatus.NOT_FOUND, MilestoneStatusDTO.class);
    }

    private String userStoryExercisesUrl(long groupId) {
        return milestoneGroupsUrl() + "/" + groupId + "/user-story-exercises";
    }

    private CreateUserStoryExerciseDTO userStoryPayload(String shortNameSuffix) {
        return new CreateUserStoryExerciseDTO("User story", "us" + shortNameSuffix + TEST_PREFIX, null, "Implement the thing", null, null, null, 5.0, null, null, null, null, null,
                null, null, null, null);
    }

    // The happy path is not covered here: creating a user story runs the whole programming-exercise creation pipeline,
    // which needs the ProgrammingLanguageFeature and version control beans this independent context does not provide (the
    // same reason the milestone group above is built through the repository). What the request contract itself
    // guarantees - that a payload cannot carry the settings the group owns - is covered by CreateUserStoryExerciseDTOTest.

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void creatingAUserStoryExerciseRejectsABlankTitle() throws Exception {
        CreateUserStoryExerciseDTO blankTitle = new CreateUserStoryExerciseDTO(" ", "us3" + TEST_PREFIX, null, null, null, null, null, 5.0, null, null, null, null, null, null,
                null, null, null);

        request.postWithResponseBody(userStoryExercisesUrl(milestoneGroup.getId()), blankTitle, UserStoryExerciseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void creatingAUserStoryExerciseRejectsAVariantGroup() throws Exception {
        request.postWithResponseBody(userStoryExercisesUrl(variantGroup.getId()), userStoryPayload("4"), UserStoryExerciseDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void aTutorMayNotCreateAUserStoryExercise() throws Exception {
        request.postWithResponseBody(userStoryExercisesUrl(milestoneGroup.getId()), userStoryPayload("5"), UserStoryExerciseDTO.class, HttpStatus.FORBIDDEN);
    }

    /**
     * The course overview is what the student group view builds its groups from, so a milestone group's members have to
     * carry the anchor exercise id along with the discriminator - otherwise the view knows the group is a milestone but
     * not which exercise its actions address.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theCourseOverviewNamesTheAnchorExerciseOnAMilestoneGroupMember() throws Exception {
        UserStoryExercise member = new UserStoryExercise();
        member.setTitle("User story");
        member.setShortName("userstory" + TEST_PREFIX);
        member.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        member.setCourse(course);
        member.setMaxPoints(10.0);
        member.setReleaseDate(ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS));
        member.setExerciseVariantGroup(milestoneGroup);
        member.generateAndSetProjectKey();
        programmingExerciseRepository.save(member);

        var overview = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        // The anchor itself is never listed: MilestoneExercise.isVisibleToStudents() is always false.
        assertThat(overview.exercises()).extracting(ExerciseOverviewDTO::id).doesNotContain(milestoneExercise.getId());
        assertThat(overview.exercises()).filteredOn(exercise -> exercise.id().equals(member.getId())).singleElement().satisfies(exercise -> {
            assertThat(exercise.exerciseVariantGroup()).isNotNull();
            assertThat(exercise.exerciseVariantGroup().type()).isEqualTo("milestone");
            assertThat(exercise.exerciseVariantGroup().milestoneExerciseId()).isEqualTo(milestoneExercise.getId());
        });
    }

    /**
     * A milestone group's points are the ones its anchor {@link MilestoneExercise} carries - {@code MilestoneScoreService}
     * writes {@code sum(user story points) - static code analysis penalty} (or {@code 0} on a BLOCKING category) onto its
     * result. The per-group breakdown the group detail page shows therefore has to report the anchor's value, and the
     * course total has to count the group through the anchor as well; summing the user stories instead would both ignore
     * the penalty and, since the score calculation deliberately skips milestone group members, leave the whole group out
     * of the course score.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theCourseOverviewScoresAMilestoneGroupThroughItsAnchorExercise() throws Exception {
        // The anchor's maxPoints mirror the sum of its members' - MilestoneExercisePointsService keeps them in sync.
        milestoneExercise.setMaxPoints(10.0);
        milestoneExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        UserStoryExercise member = new UserStoryExercise();
        member.setTitle("User story");
        member.setShortName("usscored" + TEST_PREFIX);
        member.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        member.setCourse(course);
        member.setMaxPoints(10.0);
        member.setAssessmentType(AssessmentType.AUTOMATIC);
        member.setReleaseDate(milestoneExercise.getReleaseDate());
        member.setDueDate(milestoneExercise.getDueDate());
        member.setExerciseVariantGroup(milestoneGroup);
        member.generateAndSetProjectKey();
        member = (UserStoryExercise) programmingExerciseRepository.save(member);

        // The story scored full marks while the anchor was aggregated down to 60% - exactly the divergence a static code
        // analysis penalty produces. Only the anchor's 6.0 may reach the client.
        addRatedResult(member, 100.0);
        addRatedResult(milestoneExercise, 60.0);

        var overview = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        // The anchor is projected for the score calculation only; it is never rendered (MilestoneExercise.isVisibleToStudents()).
        assertThat(overview.exercises()).extracting(ExerciseOverviewDTO::id).doesNotContain(milestoneExercise.getId()).contains(member.getId());
        assertThat(overview.achievedPointsPerVariantGroup()).containsEntry(milestoneGroup.getId(), 6.0);
        assertThat(overview.totalScores().maxPoints()).isEqualTo(10.0);
        assertThat(overview.totalScores().studentScores().absoluteScore()).isEqualTo(6.0);
    }

    /** Gives {@code student1} a participation on the exercise with one rated, completed result at the given percentage. */
    private void addRatedResult(ProgrammingExercise exercise, double score) {
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setCommitHash("commit-" + exercise.getId());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        submission.setSubmitted(true);
        submission = programmingSubmissionRepository.save(submission);

        Result result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        result.setSuccessful(true);
        result.setExerciseId(exercise.getId());
        result.setSubmission(submission);
        result.setScore(score);
        result.setRated(true);
        resultRepository.save(result);
    }

    /**
     * The instructor-facing exercise endpoint serializes the {@code Exercise} entity rather than a DTO, so its embedded
     * group goes through {@link MilestoneExerciseGroup} itself. It has to expose the same flat
     * {@code milestoneExerciseId} the DTO path does (see the overview test above): the user story detail page reads it
     * to load the anchor's template/solution build status, since the user story's own participations never get one.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void theExerciseEndpointNamesTheAnchorExerciseOnAUserStoryExercise() throws Exception {
        UserStoryExercise member = new UserStoryExercise();
        member.setTitle("User story");
        member.setShortName("userstoryentity" + TEST_PREFIX);
        member.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        member.setCourse(course);
        member.setMaxPoints(10.0);
        member.setExerciseVariantGroup(milestoneGroup);
        member.generateAndSetProjectKey();
        programmingExerciseRepository.save(member);

        MvcResult result = request.performMvcRequest(MockMvcRequestBuilders.get("/api/programming/programming-exercises/" + member.getId())).andExpect(status().isOk()).andReturn();
        JsonNode group = request.getObjectMapper().readTree(result.getResponse().getContentAsString()).get("exerciseVariantGroup");

        assertThat(group).isNotNull();
        assertThat(group.get("type").asText()).isEqualTo("milestone");
        assertThat(group.get("milestoneExerciseId").asLong()).isEqualTo(milestoneExercise.getId());
    }
}
