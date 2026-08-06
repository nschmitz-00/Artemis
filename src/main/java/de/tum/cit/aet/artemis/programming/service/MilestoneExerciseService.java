package de.tum.cit.aet.artemis.programming.service;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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

    public MilestoneExerciseService(ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
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
        // maxPoints is derived from the (currently empty) UserStoryExercise children, never client-settable.
        milestoneExercise.setMaxPoints(0.0);
        ProgrammingExercise created = programmingExerciseCreationUpdateService.createProgrammingExercise(milestoneExercise, false);
        return (MilestoneExercise) created;
    }

    /**
     * Updates an existing MilestoneExercise's non-derived fields (dates, channel, categories, build configuration, ...).
     * maxPoints is left untouched here; it is only ever changed via {@link MilestoneExercise#recalculateMaxPoints()} as a
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
}
