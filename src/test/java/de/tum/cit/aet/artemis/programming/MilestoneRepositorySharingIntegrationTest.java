package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.UserStoryExerciseService;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * Tests a MilestoneExercise that works on the repositories of an earlier Milestone instead of owning any of its own - how a
 * course runs several Milestones over one continuously growing codebase.
 * <p>
 * The substance of that feature is that one repository is now shared by several Milestones with their user stories, and a push
 * to it still resolves to the Milestone that owns the repositories. What follows from that - which participations a push may
 * be authorized and graded for, and which repositories a deletion may take - is what these tests cover.
 */
class MilestoneRepositorySharingIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "milestonesharing";

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(2);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Autowired
    private MilestoneExerciseRepository milestoneExerciseRepository;

    @Autowired
    private UserStoryExerciseService userStoryExerciseService;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    private Course course;

    /** Owns the repositories the whole chain works on. Its due date has passed. */
    private MilestoneExercise repositoryOwner;

    /** Works on the owner's repositories and is the Milestone currently open. */
    private MilestoneExercise linkedMilestone;

    private String sharedRepositoryUri;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseRepository.save(CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor"));

        repositoryOwner = saveMilestone("Milestone 1", "MSONE", null, PAST_TIMESTAMP.plusDays(1));
        linkedMilestone = saveMilestone("Milestone 2", "MSTWO", repositoryOwner, FUTURE_TIMESTAMP);
    }

    private MilestoneExercise saveMilestone(String title, String shortName, MilestoneExercise repositorySource, ZonedDateTime dueDate) {
        MilestoneExercise milestoneExercise = new MilestoneExercise();
        milestoneExercise.setCourse(course);
        milestoneExercise.setTitle(title);
        milestoneExercise.setShortName(shortName);
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.setBonusPoints(0.0);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setPackageName("de.tum.in.www1");
        milestoneExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        milestoneExercise.setReleaseDate(PAST_TIMESTAMP);
        milestoneExercise.setDueDate(dueDate);
        milestoneExercise.setCategories(new HashSet<>(Set.of("{\"color\":\"#ad5658\",\"category\":\"Blub\"}")));
        milestoneExercise.setRepositorySourceMilestone(repositorySource);
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise.setBuildConfig(buildConfigRepository.save(new ProgrammingExerciseBuildConfig()));
        return milestoneExerciseRepository.save(milestoneExercise);
    }

    private UserStoryExercise addUserStory(MilestoneExercise milestoneExercise, String title, String shortName) {
        UserStoryExercise userStoryExercise = new UserStoryExercise();
        userStoryExercise.setTitle(title);
        userStoryExercise.setShortName(shortName);
        userStoryExercise.setMaxPoints(10.0);
        userStoryExercise.setBonusPoints(0.0);
        return userStoryExerciseService.createUserStoryExercise(milestoneExercise.getId(), userStoryExercise);
    }

    /**
     * Gives the student the one repository the whole chain works on: a participation per exercise, all carrying the same
     * repository uri, exactly as starting the Milestones produces them.
     */
    private void startWholeChainForStudent1() {
        ProgrammingExerciseStudentParticipation ownerParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(repositoryOwner,
                TEST_PREFIX + "student1");
        sharedRepositoryUri = ownerParticipation.getRepositoryUri();

        for (MilestoneExercise milestoneExercise : List.of(repositoryOwner, linkedMilestone)) {
            for (UserStoryExercise userStoryExercise : milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExercise.getId()).getUserStoryExercises()) {
                pointAtSharedRepository(participationUtilService.addStudentParticipationForProgrammingExercise(userStoryExercise, TEST_PREFIX + "student1"));
            }
        }
        pointAtSharedRepository(participationUtilService.addStudentParticipationForProgrammingExercise(linkedMilestone, TEST_PREFIX + "student1"));
    }

    private void pointAtSharedRepository(ProgrammingExerciseStudentParticipation participation) {
        participation.setRepositoryUri(sharedRepositoryUri);
        programmingExerciseStudentParticipationRepository.save(participation);
    }

    private long student1Id() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
    }

    /**
     * Gives a Milestone the repositories the creation pipeline would give it: template, solution and test repositories in its
     * own project, except that a Milestone continuing another's codebase points its template participation at that Milestone's
     * template repository instead.
     *
     * @param milestoneExercise the Milestone to equip
     * @param repositorySource  the Milestone whose template repository it works on, or null if it owns its own
     */
    private void giveRepositoriesTo(MilestoneExercise milestoneExercise, @Nullable MilestoneExercise repositorySource) {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(milestoneExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(milestoneExercise);
        MilestoneExercise equipped = milestoneExerciseRepository.findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(milestoneExercise.getId())
                .orElseThrow();
        // Derived from the solution uri rather than built from scratch, so it lands in the same project without needing the
        // configured local VC base uri here
        equipped.setTestRepositoryUri(equipped.getSolutionParticipation().getRepositoryUri().replace("-solution", "-tests"));

        if (repositorySource != null) {
            TemplateProgrammingExerciseParticipation templateParticipation = equipped.getTemplateParticipation();
            templateParticipation.setRepositoryUri(repositorySource.getTemplateParticipation().getRepositoryUri());
            templateProgrammingExerciseParticipationRepository.saveAndFlush(templateParticipation);
        }
        milestoneExerciseRepository.save(equipped);

        // Re-read through the entity graph: the instance save() returns carries lazy participation proxies, which the assertions
        // below would dereference after the session has closed
        MilestoneExercise reloaded = milestoneExerciseRepository.findWithUserStoryExercisesAndTemplateAndSolutionParticipationAndBuildConfigById(milestoneExercise.getId())
                .orElseThrow();
        if (milestoneExercise.getId().equals(repositoryOwner.getId())) {
            repositoryOwner = reloaded;
        }
        else {
            linkedMilestone = reloaded;
        }
    }

    /**
     * A student is handed one token per exercise, and the whole chain works on one repository - so a token from any of them
     * has to authenticate against it, not just the one from the Milestone the repository uri happens to resolve to.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldFindEveryParticipationOnTheSharedRepositoryForTokenAuthentication() {
        addUserStory(repositoryOwner, "Story 1", "STORYONE");
        addUserStory(linkedMilestone, "Story 2", "STORYTWO");
        startWholeChainForStudent1();

        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository.findAllByRepositoryUriAndStudentId(sharedRepositoryUri,
                student1Id());

        // Both Milestones and both user stories
        assertThat(participations).hasSize(4);
        assertThat(participations).extracting(participation -> participation.getExercise().getTitle()).containsExactlyInAnyOrder("Milestone 1", "Milestone 2", "Story 1",
                "Story 2");
    }

    /**
     * Grading is the opposite of token authentication here. Every Milestone has a test repository of its own, so a build result
     * only contains the tests of the Milestone it was queued for - handing it to the other Milestone's user stories would report
     * all of their tests as missing and reset the student's score there to zero.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldFanABuildResultOutToTheBuiltMilestonesUserStoriesOnly() {
        addUserStory(repositoryOwner, "Story 1", "STORYONE");
        addUserStory(linkedMilestone, "Story 2", "STORYTWO");
        startWholeChainForStudent1();

        List<ProgrammingExerciseStudentParticipation> fanOutOfLinkedMilestone = programmingExerciseStudentParticipationRepository
                .findAllUserStorySiblingsByMilestoneIdAndRepositoryUriAndStudentId(linkedMilestone.getId(), sharedRepositoryUri, student1Id());

        assertThat(fanOutOfLinkedMilestone).extracting(participation -> participation.getExercise().getTitle()).containsExactly("Story 2");
    }

    /**
     * Starting a Milestone that reuses another's repositories must continue in the repository the student already has, instead
     * of forking the template again and throwing their work away.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldFindTheStudentsExistingRepositoryOfTheChainWhenStartingALinkedMilestone() {
        ProgrammingExerciseStudentParticipation ownerParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(repositoryOwner,
                TEST_PREFIX + "student1");

        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository
                .findAllMilestoneParticipationsSharingRepositoryByOwnerIdAndStudentId(repositoryOwner.getId(), student1Id());

        assertThat(participations).extracting(ProgrammingExerciseStudentParticipation::getRepositoryUri).containsExactly(ownerParticipation.getRepositoryUri());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldNotFindAnyExistingRepositoryForAStudentWhoHasNotStartedTheChain() {
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository
                .findAllMilestoneParticipationsSharingRepositoryByOwnerIdAndStudentId(repositoryOwner.getId(), student1Id());

        assertThat(participations).isEmpty();
    }

    /**
     * The repository uri carries the owner's project key, so a push always resolves to the owner - long after its due date,
     * while the student is working on a later Milestone. Taking the owner's participation would refuse the push and attribute
     * the build to the wrong Milestone.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResolveAPushToTheMilestoneThatIsCurrentlyOpen() {
        startWholeChainForStudent1();

        ProgrammingExerciseParticipation participation = programmingExerciseParticipationService.fetchParticipationByRepository(TEST_PREFIX + "student1", sharedRepositoryUri,
                repositoryOwner);

        assertThat(participation.getExercise().getId()).isEqualTo(linkedMilestone.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResolveAPushToTheRepositoryOwnerWhileItIsTheOpenMilestone() {
        repositoryOwner.setDueDate(FUTURE_TIMESTAMP.plusDays(1));
        repositoryOwner = milestoneExerciseRepository.save(repositoryOwner);
        linkedMilestone.setDueDate(FUTURE_TIMESTAMP.plusDays(2));
        linkedMilestone = milestoneExerciseRepository.save(linkedMilestone);
        startWholeChainForStudent1();

        ProgrammingExerciseParticipation participation = programmingExerciseParticipationService.fetchParticipationByRepository(TEST_PREFIX + "student1", sharedRepositoryUri,
                repositoryOwner);

        // The earliest still-open due date wins, so the Milestone being worked on now
        assertThat(participation.getExercise().getId()).isEqualTo(repositoryOwner.getId());
    }

    /**
     * Once every Milestone of the chain is closed there is nothing to redirect to, and the push has to be rejected with the
     * same message as before rather than silently land on an arbitrary Milestone.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResolveAPushToTheResolvedMilestoneWhenTheWholeChainIsClosed() {
        linkedMilestone.setDueDate(PAST_TIMESTAMP.plusDays(1));
        linkedMilestone = milestoneExerciseRepository.save(linkedMilestone);
        startWholeChainForStudent1();

        ProgrammingExerciseParticipation participation = programmingExerciseParticipationService.fetchParticipationByRepository(TEST_PREFIX + "student1", sharedRepositoryUri,
                repositoryOwner);

        assertThat(participation.getExercise().getId()).isEqualTo(repositoryOwner.getId());
    }

    /**
     * Deleting the Milestone that owns the repositories would take the template, solution, test and every student repository of
     * the whole chain with it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRefuseToDeleteAMilestoneWhoseRepositoriesAreStillUsed() throws Exception {
        request.delete("/api/programming/programming-exercises/" + repositoryOwner.getId(), HttpStatus.BAD_REQUEST);

        assertThat(milestoneExerciseRepository.findById(repositoryOwner.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldFindTheMilestonesUsingTheRepositoriesOfAMilestone() {
        assertThat(milestoneExerciseRepository.findAllByRepositorySourceMilestoneId(repositoryOwner.getId())).extracting(MilestoneExercise::getId)
                .containsExactly(linkedMilestone.getId());
        assertThat(milestoneExerciseRepository.findAllByRepositorySourceMilestoneId(linkedMilestone.getId())).isEmpty();
    }

    /**
     * Which repositories a Milestone works on is fixed at creation - students have already pushed to them - so no update may
     * move it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldKeepTheRepositoryLinkWhenTheMilestoneIsUpdated() throws Exception {
        linkedMilestone.setTitle("Milestone 2 renamed");

        // Exactly what the client sends: the update DTO, never the entity - so the link is not in the payload at all
        request.putWithResponseBody("/api/programming/milestone-exercises/" + linkedMilestone.getId(), UpdateProgrammingExerciseDTO.of(linkedMilestone), MilestoneExercise.class,
                HttpStatus.OK);

        MilestoneExercise updated = milestoneExerciseRepository.findByIdElseThrow(linkedMilestone.getId());
        assertThat(updated.getTitle()).isEqualTo("Milestone 2 renamed");
        assertThat(updated.getRepositorySourceMilestone()).isNotNull();
        assertThat(updated.getRepositorySourceMilestone().getId()).isEqualTo(repositoryOwner.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldNotAcceptARepositorySourceThatDoesNotExist() throws Exception {
        request.postWithResponseBody("/api/programming/milestone-exercises/setup?repositorySourceMilestoneId=" + (linkedMilestone.getId() + 1000), newMilestoneRequestBody(),
                MilestoneExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldNotAcceptARepositorySourceFromAnotherCourse() throws Exception {
        Course otherCourse = courseRepository.save(CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), TEST_PREFIX + "tumuser",
                TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor"));
        MilestoneExercise milestoneOfOtherCourse = new MilestoneExercise();
        milestoneOfOtherCourse.setCourse(otherCourse);
        milestoneOfOtherCourse.setTitle("Other course milestone");
        milestoneOfOtherCourse.setShortName("MSOTHER");
        milestoneOfOtherCourse.setMaxPoints(0.0);
        milestoneOfOtherCourse.setBonusPoints(0.0);
        milestoneOfOtherCourse.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneOfOtherCourse.setPackageName("de.tum.in.www1");
        milestoneOfOtherCourse.generateAndSetProjectKey();
        milestoneOfOtherCourse.setBuildConfig(buildConfigRepository.save(new ProgrammingExerciseBuildConfig()));
        milestoneOfOtherCourse = milestoneExerciseRepository.save(milestoneOfOtherCourse);

        request.postWithResponseBody("/api/programming/milestone-exercises/setup?repositorySourceMilestoneId=" + milestoneOfOtherCourse.getId(), newMilestoneRequestBody(),
                MilestoneExercise.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Only what carries the codebase is shared. The tests decide the grading and the solution documents it, and consecutive
     * Milestones pose different problems, so both of those stay with the Milestone - in its own project.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldShareOnlyTheTemplateRepositoryAndKeepItsOwnTestsAndSolution() {
        giveRepositoriesTo(repositoryOwner, null);
        giveRepositoriesTo(linkedMilestone, repositoryOwner);

        assertThat(linkedMilestone.getTemplateParticipation().getRepositoryUri()).isEqualTo(repositoryOwner.getTemplateParticipation().getRepositoryUri());
        assertThat(linkedMilestone.getTestRepositoryUri()).isNotEqualTo(repositoryOwner.getTestRepositoryUri());
        assertThat(linkedMilestone.getTestRepositoryUri()).contains(linkedMilestone.getProjectKey().toLowerCase());
        assertThat(linkedMilestone.getSolutionParticipation().getRepositoryUri()).isNotEqualTo(repositoryOwner.getSolutionParticipation().getRepositoryUri());
        assertThat(linkedMilestone.getSolutionParticipation().getRepositoryUri()).contains(linkedMilestone.getProjectKey().toLowerCase());
        // Its own build plans as well: it builds its own test repository
        assertThat(linkedMilestone.getTemplateParticipation().getBuildPlanId()).isNotEqualTo(repositoryOwner.getTemplateParticipation().getBuildPlanId());
    }

    /**
     * Two Milestones now have a template participation on the same repository uri. The uri carries the owner's project key and
     * therefore resolves to the owner, so the lookup has to be scoped by exercise - unscoped it would fail with a non-unique
     * result on every clone of or push to the shared template repository.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResolveTheSharedTemplateRepositoryToItsOwner() {
        giveRepositoriesTo(repositoryOwner, null);
        giveRepositoriesTo(linkedMilestone, repositoryOwner);
        String sharedTemplateUri = repositoryOwner.getTemplateParticipation().getRepositoryUri();

        ProgrammingExerciseParticipation participation = programmingExerciseParticipationService.fetchParticipationByRepository(RepositoryType.TEMPLATE.toString(),
                sharedTemplateUri, repositoryOwner);

        assertThat(participation.getId()).isEqualTo(repositoryOwner.getTemplateParticipation().getId());
    }

    /**
     * A Milestone that is itself linked names the same repositories as the Milestone it points at, so the choice is stored
     * flattened to the owner. Every "which Milestones share this repository" query is then a single lookup on one column.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReportTheRepositoryOwnerOfALinkedMilestone() {
        assertThat(repositoryOwner.getRepositoryOwner().getId()).isEqualTo(repositoryOwner.getId());
        assertThat(repositoryOwner.reusesRepositoriesOfAnotherMilestone()).isFalse();
        assertThat(linkedMilestone.getRepositoryOwner().getId()).isEqualTo(repositoryOwner.getId());
        assertThat(linkedMilestone.reusesRepositoriesOfAnotherMilestone()).isTrue();
    }

    private MilestoneExercise newMilestoneRequestBody() {
        MilestoneExercise milestoneExercise = new MilestoneExercise();
        milestoneExercise.setCourse(course);
        milestoneExercise.setTitle("Milestone 3");
        milestoneExercise.setShortName("MSTHREE");
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.setBonusPoints(0.0);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setPackageName("de.tum.in.www1");
        milestoneExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        return milestoneExercise;
    }
}
