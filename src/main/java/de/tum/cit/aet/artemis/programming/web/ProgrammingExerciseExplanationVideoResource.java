package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EmptyFileException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exam.api.ExamSubmissionApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseExplanationVideoDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExplanationVideoService;

/**
 * REST controller for uploading, downloading and deleting the explanation video that a student can attach
 * to a programming exercise participation.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseExplanationVideoResource {

    private static final String ENTITY_NAME = "programmingExerciseExplanationVideo";

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExplanationVideoResource.class);

    private final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseExplanationVideoService explanationVideoService;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final Optional<ExamSubmissionApi> examSubmissionApi;

    public ProgrammingExerciseExplanationVideoResource(ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseExplanationVideoService explanationVideoService, AuthorizationCheckService authCheckService, UserRepository userRepository,
            Optional<ExamSubmissionApi> examSubmissionApi) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.explanationVideoService = explanationVideoService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.examSubmissionApi = examSubmissionApi;
    }

    /**
     * POST programming-exercise-participations/:participationId/explanation-video : upload or replace the explanation video for the given participation.
     *
     * @param participationId of the participation the video belongs to
     * @param file            the video file to store
     * @return the ResponseEntity with status 200 (OK) and the new explanation video path and upload date in the body (so the client can update its view
     *         without a full refetch), or an error status if the exercise does not require a video, the file is invalid, or the participation is locked
     */
    @PostMapping("programming-exercise-participations/{participationId}/explanation-video")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseExplanationVideoDTO> uploadExplanationVideo(@PathVariable long participationId, @RequestPart("file") MultipartFile file) {
        log.debug("REST request to upload explanation video for participation {}", participationId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProgrammingExerciseStudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = participation.getProgrammingExercise();

        if (!exercise.requiresExplanationVideo()) {
            throw new BadRequestAlertException("This exercise does not require an explanation video", ENTITY_NAME, "explanationVideoNotRequired");
        }
        if (!authCheckService.isOwnerOfParticipation(participation, user)) {
            throw new AccessForbiddenException();
        }
        checkFileSize(file);
        checkFileExtension(file);

        if (exercise.isExamExercise()) {
            ExamSubmissionApi api = examSubmissionApi.orElseThrow(() -> new ExamApiNotPresentException(ExamSubmissionApi.class));
            api.checkSubmissionAllowanceElseThrow(exercise, user);
        }

        ProgrammingExerciseStudentParticipation updatedParticipation;
        try {
            updatedParticipation = explanationVideoService.storeExplanationVideo(participation, file);
        }
        catch (IOException e) {
            throw new BadRequestAlertException("The uploaded video could not be saved on the server", ENTITY_NAME, "cantSaveFile");
        }
        catch (EmptyFileException e) {
            throw new BadRequestAlertException("The uploaded video is empty", ENTITY_NAME, "cantSaveFile");
        }

        return ResponseEntity.ok(new ProgrammingExerciseExplanationVideoDTO(updatedParticipation.getExplanationVideoPath(), updatedParticipation.getExplanationVideoUploadDate()));
    }

    /**
     * GET programming-exercise-participations/:participationId/explanation-video : download the explanation video for the given participation.
     *
     * @param participationId of the participation the video belongs to
     * @return the ResponseEntity with status 200 (OK) and the video as body, or 404 (Not Found) if no video exists
     */
    @GetMapping("programming-exercise-participations/{participationId}/explanation-video")
    @EnforceAtLeastStudent
    public ResponseEntity<Resource> getExplanationVideo(@PathVariable long participationId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProgrammingExerciseStudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = participation.getProgrammingExercise();

        if (!authCheckService.isOwnerOfParticipation(participation, user) && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            throw new AccessForbiddenException();
        }
        if (!participation.hasExplanationVideo()) {
            throw new EntityNotFoundException("No explanation video found for participation " + participationId);
        }

        var path = FilePathConverter.fileSystemPathForExternalUri(URI.create(participation.getExplanationVideoPath()), FilePathType.PROGRAMMING_EXPLANATION_VIDEO);
        try {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
            MediaType mediaType = mediaTypeForFilename(path.getFileName().toString());
            return ResponseEntity.ok().contentLength(Files.size(path)).contentType(mediaType).body(resource);
        }
        catch (IOException e) {
            throw new EntityNotFoundException("Explanation video file not found for participation " + participationId);
        }
    }

    /**
     * DELETE programming-exercise-participations/:participationId/explanation-video : delete the explanation video for the given participation.
     *
     * @param participationId of the participation the video belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("programming-exercise-participations/{participationId}/explanation-video")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteExplanationVideo(@PathVariable long participationId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProgrammingExerciseStudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);

        if (!authCheckService.isOwnerOfParticipation(participation, user)) {
            throw new AccessForbiddenException();
        }

        explanationVideoService.deleteExplanationVideo(participation);
        return ResponseEntity.ok().build();
    }

    private static MediaType mediaTypeForFilename(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".webm")) {
            return MediaType.parseMediaType("video/webm");
        }
        if (lower.endsWith(".mov")) {
            return MediaType.parseMediaType("video/quicktime");
        }
        return MediaType.parseMediaType("video/mp4");
    }

    private static void checkFileSize(MultipartFile file) {
        if (file.getSize() > Constants.MAX_EXPLANATION_VIDEO_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "The maximum video file size is " + Constants.MAX_EXPLANATION_VIDEO_FILE_SIZE + " bytes!");
        }
    }

    private static void checkFileExtension(MultipartFile file) {
        final String originalFilename = file.getOriginalFilename();
        final String[] splitFileName = originalFilename == null ? new String[0] : originalFilename.split("\\.");
        final String fileSuffix = splitFileName.length == 0 ? "" : splitFileName[splitFileName.length - 1].toLowerCase();
        if (!fileSuffix.matches(Constants.ALLOWED_EXPLANATION_VIDEO_FILE_EXTENSIONS_PATTERN)) {
            throw new BadRequestAlertException("The uploaded file has the wrong type! Allowed types: mp4, webm, mov", ENTITY_NAME, "explanationVideoIllegalFileType");
        }
    }
}
