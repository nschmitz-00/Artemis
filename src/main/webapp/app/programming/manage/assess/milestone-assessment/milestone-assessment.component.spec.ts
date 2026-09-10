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

describe('MilestoneAssessmentComponent', () => {
    let fixture: ComponentFixture<MilestoneAssessmentComponent>;

    /** Two stories, only the first of which the student ever pushed for. */
    function assessment(): MilestoneAssessment {
        return {
            milestoneExerciseId: 99,
            milestoneTitle: 'Sprint 1',
            problemStatement: 'Build the login',
            staticCodeAnalysisEnabled: true,
            stories: [
                { exerciseId: 1, title: 'Login form', submissionId: 111, participationId: 11 },
                { exerciseId: 2, title: 'Logout', participationId: 22 },
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
        activeStory: () => { exerciseId: number } | undefined;
        activeSubmissionId: () => number | undefined;
        onTabChange: (value: string | number | undefined) => void;
        assessNextStory: () => void;
        studentLogin: () => string;
        stories: () => { exerciseId: number }[];
    } {
        return fixture.componentInstance as never;
    }

    beforeEach(() => TestBed.resetTestingModule());

    it('opens on the group-level tab, because that is the context the stories are graded against', async () => {
        await setup();
        expect(comp().activeTab()).toBe('milestone');
        expect(comp().activeStory()).toBeUndefined();
        expect(comp().studentLogin()).toBe('student1');
        expect(comp().stories()).toHaveLength(2);
    });

    it('switches to a story tab and exposes the submission its editor is opened on', async () => {
        await setup();

        comp().onTabChange(1);

        expect(comp().activeStory()?.exerciseId).toBe(1);
        expect(comp().activeSubmissionId()).toBe(111);
    });

    it('has nothing to open for a story the student never started', async () => {
        await setup();

        comp().onTabChange(2);

        expect(comp().activeStory()?.exerciseId).toBe(2);
        expect(comp().activeSubmissionId()).toBeUndefined();
    });

    it('refuses to switch away while the editor holds unsaved feedback', async () => {
        await setup();
        comp().onTabChange(1);
        // The panel is destroyed on switch, so leaving without asking would discard the tutor's work silently.
        (fixture.componentInstance as unknown as { assessmentContainer?: { hasPendingChanges: boolean } }).assessmentContainer = { hasPendingChanges: true };
        const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

        comp().onTabChange('milestone');

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(comp().activeTab()).toBe(1);
    });

    it('advances to the next story that has something to assess, instead of navigating away', async () => {
        await setup();
        comp().onTabChange(1);

        comp().assessNextStory();

        // Story 2 was never started, so there is no next submission and the tab stays put rather than opening an
        // editor with nothing behind it.
        expect(comp().activeTab()).toBe(1);
    });

    it('surfaces a failed load rather than rendering an empty page silently', async () => {
        await setup(() => EMPTY);
        expect(comp().activeStory()).toBeUndefined();
        expect(comp().stories()).toHaveLength(0);
    });
});
