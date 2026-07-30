import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';

export interface ProgrammingExerciseExplanationVideoDTO {
    explanationVideoPath?: string;
    explanationVideoUploadDate?: dayjs.Dayjs;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseExplanationVideoService {
    private http = inject(HttpClient);

    private resourceUrl(participationId: number): string {
        return `api/programming/programming-exercise-participations/${participationId}/explanation-video`;
    }

    /**
     * Uploads (or replaces) the explanation video for the given participation.
     * The response body is the new explanation video path and upload date, so the caller can update its local
     * view immediately without needing to refetch the whole participation/exercise.
     */
    upload(participationId: number, file: File): Observable<HttpResponse<ProgrammingExerciseExplanationVideoDTO>> {
        const formData = new FormData();
        formData.append('file', file);
        return this.http.post<ProgrammingExerciseExplanationVideoDTO>(this.resourceUrl(participationId), formData, { observe: 'response' });
    }

    /**
     * Deletes the explanation video for the given participation.
     */
    delete(participationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(this.resourceUrl(participationId), { observe: 'response' });
    }

    /**
     * URL that can be used directly as a <video> element's src to stream the explanation video.
     * The browser sends the session cookie automatically, so no separate fetch/blob handling is needed.
     */
    videoUrl(participationId: number): string {
        return this.resourceUrl(participationId);
    }
}
