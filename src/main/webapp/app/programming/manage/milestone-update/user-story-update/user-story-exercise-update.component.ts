import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';
import { UserStoryExerciseService } from 'app/programming/manage/services/user-story-exercise.service';
import { EXERCISE_TITLE_NAME_PATTERN, PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN } from 'app/foundation/constants/input.constants';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';

@Component({
    selector: 'jhi-user-story-exercise-update',
    templateUrl: './user-story-exercise-update.component.html',
    imports: [TranslateDirective, FormsModule, MarkdownEditorMonacoComponent, FormFooterComponent, InputTextModule, InputNumberModule],
})
export class UserStoryExerciseUpdateComponent implements OnInit {
    private readonly milestoneExerciseService = inject(MilestoneExerciseService);
    private readonly userStoryExerciseService = inject(UserStoryExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);

    protected readonly titlePattern = EXERCISE_TITLE_NAME_PATTERN;
    protected readonly shortNamePattern = PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN;

    milestoneExercise = signal<MilestoneExercise>(undefined!);
    userStoryExercise = signal<UserStoryExercise>(undefined!);
    isSaving = signal(false);
    isCreate = signal(false);

    ngOnInit(): void {
        const milestoneExerciseId = Number(this.activatedRoute.snapshot.paramMap.get('milestoneExerciseId'));
        const userStoryExerciseId = this.activatedRoute.snapshot.paramMap.get('userStoryExerciseId');
        this.isCreate.set(!userStoryExerciseId);

        this.milestoneExerciseService.find(milestoneExerciseId).subscribe({
            next: (res: HttpResponse<MilestoneExercise>) => {
                this.milestoneExercise.set(res.body!);
                if (userStoryExerciseId) {
                    this.userStoryExerciseService.find(Number(userStoryExerciseId)).subscribe({
                        next: (userStoryRes: HttpResponse<UserStoryExercise>) => this.userStoryExercise.set(userStoryRes.body!),
                        error: (error: HttpErrorResponse) => this.alertService.addErrorAlert(error.message),
                    });
                } else {
                    const newUserStoryExercise = new UserStoryExercise(res.body!.course, undefined);
                    newUserStoryExercise.milestoneExercise = res.body!;
                    this.userStoryExercise.set(newUserStoryExercise);
                }
            },
            error: (error: HttpErrorResponse) => this.alertService.addErrorAlert(error.message),
        });
    }

    save() {
        if (this.isSaving()) {
            return;
        }
        this.isSaving.set(true);
        Exercise.sanitize(this.userStoryExercise());

        const saveObservable = this.userStoryExercise().id
            ? this.userStoryExerciseService.update(this.userStoryExercise())
            : this.userStoryExerciseService.create(this.milestoneExercise().id!, this.userStoryExercise());

        saveObservable.subscribe({
            next: () => {
                this.isSaving.set(false);
                this.navigateToMilestone();
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                this.alertService.addAlert({ type: AlertType.DANGER, message: error.headers.get('X-artemisApp-alert') ?? 'error.unexpectedError', disableTranslation: true });
            },
        });
    }

    previousState() {
        // Cancelling should return the user wherever they came from, so going back is correct here.
        this.navigationUtilService.navigateBack(this.milestoneRoute());
    }

    /**
     * Navigates to the parent Milestone after a successful save. This must be a forward navigation and must not go
     * back: the Milestone edit page is the only page that lists UserStoryExercises, so going back would drop the user
     * on whatever page they opened this form from (e.g. the course exercise list, which only lists Milestones) where
     * the story they just created is nowhere to be seen - making a successful save look like it did nothing.
     */
    private navigateToMilestone() {
        void this.router.navigate(this.milestoneRoute());
    }

    private milestoneRoute(): (string | number)[] {
        return ['/course-management', this.milestoneExercise().course!.id!, 'milestone-exercises', this.milestoneExercise().id!, 'edit'];
    }
}
