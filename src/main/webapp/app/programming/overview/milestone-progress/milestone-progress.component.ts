import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ProgressBarModule } from 'primeng/progressbar';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Message } from 'primeng/message';

import { MilestoneProgress, UserStoryProgressStatus } from 'app/programming/shared/entities/milestone-progress.model';
import { MilestoneProgressService } from 'app/programming/overview/milestone-progress/milestone-progress.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

/**
 * The story-by-story progress overview a student sees on a Milestone page: per user story its status and the points earned on
 * it, plus the aggregate over all of them.
 *
 * A Milestone carries no points of its own - its user stories are the graded units, each with its own participation and result
 * fanned out from the student's single push (see MilestoneExercise.java, server). Without this overview a student pushing to
 * the Milestone repository would see one combined result and no way to tell which user story it paid out on.
 */
@Component({
    selector: 'jhi-milestone-progress',
    templateUrl: './milestone-progress.component.html',
    imports: [ProgressBarModule, TableModule, TagModule, Message, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class MilestoneProgressComponent {
    private readonly milestoneProgressService = inject(MilestoneProgressService);

    readonly milestoneExerciseId = input.required<number>();

    private readonly progressResource = rxResource({
        params: () => this.milestoneExerciseId(),
        stream: ({ params: milestoneExerciseId }) => this.milestoneProgressService.getProgress(milestoneExerciseId),
    });

    protected readonly isLoading = computed(() => this.progressResource.isLoading());
    // Reading `value()` on a resource in the error state rethrows, so the failure has to be mapped to "nothing to show" here
    protected readonly hasFailed = computed(() => !this.progressResource.isLoading() && !this.progressResource.hasValue());
    protected readonly progress = computed((): MilestoneProgress | undefined => (this.progressResource.hasValue() ? this.progressResource.value() : undefined));
    protected readonly userStories = computed(() => this.progress()?.userStories ?? []);

    protected readonly UserStoryProgressStatus = UserStoryProgressStatus;

    /**
     * The colour the status tag of a user story is rendered in.
     * @param status of the user story
     */
    protected statusSeverity(status: UserStoryProgressStatus): 'success' | 'info' | 'secondary' {
        switch (status) {
            case UserStoryProgressStatus.COMPLETED:
                return 'success';
            case UserStoryProgressStatus.IN_PROGRESS:
                return 'info';
            default:
                return 'secondary';
        }
    }

    /**
     * The translation key naming the status of a user story.
     * @param status of the user story
     */
    protected statusTranslationKey(status: UserStoryProgressStatus): string {
        return `artemisApp.milestoneExercise.progress.status.${status}`;
    }
}
