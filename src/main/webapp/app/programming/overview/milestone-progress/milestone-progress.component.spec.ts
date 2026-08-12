import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { MilestoneProgressComponent } from 'app/programming/overview/milestone-progress/milestone-progress.component';
import { MilestoneProgressService } from 'app/programming/overview/milestone-progress/milestone-progress.service';
import { MilestoneProgress, UserStoryProgressStatus } from 'app/programming/shared/entities/milestone-progress.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MilestoneProgress Component', () => {
    const progressWithTwoStories: MilestoneProgress = {
        milestoneExerciseId: 42,
        userStories: [
            {
                userStoryExerciseId: 3,
                title: 'Story 1',
                shortName: 'STORYONE',
                maxPoints: 10,
                bonusPoints: 0,
                achievedPoints: 10,
                score: 100,
                status: UserStoryProgressStatus.COMPLETED,
                participationId: 100,
                latestResultCompletionDate: dayjs('2026-03-01T10:00:00Z'),
            },
            {
                userStoryExerciseId: 4,
                title: 'Story 2',
                shortName: 'STORYTWO',
                maxPoints: 10,
                bonusPoints: 0,
                achievedPoints: 0,
                status: UserStoryProgressStatus.NOT_STARTED,
            },
        ],
        achievedPoints: 10,
        maxPoints: 20,
        completionPercentage: 50,
        completedUserStories: 1,
        totalUserStories: 2,
        manualAssessmentPending: false,
    };

    let fixture: ComponentFixture<MilestoneProgressComponent>;
    let milestoneProgressService: MilestoneProgressService;

    const setUp = async (progress: MilestoneProgress | Error) => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        milestoneProgressService = TestBed.inject(MilestoneProgressService);
        vi.spyOn(milestoneProgressService, 'getProgress').mockReturnValue(progress instanceof Error ? throwError(() => progress) : of(progress));

        fixture = TestBed.createComponent(MilestoneProgressComponent);
        fixture.componentRef.setInput('milestoneExerciseId', 42);
        await fixture.whenStable();
        fixture.detectChanges();
    };

    it('should list every user story with its points', async () => {
        await setUp(progressWithTwoStories);

        expect(milestoneProgressService.getProgress).toHaveBeenCalledWith(42);
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('Story 1');
        expect(text).toContain('Story 2');
        expect(fixture.nativeElement.querySelector('#milestone-progress-user-story-3-points').textContent).toContain('10');
        expect(fixture.nativeElement.querySelector('#milestone-progress-user-story-4')).not.toBeNull();
    });

    it('should show the aggregate over all user stories', async () => {
        await setUp(progressWithTwoStories);

        const points = fixture.nativeElement.querySelector('#milestone-progress-points').textContent;
        expect(points).toContain('10');
        expect(points).toContain('20');
        expect(fixture.nativeElement.querySelector('#milestone-progress-bar')).not.toBeNull();
    });

    it('should tell the student that manual assessment can still change the points', async () => {
        await setUp({ ...progressWithTwoStories, manualAssessmentPending: true });

        expect(fixture.nativeElement.textContent).toContain('artemisApp.milestoneExercise.progress.manualAssessmentPending');
    });

    it('should report a milestone without user stories instead of an empty table', async () => {
        // The server omits an empty user story list entirely (NON_EMPTY), so the overview must not depend on it being there
        await setUp({
            milestoneExerciseId: 42,
            achievedPoints: 0,
            maxPoints: 0,
            completionPercentage: 0,
            completedUserStories: 0,
            totalUserStories: 0,
            manualAssessmentPending: false,
        });

        expect(fixture.nativeElement.textContent).toContain('artemisApp.milestoneExercise.userStories.noUserStories');
        expect(fixture.nativeElement.querySelector('#milestone-progress-bar')).toBeNull();
    });

    it('should show a message instead of an empty overview when the request fails', async () => {
        await setUp(new Error('request failed'));

        expect(fixture.nativeElement.textContent).toContain('artemisApp.milestoneExercise.progress.loadingFailed');
    });
});
