import { Component, computed, effect, input, signal } from '@angular/core';
import { Dayjs } from 'dayjs/esm';

import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { ExerciseTimelineComponent, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';

/**
 * Thin wrapper around the generic ExerciseTimelineComponent for a MilestoneExercise's 5 dates - all optional, with no
 * ProgrammingExercise-specific business rules (unlike ProgrammingExerciseUpdateTimelineComponent, MilestoneExercise
 * needs none of the semi-automatic-assessment / after-due-date-build-phase logic that component adds).
 */
@Component({
    selector: 'jhi-milestone-exercise-timeline',
    templateUrl: './milestone-exercise-timeline.component.html',
    imports: [ExerciseTimelineComponent],
})
export class MilestoneExerciseTimelineComponent {
    exercise = input.required<MilestoneExercise>();

    releaseDate = signal<Dayjs | undefined>(undefined);
    startDate = signal<Dayjs | undefined>(undefined);
    dueDate = signal<Dayjs | undefined>(undefined);
    assessmentDueDate = signal<Dayjs | undefined>(undefined);
    exampleSolutionPublicationDate = signal<Dayjs | undefined>(undefined);

    timelineItems = computed<TimelineItem[]>(() => [
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: this.releaseDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.startDate', date: this.startDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.dueDate', date: this.dueDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.assessmentDueDate', date: this.assessmentDueDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate', date: this.exampleSolutionPublicationDate },
    ]);

    constructor() {
        // Seed the local signals whenever a (different) exercise is loaded, then keep the exercise object in sync
        // with any subsequent edits made through the date pickers.
        let seededForExerciseId: number | undefined | 'not-yet-seeded' = 'not-yet-seeded';
        effect(() => {
            const exercise = this.exercise();
            if (seededForExerciseId === exercise.id) {
                return;
            }
            seededForExerciseId = exercise.id;
            this.releaseDate.set(exercise.releaseDate);
            this.startDate.set(exercise.startDate);
            this.dueDate.set(exercise.dueDate);
            this.assessmentDueDate.set(exercise.assessmentDueDate);
            this.exampleSolutionPublicationDate.set(exercise.exampleSolutionPublicationDate);
        });
        effect(() => {
            const exercise = this.exercise();
            exercise.releaseDate = this.releaseDate();
            exercise.startDate = this.startDate();
            exercise.dueDate = this.dueDate();
            exercise.assessmentDueDate = this.assessmentDueDate();
            exercise.exampleSolutionPublicationDate = this.exampleSolutionPublicationDate();
        });
    }
}
