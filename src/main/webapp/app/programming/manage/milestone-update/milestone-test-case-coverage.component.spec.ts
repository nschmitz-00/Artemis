import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { throwError } from 'rxjs';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { MilestoneTestCaseCoverageComponent } from 'app/programming/manage/milestone-update/milestone-test-case-coverage.component';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { MilestoneExercise, MilestoneTestCaseCoverage } from 'app/programming/shared/entities/milestone-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MilestoneTestCaseCoverage Component', () => {
    const course = { id: 123 } as Course;
    const emptyCoverage: MilestoneTestCaseCoverage = { orphanTestCases: [], duplicateTestCases: [] };

    let fixture: ComponentFixture<MilestoneTestCaseCoverageComponent>;
    let milestoneExerciseService: MilestoneExerciseService;

    const setUp = async (milestoneExerciseId: number | undefined, coverage: MilestoneTestCaseCoverage | Error) => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        milestoneExerciseService = TestBed.inject(MilestoneExerciseService);
        vi.spyOn(milestoneExerciseService, 'getTestCaseCoverage').mockReturnValue(coverage instanceof Error ? throwError(() => coverage) : of(coverage));

        const milestoneExercise = new MilestoneExercise(course, undefined);
        milestoneExercise.id = milestoneExerciseId;

        fixture = TestBed.createComponent(MilestoneTestCaseCoverageComponent);
        fixture.componentRef.setInput('milestoneExercise', milestoneExercise);
        await fixture.whenStable();
        fixture.detectChanges();
    };

    it('should render nothing when every test case is claimed exactly once', async () => {
        await setUp(2, emptyCoverage);

        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });

    it('should not request the coverage for a milestone that has not been saved yet', async () => {
        await setUp(undefined, emptyCoverage);

        expect(milestoneExerciseService.getTestCaseCoverage).not.toHaveBeenCalled();
        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });

    it('should list orphan and duplicate test cases with the user stories claiming them', async () => {
        await setUp(2, {
            orphanTestCases: [{ testCaseId: 10, testName: 'testUnclaimed', referencingUserStories: [] }],
            duplicateTestCases: [
                {
                    testCaseId: 11,
                    testName: 'testShared',
                    referencingUserStories: [
                        { id: 3, title: 'Story 1' },
                        { id: 4, title: 'Story 2' },
                    ],
                },
            ],
        });

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('testUnclaimed');
        expect(text).toContain('testShared');
        expect(text).toContain('Story 1');
        expect(text).toContain('Story 2');
    });

    it('should stay silent when the coverage request fails', async () => {
        await setUp(2, new Error('request failed'));

        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });
});
