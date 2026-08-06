import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';

export type EntityResponseType = HttpResponse<MilestoneExercise>;
export type EntityArrayResponseType = HttpResponse<MilestoneExercise[]>;

/**
 * A MilestoneExercise is a ProgrammingExercise (see MilestoneExercise.java), so date/category conversion and the
 * repository/build-plan setup request shape are reused from ProgrammingExerciseService rather than reimplemented.
 */
@Injectable({ providedIn: 'root' })
export class MilestoneExerciseService {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    public resourceUrl = 'api/programming/milestone-exercises';

    /**
     * Sets up a new MilestoneExercise, including its three repositories and build plan.
     * @param milestoneExercise which should be set up
     */
    create(milestoneExercise: MilestoneExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(milestoneExercise);
        return this.http
            .post<MilestoneExercise>(`${this.resourceUrl}/setup`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processEntityResponse(res)));
    }

    /**
     * Updates an existing MilestoneExercise's non-derived fields. maxPoints is always derived from the
     * UserStoryExercise children and is ignored even if set on the passed exercise.
     * @param milestoneExercise which should be updated
     */
    update(milestoneExercise: MilestoneExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(milestoneExercise);
        return this.http
            .put<MilestoneExercise>(`${this.resourceUrl}/${milestoneExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processEntityResponse(res)));
    }

    /**
     * Finds the MilestoneExercise for the given exerciseId, including its UserStoryExercise children in one call.
     * @param milestoneExerciseId of the MilestoneExercise to retrieve
     */
    find(milestoneExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<MilestoneExercise>(`${this.resourceUrl}/${milestoneExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processEntityResponse(res)));
    }

    /**
     * Gets all MilestoneExercises of a course (for the course management exercise list).
     * @param courseId of the course
     */
    findAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<MilestoneExercise[]>(`api/programming/courses/${courseId}/milestone-exercises`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => {
                ExerciseService.convertExerciseArrayDatesFromServer(res);
                ExerciseService.convertExerciseCategoryArrayFromServer(res);
                return res;
            }),
        );
    }

    /**
     * Deletes a MilestoneExercise - reuses the regular ProgrammingExercise deletion endpoint since a Milestone owns
     * real repositories/build plans exactly like any other programming exercise.
     * @param milestoneExerciseId of the MilestoneExercise to delete
     */
    delete(milestoneExerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/programming/programming-exercises/${milestoneExerciseId}`, { observe: 'response' });
    }

    private convertDataFromClient(milestoneExercise: MilestoneExercise): MilestoneExercise {
        return this.programmingExerciseService.convertDataFromClient(milestoneExercise);
    }

    private processEntityResponse(exerciseRes: EntityResponseType): EntityResponseType {
        ProgrammingExerciseService.convertProgrammingExerciseResponseDatesFromServer(exerciseRes);
        ExerciseService.convertExerciseCategoriesFromServer(exerciseRes);
        this.exerciseService.setAccessRightsExerciseEntityResponseType(exerciseRes);
        this.exerciseService.sendExerciseTitleToTitleService(exerciseRes?.body ?? undefined);
        return exerciseRes;
    }
}
