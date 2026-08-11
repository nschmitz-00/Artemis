package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UserStoryAssessmentDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryManualResultDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Assesses a {@link MilestoneExercise} submission by distributing the tutor's feedback over its
 * {@link UserStoryExercise} children.
 * <p>
 * A student pushes once to the Milestone's repository and every user story is graded from that same push: the build result is
 * fanned out into one {@link Result} per user story sibling participation (see
 * {@code LocalCIResultProcessingService#processResult}). Manual assessment mirrors that fan-out - the tutor works through the
 * submission once and each piece of feedback is stored on the result of the user story it was written for, because the user
 * stories, not the Milestone, are the graded units (see {@code CourseScoreCalculationService}).
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneAssessmentService {

    private static final String ENTITY_NAME = "milestoneAssessment";

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingAssessmentService programmingAssessmentService;

    public MilestoneAssessmentService(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingAssessmentService programmingAssessmentService) {
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingAssessmentService = programmingAssessmentService;
    }

    /**
     * Collects what the tutor needs to assess every user story of the given Milestone submission: the points each user story is
     * worth, the sibling participation it is graded on, and the result that participation already has.
     *
     * @param milestoneExercise      the Milestone whose user stories are assessed
     * @param milestoneParticipation the student's participation in the Milestone (the one that owns the repository)
     * @return one entry per user story the student participates in, ordered by user story id
     */
    public List<UserStoryAssessmentDTO> getUserStoryAssessments(MilestoneExercise milestoneExercise, ProgrammingExerciseStudentParticipation milestoneParticipation) {
        return findUserStorySiblings(milestoneExercise, milestoneParticipation).stream().sorted(Comparator.comparing(participation -> participation.getExercise().getId()))
                .map(this::toUserStoryAssessment).toList();
    }

    private UserStoryAssessmentDTO toUserStoryAssessment(ProgrammingExerciseStudentParticipation participation) {
        UserStoryExercise userStoryExercise = (UserStoryExercise) participation.getExercise();
        Result latestResult = resultRepository.findLatestResultWithFeedbacksForParticipation(participation.getId(), true).orElse(null);
        return new UserStoryAssessmentDTO(userStoryExercise.getId(), userStoryExercise.getTitle(), userStoryExercise.getShortName(), userStoryExercise.getMaxPoints(),
                userStoryExercise.getBonusPoints(), participation.getId(), latestResult);
    }

    /**
     * Saves - and, if requested, submits - the manual results the tutor authored for the user stories of one Milestone
     * submission. Each result is stored on the sibling participation of the user story it belongs to, so the student's score
     * per user story is the automatic test result plus exactly the feedback written for that user story.
     *
     * @param milestoneExercise      the Milestone that was assessed
     * @param milestoneParticipation the student's participation in the Milestone
     * @param userStoryResults       the manual results per user story, as sent by the client
     * @param submit                 whether the assessments should be submitted rather than only saved
     * @param assessor               the tutor who authored the assessment
     * @param isAtLeastInstructor    whether the assessor is at least an instructor of the course, which decides whether an existing assessment may be overridden
     * @return the saved results, in the order the user stories were sent in
     */
    public List<Result> saveOrSubmitUserStoryAssessments(MilestoneExercise milestoneExercise, ProgrammingExerciseStudentParticipation milestoneParticipation,
            List<UserStoryManualResultDTO> userStoryResults, boolean submit, User assessor, boolean isAtLeastInstructor) {
        Map<Long, ProgrammingExerciseStudentParticipation> siblingsByUserStoryId = findUserStorySiblings(milestoneExercise, milestoneParticipation).stream()
                .collect(Collectors.toMap(participation -> participation.getExercise().getId(), Function.identity()));

        List<Result> savedResults = new ArrayList<>();
        for (UserStoryManualResultDTO userStoryResult : userStoryResults) {
            ProgrammingExerciseStudentParticipation siblingParticipation = siblingsByUserStoryId.get(userStoryResult.userStoryExerciseId());
            if (siblingParticipation == null) {
                throw new BadRequestAlertException("The student does not participate in the user story with id " + userStoryResult.userStoryExerciseId(), ENTITY_NAME,
                        "userStoryParticipationNotFound");
            }
            savedResults.add(saveOrSubmitUserStoryAssessment(siblingParticipation, userStoryResult.result(), submit, assessor, isAtLeastInstructor));
        }
        return savedResults;
    }

    private Result saveOrSubmitUserStoryAssessment(ProgrammingExerciseStudentParticipation siblingParticipation, Result newManualResult, boolean submit, User assessor,
            boolean isAtLeastInstructor) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(siblingParticipation.getId());
        UserStoryExercise userStoryExercise = (UserStoryExercise) siblingParticipation.getExercise();

        // Unlike a plain programming assessment, the tutor never locked this participation individually: they locked the
        // Milestone submission, which is what guards against two tutors assessing the same student at once. The manual result
        // of a user story is therefore created here, on first save, rather than by a lock request of its own.
        Result existingManualResult = findLatestManualResult(participation).orElseGet(() -> lockLatestSubmission(participation));

        // Reloaded before the permission check below, which compares the assessor of the existing result with the current user:
        // the result as it comes from the participation carries a lazy assessor proxy, which never compares equal to the user
        Result reloadedExistingResult = resultRepository
                .findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(existingManualResult.getId());

        if (!programmingAssessmentService.isAllowedToCreateOrOverrideResult(reloadedExistingResult, userStoryExercise, participation, assessor, isAtLeastInstructor)) {
            throw new AccessForbiddenException("The user is not allowed to override the assessment of user story " + userStoryExercise.getId());
        }

        // Prevent tutors from creating a second manual result for the same user story
        newManualResult.setId(existingManualResult.getId());
        newManualResult.setSubmission(reloadedExistingResult.getSubmission());
        clampScoreToReachablePoints(newManualResult, userStoryExercise);

        return programmingAssessmentService.saveAndSubmitManualAssessment(participation, newManualResult, reloadedExistingResult, assessor, submit);
    }

    private Optional<Result> findLatestManualResult(StudentParticipation participation) {
        return participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull).filter(Result::isManual))
                .max(Comparator.comparing(Result::getId));
    }

    private Result lockLatestSubmission(StudentParticipation participation) {
        Optional<Submission> latestSubmission = participation.findLatestSubmission();
        if (latestSubmission.isEmpty()) {
            throw new BadRequestAlertException("The user story participation with id " + participation.getId() + " has no submission to assess", ENTITY_NAME,
                    "noSubmissionToAssess");
        }
        return programmingSubmissionService.lockSubmission(latestSubmission.get(), 0);
    }

    /**
     * Keeps the score the client computed within what the user story can actually pay out. Feedback is authored against the
     * Milestone submission as a whole, so a deduction meant for the whole submission - or a bonus counted twice - could
     * otherwise push a single user story below zero or above its own maximum.
     *
     * @param result            the manual result to bound
     * @param userStoryExercise the user story the result belongs to, which defines the reachable points
     */
    private void clampScoreToReachablePoints(Result result, UserStoryExercise userStoryExercise) {
        double maxPoints = userStoryExercise.getMaxPoints() != null ? userStoryExercise.getMaxPoints() : 0.0;
        double bonusPoints = userStoryExercise.getBonusPoints() != null ? userStoryExercise.getBonusPoints() : 0.0;
        // A user story worth no points at all can only ever be scored as 0 %, since every score is a percentage of its max points
        double maxScore = maxPoints > 0.0 ? (maxPoints + bonusPoints) / maxPoints * 100.0 : 0.0;
        result.setScore(Math.clamp(result.getScore(), 0.0, maxScore));
    }

    /**
     * Finds the participations the student has in the user stories of the given Milestone. They are siblings of the Milestone
     * participation: same student, same repository, one per user story (see {@link UserStoryExercise}).
     *
     * @param milestoneExercise      the Milestone the user stories belong to
     * @param milestoneParticipation the student's participation in the Milestone
     * @return the sibling participations, one per user story the student has started
     */
    private List<ProgrammingExerciseStudentParticipation> findUserStorySiblings(MilestoneExercise milestoneExercise,
            ProgrammingExerciseStudentParticipation milestoneParticipation) {
        User student = milestoneParticipation.getStudent()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Assessing a milestone is only supported for individual participations, but participation " + milestoneParticipation.getId() + " has no student",
                        ENTITY_NAME, "noIndividualParticipation"));
        return programmingExerciseStudentParticipationRepository.findAllUserStorySiblingsByMilestoneIdAndRepositoryUriAndStudentId(milestoneExercise.getId(),
                milestoneParticipation.getRepositoryUri(), student.getId());
    }
}
