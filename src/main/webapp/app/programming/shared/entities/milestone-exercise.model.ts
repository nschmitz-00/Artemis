import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';

/**
 * A MilestoneExercise is a ProgrammingExercise that additionally groups 0..n UserStoryExercises. It owns the three
 * repositories (template, solution, tests) exactly like a regular ProgrammingExercise; its UserStoryExercise children
 * never have repositories of their own and instead reuse the Milestone's. See MilestoneExercise.java (server).
 */
/**
 * The minimal identification of a UserStoryExercise, used to name it in the test case coverage warnings.
 * See UserStoryReferenceDTO.java (server).
 */
export interface UserStoryReference {
    id: number;
    title?: string;
}

/**
 * One test case of a Milestone that its user stories do not claim exactly once. See MilestoneTestCaseIssueDTO.java (server).
 */
export interface MilestoneTestCaseIssue {
    testCaseId: number;
    testName?: string;
    referencingUserStories: UserStoryReference[];
}

/**
 * How well the user stories of a Milestone partition its active test cases. Grading a user story only ever considers the test
 * cases its problem statement references, so an orphan test case is unreachable for students and a duplicate one pays out
 * several times. Purely informational - the server never rejects a save because of it. See MilestoneTestCaseCoverageDTO.java.
 */
export interface MilestoneTestCaseCoverage {
    orphanTestCases: MilestoneTestCaseIssue[];
    duplicateTestCases: MilestoneTestCaseIssue[];
}

export class MilestoneExercise extends ProgrammingExercise {
    /**
     * The child UserStoryExercises of this Milestone. maxPoints on this Milestone itself is derived (server-computed)
     * as the sum of the maxPoints of these children and must never be set directly by the client.
     */
    public userStoryExercises?: UserStoryExercise[];

    /**
     * How many UserStoryExercises belong to this Milestone. Server-computed and only sent by the course exercise list
     * endpoint, which counts the children instead of shipping them (see MilestoneExercise.java, server). Undefined on
     * the endpoints that return `userStoryExercises` in full - use that array's length there.
     */
    public numberOfUserStoryExercises?: number;

    /**
     * The MilestoneExercise whose repositories this Milestone works on instead of owning any of its own, or undefined if it
     * owns them. Chosen once, at creation, and never editable afterwards - it decides where the repositories students push to
     * live. Always a Milestone that owns its repositories, never itself a linked one. See MilestoneExercise.java (server).
     */
    public repositorySourceMilestone?: MilestoneExercise;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(course, exerciseGroup);
        this.type = ExerciseType.MILESTONE;
        this.userStoryExercises = [];
    }
}
