import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ExerciseComponent } from 'app/exercise/exercise.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faPencilAlt, faTimes, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-milestone-exercise',
    templateUrl: './milestone-exercise.component.html',
    imports: [TranslateDirective, FaIconComponent, RouterLink, ExerciseCategoriesComponent, DeleteButtonDirective, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class MilestoneExerciseComponent extends ExerciseComponent {
    private readonly milestoneExerciseService = inject(MilestoneExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly profileService = inject(ProfileService);

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faWrench = faWrench;
    protected readonly faTrash = faTrash;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly RepositoryType = RepositoryType;

    readonly onlineIdeEnabled = signal(false);

    milestoneExercises = signal<MilestoneExercise[]>([]);
    filteredMilestoneExercises = signal<MilestoneExercise[]>([]);

    protected get exercises(): Exercise[] {
        return this.milestoneExercises();
    }

    protected loadExercises(): void {
        this.onlineIdeEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_THEIA));
        this.milestoneExerciseService.findAllForCourse(this.courseId()).subscribe({
            next: (res) => {
                const milestoneExercises = res.body ?? [];
                milestoneExercises.forEach((milestoneExercise) => (milestoneExercise.course = this.courseContext()));
                this.milestoneExercises.set(milestoneExercises);
                this.emitExerciseCount(this.milestoneExercises().length);
                this.applyFilter();
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    fetchExerciseDeletionSummary(milestoneExercise: MilestoneExercise): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(milestoneExercise);
    }

    protected applyFilter(): void {
        this.filteredMilestoneExercises.set(this.milestoneExercises().filter((milestoneExercise) => this.filter.matchesExercise(milestoneExercise)));
        this.emitFilteredExerciseCount(this.filteredMilestoneExercises().length);
    }

    protected getChangeEventName(): string {
        return 'milestoneExerciseListModification';
    }

    deleteMilestoneExercise(milestoneExercise: MilestoneExercise) {
        this.milestoneExerciseService.delete(milestoneExercise.id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: this.getChangeEventName(),
                    content: 'Deleted a milestone exercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
