package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.TaskPriority;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryTask;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortStatusDTO;
import de.tum.cit.aet.artemis.programming.repository.UserStoryTaskRepository;
import de.tum.cit.aet.artemis.programming.service.MilestoneEffortGateService;

/**
 * Covers the effort a student reports for a user story exercise - summed from their task board rather than entered
 * by hand - and the gate that refuses writes to a milestone group's shared repository while a started story still
 * has no tasks.
 */
class UserStoryEffortIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userstoryeffort";

    @Autowired
    private UserStoryTaskRepository userStoryTaskRepository;

    @Autowired
    private MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    @Autowired
    private MilestoneEffortGateService milestoneEffortGateService;

    private Course course;

    private MilestoneExercise milestoneExercise;

    private MilestoneExerciseGroup group;

    private UserStoryExercise userStory;

    private String studentLogin;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        studentLogin = TEST_PREFIX + "student1";
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        milestoneExercise = new MilestoneExercise();
        milestoneExercise.setTitle("Milestone");
        milestoneExercise.setShortName("ms" + TEST_PREFIX);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setCourse(course);
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.setDueDate(ZonedDateTime.now().plusDays(7));
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        group = new MilestoneExerciseGroup();
        group.setTitle("Sprint 1");
        group.setMilestoneExercise(milestoneExercise);
        group = milestoneExerciseGroupRepository.save(group);

        course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);

        userStory = createUserStory("us1", ZonedDateTime.now().plusDays(7));
    }

    private UserStoryExercise createUserStory(String shortName, ZonedDateTime dueDate) {
        UserStoryExercise exercise = new UserStoryExercise();
        exercise.setTitle("User story " + shortName);
        exercise.setShortName(shortName + TEST_PREFIX);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setCourse(course);
        exercise.setMaxPoints(2.0);
        exercise.setDueDate(dueDate);
        exercise.setExerciseVariantGroup(group);
        exercise.generateAndSetProjectKey();
        return (UserStoryExercise) programmingExerciseRepository.save(exercise);
    }

    private String effortUrl(long exerciseId) {
        return "/api/programming/user-story-exercises/" + exerciseId + "/effort";
    }

    private UserStoryTask taskFor(ProgrammingExerciseStudentParticipation participation, double estimatedHours, @Nullable Double actualHours) {
        UserStoryTask task = new UserStoryTask();
        task.setParticipation(participation);
        task.setTitle("Task");
        task.setTaskPoints(1);
        task.setPriority(TaskPriority.LOW);
        task.setEstimatedEffortHours(estimatedHours);
        task.setActualEffortHours(actualHours);
        return userStoryTaskRepository.save(task);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void readingEffortReturnsAZeroEstimateAndNoActualBeforeAnyTaskExists() throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);

        UserStoryEffortDTO effort = request.get(effortUrl(userStory.getId()), HttpStatus.OK, UserStoryEffortDTO.class);

        assertThat(effort.estimatedEffort()).isEqualTo(0.0);
        assertThat(effort.actualEffort()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void readingEffortSumsTheParticipantsTasks() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        taskFor(participation, 2.0, 1.0);
        taskFor(participation, 3.5, null);

        UserStoryEffortDTO effort = request.get(effortUrl(userStory.getId()), HttpStatus.OK, UserStoryEffortDTO.class);

        assertThat(effort.estimatedEffort()).isEqualTo(5.5);
        // SUM ignores the task with no actual effort logged yet rather than treating it as 0.
        assertThat(effort.actualEffort()).isEqualTo(1.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void readingEffortIsRejectedWithoutAParticipation() throws Exception {
        request.get(effortUrl(userStory.getId()), HttpStatus.BAD_REQUEST, UserStoryEffortDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void readingEffortIsRejectedForANonUserStoryExercise() throws Exception {
        request.get(effortUrl(milestoneExercise.getId()), HttpStatus.BAD_REQUEST, UserStoryEffortDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theCourseLookupReportsEveryStartedStory() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        taskFor(participation, 2.0, null);
        UserStoryExercise otherStory = createUserStory("us4", ZonedDateTime.now().plusDays(7));
        participationUtilService.addStudentParticipationForProgrammingExercise(otherStory, studentLogin);

        List<UserStoryEffortStatusDTO> statuses = request.getList("/api/programming/courses/" + course.getId() + "/user-story-efforts", HttpStatus.OK,
                UserStoryEffortStatusDTO.class);

        assertThat(statuses).extracting(UserStoryEffortStatusDTO::exerciseId).containsExactlyInAnyOrder(userStory.getId(), otherStory.getId());
        assertThat(statuses).filteredOn(status -> status.exerciseId().equals(userStory.getId())).singleElement().extracting(UserStoryEffortStatusDTO::estimatedEffort)
                .isEqualTo(2.0);
        // The story with no tasks yet sums to 0, not null - there is simply nothing to add up.
        assertThat(statuses).filteredOn(status -> status.exerciseId().equals(otherStory.getId())).singleElement().extracting(UserStoryEffortStatusDTO::estimatedEffort)
                .isEqualTo(0.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void aTutorCanReadTheEffortOnAParticipationTheyAssess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        taskFor(participation, 4.0, 6.0);

        UserStoryEffortDTO effort = request.get("/api/programming/participations/" + participation.getId() + "/user-story-effort", HttpStatus.OK, UserStoryEffortDTO.class);

        assertThat(effort.estimatedEffort()).isEqualTo(4.0);
        assertThat(effort.actualEffort()).isEqualTo(6.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void anotherStudentCannotReadTheEffortOnAParticipation() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        taskFor(participation, 4.0, null);

        request.get("/api/programming/participations/" + participation.getId() + "/user-story-effort", HttpStatus.FORBIDDEN, UserStoryEffortDTO.class);
    }

    @Test
    void theGateBlocksAStartedStoryWithoutAnyTasks() {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);

        List<String> blocking = milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student);

        assertThat(blocking).containsExactly(userStory.getTitle());
        assertThat(milestoneEffortGateService.buildRejectionMessage(blocking)).contains(userStory.getTitle());
    }

    @Test
    void theGateIgnoresAStoryTheStudentHasNotStarted() {
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);

        // No participation in the story, so there is nowhere to create a task - it must not block the push.
        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student)).isEmpty();
    }

    @Test
    void theGateClearsOnceATaskExists() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);
        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student)).isNotEmpty();

        taskFor(participation, 2.0, null);

        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student)).isEmpty();
    }

    @Test
    void theGateNeverBlocksTeachingStaff() {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, tutor)).isEmpty();
    }

    @Test
    void theGateIgnoresExercisesThatAreNotMilestones() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);

        // The gate only guards a milestone group's shared repository; a story's own repository is not one.
        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(userStory, participation, student)).isEmpty();
    }
}
