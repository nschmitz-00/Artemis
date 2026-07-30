import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { faCheckCircle, faTrash, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonDirective } from 'primeng/button';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExerciseExplanationVideoService } from 'app/programming/shared/explanation-video/programming-exercise-explanation-video.service';
import { ALLOWED_EXPLANATION_VIDEO_FILE_EXTENSIONS_PATTERN, MAX_EXPLANATION_VIDEO_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { onError } from 'app/foundation/util/global.utils';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-programming-exercise-explanation-video',
    templateUrl: './programming-exercise-explanation-video.component.html',
    styleUrl: './programming-exercise-explanation-video.component.scss',
    imports: [TranslateDirective, ArtemisTranslatePipe, FaIconComponent, ButtonDirective, DeleteButtonDirective, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseExplanationVideoComponent implements OnDestroy {
    private explanationVideoService = inject(ProgrammingExerciseExplanationVideoService);
    private alertService = inject(AlertService);

    readonly participation = input.required<ProgrammingExerciseStudentParticipation>();
    readonly readOnly = input(false);
    readonly videoUploaded = output<void>();

    readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('explanationVideoInput');

    readonly selectedFile = signal<File | undefined>(undefined);
    readonly isUploading = signal(false);

    // Matches the delete-confirmation pattern used everywhere else in Artemis (jhiDeleteButton): the dialog stays
    // open and shows the error on failure, and is dismissed by pushing an empty string on success.
    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    readonly faUpload = faUpload;
    readonly faTrash = faTrash;
    readonly faCheckCircle = faCheckCircle;

    // Starts in sync with the participation input, but can be updated locally (via upload()/deleteVideo()) so the
    // widget reflects a successful upload/delete immediately - the parent component's `participation` object is a
    // snapshot from before the change and nothing else refreshes it, so relying on the input alone would leave the
    // UI stuck showing "not uploaded" even after a successful upload.
    readonly explanationVideoPath = linkedSignal(() => this.participation()?.explanationVideoPath);
    readonly explanationVideoUploadDate = linkedSignal(() => this.participation()?.explanationVideoUploadDate);

    // Bumped on every successful upload so the <video> src string changes even when the stored filename is
    // unchanged (e.g. re-uploading a file with the same name) - otherwise the browser would keep showing the
    // previously loaded (now stale) video content instead of reloading it.
    private readonly videoVersion = signal(0);

    readonly hasVideo = computed(() => !!this.explanationVideoPath());
    readonly videoUrl = computed(() => {
        const participationId = this.participation()?.id;
        if (!this.hasVideo() || !participationId) {
            return undefined;
        }
        return `${this.explanationVideoService.videoUrl(participationId)}?v=${this.videoVersion()}`;
    });

    readonly explanationVideoFileName = computed(() => {
        const path = this.explanationVideoPath();
        return path ? path.split('/').pop() : undefined;
    });

    /**
     * Validates the file picked in the native file dialog and, if valid, uploads it right away - browsing and
     * uploading are a single step from the student's point of view, so there's no separate "Upload" button they
     * could forget to click after picking a file.
     */
    onFileSelected(event: Event): void {
        const target = event.target as HTMLInputElement;
        const file = target.files?.[0];
        if (!file) {
            return;
        }
        if (!ALLOWED_EXPLANATION_VIDEO_FILE_EXTENSIONS_PATTERN.test(file.name)) {
            this.alertService.error('artemisApp.programmingExercise.explanationVideo.fileExtensionError');
            this.resetFileInput();
            return;
        }
        if (file.size > MAX_EXPLANATION_VIDEO_FILE_SIZE) {
            this.alertService.error('artemisApp.programmingExercise.explanationVideo.fileTooBigError', { fileName: file.name });
            this.resetFileInput();
            return;
        }
        this.selectedFile.set(file);
        this.upload();
    }

    upload(): void {
        const file = this.selectedFile();
        const participationId = this.participation()?.id;
        if (!file || !participationId || this.isUploading()) {
            return;
        }
        this.isUploading.set(true);
        this.explanationVideoService.upload(participationId, file).subscribe({
            next: (response) => {
                this.isUploading.set(false);
                this.selectedFile.set(undefined);
                this.resetFileInput();
                this.explanationVideoPath.set(response.body?.explanationVideoPath);
                this.explanationVideoUploadDate.set(response.body?.explanationVideoUploadDate);
                this.videoVersion.update((version) => version + 1);
                this.alertService.success('artemisApp.programmingExercise.explanationVideo.uploadSuccessful');
                this.videoUploaded.emit();
            },
            error: (error: HttpErrorResponse) => {
                this.isUploading.set(false);
                onError(this.alertService, error);
            },
        });
    }

    deleteVideo(): void {
        const participationId = this.participation()?.id;
        if (!participationId) {
            return;
        }
        this.explanationVideoService.delete(participationId).subscribe({
            next: () => {
                this.explanationVideoPath.set(undefined);
                this.explanationVideoUploadDate.set(undefined);
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.programmingExercise.explanationVideo.deleteSuccessful');
                this.videoUploaded.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /** Opens the native file picker via the hidden file input. */
    selectFile(): void {
        this.fileInput()?.nativeElement.click();
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private resetFileInput(): void {
        const input = this.fileInput();
        if (input?.nativeElement) {
            input.nativeElement.value = '';
        }
    }
}
