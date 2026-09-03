package de.tum.cit.aet.artemis.programming.domain;

/**
 * The progress of a {@link UserStoryTask}, shifted one step at a time from {@link #NEW} through {@link #IN_PROGRESS}
 * to {@link #DONE} as the participant works on it.
 */
public enum TaskState {

    NEW, IN_PROGRESS, DONE;

    /**
     * The state a task moves to when its participant advances it, or {@code null} once it is {@link #DONE} - there is
     * nowhere further to shift.
     *
     * @return the next state, or {@code null} if this is {@link #DONE}
     */
    public TaskState next() {
        return switch (this) {
            case NEW -> IN_PROGRESS;
            case IN_PROGRESS -> DONE;
            case DONE -> null;
        };
    }
}
