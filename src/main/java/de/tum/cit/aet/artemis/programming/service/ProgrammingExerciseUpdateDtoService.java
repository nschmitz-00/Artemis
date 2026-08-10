package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.AuxiliaryRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;

/**
 * Applies an {@link UpdateProgrammingExerciseDTO} onto an already loaded {@link ProgrammingExercise}.
 * <p>
 * Update endpoints must never persist a client-deserialized exercise entity: {@link ProgrammingExercise} and its
 * superclass declare 16 associations with {@code orphanRemoval = true} (student participations, teams, attachments,
 * test cases, grading criteria, ...), so any collection the client omits would be deleted on merge. Deserializing into
 * this DTO and applying it onto the managed entity keeps every one of those associations untouched.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseUpdateDtoService {

    private static final String ENTITY_NAME = "programmingExercise";

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public ProgrammingExerciseUpdateDtoService(CompetencyExerciseLinkService competencyExerciseLinkService) {
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * Updates the existing ProgrammingExercise entity with values from the DTO.
     * This includes updating competency links using the proper mechanism.
     *
     * @param dto      the DTO containing updated values
     * @param exercise the existing exercise entity to update
     * @return the updated exercise entity
     */
    public ProgrammingExercise applyTo(UpdateProgrammingExerciseDTO dto, ProgrammingExercise exercise) {
        if (dto == null) {
            throw new BadRequestAlertException("No programming exercise was provided.", ENTITY_NAME, "isNull");
        }

        // Update base exercise fields
        exercise.setTitle(dto.title());
        exercise.validateTitle();
        exercise.setShortName(dto.shortName());

        // The problem statement is owned by the collaborative (Yjs) editor and its dedicated PATCH endpoint, not by this metadata
        // update. A blank or absent value here means the editor has not finished its initial sync yet (e.g. the user saved a
        // category change on a slow connection before the statement loaded), so we keep the persisted statement instead of wiping
        // it. See issue #13046.
        if (dto.problemStatement() != null && !dto.problemStatement().isBlank()) {
            exercise.setProblemStatement(dto.problemStatement());
        }

        exercise.setChannelName(dto.channelName());
        exercise.setCategories(dto.categories());
        exercise.setDifficulty(dto.difficulty());

        exercise.setMaxPoints(dto.maxPoints());
        exercise.setBonusPoints(dto.bonusPoints());
        exercise.setIncludedInOverallScore(dto.includedInOverallScore());

        exercise.setReleaseDate(dto.releaseDate());
        exercise.setStartDate(dto.startDate());
        exercise.setDueDate(dto.dueDate());
        exercise.setAssessmentDueDate(dto.assessmentDueDate());
        exercise.setAssessmentType(dto.assessmentType());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());

        // Only set boolean values if they are explicitly provided (not null)
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.allowFeedbackRequests() != null) {
            exercise.setAllowFeedbackRequests(dto.allowFeedbackRequests());
        }
        if (dto.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(dto.presentationScoreEnabled());
        }
        if (dto.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(dto.secondCorrectionEnabled());
        }

        exercise.setFeedbackSuggestionModule(dto.feedbackSuggestionModule());
        exercise.setGradingInstructions(dto.gradingInstructions());

        // Update programming exercise specific fields
        if (dto.allowOnlineEditor() != null) {
            exercise.setAllowOnlineEditor(dto.allowOnlineEditor());
        }
        if (dto.allowOfflineIde() != null) {
            exercise.setAllowOfflineIde(dto.allowOfflineIde());
        }
        exercise.setAllowOnlineIde(dto.allowOnlineIde());

        if (dto.maxStaticCodeAnalysisPenalty() != null) {
            exercise.setMaxStaticCodeAnalysisPenalty(dto.maxStaticCodeAnalysisPenalty());
        }

        exercise.setShowTestNamesToStudents(dto.showTestNamesToStudents());
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(dto.buildAndTestStudentSubmissionsAfterDueDate());

        if (dto.testCasesChanged() != null) {
            exercise.setTestCasesChanged(dto.testCasesChanged());
        }

        exercise.setSubmissionPolicy(dto.submissionPolicy());
        exercise.setProjectType(dto.projectType());
        exercise.setReleaseTestsWithExampleSolution(dto.releaseTestsWithExampleSolution());

        // Update auxiliary repositories
        if (dto.auxiliaryRepositories() != null) {
            List<AuxiliaryRepository> auxRepos = dto.auxiliaryRepositories().stream().map(AuxiliaryRepositoryDTO::toEntity).toList();
            exercise.setAuxiliaryRepositories(new ArrayList<>(auxRepos));
        }

        // Update build config
        updateBuildConfig(dto.buildConfig(), exercise.getBuildConfig());

        // Update grading criteria
        updateGradingCriteria(dto, exercise);

        // Update competency links using the proper mechanism
        competencyExerciseLinkService.updateCompetencyLinks(dto, exercise);

        return exercise;
    }

    /**
     * Updates the build config entity with values from the DTO.
     *
     * @param dto         the DTO containing updated build config values
     * @param buildConfig the existing build config entity to update
     */
    private void updateBuildConfig(UpdateProgrammingExerciseBuildConfigDTO dto, ProgrammingExerciseBuildConfig buildConfig) {
        if (dto == null || buildConfig == null) {
            return;
        }

        if (dto.sequentialTestRuns() != null) {
            buildConfig.setSequentialTestRuns(dto.sequentialTestRuns());
        }
        // Note: branch is preserved from original (immutable during update)
        if (dto.buildPlanConfiguration() != null) {
            buildConfig.setBuildPlanConfiguration(dto.buildPlanConfiguration());
        }
        buildConfig.setBuildScript(null);
        buildConfig.setCheckoutSolutionRepository(dto.checkoutSolutionRepository());
        buildConfig.setTestCheckoutPath(dto.testCheckoutPath());
        buildConfig.setAssignmentCheckoutPath(dto.assignmentCheckoutPath());
        buildConfig.setSolutionCheckoutPath(dto.solutionCheckoutPath());
        buildConfig.setTimeoutSeconds(dto.timeoutSeconds());
        buildConfig.setDockerFlags(dto.dockerFlags());
        buildConfig.setTheiaImage(dto.theiaImage());
        buildConfig.setAllowBranching(dto.allowBranching());
        buildConfig.setBranchRegex(dto.branchRegex());
    }

    /**
     * Updates grading criteria from the DTO.
     *
     * @param dto      the DTO containing grading criteria
     * @param exercise the exercise to update
     */
    private void updateGradingCriteria(UpdateProgrammingExerciseDTO dto, ProgrammingExercise exercise) {
        if (dto.gradingCriteria() == null || dto.gradingCriteria().isEmpty()) {
            Set<GradingCriterion> criteria = exercise.ensureGradingCriteriaSet();
            criteria.clear();
            return;
        }

        Set<GradingCriterion> managedCriteria = exercise.ensureGradingCriteriaSet();

        // Preserve existing criteria by matching on ID to avoid dangling Feedback.gradingInstruction references
        Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(gc -> gc.getId() != null)
                .collect(Collectors.toMap(GradingCriterion::getId, gc -> gc, (a, b) -> a));

        Set<GradingCriterion> updated = dto.gradingCriteria().stream().map(gcDto -> {
            GradingCriterion criterion = (gcDto.id() != null) ? existingById.get(gcDto.id()) : null;
            if (criterion == null) {
                criterion = gcDto.toEntity();
                criterion.setExercise(exercise);
            }
            else {
                gcDto.applyTo(criterion);
            }
            return criterion;
        }).collect(Collectors.toSet());

        managedCriteria.clear();
        managedCriteria.addAll(updated);
    }
}
