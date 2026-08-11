import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ResultService } from 'app/exercise/result/result.service';

/**
 * One user story of a milestone submission, as the tutor assesses it: what it is worth, which participation of the student
 * carries it, and the result it already has. See UserStoryAssessmentDTO.java (server).
 */
export interface UserStoryAssessment {
    userStoryExerciseId: number;
    title?: string;
    shortName?: string;
    maxPoints?: number;
    bonusPoints?: number;
    participationId: number;
    latestResult?: Result;
}

/**
 * The manual result a tutor authored for one user story while assessing the milestone submission.
 * See UserStoryManualResultDTO.java (server).
 */
export interface UserStoryManualResult {
    userStoryExerciseId: number;
    result: Result;
}

/**
 * Assessing a milestone means assessing all of its user stories at once: the student pushes once, every user story is graded
 * from that same submission, so the tutor works through it in one session and the feedback is distributed over the user story
 * results. See MilestoneAssessmentResource.java (server).
 */
@Injectable({ providedIn: 'root' })
export class MilestoneAssessmentService {
    private readonly http = inject(HttpClient);
    private readonly resultService = inject(ResultService);

    private readonly resourceUrl = 'api/programming/milestone-exercises';

    /**
     * Gets the user stories of the given milestone submission together with their reachable points and their latest result.
     * @param milestoneExerciseId of the milestone being assessed
     * @param participationId of the student's participation in that milestone
     */
    getUserStoryAssessments(milestoneExerciseId: number, participationId: number): Observable<UserStoryAssessment[]> {
        return this.http
            .get<UserStoryAssessment[]>(`${this.resourceUrl}/${milestoneExerciseId}/participations/${participationId}/user-story-assessments`)
            .pipe(map((userStoryAssessments) => userStoryAssessments.map((userStoryAssessment) => this.convertResultDatesFromServer(userStoryAssessment))));
    }

    /**
     * Saves - and, if submit is set, submits - the manual results the tutor authored for the user stories of one milestone submission.
     * @param milestoneExerciseId of the milestone being assessed
     * @param participationId of the student's participation in that milestone
     * @param userStoryResults one entry per user story the tutor assessed
     * @param submit whether the assessments should be submitted rather than only saved
     */
    saveAssessment(milestoneExerciseId: number, participationId: number, userStoryResults: UserStoryManualResult[], submit = false): Observable<Result[]> {
        let params = new HttpParams();
        if (submit) {
            params = params.set('submit', 'true');
        }
        const body = userStoryResults.map((userStoryResult) => ({
            userStoryExerciseId: userStoryResult.userStoryExerciseId,
            result: this.resultService.convertResultDatesFromClient(userStoryResult.result),
        }));
        return this.http.put<Result[]>(`${this.resourceUrl}/${milestoneExerciseId}/participations/${participationId}/manual-results`, body, { params });
    }

    private convertResultDatesFromServer(userStoryAssessment: UserStoryAssessment): UserStoryAssessment {
        if (userStoryAssessment.latestResult) {
            // ResultService only exposes its date conversion for a whole HttpResponse, so the result is wrapped in one here
            // rather than converting the date a second time in this service
            this.resultService.convertResultResponseDatesFromServer(new HttpResponse<Result>({ body: userStoryAssessment.latestResult }));
        }
        return userStoryAssessment;
    }
}
