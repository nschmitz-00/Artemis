import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EMPTY, Observable, of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MilestoneAssessmentComponent } from 'app/programming/manage/assess/milestone-assessment/milestone-assessment.component';
import { MilestoneAssessment, MilestoneAssessmentService } from 'app/programming/manage/assess/milestone-assessment/milestone-assessment.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('MilestoneAssessmentComponent', () => {
    let fixture: ComponentFixture<MilestoneAssessmentComponent>;

    /** Two stories, only the first of which the student ever pushed for, then a submitted quiz and a submitted text exercise. */
    function assessment(): MilestoneAssessment {
        return {
            milestoneExerciseId: 99,
            milestoneTitle: 'Sprint 1',
            problemStatement: 'Build the login',
            staticCodeAnalysisEnabled: true,
            exercises: [
                { exerciseId: 1, title: 'Login form', exerciseType: ExerciseType.PROGRAMMING, userStory: true, submissionId: 111, participationId: 11 },
                { exerciseId: 2, title: 'Logout', exerciseType: ExerciseType.PROGRAMMING, userStory: true, participationId: 22 },
                { exerciseId: 3, title: 'Retro quiz', exerciseType: ExerciseType.QUIZ, submissionId: 333, participationId: 33, latestScore: 50 },
                { exerciseId: 4, title: 'Reflection', exerciseType: ExerciseType.TEXT, submissionId: 444, participationId: 44 },
            ],
        };
    }

    async function setup(getAssessment: () => Observable<MilestoneAssessment> = () => of(assessment())): Promise<void> {
        const route = { params: of({ courseId: '1', groupId: '10', studentLogin: 'student1' }) } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [MilestoneAssessmentComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(MilestoneAssessmentService, { getAssessmentForStudent: getAssessment as never }),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // The assessment editor is a routed, service-heavy component of its own; this spec is about the shell.
            .overrideComponent(MilestoneAssessmentComponent, { set: { template: '' } })
            .compileComponents();

        fixture = TestBed.createComponent(MilestoneAssessmentComponent);
        fixture.detectChanges();
    }

    /** Access to the protected state under test. */
    function comp(): {
        activeTab: () => string | number | undefined;
        activeExercise: () => { exerciseId: number; submissionId?: number } | undefined;
        onTabChange: (value: string | number | undefined) => void;
        assessNextExercise: () => void;
        nextExercise: () => { exerciseId: number } | undefined;
        studentLogin: () => string;
        exercises: () => { exerciseId: number }[];
    } {
        return fixture.componentInstance as never;
    }

    beforeEach(() => TestBed.resetTestingModule());

    it('opens on the group-level tab, because that is the context the stories are graded against', async () => {
        await setup();
        expect(comp().activeTab()).toBe('milestone');
        expect(comp().activeExercise()).toBeUndefined();
        expect(comp().studentLogin()).toBe('student1');
        expect(comp().exercises()).toHaveLength(4);
    });

    it('switches to a story tab and exposes the submission its editor is opened on', async () => {
        await setup();

        comp().onTabChange(1);

        expect(comp().activeExercise()?.exerciseId).toBe(1);
        expect(comp().activeExercise()?.submissionId).toBe(111);
    });

    it('has nothing to open for a story the student never started', async () => {
        await setup();

        comp().onTabChange(2);

        expect(comp().activeExercise()?.exerciseId).toBe(2);
        expect(comp().activeExercise()?.submissionId).toBeUndefined();
    });

    it('refuses to switch away while the editor holds unsaved feedback', async () => {
        await setup();
        comp().onTabChange(1);
        // The panel is destroyed on switch, so leaving without asking would discard the tutor's work silently.
        (fixture.componentInstance as unknown as { assessmentEditor: () => { hasPendingChanges: boolean } }).assessmentEditor = () => ({ hasPendingChanges: true });
        const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

        comp().onTabChange('milestone');

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(comp().activeTab()).toBe(1);
    });

    it('advances past a story that was never started and past a quiz, to the next exercise a tutor assesses', async () => {
        await setup();
        comp().onTabChange(1);

        // Story 2 was never started and the quiz is graded automatically, so the text exercise comes next.
        expect(comp().nextExercise()?.exerciseId).toBe(4);
        comp().assessNextExercise();

        expect(comp().activeTab()).toBe(4);
    });

    it('has no next exercise on the last one, so the button is hidden rather than doing nothing', async () => {
        await setup();
        comp().onTabChange(4);

        expect(comp().nextExercise()).toBeUndefined();
        comp().assessNextExercise();

        expect(comp().activeTab()).toBe(4);
    });

    it('offers the first assessable exercise as next from the group-level tab', async () => {
        await setup();

        expect(comp().nextExercise()?.exerciseId).toBe(1);
        comp().assessNextExercise();

        expect(comp().activeTab()).toBe(1);
    });

    it('surfaces a failed load rather than rendering an empty page silently', async () => {
        await setup(() => EMPTY);
        expect(comp().activeExercise()).toBeUndefined();
        expect(comp().exercises()).toHaveLength(0);
    });
});
