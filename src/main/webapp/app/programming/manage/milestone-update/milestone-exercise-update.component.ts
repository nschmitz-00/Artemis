import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { loadCourseExerciseCategories } from 'app/exercise/course-exercises/course-utils';
import { EXERCISE_TITLE_NAME_PATTERN, PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN } from 'app/foundation/constants/input.constants';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseTitleChannelNamePrimengComponent } from 'app/exercise/exercise-title-channel-name-primeng/exercise-title-channel-name-primeng.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { MilestoneExerciseTimelineComponent } from 'app/programming/manage/milestone-update/milestone-exercise-timeline.component';
import { MilestoneUserStoriesComponent } from 'app/programming/manage/milestone-update/milestone-user-stories.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { MarkdownEditorHeight } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { Select } from 'primeng/select';
import { Checkbox } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-milestone-exercise-update',
    templateUrl: './milestone-exercise-update.component.html',
    styleUrls: ['../../shared/programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        ExerciseTitleChannelNamePrimengComponent,
        CategorySelectorPrimengComponent,
        MilestoneExerciseTimelineComponent,
        MilestoneUserStoriesComponent,
        ProgrammingExerciseEditableInstructionComponent,
        FormFooterComponent,
        Select,
        Checkbox,
        InputTextModule,
        ArtemisTranslatePipe,
    ],
})
export class MilestoneExerciseUpdateComponent implements OnInit {
    private readonly milestoneExerciseService = inject(MilestoneExerciseService);
    private readonly courseService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly titlePattern = EXERCISE_TITLE_NAME_PATTERN;
    protected readonly shortNamePattern = PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN;
    protected readonly programmingLanguages = Object.values(ProgrammingLanguage).filter((language) => language !== ProgrammingLanguage.EMPTY);
    protected readonly projectTypes = Object.values(ProjectType);
    // All fields are always shown (there is no simple/detailed mode toggle for MilestoneExercise, unlike ProgrammingExercise).
    protected readonly isEditFieldDisplayedRecord = Object.fromEntries(Object.values(ProgrammingExerciseInputField).map((field) => [field, true])) as Record<
        ProgrammingExerciseInputField,
        boolean
    >;

    milestoneExercise = signal<MilestoneExercise>(undefined!);
    isSaving = signal(false);
    isCreate = signal(false);
    existingCategories = signal<ExerciseCategory[]>([]);

    ngOnInit(): void {
        const exerciseIdParam = this.activatedRoute.snapshot.paramMap.get('exerciseId');
        const courseIdParam = this.activatedRoute.snapshot.paramMap.get('courseId');
        this.isCreate.set(!exerciseIdParam);

        if (exerciseIdParam) {
            this.milestoneExerciseService.find(Number(exerciseIdParam)).subscribe({
                next: (res: HttpResponse<MilestoneExercise>) => {
                    this.milestoneExercise.set(res.body!);
                    this.loadCategories(res.body!.course?.id);
                },
                error: (error: HttpErrorResponse) => this.alertService.addErrorAlert(error.message),
            });
        } else if (courseIdParam) {
            this.courseService.find(Number(courseIdParam)).subscribe((res) => {
                const newMilestoneExercise = new MilestoneExercise(res.body!, undefined);
                this.milestoneExercise.set(newMilestoneExercise);
                this.loadCategories(res.body!.id);
            });
        }
    }

    private loadCategories(courseId?: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories.set(existingCategories);
        });
    }

    /**
     * The problem statement is what students see when they open the Milestone (the exercise details page renders it for
     * every non-quiz exercise type), so this doubles as the Milestone's description.
     */
    updateProblemStatement(problemStatement: string) {
        this.milestoneExercise.update((milestoneExercise) => {
            milestoneExercise.problemStatement = problemStatement;
            return milestoneExercise;
        });
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.milestoneExercise.update((milestoneExercise) => {
            milestoneExercise.categories = categories;
            return milestoneExercise;
        });
    }

    save() {
        if (this.isSaving()) {
            return;
        }
        this.isSaving.set(true);
        Exercise.sanitize(this.milestoneExercise());

        const saveObservable = this.milestoneExercise().id
            ? this.milestoneExerciseService.update(this.milestoneExercise())
            : this.milestoneExerciseService.create(this.milestoneExercise());

        saveObservable.subscribe({
            next: () => {
                this.isSaving.set(false);
                this.navigationUtilService.navigateBack(['/course-management', this.milestoneExercise().course!.id!, 'exercises']);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                this.alertService.addAlert({ type: AlertType.DANGER, message: error.headers.get('X-artemisApp-alert') ?? 'error.unexpectedError', disableTranslation: true });
            },
        });
    }

    previousState() {
        this.navigationUtilService.navigateBack(['/course-management', this.milestoneExercise().course!.id!, 'exercises']);
    }
}
