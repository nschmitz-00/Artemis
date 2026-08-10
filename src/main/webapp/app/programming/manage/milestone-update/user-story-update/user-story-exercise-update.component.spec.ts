import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { UserStoryExerciseUpdateComponent } from 'app/programming/manage/milestone-update/user-story-update/user-story-exercise-update.component';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';
import { UserStoryExerciseService } from 'app/programming/manage/services/user-story-exercise.service';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProvider } from 'ng-mocks';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';

describe('UserStoryExerciseUpdate Component', () => {
    const course = { id: 123 } as Course;

    let fixture: ComponentFixture<UserStoryExerciseUpdateComponent>;
    let userStoryExerciseService: UserStoryExerciseService;
    let milestoneExercise: MilestoneExercise;
    let navigate: ReturnType<typeof vi.spyOn>;
    let navigateBack: ReturnType<typeof vi.spyOn>;

    const milestoneEditRoute = () => ['/course-management', course.id, 'milestone-exercises', 2, 'edit'];

    /** Sets the component up in creation mode, or - when an existing story is passed - in edit mode for it. */
    const setUp = async (existingUserStoryExercise?: UserStoryExercise) => {
        milestoneExercise = new MilestoneExercise(course, undefined);
        milestoneExercise.id = 2;
        milestoneExercise.title = 'Milestone';

        const paramMap = convertToParamMap(
            existingUserStoryExercise ? { milestoneExerciseId: '2', userStoryExerciseId: String(existingUserStoryExercise.id) } : { milestoneExerciseId: '2' },
        );

        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(AlertService),
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                // Must come after provideRouter, which provides an ActivatedRoute of its own that would win otherwise
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap } } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(UserStoryExerciseUpdateComponent);
        userStoryExerciseService = TestBed.inject(UserStoryExerciseService);
        navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        navigateBack = vi.spyOn(TestBed.inject(ArtemisNavigationUtilService), 'navigateBack');

        vi.spyOn(TestBed.inject(MilestoneExerciseService), 'find').mockReturnValue(of(new HttpResponse({ body: milestoneExercise })));
        if (existingUserStoryExercise) {
            // Must be stubbed before the first change detection, since ngOnInit loads the story straight away
            vi.spyOn(userStoryExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: existingUserStoryExercise })));
        }

        fixture.detectChanges();
    };

    /** Types into the given native input the way a user would, so the ngModel write-back path is exercised. */
    const typeInto = (id: string, value: string) => {
        const input: HTMLInputElement = fixture.nativeElement.querySelector(`#${id}`);
        input.value = value;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    };

    it('should send the configured title and short name when generating a new user story', async () => {
        await setUp();
        const create = vi.spyOn(userStoryExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: { id: 9, title: 'Story 1' } as UserStoryExercise })));

        typeInto('title', 'Story 1');
        typeInto('shortName', 'story1');

        fixture.nativeElement.querySelector('#save-entity').click();

        expect(create).toHaveBeenCalledOnce();
        expect(create.mock.calls[0][0]).toBe(milestoneExercise.id);
        expect(create.mock.calls[0][1]).toEqual(expect.objectContaining({ title: 'Story 1', shortName: 'story1' }));
    });

    // The parent milestone's points and bonus points are the sums over its user stories, so both have to be editable here -
    // they are the only place either value is ever entered.
    it('should offer both a points and a bonus points input', async () => {
        await setUp();

        expect(fixture.nativeElement.querySelector('#maxPoints')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#bonusPoints')).not.toBeNull();
    });

    it('should send the bonus points when updating a user story', async () => {
        await setUp({ id: 9, title: 'Story 1', maxPoints: 10, bonusPoints: 4 } as UserStoryExercise);
        const update = vi.spyOn(userStoryExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: { id: 9 } as UserStoryExercise })));

        fixture.nativeElement.querySelector('#save-entity').click();

        expect(update.mock.calls[0][0]).toEqual(expect.objectContaining({ maxPoints: 10, bonusPoints: 4 }));
    });

    it('should navigate forward to the milestone after creating, not back to wherever the form was opened from', async () => {
        await setUp();
        vi.spyOn(userStoryExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: { id: 9 } as UserStoryExercise })));

        typeInto('title', 'Story 1');
        fixture.nativeElement.querySelector('#save-entity').click();

        // Going back would land on the page the form was opened from (e.g. the course exercise list), which does not
        // list user stories at all - making the successful creation look like nothing happened.
        expect(navigate).toHaveBeenCalledExactlyOnceWith(milestoneEditRoute());
        expect(navigateBack).not.toHaveBeenCalled();
    });

    it('should navigate forward to the milestone after updating an existing user story', async () => {
        await setUp({ id: 9, title: 'Story 1' } as UserStoryExercise);
        const update = vi.spyOn(userStoryExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: { id: 9 } as UserStoryExercise })));

        fixture.nativeElement.querySelector('#save-entity').click();

        expect(update).toHaveBeenCalledOnce();
        expect(navigate).toHaveBeenCalledExactlyOnceWith(milestoneEditRoute());
    });

    it('should go back when cancelling instead of navigating to the milestone', async () => {
        await setUp();

        fixture.nativeElement.querySelector('#cancel-save').click();

        expect(navigateBack).toHaveBeenCalledExactlyOnceWith(milestoneEditRoute());
        expect(navigate).not.toHaveBeenCalled();
    });
});
