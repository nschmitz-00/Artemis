import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ExerciseService } from 'app/exercise/services/exercise.service';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';

export type EntityResponseType = HttpResponse<UserStoryExercise>;

/**
 * A UserStoryExercise has no repositories or build plan of its own (see UserStoryExercise.java, server) - unlike
 * MilestoneExerciseService, none of these calls touch repository setup at all, they are plain CRUD.
 */
@Injectable({ providedIn: 'root' })
export class UserStoryExerciseService {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    public resourceUrl = 'api/programming/user-story-exercises';

    /**
     * Creates a new UserStoryExercise under a MilestoneExercise.
     * @param milestoneExerciseId the id of the parent MilestoneExercise
     * @param userStoryExercise the UserStoryExercise to create (title, shortName, maxPoints, problemStatement)
     */
    create(milestoneExerciseId: number, userStoryExercise: UserStoryExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(userStoryExercise);
        return this.http
            .post<UserStoryExercise>(`api/programming/milestone-exercises/${milestoneExerciseId}/user-story-exercises`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processEntityResponse(res)));
    }

    /**
     * Updates an existing UserStoryExercise's title, short name, max points, and problem statement.
     * @param userStoryExercise the UserStoryExercise carrying the updated fields
     */
    update(userStoryExercise: UserStoryExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(userStoryExercise);
        return this.http
            .put<UserStoryExercise>(`${this.resourceUrl}/${userStoryExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processEntityResponse(res)));
    }

    /**
     * Finds the UserStoryExercise for the given exerciseId, with its parent MilestoneExercise eagerly loaded.
     * @param userStoryExerciseId of the UserStoryExercise to retrieve
     */
    find(userStoryExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<UserStoryExercise>(`${this.resourceUrl}/${userStoryExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processEntityResponse(res)));
    }

    /**
     * Deletes a UserStoryExercise (a plain DB-row delete) and recalculates the parent Milestone's total max points.
     * @param userStoryExerciseId of the UserStoryExercise to delete
     */
    delete(userStoryExerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${userStoryExerciseId}`, { observe: 'response' });
    }

    private convertDataFromClient(userStoryExercise: UserStoryExercise): UserStoryExercise {
        return this.programmingExerciseService.convertDataFromClient(userStoryExercise);
    }

    private processEntityResponse(exerciseRes: EntityResponseType): EntityResponseType {
        ProgrammingExerciseService.convertProgrammingExerciseResponseDatesFromServer(exerciseRes);
        ExerciseService.convertExerciseCategoriesFromServer(exerciseRes);
        this.exerciseService.setAccessRightsExerciseEntityResponseType(exerciseRes);
        return exerciseRes;
    }
}
