import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';

/**
 * The effort a participant has reported for a user story exercise, in hours - summed by the server from their task
 * board rather than entered by hand. `estimatedEffort` is 0 (not unset) for a board with no tasks yet; `actualEffort`
 * is unset until at least one task has any logged.
 */
export interface UserStoryEffort {
    estimatedEffort?: number;
    actualEffort?: number;
}

/** How urgent a participant considers a {@link UserStoryTask} they created for themself. */
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

/** The progress of a {@link UserStoryTask}, shifted one step at a time from NEW to IN_PROGRESS to DONE. */
export type TaskState = 'NEW' | 'IN_PROGRESS' | 'DONE';

/** A task a participant created for themself while working on a user story exercise. */
export interface UserStoryTask {
    id?: number;
    title: string;
    description?: string;
    taskPoints: number;
    priority: TaskPriority;
    estimatedEffortHours: number;
    /** The real time logged on the task so far; unset until the participant enters it for the first time. */
    actualEffortHours?: number;
    /** Server-assigned; NEW on creation, and only ever changed through {@link UserStoryTaskService.advanceState}. */
    state?: TaskState;
}

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUri?: string;
    public buildPlanId?: string;
    public branch?: string;

    // helper attribute
    public buildPlanUrl?: string;
    public userIndependentRepositoryUri?: string;
    public vcsAccessToken?: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
