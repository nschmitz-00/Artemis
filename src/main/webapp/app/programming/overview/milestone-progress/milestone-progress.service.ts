import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { MilestoneProgress } from 'app/programming/shared/entities/milestone-progress.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';

/**
 * Loads the story-by-story progress a student has on a Milestone.
 *
 * Separate from MilestoneExerciseService, which is the editor-facing service under `manage/`: this endpoint is student-level
 * and always reports on the requesting user.
 */
@Injectable({ providedIn: 'root' })
export class MilestoneProgressService {
    private readonly http = inject(HttpClient);

    private readonly resourceUrl = 'api/programming/milestone-exercises';

    /**
     * Gets the requesting student's progress on the given Milestone: one entry per user story plus the aggregate over them.
     * @param milestoneExerciseId of the MilestoneExercise to report on
     */
    getProgress(milestoneExerciseId: number): Observable<MilestoneProgress> {
        return this.http.get<MilestoneProgress>(`${this.resourceUrl}/${milestoneExerciseId}/progress`).pipe(map((progress) => MilestoneProgressService.convertDates(progress)));
    }

    /**
     * Turns the ISO date strings the server sends into dayjs objects. Mutates the freshly parsed response rather than copying
     * it - nothing else holds a reference to it yet.
     */
    private static convertDates(progress: MilestoneProgress): MilestoneProgress {
        progress.userStories?.forEach((userStory) => {
            userStory.latestResultCompletionDate = convertDateFromServer(userStory.latestResultCompletionDate);
        });
        return progress;
    }
}
