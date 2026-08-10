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

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(course, exerciseGroup);
        this.type = ExerciseType.MILESTONE;
        this.userStoryExercises = [];
    }
}
