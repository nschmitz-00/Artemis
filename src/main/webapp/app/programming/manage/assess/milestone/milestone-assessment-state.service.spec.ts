import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import { MilestoneAssessmentStateService } from 'app/programming/manage/assess/milestone/milestone-assessment-state.service';
import { MilestoneAssessmentService, UserStoryAssessment } from 'app/programming/manage/assess/milestone/milestone-assessment.service';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MilestoneAssessmentStateService', () => {
    const MILESTONE_EXERCISE_ID = 5;
    const PARTICIPATION_ID = 42;

    let state: MilestoneAssessmentStateService;
    let milestoneAssessmentService: MilestoneAssessmentService;

    const buildFeedback = (type: FeedbackType, credits: number, detailText?: string, reference?: string): Feedback => {
        const feedback = new Feedback();
        feedback.type = type;
        feedback.credits = credits;
        feedback.detailText = detailText;
        feedback.reference = reference;
        return feedback;
    };

    const inlineFeedback = (credits: number, detailText: string, userStoryExerciseId?: number): Feedback => {
        const feedback = buildFeedback(FeedbackType.MANUAL, credits, detailText, 'file:Sort.java_line:3');
        feedback.userStoryExerciseId = userStoryExerciseId;
        return feedback;
    };

    const buildUserStoryAssessment = (userStoryExerciseId: number, maxPoints: number, feedbacks: Feedback[]): UserStoryAssessment => {
        const latestResult = new Result();
        latestResult.id = userStoryExerciseId * 10;
        latestResult.feedbacks = feedbacks;
        return { userStoryExerciseId, title: `Story ${userStoryExerciseId}`, maxPoints, bonusPoints: 0, participationId: userStoryExerciseId * 100, latestResult };
    };

    const load = (assessments: UserStoryAssessment[]) => {
        vi.spyOn(milestoneAssessmentService, 'getUserStoryAssessments').mockReturnValue(of(assessments));
        state.load(MILESTONE_EXERCISE_ID, PARTICIPATION_ID).subscribe();
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MilestoneAssessmentStateService, provideHttpClient(), provideHttpClientTesting()],
        });
        state = TestBed.inject(MilestoneAssessmentStateService);
        milestoneAssessmentService = TestBed.inject(MilestoneAssessmentService);
    });

    // Which user story an inline comment belongs to follows from the result it was stored on, so it has to be restored on load.
    it('should restore the user story of inline feedback it loads', () => {
        load([buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 6), inlineFeedback(2, 'Nice loop')])]);

        expect(state.referencedFeedback()).toHaveLength(1);
        expect(state.referencedFeedback()[0].userStoryExerciseId).toBe(1);
        expect(state.allReferencedFeedbackAssigned()).toBe(true);
    });

    it('should split loaded feedback into automatic, unreferenced, and inline feedback', () => {
        load([
            buildUserStoryAssessment(1, 10, [
                buildFeedback(FeedbackType.AUTOMATIC, 6),
                buildFeedback(FeedbackType.MANUAL_UNREFERENCED, -1, 'Bad style'),
                inlineFeedback(2, 'Nice loop'),
            ]),
        ]);

        expect(state.automaticFeedbackOf(1)).toHaveLength(1);
        expect(state.unreferencedFeedbackOf(1)).toHaveLength(1);
        expect(state.referencedFeedbackOf(1)).toHaveLength(1);
        // 6 automatic + 2 inline - 1 unreferenced
        expect(state.totalPoints(1)).toBe(7);
    });

    it('should report inline feedback that names no user story', () => {
        load([buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 6)])]);

        state.setReferencedFeedback([inlineFeedback(2, 'Belongs nowhere')]);

        expect(state.allReferencedFeedbackAssigned()).toBe(false);
        expect(state.unassignedReferencedFeedback()).toHaveLength(1);
    });

    it('should build one result per user story, keeping its automatic feedback', () => {
        load([buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 6)]), buildUserStoryAssessment(2, 4, [buildFeedback(FeedbackType.AUTOMATIC, 4)])]);
        state.setReferencedFeedback([inlineFeedback(-2, 'Duplicated code', 1)]);
        state.setUnreferencedFeedback(2, [buildFeedback(FeedbackType.MANUAL_UNREFERENCED, -1, 'Slow')]);

        const userStoryResults = state.buildUserStoryResults();

        expect(userStoryResults).toHaveLength(2);
        // 6 automatic - 2 inline, out of 10
        expect(userStoryResults[0].result.score).toBe(40);
        expect(userStoryResults[0].result.feedbacks).toHaveLength(2);
        expect(userStoryResults[0].result.rated).toBe(true);
        // 4 automatic - 1 unreferenced, out of 4
        expect(userStoryResults[1].result.score).toBe(75);
    });

    // Feedback is written against the submission as a whole, so it must not push a single user story out of its own range.
    it('should never build a score below zero or above the reachable points', () => {
        load([buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 3)]), buildUserStoryAssessment(2, 4, [buildFeedback(FeedbackType.AUTOMATIC, 4)])]);
        state.setUnreferencedFeedback(1, [buildFeedback(FeedbackType.MANUAL_UNREFERENCED, -20, 'Way too harsh')]);
        state.setUnreferencedFeedback(2, [buildFeedback(FeedbackType.MANUAL_UNREFERENCED, 20, 'Way too generous')]);

        const userStoryResults = state.buildUserStoryResults();

        expect(userStoryResults[0].result.score).toBe(0);
        expect(userStoryResults[1].result.score).toBe(100);
    });

    it('should leave inline feedback without a user story out of every result', () => {
        load([buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 6)])]);
        state.setReferencedFeedback([inlineFeedback(3, 'Belongs nowhere')]);

        const userStoryResults = state.buildUserStoryResults();

        expect(userStoryResults[0].result.feedbacks).toHaveLength(1);
        expect(userStoryResults[0].result.score).toBe(60);
    });

    it('should save the results of all user stories in one request', () => {
        load([buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 6)])]);
        const saveAssessment = vi.spyOn(milestoneAssessmentService, 'saveAssessment').mockReturnValue(of([]));

        state.saveOrSubmit(true).subscribe();

        expect(saveAssessment).toHaveBeenCalledOnce();
        expect(saveAssessment.mock.calls[0][0]).toBe(MILESTONE_EXERCISE_ID);
        expect(saveAssessment.mock.calls[0][1]).toBe(PARTICIPATION_ID);
        expect(saveAssessment.mock.calls[0][3]).toBe(true);
    });

    it('should do nothing when no milestone assessment is loaded', () => {
        const saveAssessment = vi.spyOn(milestoneAssessmentService, 'saveAssessment');
        let savedResults: unknown[] | undefined;

        state.saveOrSubmit(true).subscribe((results) => (savedResults = results));

        expect(saveAssessment).not.toHaveBeenCalled();
        expect(savedResults).toEqual([]);
    });
});
