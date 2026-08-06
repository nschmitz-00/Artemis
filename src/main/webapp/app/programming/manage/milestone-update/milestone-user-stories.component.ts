import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';

import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';
import { UserStoryExerciseService } from 'app/programming/manage/services/user-story-exercise.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPencilAlt, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Lists the UserStoryExercise children of a MilestoneExercise, with add/edit/remove actions - this is the in-page
 * surface satisfying "add a UserStoryExercise to a Milestone" from the specification. Adding a UserStory requires the
 * Milestone to already be persisted (it needs a real id to attach children to), so the add action is disabled until then.
 */
@Component({
    selector: 'jhi-milestone-user-stories',
    templateUrl: './milestone-user-stories.component.html',
    imports: [TranslateDirective, FaIconComponent, RouterLink, DeleteButtonDirective, ArtemisTranslatePipe],
})
export class MilestoneUserStoriesComponent {
    private readonly userStoryExerciseService = inject(UserStoryExerciseService);

    protected readonly faPlus = faPlus;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrash = faTrash;

    milestoneExercise = input.required<MilestoneExercise>();

    protected readonly dialogErrorSource = new Subject<string>();
    protected readonly dialogError$ = this.dialogErrorSource.asObservable();

    deleteUserStoryExercise(userStoryExercise: UserStoryExercise) {
        this.userStoryExerciseService.delete(userStoryExercise.id!).subscribe({
            next: () => {
                this.milestoneExercise().userStoryExercises = this.milestoneExercise().userStoryExercises?.filter((u) => u.id !== userStoryExercise.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
