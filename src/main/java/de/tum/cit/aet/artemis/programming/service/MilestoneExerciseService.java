package de.tum.cit.aet.artemis.programming.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.MilestoneTestCaseCoverageDTO;
import de.tum.cit.aet.artemis.programming.dto.MilestoneTestCaseIssueDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryReferenceDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryTestCaseReferenceDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/**
 * Thin orchestration wrapper around {@link ProgrammingExerciseCreationUpdateService} for {@link MilestoneExercise}s.
 * <p>
 * A MilestoneExercise IS a ProgrammingExercise (see {@link MilestoneExercise}), so the entire repository/build-plan/channel
 * creation pipeline is reused verbatim - this class only adapts return types and centralizes the "maxPoints is derived from
 * UserStory children, not client-settable" rule.
 */
@Profile(Constants.PROFILE_CORE)
@Lazy
@Service
public class MilestoneExerciseService {

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    public MilestoneExerciseService(ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
    }

    /**
     * Sets up a new MilestoneExercise, including its three repositories and build plan - identical to setting up a
     * regular ProgrammingExercise since MilestoneExercise reuses that entire class/pipeline.
     *
     * @param milestoneExercise the MilestoneExercise to set up (with no UserStoryExercise children yet)
     * @return the persisted MilestoneExercise
     * @throws GitAPIException if the repositories could not be set up
     * @throws IOException     if the exercise template files could not be read
     */
    public MilestoneExercise createMilestoneExercise(MilestoneExercise milestoneExercise) throws GitAPIException, IOException {
        return createMilestoneExercise(milestoneExercise, null);
    }

    /**
     * Sets up a new MilestoneExercise that works on the template repository of an existing Milestone instead of getting one of
     * its own.
     * <p>
     * This is how a course runs several Milestones over one continuously growing codebase. Shared is what carries that
     * codebase: the template repository, and through it every student's repository, in which they keep working across all
     * Milestones of the chain (see {@code ParticipationService#startExercise}). Not shared are the test and solution
     * repositories - consecutive Milestones pose different problems, so each one is graded by its own tests and documented by
     * its own reference solution. The new solution repository is created as a copy of the picked Milestone's, so it continues
     * where that one left off.
     * <p>
     * Two different Milestones are involved, deliberately. {@code repositorySource} is the one the instructor picked: its
     * solution repository is copied and its codebase settings are taken over. The stored link, however, is flattened to that
     * Milestone's {@link MilestoneExercise#getRepositoryOwner()} - the Milestone that actually owns the template repository -
     * so a Milestone never points at another linked one and "every Milestone on this repository" stays a single query.
     *
     * @param milestoneExercise the MilestoneExercise to set up (with no UserStoryExercise children yet)
     * @param repositorySource  the Milestone whose template repository should be used and whose solution is copied, or null to
     *                              provision new repositories
     * @return the persisted MilestoneExercise
     * @throws GitAPIException if the repositories could not be set up
     * @throws IOException     if the exercise template files could not be read
     */
    public MilestoneExercise createMilestoneExercise(MilestoneExercise milestoneExercise, @Nullable MilestoneExercise repositorySource) throws GitAPIException, IOException {
        // Both point totals are derived from the (currently empty) UserStoryExercise children, never client-settable.
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.setBonusPoints(0.0);

        if (repositorySource == null) {
            milestoneExercise.setRepositorySourceMilestone(null);
            return (MilestoneExercise) programmingExerciseCreationUpdateService.createProgrammingExercise(milestoneExercise, false);
        }

        milestoneExercise.setRepositorySourceMilestone(repositorySource.getRepositoryOwner());
        adoptCodebaseSettings(milestoneExercise, repositorySource);
        return (MilestoneExercise) programmingExerciseCreationUpdateService.createProgrammingExerciseReusingRepositories(milestoneExercise, repositorySource);
    }

    /**
     * Takes over everything about the shared codebase from the Milestone this one continues, overwriting whatever the client
     * sent. Both work on the same template repository and the same student repositories, so a differing language, project type
     * or package name could only ever produce builds that do not match the code.
     *
     * @param milestoneExercise the Milestone being created
     * @param repositorySource  the Milestone whose codebase is continued, with its build config loaded
     */
    private void adoptCodebaseSettings(MilestoneExercise milestoneExercise, MilestoneExercise repositorySource) {
        milestoneExercise.setProgrammingLanguage(repositorySource.getProgrammingLanguage());
        milestoneExercise.setProjectType(repositorySource.getProjectType());
        milestoneExercise.setPackageName(repositorySource.getPackageName());
        milestoneExercise.setStaticCodeAnalysisEnabled(repositorySource.isStaticCodeAnalysisEnabled());
        milestoneExercise.setMaxStaticCodeAnalysisPenalty(repositorySource.getMaxStaticCodeAnalysisPenalty());

        // A build config of its own, carrying the source's values as a starting point: the config is a per-exercise 1:1
        // association, and this Milestone builds its own test repository, so the instructor may well adjust it afterwards
        ProgrammingExerciseBuildConfig sourceBuildConfig = repositorySource.getBuildConfig();
        if (sourceBuildConfig != null) {
            milestoneExercise.setBuildConfig(new ProgrammingExerciseBuildConfig(sourceBuildConfig));
        }
    }

    /**
     * Updates an existing MilestoneExercise's non-derived fields (dates, channel, categories, build configuration, ...).
     * maxPoints is left untouched here; it is only ever changed via {@link MilestoneExercise#recalculateDerivedPoints()} as a
     * side effect of adding/updating/removing UserStoryExercise children.
     *
     * @param updatedMilestoneExercise the MilestoneExercise with updated fields
     * @param notificationText         optional text to notify students about the update
     * @param originalCompetencyIds    the competency ids that were linked before the update
     * @return the persisted, updated MilestoneExercise
     * @throws JsonProcessingException if the build plan configuration could not be serialized
     */
    public MilestoneExercise updateMilestoneExercise(MilestoneExercise updatedMilestoneExercise, String notificationText, Set<Long> originalCompetencyIds)
            throws JsonProcessingException {
        ProgrammingExercise updated = programmingExerciseCreationUpdateService.updateProgrammingExercise(updatedMilestoneExercise, notificationText, originalCompetencyIds, null,
                null, null, null, null);
        return (MilestoneExercise) updated;
    }

    /**
     * Checks how well the UserStoryExercises of a MilestoneExercise partition the Milestone's active test cases.
     * <p>
     * Only active test cases are considered, because only those are ever graded (see
     * {@link ProgrammingExerciseGradingService#calculateScoreForResult}). A test case referenced by no UserStory is unreachable
     * for students; one referenced by several UserStories pays out several times. Several tasks of the same UserStory referencing
     * the same test case is not a duplicate - the repository query returns distinct (test case, UserStory) pairs.
     *
     * @param milestoneExerciseId the id of the MilestoneExercise whose test cases should be checked
     * @return the orphan and duplicate test cases, each sorted by test name, both empty if the partition is exact
     */
    public MilestoneTestCaseCoverageDTO analyseTestCaseCoverage(long milestoneExerciseId) {
        Map<Long, List<UserStoryReferenceDTO>> userStoriesByTestCaseId = programmingExerciseTaskRepository.findUserStoryTestCaseReferencesByMilestoneExerciseId(milestoneExerciseId)
                .stream().collect(Collectors.groupingBy(UserStoryTestCaseReferenceDTO::testCaseId, Collectors.mapping(
                        reference -> new UserStoryReferenceDTO(reference.userStoryExerciseId(), reference.userStoryExerciseTitle()), Collectors.toCollection(ArrayList::new))));

        List<MilestoneTestCaseIssueDTO> orphanTestCases = new ArrayList<>();
        List<MilestoneTestCaseIssueDTO> duplicateTestCases = new ArrayList<>();
        for (ProgrammingExerciseTestCase testCase : programmingExerciseTestCaseRepository.findByExerciseId(milestoneExerciseId)) {
            if (!Boolean.TRUE.equals(testCase.isActive())) {
                continue;
            }
            List<UserStoryReferenceDTO> referencingUserStories = userStoriesByTestCaseId.getOrDefault(testCase.getId(), List.of());
            if (referencingUserStories.isEmpty()) {
                orphanTestCases.add(new MilestoneTestCaseIssueDTO(testCase.getId(), testCase.getTestName(), List.of()));
            }
            else if (referencingUserStories.size() > 1) {
                List<UserStoryReferenceDTO> sortedUserStories = referencingUserStories.stream()
                        .sorted(Comparator.comparing(UserStoryReferenceDTO::title, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
                duplicateTestCases.add(new MilestoneTestCaseIssueDTO(testCase.getId(), testCase.getTestName(), sortedUserStories));
            }
        }

        Comparator<MilestoneTestCaseIssueDTO> byTestName = Comparator.comparing(MilestoneTestCaseIssueDTO::testName, Comparator.nullsLast(Comparator.naturalOrder()));
        orphanTestCases.sort(byTestName);
        duplicateTestCases.sort(byTestName);
        return new MilestoneTestCaseCoverageDTO(orphanTestCases, duplicateTestCases);
    }
}
