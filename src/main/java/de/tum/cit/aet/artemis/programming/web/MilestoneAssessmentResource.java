package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.dto.UserStoryAssessmentDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryManualResultDTO;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.MilestoneAssessmentService;

/**
 * REST controller for assessing a {@link MilestoneExercise} submission across its user stories.
 * <p>
 * A Milestone is assessed once per student: the tutor works through the one submission and writes each piece of feedback for
 * the user story it belongs to, and these endpoints distribute it over the user story results (see
 * {@link MilestoneAssessmentService}). Assessing a user story on its own - through
 * {@link ProgrammingAssessmentResource} - is possible but not what the client offers, since every user story of a Milestone is
 * graded from the same submission.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class MilestoneAssessmentResource {

    private static final String ENTITY_NAME = "milestoneAssessment";

    private static final Logger log = LoggerFactory.getLogger(MilestoneAssessmentResource.class);

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final MilestoneExerciseRepository milestoneExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final MilestoneAssessmentService milestoneAssessmentService;

    public MilestoneAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, MilestoneExerciseRepository milestoneExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, MilestoneAssessmentService milestoneAssessmentService) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.milestoneExerciseRepository = milestoneExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.milestoneAssessmentService = milestoneAssessmentService;
    }

    /**
     * GET /milestone-exercises/{milestoneExerciseId}/participations/{participationId}/user-story-assessments : Gets the user
     * stories of a Milestone submission together with their reachable points and their latest result, so that the tutor can
     * assess all of them in one session.
     *
     * @param milestoneExerciseId the id of the MilestoneExercise being assessed
     * @param participationId     the id of the student's participation in that Milestone
     * @return the ResponseEntity with status 200 (OK) and one entry per user story the student participates in
     */
    @GetMapping("milestone-exercises/{milestoneExerciseId}/participations/{participationId}/user-story-assessments")
    @EnforceAtLeastTutor
    public ResponseEntity<List<UserStoryAssessmentDTO>> getUserStoryAssessments(@PathVariable long milestoneExerciseId, @PathVariable long participationId) {
        log.debug("REST request to get the user story assessments of participation {} of MilestoneExercise {}", participationId, milestoneExerciseId);
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findByIdElseThrow(milestoneExerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, milestoneExercise, user);

        ProgrammingExerciseStudentParticipation participation = findMilestoneParticipationElseThrow(milestoneExercise, participationId);
        return ResponseEntity.ok(milestoneAssessmentService.getUserStoryAssessments(milestoneExercise, participation));
    }

    /**
     * PUT /milestone-exercises/{milestoneExerciseId}/participations/{participationId}/manual-results : Saves - and, if
     * requested, submits - the manual results a tutor authored for the user stories of one Milestone submission.
     * <p>
     * The tutor assesses the Milestone submission as a whole, but its user stories are what carries the points, so each result
     * is stored on the participation of the user story it was written for.
     *
     * @param milestoneExerciseId the id of the MilestoneExercise being assessed
     * @param participationId     the id of the student's participation in that Milestone
     * @param submit              whether the assessments should be submitted rather than only saved
     * @param userStoryResults    the manual results, one per user story the tutor touched
     * @return the ResponseEntity with status 200 (OK) and the saved results
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("milestone-exercises/{milestoneExerciseId}/participations/{participationId}/manual-results")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<List<Result>> saveMilestoneAssessment(@PathVariable long milestoneExerciseId, @PathVariable long participationId,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody List<UserStoryManualResultDTO> userStoryResults) {
        log.debug("REST request to save the assessment of participation {} of MilestoneExercise {}", participationId, milestoneExerciseId);
        MilestoneExercise milestoneExercise = milestoneExerciseRepository.findByIdElseThrow(milestoneExerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, milestoneExercise, user);

        // The assessment settings live on the Milestone and are inherited by every user story (see UserStoryExercise), so this
        // one check covers all of them
        if (!milestoneExercise.areManualResultsAllowed()) {
            throw new AccessForbiddenException("Creating manual results is disabled for this exercise!");
        }
        if (userStoryResults.isEmpty()) {
            throw new BadRequestAlertException("An assessment has to contain at least one user story result", ENTITY_NAME, "noUserStoryResults");
        }
        userStoryResults.forEach(userStoryResult -> validateResult(userStoryResult.result()));

        ProgrammingExerciseStudentParticipation participation = findMilestoneParticipationElseThrow(milestoneExercise, participationId);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(milestoneExercise, user);
        List<Result> savedResults = milestoneAssessmentService.saveOrSubmitUserStoryAssessments(milestoneExercise, participation, userStoryResults, submit, user,
                isAtLeastInstructor);

        savedResults.forEach(result -> {
            // remove information about the student for tutors to ensure double-blind assessment
            if (!isAtLeastInstructor) {
                result.getSubmission().getParticipation().filterSensitiveInformation();
            }
            // Not needed in the client
            result.getSubmission().getParticipation().setExercise(null);
        });
        return ResponseEntity.ok(savedResults);
    }

    private ProgrammingExerciseStudentParticipation findMilestoneParticipationElseThrow(MilestoneExercise milestoneExercise, long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        if (!milestoneExercise.getId().equals(participation.getExercise().getId())) {
            throw new BadRequestAlertException("The participation does not belong to the milestone exercise", ENTITY_NAME, "participationExerciseMismatch");
        }
        return participation;
    }

    /**
     * Runs the same checks on a submitted result that {@link ProgrammingAssessmentResource#saveProgrammingAssessment} runs, so
     * that assessing user stories through the Milestone cannot store results that assessing them one by one would reject.
     *
     * @param result the manual result to validate
     */
    private void validateResult(Result result) {
        if (result == null) {
            throw new BadRequestAlertException("A user story assessment must contain a result", ENTITY_NAME, "resultNull");
        }
        if (!result.isRated()) {
            throw new BadRequestAlertException("Result is not rated", ENTITY_NAME, "resultNotRated");
        }
        if (result.getScore() == null) {
            throw new BadRequestAlertException("Score is required.", ENTITY_NAME, "scoreNull");
        }
        if (result.getScore() < 100 && result.isSuccessful()) {
            throw new BadRequestAlertException("Only result with score 100% can be successful.", ENTITY_NAME, "scoreAndSuccessfulNotMatching");
        }
        // Unreferenced feedback needs a detail text unless it is associated with a grading instruction, which carries one
        if (result.getFeedbacks().stream()
                .anyMatch(feedback -> feedback.getType() == FeedbackType.MANUAL_UNREFERENCED && feedback.getGradingInstruction() == null && feedback.getDetailText() == null)) {
            throw new BadRequestAlertException("In case tutor feedback is present, a feedback detail text is mandatory.", ENTITY_NAME, "feedbackDetailTextNull");
        }
        if (result.getFeedbacks().stream().anyMatch(feedback -> feedback.getCredits() == null)) {
            throw new BadRequestAlertException("In case feedback is present, a feedback must contain points.", ENTITY_NAME, "feedbackCreditsNull");
        }
    }
}
