import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EMPTY, Observable, of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MilestoneAssessmentDashboardComponent } from 'app/programming/manage/assess/milestone-assessment/milestone-assessment-dashboard.component';
import { MilestoneAssessmentService, MilestoneAssessmentStudent } from 'app/programming/manage/assess/milestone-assessment/milestone-assessment.service';

describe('MilestoneAssessmentDashboardComponent', () => {
    let fixture: ComponentFixture<MilestoneAssessmentDashboardComponent>;

    function students(): MilestoneAssessmentStudent[] {
        return [
            {
                studentLogin: 'student1',
                studentName: 'Ada',
                milestoneParticipationId: 5,
                stories: [
                    { exerciseId: 1, title: 'Login form', submissionId: 111, assessed: true, latestScore: 80, assessorLogin: 'tutor1' },
                    { exerciseId: 2, title: 'Logout', submissionId: 222 },
                ],
            },
            {
                studentLogin: 'student2',
                milestoneParticipationId: 6,
                stories: [
                    { exerciseId: 1, title: 'Login form' },
                    { exerciseId: 2, title: 'Logout' },
                ],
            },
        ];
    }

    async function setup(getDashboard: () => Observable<MilestoneAssessmentStudent[]> = () => of(students())): Promise<void> {
        const route = { params: of({ courseId: '1', groupId: '10' }) } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [MilestoneAssessmentDashboardComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(MilestoneAssessmentService, { getAssessmentDashboard: getDashboard as never }),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(MilestoneAssessmentDashboardComponent);
        fixture.detectChanges();
    }

    /** Access to the protected state under test. */
    function comp(): {
        students: () => MilestoneAssessmentStudent[];
        storyColumns: () => { exerciseId: number }[];
        assessedCount: (student: MilestoneAssessmentStudent) => number;
        assessmentLink: (student: MilestoneAssessmentStudent) => (string | number)[];
    } {
        return fixture.componentInstance as never;
    }

    beforeEach(() => TestBed.resetTestingModule());

    it('renders one row per student and one column per story', async () => {
        await setup();

        expect(comp().students()).toHaveLength(2);
        // Taken from the first row: every row carries the group's stories in the same order, started or not.
        expect(comp().storyColumns()).toHaveLength(2);
        expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    });

    it('counts what is already assessed, which is what tells a tutor where to spend their time', async () => {
        await setup();

        expect(comp().assessedCount(students()[0])).toBe(1);
        expect(comp().assessedCount(students()[1])).toBe(0);
    });

    it('links to the tabbed page for that student, addressed by login', async () => {
        await setup();

        expect(comp().assessmentLink(students()[0])).toEqual(['/course-management', 1, 'milestone-exercise-groups', 10, 'assessment', 'student1']);
    });

    it('says so when nobody has started the milestone yet, rather than rendering an empty table', async () => {
        await setup(() => of([]));

        expect(fixture.nativeElement.querySelector('table')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.milestoneAssessment.dashboard.noStudents');
    });

    it('does not render a table while the request is still outstanding', async () => {
        await setup(() => EMPTY);

        expect(fixture.nativeElement.querySelector('table')).toBeNull();
    });
});
