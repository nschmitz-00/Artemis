import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { formatEffortHours } from 'app/course/overview/exercise-details/user-story-tasks/task-effort-format.util';

/**
 * The contents of one reported-effort information box: the number summed from the student's task board, rendered as
 * `hh:mm` - the same format the task tiles use - or a placeholder while their board has nothing to sum yet.
 *
 * Purely presentational and read-only - the value comes from the header, which owns the request.
 */
@Component({
    selector: 'jhi-user-story-effort-field',
    templateUrl: './user-story-effort-field.component.html',
    imports: [ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserStoryEffortFieldComponent {
    readonly value = input<number | undefined>(undefined);

    protected readonly displayValue = computed(() => {
        const value = this.value();
        return value === undefined ? undefined : formatEffortHours(value);
    });
}
