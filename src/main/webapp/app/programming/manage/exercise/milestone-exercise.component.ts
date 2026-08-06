import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { ExerciseComponent } from 'app/exercise/exercise.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPencilAlt, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-milestone-exercise',
    templateUrl: './milestone-exercise.component.html',
    imports: [TranslateDirective, FaIconComponent, RouterLink, ExerciseCategoriesComponent, DeleteButtonDirective, ArtemisTranslatePipe],
})
export class MilestoneExerciseComponent extends ExerciseComponent {
    private readonly milestoneExerciseService = inject(MilestoneExerciseService);
    private readonly alertService = inject(AlertService);

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrash = faTrash;

    milestoneExercises = signal<MilestoneExercise[]>([]);
    filteredMilestoneExercises = signal<MilestoneExercise[]>([]);

    protected get exercises(): Exercise[] {
        return this.milestoneExercises();
    }

    protected loadExercises(): void {
        this.milestoneExerciseService.findAllForCourse(this.courseId()).subscribe({
            next: (res) => {
                this.milestoneExercises.set(res.body ?? []);
                this.emitExerciseCount(this.milestoneExercises().length);
                this.applyFilter();
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
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
