import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';

import { ProgrammingExerciseExplanationVideoComponent } from './programming-exercise-explanation-video.component';
import { ProgrammingExerciseExplanationVideoDTO, ProgrammingExerciseExplanationVideoService } from './programming-exercise-explanation-video.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { MAX_EXPLANATION_VIDEO_FILE_SIZE } from 'app/foundation/constants/input.constants';

describe('ProgrammingExerciseExplanationVideoComponent', () => {
    let component: ProgrammingExerciseExplanationVideoComponent;
    let fixture: ComponentFixture<ProgrammingExerciseExplanationVideoComponent>;
    let explanationVideoService: ProgrammingExerciseExplanationVideoService;
    let alertService: AlertService;

    const createFile = (name: string, type: string, size = 1000): File => {
        const file = new File(['test content'], name, { type });
        Object.defineProperty(file, 'size', { value: size, writable: false });
        return file;
    };

    const selectFileEvent = (file: File): Event => ({ target: { files: [file] } }) as unknown as Event;

    const createParticipation = (overrides?: Partial<ProgrammingExerciseStudentParticipation>): ProgrammingExerciseStudentParticipation => {
        const participation = new ProgrammingExerciseStudentParticipation();
        participation.id = 111;
        if (overrides) {
            Object.assign(participation, overrides);
        }
        return participation;
    };

    const uploadResponse = (overrides?: Partial<ProgrammingExerciseExplanationVideoDTO>) =>
        of(
            new HttpResponse<ProgrammingExerciseExplanationVideoDTO>({
                status: 200,
                body: {
                    explanationVideoPath: 'programming-exercises/1/participations/111/explanation.mp4',
                    explanationVideoUploadDate: dayjs('2026-07-30T10:00:00Z'),
                    ...overrides,
                },
            }),
        );

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseExplanationVideoComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService(), { provide: DialogService, useValue: { open: vi.fn() } }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseExplanationVideoComponent);
        component = fixture.componentInstance;
        explanationVideoService = TestBed.inject(ProgrammingExerciseExplanationVideoService);
        alertService = TestBed.inject(AlertService);

        fixture.componentRef.setInput('participation', createParticipation());
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should have no video initially', () => {
        expect(component.hasVideo()).toBe(false);
        expect(component.videoUrl()).toBeUndefined();
        expect(component.explanationVideoFileName()).toBeUndefined();
    });

    it('should reflect an existing video from the participation, including file name and upload date', () => {
        fixture.componentRef.setInput(
            'participation',
            createParticipation({
                explanationVideoPath: 'programming-exercises/1/participations/111/explanation.mp4',
                explanationVideoUploadDate: dayjs('2026-07-30T10:00:00Z'),
            }),
        );
        fixture.detectChanges();

        expect(component.hasVideo()).toBe(true);
        expect(component.videoUrl()).toContain('api/programming/programming-exercise-participations/111/explanation-video');
        expect(component.explanationVideoFileName()).toBe('explanation.mp4');
        expect(component.explanationVideoUploadDate()).toEqual(dayjs('2026-07-30T10:00:00Z'));
    });

    it('should reject a file with a disallowed extension and not upload it', () => {
        const uploadSpy = vi.spyOn(explanationVideoService, 'upload');
        const alertSpy = vi.spyOn(alertService, 'error');

        component.onFileSelected(selectFileEvent(createFile('explanation.txt', 'text/plain')));

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.explanationVideo.fileExtensionError');
        expect(component.selectedFile()).toBeUndefined();
        expect(uploadSpy).not.toHaveBeenCalled();
    });

    it('should reject a file that is too large and not upload it', () => {
        const uploadSpy = vi.spyOn(explanationVideoService, 'upload');
        const alertSpy = vi.spyOn(alertService, 'error');

        component.onFileSelected(selectFileEvent(createFile('explanation.mp4', 'video/mp4', MAX_EXPLANATION_VIDEO_FILE_SIZE + 1)));

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.explanationVideo.fileTooBigError', { fileName: 'explanation.mp4' });
        expect(component.selectedFile()).toBeUndefined();
        expect(uploadSpy).not.toHaveBeenCalled();
    });

    it('regression: selecting a valid file uploads it immediately, without a separate "Upload" button click', () => {
        // This is the whole point of unifying browse+upload: a student who picks a file in the native file dialog
        // must not have to remember a second, separate step to actually submit it.
        const file = createFile('explanation.mp4', 'video/mp4');
        const uploadSpy = vi.spyOn(explanationVideoService, 'upload').mockReturnValue(uploadResponse());

        component.onFileSelected(selectFileEvent(file));

        expect(uploadSpy).toHaveBeenCalledWith(111, file);
    });

    it('should upload the selected file and emit videoUploaded on success', () => {
        const file = createFile('explanation.mp4', 'video/mp4');
        const uploadSpy = vi.spyOn(explanationVideoService, 'upload').mockReturnValue(uploadResponse());
        const alertSpy = vi.spyOn(alertService, 'success');
        const emitSpy = vi.spyOn(component.videoUploaded, 'emit');

        component.onFileSelected(selectFileEvent(file));

        expect(uploadSpy).toHaveBeenCalledWith(111, file);
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.explanationVideo.uploadSuccessful');
        expect(emitSpy).toHaveBeenCalled();
        expect(component.selectedFile()).toBeUndefined();
        expect(component.isUploading()).toBe(false);
    });

    it('regression (previously broken): should immediately show the video as uploaded after a successful upload, without the participation input changing', () => {
        // Guards against relying solely on the `participation` input for hasVideo()/videoUrl() - the parent never
        // refreshes that object after a successful upload, so the widget must track the server's response itself.
        vi.spyOn(explanationVideoService, 'upload').mockReturnValue(uploadResponse());

        expect(component.hasVideo()).toBe(false);

        component.onFileSelected(selectFileEvent(createFile('explanation.mp4', 'video/mp4')));

        // The `participation` input itself is untouched (still has no explanationVideoPath) - only the server
        // response drove the update.
        expect(component.participation().explanationVideoPath).toBeUndefined();
        expect(component.hasVideo()).toBe(true);
        expect(component.videoUrl()).toContain('api/programming/programming-exercise-participations/111/explanation-video');
    });

    it('should show the file name and upload date returned by the upload response', () => {
        vi.spyOn(explanationVideoService, 'upload').mockReturnValue(
            uploadResponse({ explanationVideoPath: 'programming-exercises/1/participations/111/my-explanation.mov', explanationVideoUploadDate: dayjs('2026-07-30T12:34:00Z') }),
        );

        component.onFileSelected(selectFileEvent(createFile('my-explanation.mov', 'video/quicktime')));

        expect(component.explanationVideoFileName()).toBe('my-explanation.mov');
        expect(component.explanationVideoUploadDate()).toEqual(dayjs('2026-07-30T12:34:00Z'));
    });

    it('should bust the video cache on re-upload so a same-named replacement video is not shown stale', () => {
        vi.spyOn(explanationVideoService, 'upload').mockReturnValue(uploadResponse());

        component.onFileSelected(selectFileEvent(createFile('explanation.mp4', 'video/mp4')));
        const firstUrl = component.videoUrl();

        component.onFileSelected(selectFileEvent(createFile('explanation.mp4', 'video/mp4')));
        const secondUrl = component.videoUrl();

        expect(firstUrl).not.toBe(secondUrl);
    });

    it('should surface an error alert if the upload fails', () => {
        vi.spyOn(explanationVideoService, 'upload').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const alertSpy = vi.spyOn(alertService, 'error');

        component.onFileSelected(selectFileEvent(createFile('explanation.mp4', 'video/mp4')));

        expect(alertSpy).toHaveBeenCalled();
        expect(component.isUploading()).toBe(false);
    });

    it('should delete the video and emit videoUploaded on success', () => {
        fixture.componentRef.setInput(
            'participation',
            createParticipation({
                explanationVideoPath: 'programming-exercises/1/participations/111/explanation.mp4',
                explanationVideoUploadDate: dayjs('2026-07-30T10:00:00Z'),
            }),
        );
        fixture.detectChanges();

        const deleteSpy = vi.spyOn(explanationVideoService, 'delete').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
        const alertSpy = vi.spyOn(alertService, 'success');
        const emitSpy = vi.spyOn(component.videoUploaded, 'emit');
        const dialogErrorValues: string[] = [];
        component.dialogError$.subscribe((value) => dialogErrorValues.push(value));

        component.deleteVideo();

        expect(deleteSpy).toHaveBeenCalledWith(111);
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.explanationVideo.deleteSuccessful');
        expect(emitSpy).toHaveBeenCalled();
        expect(component.hasVideo()).toBe(false);
        expect(component.explanationVideoFileName()).toBeUndefined();
        expect(component.explanationVideoUploadDate()).toBeUndefined();
        // The delete-confirmation dialog (jhiDeleteButton) closes itself when it receives an empty string.
        expect(dialogErrorValues).toEqual(['']);
    });

    it('should push the error message to the delete-confirmation dialog if deletion fails, keeping it open', () => {
        fixture.componentRef.setInput('participation', createParticipation({ explanationVideoPath: 'programming-exercises/1/participations/111/explanation.mp4' }));
        fixture.detectChanges();

        vi.spyOn(explanationVideoService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'deletion failed' })));
        const dialogErrorValues: string[] = [];
        component.dialogError$.subscribe((value) => dialogErrorValues.push(value));

        component.deleteVideo();

        expect(dialogErrorValues).toHaveLength(1);
        expect(dialogErrorValues[0]).not.toBe('');
        // The video must still be shown as uploaded - a failed delete must not silently clear the local state.
        expect(component.hasVideo()).toBe(true);
    });

    it('should open the native file picker when selectFile() is called', () => {
        const clickSpy = vi.fn();
        vi.spyOn(component.fileInput()!, 'nativeElement', 'get').mockReturnValue({ click: clickSpy } as unknown as HTMLInputElement);

        component.selectFile();

        expect(clickSpy).toHaveBeenCalledOnce();
    });
});
