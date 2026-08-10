import { describe, expect, it, vi } from 'vitest';

// Mock y-monaco to avoid needing the full Monaco API in tests. The form transitively imports the editable-instruction
// editor, which pulls in y-monaco's deep `monaco-editor/esm/...` import; that subpath escapes the `monaco-editor` alias
// in vitest.config and breaks dependency resolution. Stubbing the module here mirrors what
// programming-exercise-problem.component.spec.ts does.
vi.mock('y-monaco', () => ({
    // Use a real `function` (not an arrow) so the production code can invoke it with `new`.
    MonacoBinding: vi.fn(function (this: any) {
        this.destroy = vi.fn();
    }),
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { MilestoneExerciseUpdateComponent } from 'app/programming/manage/milestone-update/milestone-exercise-update.component';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProvider } from 'ng-mocks';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';

describe('MilestoneExerciseUpdate Component', () => {
    const course = { id: 123 } as Course;

    let fixture: ComponentFixture<MilestoneExerciseUpdateComponent>;
    let comp: MilestoneExerciseUpdateComponent;
    let milestoneExerciseService: MilestoneExerciseService;
    let milestoneExercise: MilestoneExercise;

    const setUp = async (exerciseId?: string, userStoryExercises?: UserStoryExercise[]) => {
        milestoneExercise = new MilestoneExercise(course, undefined);
        milestoneExercise.id = 2;
        milestoneExercise.title = 'Milestone';
        milestoneExercise.problemStatement = 'Existing description';
        if (userStoryExercises) {
            milestoneExercise.userStoryExercises = userStoryExercises;
        }

        const paramMap = convertToParamMap(exerciseId ? { exerciseId, courseId: String(course.id) } : { courseId: String(course.id) });

        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                // The shared language section resolves the supported languages and build options through these
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(AlertService),
                MockProvider(ExerciseService),
                MockProvider(CourseManagementService),
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                // Must come after provideRouter, which provides an ActivatedRoute of its own that would win otherwise
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap } } },
            ],
        })
            // The form embeds the Monaco-based instructions editor, which is not what these tests are about
            .overrideTemplate(MilestoneExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MilestoneExerciseUpdateComponent);
        comp = fixture.componentInstance;
        milestoneExerciseService = TestBed.inject(MilestoneExerciseService);
        vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        const courseService = TestBed.inject(CourseManagementService);
        vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(courseService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: [] })));
        vi.spyOn(TestBed.inject(ExerciseService), 'convertExerciseCategoriesAsStringFromServer').mockReturnValue([]);
        vi.spyOn(milestoneExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: milestoneExercise })));

        fixture.detectChanges();
    };

    it('should load the existing description into the form when editing', async () => {
        await setUp('2');

        expect(comp.milestoneExercise().problemStatement).toBe('Existing description');
    });

    it('should apply editor changes to the milestone description', async () => {
        await setUp('2');

        comp.updateProblemStatement('# Rewritten description');

        expect(comp.milestoneExercise().problemStatement).toBe('# Rewritten description');
    });

    it('should send the edited description to the server when saving', async () => {
        await setUp('2');
        const update = vi.spyOn(milestoneExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: milestoneExercise })));

        comp.updateProblemStatement('# Rewritten description');
        comp.save();

        expect(update).toHaveBeenCalledOnce();
        expect(update.mock.calls[0][0].problemStatement).toBe('# Rewritten description');
    });

    // The grading section is the plain programming exercise one, minus the two point inputs: both totals are sums over the user
    // stories, so typing them here would be overwritten by the next child change.
    describe('grading section', () => {
        it('should hide the points and bonus points inputs', async () => {
            await setUp('2');

            expect(comp['gradingFieldsDisplayedRecord']()[ProgrammingExerciseInputField.POINTS]).toBe(false);
            expect(comp['gradingFieldsDisplayedRecord']()[ProgrammingExerciseInputField.BONUS_POINTS]).toBe(false);
        });

        it('should keep every other grading field of the standard form', async () => {
            await setUp('2');
            const record = comp['gradingFieldsDisplayedRecord']();

            expect(record[ProgrammingExerciseInputField.INCLUDE_EXERCISE_IN_COURSE_SCORE_CALCULATION]).toBe(true);
            expect(record[ProgrammingExerciseInputField.SUBMISSION_POLICY]).toBe(true);
            expect(record[ProgrammingExerciseInputField.TIMELINE]).toBe(true);
        });
    });

    // The milestone owns the test repository, but its tests are referenced from the user stories' problem statements. The
    // instruction status bar needs those statements or it reports every test case of the milestone as unused.
    describe('test cases covered by the user stories', () => {
        it('should hand the user story problem statements to the instruction editor', async () => {
            await setUp('2', [
                { id: 3, problemStatement: '[task][Sort](testBubbleSort)' } as UserStoryExercise,
                { id: 4, problemStatement: '[task][Merge](testMergeSort)' } as UserStoryExercise,
            ]);

            expect(comp['userStoryProblemStatements']()).toEqual(['[task][Sort](testBubbleSort)', '[task][Merge](testMergeSort)']);
        });

        it('should fall back to an empty string for a user story without a problem statement', async () => {
            await setUp('2', [{ id: 3 } as UserStoryExercise]);

            expect(comp['userStoryProblemStatements']()).toEqual(['']);
        });

        it('should hand over nothing for a milestone without user stories', async () => {
            await setUp('2', []);

            expect(comp['userStoryProblemStatements']()).toHaveLength(0);
        });
    });

    it('should send the description when creating a new milestone', async () => {
        await setUp();
        const create = vi.spyOn(milestoneExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: milestoneExercise })));

        comp.updateProblemStatement('# Brand new description');
        comp.save();

        expect(create).toHaveBeenCalledOnce();
        expect(create.mock.calls[0][0].problemStatement).toBe('# Brand new description');
    });
});
