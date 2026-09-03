package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.TaskPriority;
import de.tum.cit.aet.artemis.programming.domain.TaskState;
import de.tum.cit.aet.artemis.programming.domain.UserStoryTask;

/**
 * A task a participant created for themself while working on a user story exercise. Used in both directions: read
 * back to the participant, and sent by them to create or update one.
 * <p>
 * {@link #state()} is always {@link TaskState#NEW} on creation regardless of what is sent; an update, however, may set
 * it directly to any state (an edit), separately from the dedicated advance-state endpoint (a one-step shift).
 * <p>
 * {@link #actualEffortHours()} is always {@code null} on creation regardless of what is sent - the participant only
 * starts logging real time once they are working on the task, entered inline on their board rather than at creation.
 *
 * @param id                   the id of the task, {@code null} when creating a new one
 * @param title                a short title for the task
 * @param description          an optional longer description
 * @param taskPoints           the points the participant assigned to the task
 * @param priority             how urgent the participant considers the task
 * @param estimatedEffortHours the effort in hours the participant estimated for the task
 * @param actualEffortHours    the real time in hours the participant has logged so far, or {@code null} if none yet
 * @param state                the task's progress
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryTaskDTO(@Nullable Long id, String title, @Nullable String description, Integer taskPoints, TaskPriority priority, Double estimatedEffortHours,
        @Nullable Double actualEffortHours, TaskState state) {

    public static UserStoryTaskDTO of(UserStoryTask task) {
        return new UserStoryTaskDTO(task.getId(), task.getTitle(), task.getDescription(), task.getTaskPoints(), task.getPriority(), task.getEstimatedEffortHours(),
                task.getActualEffortHours(), task.getState());
    }
}
