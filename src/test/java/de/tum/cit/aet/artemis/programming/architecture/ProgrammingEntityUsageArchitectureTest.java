package de.tum.cit.aet.artemis.programming.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Programming module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class ProgrammingEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".programming";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities
    // 38 -> 46: MilestoneExerciseResource (4) and UserStoryExerciseResource (3) return their exercise entity exactly like the
    // ProgrammingExerciseCreation/Update/Retrieval resources they are modelled on and which make up most of the baseline, and
    // MilestoneAssessmentResource#saveMilestoneAssessment returns the saved Results like ProgrammingAssessmentResource does.
    // Converting them means converting that whole family of endpoints (and their clients) at once, not these eight alone.
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 46;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    // 7 -> 10: UserStoryExerciseResource#createUserStoryExercise/#updateUserStoryExercise and
    // MilestoneExerciseResource#createMilestoneExercise take their exercise entity like
    // ProgrammingExerciseCreationResource#createProgrammingExercise in the baseline does. Note that the Milestone *update*
    // endpoint already takes a DTO - it had to, because persisting a client-deserialized exercise deletes every orphanRemoval
    // collection the payload omits.
    @Override
    protected int getExpectedEntityInputViolations() {
        return 10;
    }

    // TODO: Reduce this to 0 by removing entity references from DTOs
    // 3 -> 5: UserStoryAssessmentDTO and UserStoryManualResultDTO carry a Result, which is what the manual assessment endpoints
    // of ProgrammingAssessmentResource exchange as well - the milestone variants only fan that same payload out per user story.
    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 5;
    }
}
