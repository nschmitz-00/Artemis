package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EmptyFileException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Handles storing, replacing and deleting the explanation video that a student can (optionally) attach
 * to a {@link ProgrammingExerciseStudentParticipation}, and re-rating results once a required video becomes available.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseExplanationVideoService {

    private final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final FileService fileService;

    private final ExerciseDateService exerciseDateService;

    public ProgrammingExerciseExplanationVideoService(ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository,
            FileService fileService, ExerciseDateService exerciseDateService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.fileService = fileService;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Stores (or replaces) the explanation video for the given participation, then re-rates any automatic results
     * that were previously withheld from the score because no video was present.
     *
     * @param participation the participation the video belongs to
     * @param file          the video file to store
     * @return the updated participation
     * @throws IOException              if the file can't be saved
     * @throws EmptyFileException       if the file is empty
     * @throws AccessForbiddenException if the participation is locked because the due date has passed
     */
    public ProgrammingExerciseStudentParticipation storeExplanationVideo(ProgrammingExerciseStudentParticipation participation, MultipartFile file)
            throws IOException, EmptyFileException {
        // Don't allow (re-)uploads after the due date (except if the exercise/participation was started after the due date, e.g. late exam start)
        final var dueDate = ExerciseDateService.getDueDate(participation);
        if (dueDate.isPresent() && exerciseDateService.isAfterDueDate(participation) && participation.getInitializationDate().isBefore(dueDate.get())) {
            throw new AccessForbiddenException();
        }

        if (file.isEmpty()) {
            throw new EmptyFileException(file.getOriginalFilename());
        }

        final String previousVideoPath = participation.getExplanationVideoPath();
        final String multipartFileHash = DigestUtils.md5Hex(file.getInputStream());

        final Path savePath = saveVideoFile(file, participation);
        final URI newVideoPath = FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.PROGRAMMING_EXPLANATION_VIDEO, participation.getId());

        final var storedFileHash = DigestUtils.md5Hex(Files.newInputStream(savePath));
        if (!multipartFileHash.equals(storedFileHash)) {
            throw new IOException("The file " + file.getName() + " could not be stored");
        }

        participation.setExplanationVideoPath(newVideoPath.toString());
        participation.setExplanationVideoUploadDate(ZonedDateTime.now());
        participation = studentParticipationRepository.save(participation);

        if (previousVideoPath != null && !previousVideoPath.equals(newVideoPath.toString())) {
            // IMPORTANT: only delete the old file when the new one has a different name, otherwise this removes the file we just stored
            var oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(previousVideoPath), FilePathType.PROGRAMMING_EXPLANATION_VIDEO);
            fileService.schedulePathForDeletion(oldPath, 0);
        }
        else if (previousVideoPath != null) {
            fileService.evictCacheForPath(savePath);
        }

        reRateAutomaticResults(participation);
        return participation;
    }

    /**
     * Deletes the explanation video (if any) for the given participation.
     *
     * @param participation the participation to remove the explanation video from
     */
    public void deleteExplanationVideo(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getExplanationVideoPath() == null) {
            return;
        }
        var path = FilePathConverter.fileSystemPathForExternalUri(URI.create(participation.getExplanationVideoPath()), FilePathType.PROGRAMMING_EXPLANATION_VIDEO);
        fileService.schedulePathForDeletion(path, 0);
        participation.setExplanationVideoPath(null);
        participation.setExplanationVideoUploadDate(null);
        studentParticipationRepository.save(participation);
    }

    private Path saveVideoFile(MultipartFile file, ProgrammingExerciseStudentParticipation participation) throws IOException {
        var filename = file.getOriginalFilename();
        if (filename != null && filename.contains("\\")) {
            // this can happen on Windows computers, then we want to take the last element of the file path
            var components = filename.split("\\\\");
            filename = components[components.length - 1];
        }
        filename = FileUtil.sanitizeFilename(filename);
        if (filename.length() < 5) {
            // prevent potential problems when users call their file e.g. ß.mp4
            filename = "video" + filename;
        }
        final Path dirPath = FilePathConverter.buildProgrammingExplanationVideoPath(participation.getProgrammingExercise().getId(), participation.getId());
        final Path filePath = dirPath.resolve(filename);
        final File savedFile = filePath.toFile();

        FileUtils.copyInputStreamToFile(file.getInputStream(), savedFile);

        return filePath;
    }

    /**
     * Re-applies the normal due-date-based rating to every automatic result of the given participation.
     * Automatic results are forced unrated while a required explanation video is missing (see
     * {@code ProgrammingExerciseGradingService.processNewProgrammingExerciseResult}); once the video is uploaded,
     * they need to be re-rated so they count towards the score again.
     *
     * @param participation the participation whose automatic results should be re-rated
     */
    private void reRateAutomaticResults(ProgrammingExerciseStudentParticipation participation) {
        Optional<ProgrammingExerciseStudentParticipation> participationWithResults = studentParticipationRepository
                .findByIdWithAllResultsAndRelatedSubmissions(participation.getId());
        if (participationWithResults.isEmpty()) {
            return;
        }

        List<Result> resultsToUpdate = participationWithResults.get().getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).filter(Result::isAutomatic)
                .filter(result -> !result.isRated()).toList();

        for (Result result : resultsToUpdate) {
            result.setRatedIfNotAfterDueDate();
        }
        resultRepository.saveAll(resultsToUpdate);
    }
}
