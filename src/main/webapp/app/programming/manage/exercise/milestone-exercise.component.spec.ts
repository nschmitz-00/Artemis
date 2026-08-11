import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';

import { MilestoneExerciseComponent } from 'app/programming/manage/exercise/milestone-exercise.component';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockProvider } from 'ng-mocks';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('MilestoneExercise Management Component', () => {
    const course = { id: 123, title: 'Course' } as Course;

    const buildMilestoneExercise = (id: number, title: string, maxPoints: number, dueDate: dayjs.Dayjs, numberOfUserStoryExercises?: number): MilestoneExercise => {
        const milestoneExercise = new MilestoneExercise(course, undefined);
        milestoneExercise.id = id;
        milestoneExercise.title = title;
        milestoneExercise.maxPoints = maxPoints;
        milestoneExercise.dueDate = dueDate;
        milestoneExercise.numberOfUserStoryExercises = numberOfUserStoryExercises;
        // The list endpoint is tutor-level, so the row actions are gated on the per-exercise rights the component derives from
        // the course. MockAccountService#setAccessRightsForExercise is a no-op, so they have to be set here.
        milestoneExercise.isAtLeastTutor = true;
        milestoneExercise.isAtLeastEditor = true;
        milestoneExercise.isAtLeastInstructor = true;
        return milestoneExercise;
    };

    /** Strips an exercise down to the rights a tutor has, the way the component would for a tutor of the course. */
    const asTutorOnly = (exercise: MilestoneExercise): MilestoneExercise => {
        exercise.isAtLeastEditor = false;
        exercise.isAtLeastInstructor = false;
        return exercise;
    };

    let comp: MilestoneExerciseComponent;
    let fixture: ComponentFixture<MilestoneExerciseComponent>;
    let milestoneExerciseService: MilestoneExerciseService;
    let alertService: AlertService;
    // ng-mocks stubs are plain functions, so the assertions below need an explicit spy
    let broadcast: ReturnType<typeof vi.spyOn>;

    let milestoneExercise: MilestoneExercise;
    let otherMilestoneExercise: MilestoneExercise;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(async () => {
        // Deliberately not in sorted order for any of the three sortable columns, so sorting has to actually reorder them
        milestoneExercise = buildMilestoneExercise(456, 'Zebra Milestone', 10, dayjs('2026-09-01'), 3);
        otherMilestoneExercise = buildMilestoneExercise(457, 'Alpha Milestone', 5, dayjs('2026-08-01'), 0);

        await TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: DialogService, useClass: MockDialogService },
                LocalStorageService,
                SessionStorageService,
                MockProvider(EventManager),
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(MilestoneExerciseComponent);
        comp = fixture.componentInstance;
        milestoneExerciseService = TestBed.inject(MilestoneExerciseService);
        alertService = TestBed.inject(AlertService);
        broadcast = vi.spyOn(TestBed.inject(EventManager), 'broadcast');

        vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [milestoneExercise, otherMilestoneExercise] })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** Runs the component's normal init path with the course already provided, so no course lookup is needed. */
    const initWithCourse = (courseToUse: Course = course) => {
        fixture.componentRef.setInput('course', courseToUse);
        fixture.detectChanges();
    };

    /** The selection checkboxes are only rendered for instructors, so those tests need an instructor course. */
    const initAsInstructor = () => initWithCourse({ ...course, isAtLeastInstructor: true } as Course);

    const checkboxFor = (id: number): HTMLInputElement | null => fixture.nativeElement.querySelector(`#select-milestone-exercise-${id}`);

    /** The href the router renders for the title link of the given milestone exercise, or undefined if the title is plain text. */
    const titleHref = (id: number): string | undefined => fixture.nativeElement.querySelector(`#milestone-exercise-${id}-title a`)?.getAttribute('href') ?? undefined;

    it('should load the milestone exercises of the course on init', () => {
        initWithCourse();

        expect(milestoneExerciseService.findAllForCourse).toHaveBeenCalledExactlyOnceWith(course.id);
        expect(comp.milestoneExercises()).toHaveLength(2);
        expect(comp.filteredMilestoneExercises()).toHaveLength(2);
        // The list endpoint returns exercises without their course, which the row links depend on
        expect(comp.milestoneExercises()[0].course).toEqual(course);
    });

    it('should show an alert when loading the milestone exercises fails', () => {
        const alertError = vi.spyOn(alertService, 'error').mockImplementation(vi.fn());
        vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        initWithCourse();

        expect(alertError).toHaveBeenCalledExactlyOnceWith('error.http.404');
        expect(comp.milestoneExercises()).toHaveLength(0);
    });

    it('should only keep the milestone exercises matching the filter', () => {
        initWithCourse();

        comp.filter = new ExerciseFilter('Alpha');
        comp['applyFilter']();

        expect(comp.milestoneExercises()).toHaveLength(2);
        expect(comp.filteredMilestoneExercises()).toEqual([otherMilestoneExercise]);
    });

    it('should broadcast the list modification event after deleting a milestone exercise', () => {
        vi.spyOn(milestoneExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        initWithCourse();

        comp.deleteMilestoneExercise(milestoneExercise);

        expect(milestoneExerciseService.delete).toHaveBeenCalledExactlyOnceWith(milestoneExercise.id);
        expect(broadcast).toHaveBeenCalledWith(expect.objectContaining({ name: 'milestoneExerciseListModification' }));
    });

    it('should forward the error to the delete dialog when deleting fails', () => {
        vi.spyOn(milestoneExerciseService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        const dialogError = vi.fn();
        comp.dialogError$.subscribe(dialogError);
        initWithCourse();

        comp.deleteMilestoneExercise(milestoneExercise);

        expect(dialogError).toHaveBeenCalledOnce();
        expect(broadcast).not.toHaveBeenCalled();
    });

    describe('Add user story button', () => {
        /** The href the router renders for the "Add User Story" link of the given milestone exercise. */
        const addUserStoryHref = (id: number): string | undefined =>
            fixture.nativeElement.querySelector(`#milestone-exercise-${id}-add-user-story`)?.getAttribute('href') ?? undefined;

        it('should link to the user story creation page of the respective milestone exercise', () => {
            initWithCourse();

            expect(addUserStoryHref(milestoneExercise.id!)).toBe(`/course-management/${course.id}/milestone-exercises/${milestoneExercise.id}/user-story-exercises/new`);
            expect(addUserStoryHref(otherMilestoneExercise.id!)).toBe(`/course-management/${course.id}/milestone-exercises/${otherMilestoneExercise.id}/user-story-exercises/new`);
        });

        it('should be rendered next to the edit and delete buttons', () => {
            initWithCourse();

            const buttonGroup = fixture.nativeElement.querySelector(`#exercise-card-${milestoneExercise.id} .flex-btn-group-container`);
            const addUserStoryButton = buttonGroup.querySelector(`#milestone-exercise-${milestoneExercise.id}-add-user-story`);
            const editButton = buttonGroup.querySelector(`a[href$="/milestone-exercises/${milestoneExercise.id}/edit"]`);
            const deleteButton = buttonGroup.querySelector('#delete-exercise');

            expect(addUserStoryButton).not.toBeNull();
            expect(editButton).not.toBeNull();
            expect(deleteButton).not.toBeNull();
        });

        it('should not be rendered when the course has no milestone exercises', () => {
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));

            initWithCourse();

            expect(fixture.nativeElement.querySelectorAll('[id$="-add-user-story"]')).toHaveLength(0);
        });
    });

    describe('Edit in editor button', () => {
        /** A milestone exercise as the list endpoint returns it for an editor: with its template participation fetched. */
        const buildWithTemplateParticipation = (id: number, templateParticipationId: number): MilestoneExercise => {
            const exercise = buildMilestoneExercise(id, 'Milestone', 10, dayjs('2026-09-01'), 1);
            exercise.templateParticipation = { id: templateParticipationId } as TemplateProgrammingExerciseParticipation;
            return exercise;
        };

        const editInEditorHref = (id: number): string | undefined =>
            fixture.nativeElement.querySelector(`#milestone-exercise-${id}-edit-in-editor`)?.getAttribute('href') ?? undefined;

        it('should link to the code editor for the template repository', () => {
            const exercise = buildWithTemplateParticipation(456, 42);
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [exercise] })));

            initWithCourse();

            expect(editInEditorHref(exercise.id!)).toBe(`/course-management/${course.id}/programming-exercises/${exercise.id}/code-editor/TEMPLATE/42`);
        });

        it('should be rendered next to the other row actions', () => {
            const exercise = buildWithTemplateParticipation(456, 42);
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [exercise] })));

            initWithCourse();

            const buttonGroup = fixture.nativeElement.querySelector(`#exercise-card-${exercise.id} .flex-btn-group-container`);
            expect(buttonGroup.querySelector(`#milestone-exercise-${exercise.id}-edit-in-editor`)).not.toBeNull();
            expect(buttonGroup.querySelector('#delete-exercise')).not.toBeNull();
        });

        /**
         * The route addresses the template repository by the participation id, so an exercise whose repositories have not been
         * provisioned (the list endpoint then sends no participation at all) must not render a link to nowhere.
         */
        it('should not be rendered when the exercise has no template participation', () => {
            milestoneExercise.templateParticipation = undefined;
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [milestoneExercise] })));

            initWithCourse();

            expect(editInEditorHref(milestoneExercise.id!)).toBeUndefined();
        });

        it('should not be rendered for users below editor', () => {
            const exercise = asTutorOnly(buildWithTemplateParticipation(456, 42));
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [exercise] })));

            initWithCourse();

            expect(editInEditorHref(exercise.id!)).toBeUndefined();
        });

        it('should set the access rights on the loaded exercises', () => {
            const setAccessRights = vi.spyOn(TestBed.inject(AccountService), 'setAccessRightsForExercise');

            initWithCourse();

            expect(setAccessRights).toHaveBeenCalledTimes(2);
            // The course is what the access rights are derived from, so it has to be reconnected first
            expect(setAccessRights.mock.calls[0][0].course).toEqual(course);
        });
    });

    // The list endpoint is tutor-level so that tutors can open the course exercises page at all; the table has to be read-only
    // for them, since every write action behind these buttons still requires editor or instructor rights.
    describe('Read-only view for tutors', () => {
        const initAsTutor = () => {
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [asTutorOnly(milestoneExercise)] })));
            initWithCourse();
        };

        it('should still list the milestone exercises', () => {
            initAsTutor();

            expect(comp.filteredMilestoneExercises()).toHaveLength(1);
            expect(fixture.nativeElement.querySelector(`#milestone-exercise-${milestoneExercise.id}-title`).textContent.trim()).toBe(milestoneExercise.title);
        });

        it('should link the title to the read-only detail page, which is tutor-level as well', () => {
            initAsTutor();

            expect(titleHref(milestoneExercise.id!)).toBe(`/course-management/${course.id}/milestone-exercises/${milestoneExercise.id}`);
        });

        it('should render the title as plain text for users below tutor', () => {
            const withoutRights = asTutorOnly(milestoneExercise);
            withoutRights.isAtLeastTutor = false;
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [withoutRights] })));

            initWithCourse();

            expect(titleHref(milestoneExercise.id!)).toBeUndefined();
            expect(fixture.nativeElement.querySelector(`#milestone-exercise-${milestoneExercise.id}-title`).textContent.trim()).toBe(milestoneExercise.title);
        });

        it('should not offer the edit, add user story, or delete actions', () => {
            initAsTutor();

            const buttonGroup = fixture.nativeElement.querySelector(`#exercise-card-${milestoneExercise.id} .flex-btn-group-container`);
            expect(buttonGroup.querySelector(`#milestone-exercise-${milestoneExercise.id}-add-user-story`)).toBeNull();
            expect(buttonGroup.querySelector(`a[href$="/milestone-exercises/${milestoneExercise.id}/edit"]`)).toBeNull();
            expect(buttonGroup.querySelector('#delete-exercise')).toBeNull();
        });
    });

    describe('Selecting multiple milestone exercises', () => {
        it('should render a checkbox per milestone exercise plus a select-all checkbox for instructors', () => {
            initAsInstructor();

            expect(fixture.nativeElement.querySelector('#select-all-milestone-exercises')).not.toBeNull();
            expect(checkboxFor(milestoneExercise.id!)).not.toBeNull();
            expect(checkboxFor(otherMilestoneExercise.id!)).not.toBeNull();
        });

        it('should not render any checkbox for non-instructors', () => {
            initWithCourse();

            expect(fixture.nativeElement.querySelector('#select-all-milestone-exercises')).toBeNull();
            expect(checkboxFor(milestoneExercise.id!)).toBeNull();
        });

        it('should select and deselect a single milestone exercise when its checkbox is clicked', () => {
            initAsInstructor();

            checkboxFor(milestoneExercise.id!)!.click();
            fixture.detectChanges();

            expect(comp.selectedExercises()).toEqual([milestoneExercise]);
            expect(comp.allChecked()).toBeFalsy();

            checkboxFor(milestoneExercise.id!)!.click();
            fixture.detectChanges();

            expect(comp.selectedExercises()).toHaveLength(0);
        });

        it('should select every milestone exercise when the select-all checkbox is clicked, and clear the selection on a second click', () => {
            initAsInstructor();

            fixture.nativeElement.querySelector('#select-all-milestone-exercises').click();
            fixture.detectChanges();

            expect(comp.selectedExercises()).toEqual([milestoneExercise, otherMilestoneExercise]);
            expect(comp.allChecked()).toBeTruthy();

            fixture.nativeElement.querySelector('#select-all-milestone-exercises').click();
            fixture.detectChanges();

            expect(comp.selectedExercises()).toHaveLength(0);
        });

        it('should only offer the bulk delete once something is selected', () => {
            initAsInstructor();
            expect(fixture.nativeElement.querySelector('#delete-all-milestone-exercises')).toBeNull();

            checkboxFor(milestoneExercise.id!)!.click();
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('#delete-all-milestone-exercises')).not.toBeNull();
        });

        it('should delete every selected milestone exercise and broadcast the list modification once', () => {
            const deleteSpy = vi.spyOn(milestoneExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            initAsInstructor();

            fixture.nativeElement.querySelector('#select-all-milestone-exercises').click();
            fixture.detectChanges();
            comp.deleteMultipleExercises(comp.selectedExercises(), comp['milestoneExerciseService']);

            expect(deleteSpy.mock.calls.map((call) => call[0])).toEqual([milestoneExercise.id, otherMilestoneExercise.id]);
            expect(broadcast).toHaveBeenCalledWith(expect.objectContaining({ name: 'milestoneExerciseListModification' }));
        });
    });

    describe('User story count column', () => {
        const userStoryCountFor = (id: number): string | undefined => fixture.nativeElement.querySelector(`#milestone-exercise-${id}-userStoryCount`)?.textContent?.trim();

        it('should show the number of user stories the server reported for each milestone exercise', () => {
            initWithCourse();

            expect(userStoryCountFor(milestoneExercise.id!)).toBe('3');
            expect(userStoryCountFor(otherMilestoneExercise.id!)).toBe('0');
        });

        it('should fall back to zero when the server did not report a count', () => {
            const withoutCount = buildMilestoneExercise(458, 'No Count Milestone', 1, dayjs('2026-08-15'), undefined);
            vi.spyOn(milestoneExerciseService, 'findAllForCourse').mockReturnValue(of(new HttpResponse({ body: [withoutCount] })));

            initWithCourse();

            expect(userStoryCountFor(withoutCount.id!)).toBe('0');
        });
    });

    describe('Sorting the milestone exercise columns', () => {
        /** Clicks the header cell carrying the given jhiSortBy key, the way a user sorts a column. */
        const clickHeader = (sortBy: string) => {
            fixture.nativeElement.querySelector(`th[jhiSortBy="${sortBy}"]`).click();
            fixture.detectChanges();
        };

        const renderedTitles = (): string[] => [...fixture.nativeElement.querySelectorAll('tbody tr [id$="-title"]')].map((cell) => (cell as HTMLElement).textContent!.trim());

        it('should offer a sortable header for title, dates, and points', () => {
            initWithCourse();

            expect(fixture.nativeElement.querySelector('th[jhiSortBy="title"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('th[jhiSortBy="dueDate"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('th[jhiSortBy="maxPoints"]')).not.toBeNull();
        });

        it('should sort by title ascending on the first click and descending on the second', () => {
            initWithCourse();
            expect(renderedTitles()).toEqual(['Zebra Milestone', 'Alpha Milestone']);

            clickHeader('title');
            expect(renderedTitles()).toEqual(['Alpha Milestone', 'Zebra Milestone']);

            clickHeader('title');
            expect(renderedTitles()).toEqual(['Zebra Milestone', 'Alpha Milestone']);
        });

        it('should sort by due date', () => {
            initWithCourse();

            clickHeader('dueDate');

            expect(comp.filteredMilestoneExercises().map((exercise) => exercise.id)).toEqual([otherMilestoneExercise.id, milestoneExercise.id]);
        });

        it('should sort by max points', () => {
            initWithCourse();

            clickHeader('maxPoints');

            expect(comp.filteredMilestoneExercises().map((exercise) => exercise.maxPoints)).toEqual([5, 10]);
        });

        it('should reorder the filtered list too, and keep the filter applied', () => {
            initWithCourse();
            // Matches both exercises, so the new order has to reach the filtered list that the table actually renders
            comp.filter = new ExerciseFilter('Milestone');
            comp['applyFilter']();
            expect(renderedTitles()).toEqual(['Zebra Milestone', 'Alpha Milestone']);

            clickHeader('title');

            expect(renderedTitles()).toEqual(['Alpha Milestone', 'Zebra Milestone']);
            expect(comp.filteredMilestoneExercises()).toHaveLength(2);
        });
    });
});
