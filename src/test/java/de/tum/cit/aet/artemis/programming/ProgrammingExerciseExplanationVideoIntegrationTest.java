package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

class ProgrammingExerciseExplanationVideoIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexplanationvideo";

    private final String baseUrl = "/api/programming/programming-exercise-participations/";

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    private final MockMultipartFile validVideo = new MockMultipartFile("file", "explanation.mp4", "video/mp4", "some video bytes".getBytes());

    private final MockMultipartFile wrongExtensionFile = new MockMultipartFile("file", "explanation.txt", "text/plain", "not a video".getBytes());

    @Autowired
    private ResultTestRepository resultRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setRequiresExplanationVideo(true);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void uploadExplanationVideo_asOwner_succeeds() throws Exception {
        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", validVideo, HttpStatus.OK);

        var updatedParticipation = studentParticipationRepository.findById(participation.getId()).orElseThrow();
        assertThat(((ProgrammingExerciseStudentParticipation) updatedParticipation).hasExplanationVideo()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void uploadExplanationVideo_asNonOwner_isForbidden() throws Exception {
        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", validVideo, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void uploadExplanationVideo_wrongExtension_isRejected() throws Exception {
        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", wrongExtensionFile, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void uploadExplanationVideo_whenNotRequired_isRejected() throws Exception {
        programmingExercise.setRequiresExplanationVideo(false);
        programmingExerciseRepository.save(programmingExercise);

        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", validVideo, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void downloadExplanationVideo_afterUpload_returnsVideo() throws Exception {
        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", validVideo, HttpStatus.OK);

        var response = request.get(baseUrl + participation.getId() + "/explanation-video", HttpStatus.OK, byte[].class);
        assertThat(response).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void downloadExplanationVideo_withoutUpload_returnsNotFound() throws Exception {
        request.get(baseUrl + participation.getId() + "/explanation-video", HttpStatus.NOT_FOUND, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void downloadExplanationVideo_asNonOwnerNonTutor_isForbidden() throws Exception {
        // Simulate student1 having already uploaded a video, without going through the upload endpoint (which student2 isn't allowed to call for this participation).
        participation.setExplanationVideoPath("programming-exercises/" + programmingExercise.getId() + "/participations/" + participation.getId() + "/explanation.mp4");
        studentParticipationRepository.save(participation);

        request.get(baseUrl + participation.getId() + "/explanation-video", HttpStatus.FORBIDDEN, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void deleteExplanationVideo_removesVideo() throws Exception {
        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", validVideo, HttpStatus.OK);

        request.delete(baseUrl + participation.getId() + "/explanation-video", HttpStatus.OK);

        var updatedParticipation = studentParticipationRepository.findById(participation.getId()).orElseThrow();
        assertThat(((ProgrammingExerciseStudentParticipation) updatedParticipation).hasExplanationVideo()).isFalse();
        request.get(baseUrl + participation.getId() + "/explanation-video", HttpStatus.NOT_FOUND, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void uploadExplanationVideo_reRatesPreviouslyGatedAutomaticResult() throws Exception {
        // The submission date has to be set: re-rating compares it against the due date, and every ProgrammingSubmission the
        // build pipeline creates carries one (a missing one made the upload fail with a 500 instead of re-rating the result).
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        Submission submission = participationUtilService.addSubmission(participation, programmingSubmission);
        // Simulate the grading gate having withheld this automatic result because no video was present yet (rated = false).
        Submission submissionWithResult = participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 100D, false);
        Result result = submissionWithResult.getFirstResult();

        request.postMultipartFileOnly(baseUrl + participation.getId() + "/explanation-video", validVideo, HttpStatus.OK);

        var reRatedResult = resultRepository.findByIdElseThrow(result.getId());
        assertThat(reRatedResult.isRated()).isTrue();
    }

    @Test
    void requiresExplanationVideo_helperReflectsFlag() {
        assertThat(programmingExercise.requiresExplanationVideo()).isTrue();
        programmingExercise.setRequiresExplanationVideo(null);
        assertThat(programmingExercise.requiresExplanationVideo()).isFalse();
    }

    @Test
    void hasExplanationVideo_helperReflectsPath() {
        assertThat(participation.hasExplanationVideo()).isFalse();
        participation.setExplanationVideoPath("programming-exercises/1/participations/2/explanation.mp4");
        assertThat(participation.hasExplanationVideo()).isTrue();
    }
}
