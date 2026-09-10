import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MilestoneAssessment, MilestoneAssessmentService, MilestoneAssessmentStudent } from 'app/programming/manage/assess/milestone-assessment/milestone-assessment.service';

describe('MilestoneAssessmentService', () => {
    let service: MilestoneAssessmentService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(MilestoneAssessmentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('reads the dashboard from the group-scoped assessment endpoint', () => {
        const expected: MilestoneAssessmentStudent[] = [{ studentLogin: 'student1', milestoneParticipationId: 5, stories: [] }];
        let actual: MilestoneAssessmentStudent[] | undefined;

        service.getAssessmentDashboard(1, 10).subscribe((students) => (actual = students));

        const request = httpMock.expectOne('api/exercise/courses/1/milestone-exercise-groups/10/assessment/students');
        expect(request.request.method).toBe('GET');
        request.flush(expected);
        expect(actual).toEqual(expected);
        httpMock.verify();
    });

    it("reads one student's assessment from the same endpoint, addressed by login", () => {
        const expected = { milestoneExerciseId: 99, milestoneTitle: 'Sprint 1', stories: [] } as MilestoneAssessment;
        let actual: MilestoneAssessment | undefined;

        service.getAssessmentForStudent(1, 10, 'student1').subscribe((assessment) => (actual = assessment));

        const request = httpMock.expectOne('api/exercise/courses/1/milestone-exercise-groups/10/assessment/students/student1');
        expect(request.request.method).toBe('GET');
        request.flush(expected);
        expect(actual).toEqual(expected);
        httpMock.verify();
    });
});
