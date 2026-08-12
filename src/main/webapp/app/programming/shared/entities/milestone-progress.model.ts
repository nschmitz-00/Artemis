import dayjs from 'dayjs/esm';

/**
 * How far a student has come with one user story of a Milestone. See UserStoryProgressStatus.java (server).
 */
export enum UserStoryProgressStatus {
    NOT_STARTED = 'NOT_STARTED',
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
}

/**
 * A student's progress on one user story of a Milestone. See UserStoryProgressDTO.java (server).
 */
export interface UserStoryProgress {
    userStoryExerciseId: number;
    title?: string;
    shortName?: string;
    maxPoints: number;
    bonusPoints: number;
    achievedPoints: number;
    /** The percentage the student reached, or undefined while no result counts for this user story yet. */
    score?: number;
    status: UserStoryProgressStatus;
    participationId?: number;
    latestResultCompletionDate?: dayjs.Dayjs;
}

/**
 * A student's progress over all user stories of one Milestone. A Milestone carries no points of its own - its user stories
 * are the graded units - so the aggregate below is summed over them. See MilestoneProgressDTO.java (server).
 */
export interface MilestoneProgress {
    milestoneExerciseId: number;
    /** Absent - not an empty array - for a Milestone without user stories, since the server serializes with NON_EMPTY. */
    userStories?: UserStoryProgress[];
    achievedPoints: number;
    maxPoints: number;
    completionPercentage: number;
    completedUserStories: number;
    totalUserStories: number;
    /** True while manual assessments exist but are not published yet, so the numbers above can still grow. */
    manualAssessmentPending: boolean;
}
