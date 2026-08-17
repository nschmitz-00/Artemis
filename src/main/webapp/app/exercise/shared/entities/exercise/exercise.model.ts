import { BaseEntity } from 'app/foundation/model/base-entity';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TutorParticipation } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { CompetencyExerciseLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseInfo } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { faBookOpen, faCheckDouble, faFileUpload, faFlagCheckered, faFont, faKeyboard, faProjectDiagram, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';

export enum DifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD',
}

export enum ExerciseMode {
    INDIVIDUAL = 'INDIVIDUAL',
    TEAM = 'TEAM',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export enum ExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload',
    MILESTONE = 'milestone',
    USER_STORY = 'user-story',
}

export type ScoresPerExerciseType = Map<ExerciseType, CourseScores>;

export interface ValidationReason {
    translateKey: string;
    translateValues: { [key: string]: unknown };
}

export interface PlagiarismDetectionConfig {
    continuousPlagiarismControlEnabled?: boolean;
    continuousPlagiarismControlPostDueDateChecksEnabled?: boolean;
    continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod?: number;
    similarityThreshold?: number;
    minimumScore?: number;
    minimumSize?: number;
}

export const DEFAULT_PLAGIARISM_DETECTION_CONFIG: PlagiarismDetectionConfig = {
    continuousPlagiarismControlEnabled: false,
    continuousPlagiarismControlPostDueDateChecksEnabled: false,
    continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
    similarityThreshold: 90,
    minimumSize: 50,
    minimumScore: 0,
};

export const exerciseTypes: ExerciseType[] = [
    ExerciseType.TEXT,
    ExerciseType.MODELING,
    ExerciseType.PROGRAMMING,
    ExerciseType.FILE_UPLOAD,
    ExerciseType.QUIZ,
    ExerciseType.MILESTONE,
    ExerciseType.USER_STORY,
];

/**
 * Whether exercises of this type contribute their own max points and achieved points to aggregated scores.
 *
 * A milestone is a container: its max points are the sum of its user story children's max points, and a build result is
 * graded once against the milestone and once against every child. Counting both sides would double every milestone's
 * contribution, so the children count and the milestone itself does not.
 *
 * IMPORTANT NOTICE: has to stay consistent with ExerciseType#contributesPointsToAggregatedScores in ExerciseType.java
 */
export function contributesPointsToAggregatedScores(type?: ExerciseType): boolean {
    return type !== ExerciseType.MILESTONE;
}

/**
 * The exercise type under which scores of the given type are reported when scores are grouped by exercise type.
 * A user story is programming work performed in the parent milestone's repository, so it is reported under PROGRAMMING;
 * that keeps the per-type sums adding up to the course total.
 *
 * IMPORTANT NOTICE: has to stay consistent with ExerciseType#scoreAggregationBucket in ExerciseType.java
 */
export function scoreAggregationBucket(type?: ExerciseType): ExerciseType | undefined {
    return type === ExerciseType.USER_STORY ? ExerciseType.PROGRAMMING : type;
}

/**
 * The exercise types that can appear as a bucket when scores are grouped by exercise type. Milestones contribute no
 * points at all and user stories are reported under {@link ExerciseType.PROGRAMMING}, so neither is a bucket of its own.
 */
export const scoreAggregationExerciseTypes: ExerciseType[] = exerciseTypes.filter((type) => contributesPointsToAggregatedScores(type) && scoreAggregationBucket(type) === type);

/**
 * Whether exercises of this type are backed by a programming exercise, i.e. by a git repository, test cases, and build
 * results. Milestones and user stories are `ProgrammingExercise` subclasses on the server (MilestoneExercise.java,
 * UserStoryExercise.java), so everything built for programming exercises - the instruction renderer with its task
 * overview, test case status, build result handling - applies to them unchanged.
 *
 * Use this instead of comparing against {@link ExerciseType.PROGRAMMING} whenever the check is about "does this behave
 * like a programming exercise", not about "is this exactly the plain programming exercise type".
 */
export function isProgrammingBasedExerciseType(type?: ExerciseType): boolean {
    return type === ExerciseType.PROGRAMMING || type === ExerciseType.MILESTONE || type === ExerciseType.USER_STORY;
}

/**
 * The exercise type whose UI branch applies to the given type. Milestones and user stories map to
 * {@link ExerciseType.PROGRAMMING} because they need exactly its actions - starting a participation, the code/clone button, the
 * online editor - so a `@switch` over this renders the right branch for them without a case of their own.
 *
 * This is about which UI to show, not about scoring: use {@link scoreAggregationBucket} for the latter, which keeps milestones
 * separate because they contribute no points.
 */
export function uiExerciseTypeBranch(type?: ExerciseType): ExerciseType | undefined {
    return isProgrammingBasedExerciseType(type) ? ExerciseType.PROGRAMMING : type;
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export enum IncludedInOverallScore {
    INCLUDED_COMPLETELY = 'INCLUDED_COMPLETELY',
    INCLUDED_AS_BONUS = 'INCLUDED_AS_BONUS',
    NOT_INCLUDED = 'NOT_INCLUDED',
}

export abstract class Exercise implements BaseEntity {
    public id?: number;
    public problemStatement?: string;
    public gradingInstructions?: string;
    public title?: string;
    public shortName?: string;
    public releaseDate?: dayjs.Dayjs;
    public startDate?: dayjs.Dayjs;
    public dueDate?: dayjs.Dayjs;
    public assessmentDueDate?: dayjs.Dayjs;
    public maxPoints?: number;
    public bonusPoints?: number;
    public assessmentType?: AssessmentType;
    public allowComplaintsForAutomaticAssessments?: boolean;
    public allowFeedbackRequests?: boolean;
    public difficulty?: DifficultyLevel;
    public mode?: ExerciseMode = ExerciseMode.INDIVIDUAL; // default value
    public includedInOverallScore?: IncludedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY; // default value
    public teamAssignmentConfig?: TeamAssignmentConfig;
    public categories?: ExerciseCategory[];
    public type?: ExerciseType;
    public exampleSolutionPublicationDate?: dayjs.Dayjs;

    public teams?: Team[];
    public studentParticipations?: StudentParticipation[];
    public tutorParticipations?: TutorParticipation[];
    public course?: Course;
    public exampleSubmissions?: ExampleSubmission[];
    public attachments?: Attachment[];
    public posts?: Post[];
    public gradingCriteria?: GradingCriterion[];
    public exerciseGroup?: ExerciseGroup;
    public competencyLinks?: CompetencyExerciseLink[];

    public plagiarismDetectionConfig?: PlagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG; // default value

    // transient objects which might not be set
    public numberOfSubmissions?: DueDateStat;
    public totalNumberOfAssessments?: number;
    public numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()]; // Array with number of assessments for each correction round
    public numberOfComplaints?: number;
    public numberOfOpenComplaints?: number;
    public numberOfMoreFeedbackRequests?: number;
    public numberOfOpenMoreFeedbackRequests?: number;
    public studentAssignedTeamId?: number;
    public studentAssignedTeamIdComputed = false;
    public numberOfParticipations?: number;
    public testRunParticipationsExist?: boolean;
    public averageRating?: number;
    public numberOfRatings?: number;
    public channelName?: string;
    public completed?: boolean;

    // helper attributes
    public secondCorrectionEnabled = false;
    public feedbackSuggestionModule?: string;
    public isAtLeastTutor?: boolean;
    public isAtLeastEditor?: boolean;
    public isAtLeastInstructor?: boolean;
    public teamMode?: boolean;
    public assessmentDueDateError?: boolean;
    public dueDateError?: boolean;
    public startDateError?: boolean;
    public exampleSolutionPublicationDateError?: boolean;
    public exampleSolutionPublicationDateWarning?: boolean;
    public loading?: boolean;
    public numberOfParticipationsWithRatedResult?: number;
    public numberOfSuccessfulParticipations?: number;
    public averagePoints?: number;
    public presentationScoreEnabled?: boolean;
    public gradingInstructionFeedbackUsed?: boolean;
    public zipFileForImport?: File;

    protected constructor(type: ExerciseType) {
        this.type = type;
        this.bonusPoints = 0; // default value
        this.isAtLeastTutor = false; // default value
        this.isAtLeastEditor = false; // default value
        this.isAtLeastInstructor = false; // default value
        this.teamMode = false; // default value
        this.assessmentDueDateError = false;
        this.dueDateError = false;
        this.startDateError = false;
        this.exampleSolutionPublicationDateError = false;
        this.presentationScoreEnabled = false; // default value;
        this.allowComplaintsForAutomaticAssessments = false; // default value;
        this.allowFeedbackRequests = false; // default value;
    }

    /**
     * Sanitize exercise attributes.
     * This method should be used before sending an exercise to the server.
     *
     * @param exercise
     */
    public static sanitize<T extends Exercise>(exercise: T): T {
        exercise.title = exercise.title?.trim();
        return exercise;
    }
}

/**
 * Get an icon for the type of the given exercise.
 * @param exerciseType {ExerciseType}
 */
export function getIcon(exerciseType?: ExerciseType): IconProp {
    if (!exerciseType) {
        return faQuestion;
    }

    const icons: Record<string, IconProp> = {
        [ExerciseType.PROGRAMMING]: faKeyboard,
        [ExerciseType.MODELING]: faProjectDiagram,
        [ExerciseType.QUIZ]: faCheckDouble,
        [ExerciseType.TEXT]: faFont,
        [ExerciseType.FILE_UPLOAD]: faFileUpload,
        [ExerciseType.MILESTONE]: faFlagCheckered,
        [ExerciseType.USER_STORY]: faBookOpen,
    };

    return icons[exerciseType] ?? faQuestion;
}

export function getIconTooltip(exerciseType?: ExerciseType): string {
    if (!exerciseType) {
        return '';
    }
    const tooltips = {
        [ExerciseType.PROGRAMMING]: 'artemisApp.exercise.isProgramming',
        [ExerciseType.MODELING]: 'artemisApp.exercise.isModeling',
        [ExerciseType.QUIZ]: 'artemisApp.exercise.isQuiz',
        [ExerciseType.TEXT]: 'artemisApp.exercise.isText',
        [ExerciseType.FILE_UPLOAD]: 'artemisApp.exercise.isFileUpload',
        [ExerciseType.MILESTONE]: 'artemisApp.exercise.isMilestone',
        [ExerciseType.USER_STORY]: 'artemisApp.exercise.isUserStory',
    };

    return tooltips[exerciseType];
}

/**
 * Get the course id for an exercise.
 * The course id is extracted from the course of the exercise if present, if not present (exam mode), it is extracted from the corresponding exam.
 * @param exercise the exercise for which the course id should be extracted
 */
export function getCourseId(exercise: Exercise | undefined): number | undefined {
    return getCourseFromExercise(exercise)?.id;
}

/**
 * Get the course for an exercise.
 * The course is extracted from the course of the exercise if present, if not present (exam mode), it is extracted from the corresponding exam.
 * @param exercise the exercise for which the course should be extracted
 */
export function getCourseFromExercise(exercise: Exercise | undefined): Course | undefined {
    return exercise?.course || exercise?.exerciseGroup?.exam?.course;
}

/**
 * In order to create an ExerciseType enum, we take the ExerciseInfo (which can be fetched from the server) and map it to the ExerciseType
 * @param exerciseInfo the exercise information which is given by the server java class
 * @return ExerciseType or undefined if the exerciseInfo does not match
 */
export function declareExerciseType(exerciseInfo: ExerciseInfo): ExerciseType | undefined {
    switch (exerciseInfo.exerciseType) {
        case 'TextExercise':
            return ExerciseType.TEXT;
        case 'ModelingExercise':
            return ExerciseType.MODELING;
        case 'ProgrammingExercise':
            return ExerciseType.PROGRAMMING;
        case 'FileUploadExercise':
            return ExerciseType.FILE_UPLOAD;
        case 'QuizExercise':
            return ExerciseType.QUIZ;
        case 'MilestoneExercise':
            return ExerciseType.MILESTONE;
        case 'UserStoryExercise':
            return ExerciseType.USER_STORY;
    }
    return undefined;
}

/**
 * Get the url segment for different types of exercises.
 * @param exerciseType The type of the exercise
 * @return The url segment for the exercise type
 */
export function getExerciseUrlSegment(exerciseType?: ExerciseType): string {
    switch (exerciseType) {
        case ExerciseType.TEXT:
            return 'text-exercises';
        case ExerciseType.MODELING:
            return 'modeling-exercises';
        case ExerciseType.PROGRAMMING:
            return 'programming-exercises';
        case ExerciseType.FILE_UPLOAD:
            return 'file-upload-exercises';
        case ExerciseType.QUIZ:
            return 'quiz-exercises';
        case ExerciseType.MILESTONE:
            return 'milestone-exercises';
        case ExerciseType.USER_STORY:
            return 'user-story-exercises';
        default:
            throw Error('Unexpected exercise type: ' + exerciseType);
    }
}

export function resetForImport(exercise: Exercise) {
    exercise.releaseDate = undefined;
    exercise.startDate = undefined;
    exercise.dueDate = undefined;
    exercise.assessmentDueDate = undefined;
    exercise.exampleSolutionPublicationDate = undefined;

    // without dates set, they can only be false
    exercise.allowComplaintsForAutomaticAssessments = false;
    exercise.allowFeedbackRequests = false;

    exercise.competencyLinks = [];
}

/**
 * Checks if the due date of the given exercise is in the past.
 * @param exercise the exercise to check
 * @return true if the due date is in the past, false otherwise (including if no due date is set)
 */
export function hasDueDatePassed(exercise: Exercise): boolean {
    if (!exercise.dueDate) {
        return false;
    }
    return exercise.dueDate.isBefore(dayjs());
}

/**
 * Extracts the competencies from an exercise's competency links.
 * @param exercise the exercise to extract competencies from
 * @return array of competencies linked to the exercise, empty array if none
 */
export function getExerciseCompetencies(exercise: Exercise): CourseCompetency[] {
    return exercise.competencyLinks?.map((link) => link.competency).filter((competency): competency is CourseCompetency => competency != null) ?? [];
}

/**
 * The exercise whose channel the Communication panel should show for the given exercise. A UserStoryExercise never
 * gets a channel of its own - only its parent Milestone does, since UserStoryExerciseService deliberately does not
 * call ChannelService#createExerciseChannel the way the Milestone creation path does - so discussion about a user
 * story belongs in the Milestone's channel. Returns the exercise unchanged for every other type, and undefined when
 * no channel can be resolved (which should hide the discussion panel).
 *
 * @param exercise the exercise to resolve the discussion channel target for
 * @return the exercise whose channel to show, or undefined if none can be resolved
 */
export function resolveDiscussionExercise(exercise: Exercise): Exercise | undefined {
    if (exercise.type !== ExerciseType.USER_STORY) {
        return exercise;
    }
    const milestoneExercise = (exercise as Exercise & { milestoneExercise?: Exercise }).milestoneExercise;
    if (!milestoneExercise) {
        return undefined;
    }
    // The parent Milestone is serialized without its course (it is always the same course the user story is in), but
    // DiscussionSectionComponent needs a course id as well as the exercise id to look the channel up.
    const discussionTarget = deepClone(milestoneExercise);
    discussionTarget.course = exercise.course;
    return discussionTarget;
}

/**
 * A DTO representing an exercise.
 *
 * @param id   the id of the exercise
 * @param type the type of the exercise (programming, modeling, quiz, text, file-upload)
 */
export class ExerciseDTO {
    id?: number;
    type?: ExerciseType;
}
