package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/**
 * A UserStoryExercise belongs to exactly one {@link MilestoneExercise}. It has its own title, short name,
 * max points, and problem statement, but inherits all dates, repositories, and assessment settings from its parent Milestone.
 * <p>
 * It extends {@link ProgrammingExercise} (rather than {@link de.tum.cit.aet.artemis.exercise.domain.Exercise} directly) so that
 * it can participate unchanged in the existing programming-exercise participation/grading/CI pipeline (which is typed against
 * {@link ProgrammingExercise} / {@link ProgrammingExerciseParticipation} throughout) - all repo- and build-config-related state is
 * delegated to the parent Milestone rather than owned locally.
 */
@Entity
@DiscriminatorValue(value = "US")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserStoryExercise extends ProgrammingExercise {

    // EAGER (not LAZY): every date/language/build-config getter on this class delegates straight through this
    // association (see below), and the many generic, pre-existing Exercise-fetching queries across the codebase
    // (student dashboard, course overview, statistics, ...) have no reason to know they need to join-fetch it for a
    // UserStoryExercise row. A LAZY proxy accessed after the request's Hibernate session has closed (e.g. during JSON
    // serialization) throws LazyInitializationException, which truncates the HTTP response mid-stream. EAGER makes
    // this association safe to dereference unconditionally, at the cost of one extra join/select per UserStoryExercise
    // row loaded - bounded and cheap given the low expected cardinality of user stories per milestone.
    // Both "userStoryExercises" (which would re-embed this same UserStoryExercise) and "course"/"exerciseGroup"
    // (which would re-embed the Milestone's course, whose #exercises list contains this UserStoryExercise again)
    // must be cut here, or Jackson recurses forever: milestoneExercise -> course -> exercises -> (this) -> milestoneExercise -> ...
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "milestone_exercise_id")
    @JsonIgnoreProperties(value = { "userStoryExercises", "course", "exerciseGroup" }, allowSetters = true)
    private MilestoneExercise milestoneExercise;

    @Override
    public String getType() {
        return "user-story";
    }

    @Override
    public ExerciseType getExerciseType() {
        return ExerciseType.USER_STORY;
    }

    public MilestoneExercise getMilestoneExercise() {
        return milestoneExercise;
    }

    public void setMilestoneExercise(MilestoneExercise milestoneExercise) {
        this.milestoneExercise = milestoneExercise;
    }

    // --- All dates are inherited from the parent Milestone; the columns on this row are never populated. ---

    @Override
    @Nullable
    public ZonedDateTime getReleaseDate() {
        return milestoneExercise != null ? milestoneExercise.getReleaseDate() : null;
    }

    @Override
    @Nullable
    public ZonedDateTime getStartDate() {
        return milestoneExercise != null ? milestoneExercise.getStartDate() : null;
    }

    @Override
    @Nullable
    public ZonedDateTime getDueDate() {
        return milestoneExercise != null ? milestoneExercise.getDueDate() : null;
    }

    @Override
    @Nullable
    public ZonedDateTime getAssessmentDueDate() {
        return milestoneExercise != null ? milestoneExercise.getAssessmentDueDate() : null;
    }

    @Override
    @Nullable
    public ZonedDateTime getExampleSolutionPublicationDate() {
        return milestoneExercise != null ? milestoneExercise.getExampleSolutionPublicationDate() : null;
    }

    // --- Assessment settings are configured once on the parent Milestone and inherited by all of its user stories. ---
    //
    // Rationale: a Milestone's user stories are graded from one and the same submission (a single push produces one Result per
    // user story, see LocalCIResultProcessingService), so per-user-story assessment settings could not be honoured anyway - one
    // build cannot be simultaneously automatically and semi-automatically assessed, nor be past one user story's assessment due
    // date but not another's.
    //
    // Note that {@link #getAssessmentDueDate()} is part of this group as well; it is declared with the other dates above.
    //
    // Structured grading criteria (Exercise#getGradingCriteria) are deliberately NOT delegated yet: they are only consumed by
    // manual assessment, they are a cascading collection rather than a scalar, and the assessment code fetches them by exercise
    // id. They follow once manual assessment of user stories exists.

    @Override
    public AssessmentType getAssessmentType() {
        return milestoneExercise != null ? milestoneExercise.getAssessmentType() : super.getAssessmentType();
    }

    @Override
    public boolean getAllowComplaintsForAutomaticAssessments() {
        return milestoneExercise != null ? milestoneExercise.getAllowComplaintsForAutomaticAssessments() : super.getAllowComplaintsForAutomaticAssessments();
    }

    @Override
    public boolean getAllowFeedbackRequests() {
        return milestoneExercise != null ? milestoneExercise.getAllowFeedbackRequests() : super.getAllowFeedbackRequests();
    }

    @Override
    public String getFeedbackSuggestionModule() {
        return milestoneExercise != null ? milestoneExercise.getFeedbackSuggestionModule() : super.getFeedbackSuggestionModule();
    }

    @Override
    public boolean getSecondCorrectionEnabled() {
        return milestoneExercise != null ? milestoneExercise.getSecondCorrectionEnabled() : super.getSecondCorrectionEnabled();
    }

    @Override
    public Boolean getPresentationScoreEnabled() {
        return milestoneExercise != null ? milestoneExercise.getPresentationScoreEnabled() : super.getPresentationScoreEnabled();
    }

    @Override
    public IncludedInOverallScore getIncludedInOverallScore() {
        return milestoneExercise != null ? milestoneExercise.getIncludedInOverallScore() : super.getIncludedInOverallScore();
    }

    @Override
    public String getGradingInstructions() {
        return milestoneExercise != null ? milestoneExercise.getGradingInstructions() : super.getGradingInstructions();
    }

    // --- Repositories and build configuration are inherited from the parent Milestone; this row owns none of its own. ---

    @Override
    public String getTemplateRepositoryUri() {
        return milestoneExercise != null ? milestoneExercise.getTemplateRepositoryUri() : null;
    }

    @Override
    public String getSolutionRepositoryUri() {
        return milestoneExercise != null ? milestoneExercise.getSolutionRepositoryUri() : null;
    }

    @Override
    public String getTestRepositoryUri() {
        return milestoneExercise != null ? milestoneExercise.getTestRepositoryUri() : null;
    }

    @Override
    public ProgrammingExerciseBuildConfig getBuildConfig() {
        return milestoneExercise != null ? milestoneExercise.getBuildConfig() : null;
    }

    @Override
    public ProgrammingLanguage getProgrammingLanguage() {
        return milestoneExercise != null ? milestoneExercise.getProgrammingLanguage() : null;
    }

    @Override
    @Nullable
    public ProjectType getProjectType() {
        return milestoneExercise != null ? milestoneExercise.getProjectType() : null;
    }

    @Override
    public String getPackageName() {
        return milestoneExercise != null ? milestoneExercise.getPackageName() : null;
    }

    @Override
    public Boolean isStaticCodeAnalysisEnabled() {
        return milestoneExercise != null ? milestoneExercise.isStaticCodeAnalysisEnabled() : null;
    }

    @Override
    public String getProjectKey() {
        return milestoneExercise != null ? milestoneExercise.getProjectKey() : super.getProjectKey();
    }
}
