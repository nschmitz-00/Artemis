import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * A UserStoryExercise belongs to exactly one MilestoneExercise. It has its own title, short name, max points, and
 * problem statement, but inherits all dates, repositories, and assessment settings from its parent Milestone. It extends ProgrammingExercise
 * (rather than Exercise directly) because the server-side entity does too - all repository/build-config fields are
 * delegated to the parent Milestone rather than owned locally, see UserStoryExercise.java (server).
 */
export class UserStoryExercise extends ProgrammingExercise {
    /**
     * The parent MilestoneExercise this UserStory belongs to. All dates, repositories, and assessment settings are inherited from it.
     */
    public milestoneExercise?: MilestoneExercise;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(course, exerciseGroup);
        this.type = ExerciseType.USER_STORY;
    }
}
