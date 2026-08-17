import { Injectable, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';

import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getPositiveAndCappedTotalScore } from 'app/exercise/util/exercise.utils';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { MilestoneAssessmentService, UserStoryAssessment, UserStoryManualResult } from 'app/programming/manage/assess/milestone/milestone-assessment.service';

/**
 * The state of one milestone assessment session, shared by everything that takes part in it: the assessment page, the panel
 * that lists the user stories, and the inline feedback widgets in the code editor.
 * <p>
 * A milestone submission is assessed once for all of its user stories, but the points belong to the user stories - so every
 * piece of feedback the tutor writes carries the user story it was written for, and this service is what holds that
 * assignment while the tutor works. It is provided by the assessment page, so all of its descendants share one instance.
 */
@Injectable()
export class MilestoneAssessmentStateService {
    private readonly milestoneAssessmentService = inject(MilestoneAssessmentService);

    /** The user stories of the submission being assessed, in the order the server returned them. */
    readonly userStories = signal<UserStoryAssessment[]>([]);
    /** The test case feedback of each user story, shown but not editable, keyed by user story id. */
    readonly automaticFeedbackByUserStory = signal<Map<number, Feedback[]>>(new Map());
    /** The feedback the tutor wrote for a user story without pointing at a line, keyed by user story id. */
    readonly unreferencedFeedbackByUserStory = signal<Map<number, Feedback[]>>(new Map());
    /** The feedback the tutor wrote on a line of code. Each entry carries the user story it was assigned to. */
    readonly referencedFeedback = signal<Feedback[]>([]);
    readonly isLoading = signal(false);

    private milestoneExerciseId?: number;
    private participationId?: number;

    /**
     * Loads the user stories of a milestone submission and the feedback they already carry.
     * @param milestoneExerciseId of the milestone being assessed
     * @param participationId of the student's participation in that milestone
     */
    load(milestoneExerciseId: number, participationId: number): Observable<UserStoryAssessment[]> {
        this.milestoneExerciseId = milestoneExerciseId;
        this.participationId = participationId;
        this.isLoading.set(true);
        return this.milestoneAssessmentService.getUserStoryAssessments(milestoneExerciseId, participationId).pipe(
            tap({
                next: (userStoryAssessments) => this.takeOver(userStoryAssessments),
                error: () => this.isLoading.set(false),
            }),
        );
    }

    private takeOver(userStoryAssessments: UserStoryAssessment[]): void {
        const automaticFeedback = new Map<number, Feedback[]>();
        const unreferencedFeedback = new Map<number, Feedback[]>();
        const referencedFeedback: Feedback[] = [];

        for (const userStoryAssessment of userStoryAssessments) {
            const feedbacks = userStoryAssessment.latestResult?.feedbacks ?? [];
            automaticFeedback.set(
                userStoryAssessment.userStoryExerciseId,
                feedbacks.filter((feedback) => feedback.type === FeedbackType.AUTOMATIC),
            );
            unreferencedFeedback.set(
                userStoryAssessment.userStoryExerciseId,
                feedbacks.filter((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED),
            );
            // Which user story an inline feedback belongs to is not stored on the feedback but follows from the result it
            // was loaded from, so the assignment is restored here rather than persisted
            feedbacks
                .filter((feedback) => feedback.type === FeedbackType.MANUAL && feedback.reference !== undefined)
                .forEach((feedback) => {
                    feedback.userStoryExerciseId = userStoryAssessment.userStoryExerciseId;
                    referencedFeedback.push(feedback);
                });
        }

        this.userStories.set(userStoryAssessments);
        this.automaticFeedbackByUserStory.set(automaticFeedback);
        this.unreferencedFeedbackByUserStory.set(unreferencedFeedback);
        this.referencedFeedback.set(referencedFeedback);
        this.isLoading.set(false);
    }

    /** Takes over the inline feedback of the code editor, which the tutor assigns to a user story while writing it. */
    setReferencedFeedback(referencedFeedback: Feedback[]): void {
        this.referencedFeedback.set([...referencedFeedback]);
    }

    /** Takes over the feedback the tutor wrote in the section of one user story. */
    setUnreferencedFeedback(userStoryExerciseId: number, feedbacks: Feedback[]): void {
        this.unreferencedFeedbackByUserStory.update((feedbacksByUserStory) => {
            const updated = new Map(feedbacksByUserStory);
            updated.set(userStoryExerciseId, [...feedbacks]);
            return updated;
        });
    }

    automaticFeedbackOf(userStoryExerciseId: number): Feedback[] {
        return this.automaticFeedbackByUserStory().get(userStoryExerciseId) ?? [];
    }

    unreferencedFeedbackOf(userStoryExerciseId: number): Feedback[] {
        return this.unreferencedFeedbackByUserStory().get(userStoryExerciseId) ?? [];
    }

    referencedFeedbackOf(userStoryExerciseId: number): Feedback[] {
        return this.referencedFeedback().filter((feedback) => feedback.userStoryExerciseId === userStoryExerciseId);
    }

    /** Inline feedback the tutor has not assigned to any user story yet, which would score no points anywhere. */
    readonly unassignedReferencedFeedback = (): Feedback[] => this.referencedFeedback().filter((feedback) => feedback.userStoryExerciseId === undefined);

    /** The points the test cases of this user story awarded, capped at what the user story can pay out. */
    automaticPoints(userStoryExerciseId: number): number {
        const automaticPoints = this.automaticFeedbackOf(userStoryExerciseId)
            .filter((feedback) => !Feedback.isStaticCodeAnalysisFeedback(feedback))
            .reduce((points, feedback) => points + (feedback.credits ?? 0), 0);
        return Math.min(automaticPoints, this.reachablePoints(userStoryExerciseId));
    }

    /** The points the tutor added or deducted for this user story, inline and in its own section. */
    manualPoints(userStoryExerciseId: number): number {
        return [...this.unreferencedFeedbackOf(userStoryExerciseId), ...this.referencedFeedbackOf(userStoryExerciseId)].reduce(
            (points, feedback) => points + (feedback.credits ?? 0),
            0,
        );
    }

    /** What the student ends up with for this user story: never negative and never more than the user story can pay out. */
    totalPoints(userStoryExerciseId: number): number {
        return getPositiveAndCappedTotalScore(this.automaticPoints(userStoryExerciseId) + this.manualPoints(userStoryExerciseId), this.reachablePoints(userStoryExerciseId));
    }

    reachablePoints(userStoryExerciseId: number): number {
        const userStory = this.userStories().find((candidate) => candidate.userStoryExerciseId === userStoryExerciseId);
        return (userStory?.maxPoints ?? 0) + (userStory?.bonusPoints ?? 0);
    }

    /**
     * Saves - and, if submit is set, submits - one result per user story. Does nothing when there is no milestone assessment
     * in progress, so the assessment page can call this unconditionally.
     * @param submit whether the assessments should be submitted rather than only saved
     */
    saveOrSubmit(submit: boolean): Observable<Result[]> {
        if (this.milestoneExerciseId === undefined || this.participationId === undefined || this.userStories().length === 0) {
            return of([]);
        }
        return this.milestoneAssessmentService.saveAssessment(this.milestoneExerciseId, this.participationId, this.buildUserStoryResults(), submit);
    }

    /**
     * Builds the result of every user story: its own test case feedback plus exactly the feedback the tutor assigned to it.
     * Feedback that was not assigned to any user story is left out - it would otherwise score points in a user story it was
     * never meant for.
     */
    buildUserStoryResults(): UserStoryManualResult[] {
        return this.userStories().map((userStory) => {
            const result = deepClone(userStory.latestResult ?? new Result());
            // The server replaces the feedback of the stored result with what is sent here, so the automatic feedback has to
            // be sent along - otherwise saving a manual assessment would wipe the test results of that user story
            result.feedbacks = [
                ...this.automaticFeedbackOf(userStory.userStoryExerciseId),
                ...this.unreferencedFeedbackOf(userStory.userStoryExerciseId),
                ...this.referencedFeedbackOf(userStory.userStoryExerciseId),
            ];
            result.rated = true;
            const maxPoints = userStory.maxPoints ?? 0;
            result.score = maxPoints > 0 ? (this.totalPoints(userStory.userStoryExerciseId) / maxPoints) * 100 : 0;
            result.successful = result.score >= 100;
            return { userStoryExerciseId: userStory.userStoryExerciseId, result };
        });
    }

    /** @return true if every inline feedback the tutor wrote is assigned to a user story, and therefore scores somewhere */
    allReferencedFeedbackAssigned(): boolean {
        return this.unassignedReferencedFeedback().length === 0;
    }

    /**
     * Whether the assessment validity for the current submission should be overridden by the milestone-specific rule
     * (every inline feedback must be assigned to a user story), instead of the caller's own validation.
     * @param isMilestoneAssessment whether the exercise being assessed is a MilestoneExercise
     * @return the override result, or undefined when the exercise is not a MilestoneExercise
     */
    overrideValidation(isMilestoneAssessment: boolean): boolean | undefined {
        return isMilestoneAssessment ? this.allReferencedFeedbackAssigned() : undefined;
    }

    /**
     * The feedback that belongs on the manual result being saved. For a milestone, the manual feedback is stored on the
     * results of its user stories instead, which are what carries the points - keeping a copy here would pay every
     * deduction and bonus out a second time, so only the automatic feedback is kept.
     * @param isMilestoneAssessment whether the exercise being assessed is a MilestoneExercise
     */
    feedbacksForManualResult(isMilestoneAssessment: boolean, automaticFeedback: Feedback[], referencedFeedback: Feedback[], unreferencedFeedback: Feedback[]): Feedback[] {
        if (isMilestoneAssessment) {
            return [...automaticFeedback];
        }
        return [...referencedFeedback, ...unreferencedFeedback, ...automaticFeedback];
    }
}
