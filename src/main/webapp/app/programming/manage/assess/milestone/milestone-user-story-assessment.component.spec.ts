import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import { MilestoneUserStoryAssessmentComponent } from 'app/programming/manage/assess/milestone/milestone-user-story-assessment.component';
import { MilestoneAssessmentStateService } from 'app/programming/manage/assess/milestone/milestone-assessment-state.service';
import { MilestoneAssessmentService, UserStoryAssessment } from 'app/programming/manage/assess/milestone/milestone-assessment.service';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('MilestoneUserStoryAssessment Component', () => {
    let fixture: ComponentFixture<MilestoneUserStoryAssessmentComponent>;
    let milestoneAssessmentState: MilestoneAssessmentStateService;

    const buildFeedback = (type: FeedbackType, credits: number, detailText?: string, reference?: string): Feedback => {
        const feedback = new Feedback();
        feedback.type = type;
        feedback.credits = credits;
        feedback.detailText = detailText;
        feedback.reference = reference;
        return feedback;
    };

    const buildUserStoryAssessment = (userStoryExerciseId: number, maxPoints: number, feedbacks: Feedback[]): UserStoryAssessment => {
        const latestResult = new Result();
        latestResult.id = userStoryExerciseId * 10;
        latestResult.feedbacks = feedbacks;
        return { userStoryExerciseId, title: `Story ${userStoryExerciseId}`, maxPoints, bonusPoints: 0, participationId: userStoryExerciseId * 100, latestResult };
    };

    const userStoryAssessments = () => [
        buildUserStoryAssessment(1, 10, [buildFeedback(FeedbackType.AUTOMATIC, 6)]),
        buildUserStoryAssessment(2, 4, [buildFeedback(FeedbackType.AUTOMATIC, 4), buildFeedback(FeedbackType.MANUAL_UNREFERENCED, -1, 'Bad style')]),
    ];

    const setUp = async (assessments: UserStoryAssessment[] = userStoryAssessments()) => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                MilestoneAssessmentStateService,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        milestoneAssessmentState = TestBed.inject(MilestoneAssessmentStateService);
        vi.spyOn(TestBed.inject(MilestoneAssessmentService), 'getUserStoryAssessments').mockReturnValue(of(assessments));
        milestoneAssessmentState.load(5, 42).subscribe();

        fixture = TestBed.createComponent(MilestoneUserStoryAssessmentComponent);
        fixture.detectChanges();
    };

    it('should render one section per user story of the submission', async () => {
        await setUp();

        expect(fixture.nativeElement.querySelector('#user-story-assessment-1')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#user-story-assessment-2')).not.toBeNull();
    });

    it('should show the automatic, manual, and total points of a user story', async () => {
        await setUp();

        // 4 automatic points minus the point the tutor deducted
        const points = fixture.nativeElement.querySelector('#user-story-points-2').textContent;
        expect(points).toContain('4');
        expect(points).toContain('-1');
        expect(points).toContain('3');
    });

    it('should hand the feedback a tutor writes in a section to the shared state', async () => {
        await setUp();

        fixture.componentInstance['onUnreferencedFeedbacksChange'](1, [buildFeedback(FeedbackType.MANUAL_UNREFERENCED, 2, 'Well done')]);

        expect(milestoneAssessmentState.unreferencedFeedbackOf(1)).toHaveLength(1);
        expect(milestoneAssessmentState.totalPoints(1)).toBe(8);
    });

    // Inline feedback that names no user story scores no points anywhere, so the tutor has to see it before submitting.
    it('should warn about inline feedback that is not assigned to a user story', async () => {
        await setUp();
        expect(fixture.nativeElement.querySelector('#unassigned-inline-feedback-warning')).toBeNull();

        milestoneAssessmentState.setReferencedFeedback([buildFeedback(FeedbackType.MANUAL, 1, 'Nice loop', 'file:Sort.java_line:3')]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#unassigned-inline-feedback-warning')).not.toBeNull();
    });

    it('should tell for each user story how much inline feedback it carries', async () => {
        await setUp();
        const inlineFeedback = buildFeedback(FeedbackType.MANUAL, 1, 'Nice loop', 'file:Sort.java_line:3');
        inlineFeedback.userStoryExerciseId = 2;

        milestoneAssessmentState.setReferencedFeedback([inlineFeedback]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#user-story-inline-feedback-count-2')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#user-story-inline-feedback-count-1')).toBeNull();
    });
});
