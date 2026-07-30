package de.tum.cit.aet.artemis.programming.domain;

import java.net.URI;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;

@Entity
@DiscriminatorValue(value = "PESP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseStudentParticipation extends StudentParticipation implements ProgrammingExerciseParticipation {

    @Size(max = 255)
    @Column(name = "repository_url")
    private String repositoryUri;

    @Column(name = "build_plan_id")
    private String buildPlanId;

    @Column(name = "branch")
    private String branch;

    @Transient
    private final transient FileService fileService = new FileService();

    @Column(name = "explanation_video_path")
    private String explanationVideoPath;

    @Column(name = "explanation_video_upload_date")
    private ZonedDateTime explanationVideoUploadDate;

    public ProgrammingExerciseStudentParticipation() {
        // Default constructor
    }

    public ProgrammingExerciseStudentParticipation(String branch) {
        this.branch = branch;
    }

    @Override
    public String getRepositoryUri() {
        return repositoryUri;
    }

    @Override
    public void setRepositoryUri(String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    public void setRepositoryUri(@NonNull LocalVCRepositoryUri repositoryUri) {
        this.repositoryUri = repositoryUri.getURI().toString();
    }

    @Override
    public String getBuildPlanId() {
        return buildPlanId;
    }

    @Override
    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    /**
     * Getter for the stored default branch of the participation.
     *
     * @return the name of the default branch or null if not yet stored in Artemis
     */
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getExplanationVideoPath() {
        return explanationVideoPath;
    }

    public void setExplanationVideoPath(String explanationVideoPath) {
        this.explanationVideoPath = explanationVideoPath;
    }

    public ZonedDateTime getExplanationVideoUploadDate() {
        return explanationVideoUploadDate;
    }

    public void setExplanationVideoUploadDate(ZonedDateTime explanationVideoUploadDate) {
        this.explanationVideoUploadDate = explanationVideoUploadDate;
    }

    @JsonIgnore
    public boolean hasExplanationVideo() {
        return explanationVideoPath != null;
    }

    /**
     * Deletes the previously stored explanation video (if any) for this participation.
     */
    @PostRemove
    public void onDelete() {
        if (explanationVideoPath != null) {
            var actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(explanationVideoPath), FilePathType.PROGRAMMING_EXPLANATION_VIDEO);
            fileService.schedulePathForDeletion(actualPath, 0);
        }
    }

    @Override
    @JsonIgnore
    // NOTE: this is a helper method to avoid casts in other classes that want to access the underlying exercise
    public ProgrammingExercise getProgrammingExercise() {
        Exercise exercise = getExercise();
        if (exercise instanceof ProgrammingExercise) { // this should always be the case except exercise is null
            return (ProgrammingExercise) exercise;
        }
        else {
            return null;
        }
    }

    @Override
    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        setExercise(programmingExercise);
    }

    @Override
    public String getType() {
        return "programming";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "id=" + getId() + ", repositoryUri='" + getRepositoryUri() + "'" + ", buildPlanId='" + getBuildPlanId() + "'"
                + ", initializationState='" + getInitializationState() + "'" + ", initializationDate='" + getInitializationDate() + "'" + ", individualDueDate="
                + getIndividualDueDate() + "'" + ", presentationScore=" + getPresentationScore() + "}";
    }

}
