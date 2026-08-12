package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryProgressStatus;
import de.tum.cit.aet.artemis.programming.dto.MilestoneProgressDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryProgressDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Builds the story-by-story progress overview a student sees on a Milestone page.
 * <p>
 * A Milestone itself carries no points: its {@link UserStoryExercise} children are the graded units, each with its own
 * participation and its own result fanned out from the student's single push (see {@link MilestoneAssessmentService}). The
 * overview therefore reports one row per user story plus the aggregate over them, which is what the student's course score
 * counts as well.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneProgressService {

    private static final double SCORE_NORMALIZATION_VALUE = 0.01;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public MilestoneProgressService(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
    }

    /**
     * Collects how far the given student has come with every user story of the given Milestone.
     *
     * @param milestoneExercise the Milestone to report on, with its {@link UserStoryExercise} children loaded
     * @param studentId         the id of the student whose progress is reported
     * @return one entry per user story of the Milestone plus the aggregate over all of them
     */
    public MilestoneProgressDTO getProgressForStudent(MilestoneExercise milestoneExercise, long studentId) {
        Map<Long, ProgrammingExerciseStudentParticipation> participationsByUserStoryId = programmingExerciseStudentParticipationRepository
                .findAllUserStoryParticipationsWithSubmissionsAndResultsByMilestoneIdAndStudentId(milestoneExercise.getId(), studentId).stream()
                // A student can only ever have one participation per user story, but the query is a fetch join, so identity is deduplicated defensively
                .collect(Collectors.toMap(participation -> participation.getExercise().getId(), Function.identity(), (first, second) -> first));

        // Manual results exist as soon as a tutor saves them, but are only the student's until the assessment is published
        boolean manualResultsVisible = ExerciseDateService.isAfterAssessmentDueDate(milestoneExercise);
        Course course = milestoneExercise.getCourseViaExerciseGroupOrCourseMember();

        List<UserStoryProgressDTO> userStoryProgress = milestoneExercise.getUserStoryExercises().stream()
                .map(userStoryExercise -> toUserStoryProgress(userStoryExercise, participationsByUserStoryId.get(userStoryExercise.getId()), manualResultsVisible, course))
                .toList();

        double achievedPoints = userStoryProgress.stream().mapToDouble(UserStoryProgressDTO::achievedPoints).sum();
        double maxPoints = userStoryProgress.stream().mapToDouble(UserStoryProgressDTO::maxPoints).sum();
        // Clamped: bonus points let a user story pay out more than its max points, and a progress bar past 100 % completion
        // would be nonsense - the exact points are reported next to it
        double completionPercentage = maxPoints > 0.0 ? Math.min(roundScoreSpecifiedByCourseSettings(achievedPoints / maxPoints * 100.0, course), 100.0) : 0.0;
        int completedUserStories = (int) userStoryProgress.stream().filter(progress -> progress.status() == UserStoryProgressStatus.COMPLETED).count();
        // Deliberately not ProgrammingExercise#areManualResultsAllowed, which additionally requires the due date to have passed:
        // the point of this flag is to warn the student early that the numbers above are not final yet
        boolean manualAssessmentPending = milestoneExercise.getAssessmentType() != AssessmentType.AUTOMATIC && !manualResultsVisible;

        return new MilestoneProgressDTO(milestoneExercise.getId(), userStoryProgress, roundScoreSpecifiedByCourseSettings(achievedPoints, course), maxPoints, completionPercentage,
                completedUserStories, userStoryProgress.size(), manualAssessmentPending);
    }

    private UserStoryProgressDTO toUserStoryProgress(UserStoryExercise userStoryExercise, @Nullable ProgrammingExerciseStudentParticipation participation,
            boolean manualResultsVisible, Course course) {
        double maxPoints = userStoryExercise.getMaxPoints() != null ? userStoryExercise.getMaxPoints() : 0.0;
        double bonusPoints = userStoryExercise.getBonusPoints() != null ? userStoryExercise.getBonusPoints() : 0.0;
        Long participationId = participation != null ? participation.getId() : null;

        Optional<Result> countingResult = findCountingResult(participation, userStoryExercise, manualResultsVisible);
        if (countingResult.isEmpty()) {
            return new UserStoryProgressDTO(userStoryExercise.getId(), userStoryExercise.getTitle(), userStoryExercise.getShortName(), maxPoints, bonusPoints, 0.0, null,
                    UserStoryProgressStatus.NOT_STARTED, participationId, null);
        }

        Result result = countingResult.get();
        double score = result.getScore() != null ? result.getScore() : 0.0;
        // Rounded per user story rather than only on the total, so the rows the student sees add up to the total they see
        double achievedPoints = roundScoreSpecifiedByCourseSettings(score * SCORE_NORMALIZATION_VALUE * maxPoints, course);
        UserStoryProgressStatus status = score >= 100.0 ? UserStoryProgressStatus.COMPLETED : UserStoryProgressStatus.IN_PROGRESS;

        return new UserStoryProgressDTO(userStoryExercise.getId(), userStoryExercise.getTitle(), userStoryExercise.getShortName(), maxPoints, bonusPoints, achievedPoints, score,
                status, participationId, result.getCompletionDate());
    }

    /**
     * Finds the result that decides what the user story pays out.
     * <p>
     * Mirrors {@code CourseScoreCalculationService#getResultForParticipation} - the latest rated result, and once a due date is
     * set the latest one completed before it - so that the progress overview never promises points the course score does not
     * award. It differs in two ways, both of which the course score does not need: unpublished manual results are skipped, since
     * the student must not see an assessment before it is released, and "no counting result" stays distinguishable from "a
     * counting result worth 0 points".
     *
     * @param participation        the student's participation in the user story, or null if they have not started it
     * @param userStoryExercise    the user story the participation belongs to, which supplies the due date
     * @param manualResultsVisible whether manual assessments of this Milestone are published to students already
     * @return the result the user story is scored from, or empty if there is none
     */
    private Optional<Result> findCountingResult(@Nullable ProgrammingExerciseStudentParticipation participation, UserStoryExercise userStoryExercise,
            boolean manualResultsVisible) {
        if (participation == null) {
            return Optional.empty();
        }
        List<Result> ratedResults = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).filter(Objects::nonNull)
                .filter(result -> result.isRated() && result.getCompletionDate() != null).filter(result -> manualResultsVisible || !result.isManual())
                .sorted(Comparator.comparing(Result::getCompletionDate).reversed()).toList();

        if (ratedResults.isEmpty()) {
            return Optional.empty();
        }
        // Read off the user story that was passed in rather than off participation.getExercise(), which is a lazy proxy here; a
        // user story inherits its due date from the parent Milestone (see UserStoryExercise)
        ZonedDateTime dueDate = participation.getIndividualDueDate() != null ? participation.getIndividualDueDate() : userStoryExercise.getDueDate();
        if (dueDate == null) {
            return Optional.of(ratedResults.getFirst());
        }
        return ratedResults.stream().filter(result -> result.getCompletionDate().isBefore(dueDate)).findFirst();
    }
}
