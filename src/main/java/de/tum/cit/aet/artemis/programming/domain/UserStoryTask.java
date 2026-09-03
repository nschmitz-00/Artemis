package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * A small task a participant breaks a {@link UserStoryExercise} down into for themself while working on it, e.g. a
 * personal or team scrum board.
 * <p>
 * Keyed by {@link Participation}: a team has exactly one participation, so its members share one board. The
 * estimated/actual effort a participant reports for the story as a whole ({@code UserStoryEffortDTO}) is summed from
 * this board's tasks rather than stored separately.
 * <p>
 * {@link #orderIndex} is the participant's own manual ordering of their board (drag-and-drop), separate from
 * {@link #getId()}: a new task is appended at the end, and any task may later be reordered by the reorder endpoint.
 */
@Entity
@Table(name = "user_story_task")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserStoryTask extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    @Nullable
    @Column(name = "description")
    private String description;

    @Column(name = "task_points", nullable = false)
    private Integer taskPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TaskPriority priority;

    @Column(name = "estimated_effort_hours", nullable = false)
    private Double estimatedEffortHours;

    /** The real time the participant has logged on the task so far; unset until they enter it for the first time. */
    @Nullable
    @Column(name = "actual_effort_hours")
    private Double actualEffortHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private TaskState state = TaskState.NEW;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @ManyToOne
    @JoinColumn(name = "participation_id", nullable = false)
    private Participation participation;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public Integer getTaskPoints() {
        return taskPoints;
    }

    public void setTaskPoints(Integer taskPoints) {
        this.taskPoints = taskPoints;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public Double getEstimatedEffortHours() {
        return estimatedEffortHours;
    }

    public void setEstimatedEffortHours(Double estimatedEffortHours) {
        this.estimatedEffortHours = estimatedEffortHours;
    }

    @Nullable
    public Double getActualEffortHours() {
        return actualEffortHours;
    }

    public void setActualEffortHours(@Nullable Double actualEffortHours) {
        this.actualEffortHours = actualEffortHours;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = state;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    @Override
    public String toString() {
        return "UserStoryTask{id=" + getId() + ", title=" + title + ", priority=" + priority + ", taskPoints=" + taskPoints + ", estimatedEffortHours=" + estimatedEffortHours
                + ", state=" + state + "}";
    }
}
