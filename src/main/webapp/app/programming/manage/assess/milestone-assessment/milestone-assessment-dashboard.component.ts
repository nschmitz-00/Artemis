import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TumUiTableDirective } from '@tumaet/ui-angular';
import { DecimalPipe } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { MilestoneAssessmentService, MilestoneAssessmentStory, MilestoneAssessmentStudent } from './milestone-assessment.service';

/**
 * The milestone's own assessment dashboard: one row per student, one column per user story.
 * <p>
 * Deliberately not modelled on the per-exercise dashboard's "start assessing" flow, which hands a tutor a random
 * student's submission. A milestone group's stories share one repository and one build, so grading them one story at a
 * time across different students means reading the same codebase over and over. Here the tutor reads the table, sees
 * who still needs work, and picks the student.
 */
@Component({
    selector: 'jhi-milestone-assessment-dashboard',
    templateUrl: './milestone-assessment-dashboard.component.html',
    styleUrl: './milestone-assessment-dashboard.component.scss',
    imports: [RouterLink, DecimalPipe, TranslateDirective, TumUiTableDirective, CourseTitleBarTitleDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MilestoneAssessmentDashboardComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly milestoneAssessmentService = inject(MilestoneAssessmentService);
    private readonly alertService = inject(AlertService);

    protected readonly courseId = signal<number>(0);
    protected readonly groupId = signal<number>(0);
    protected readonly students = signal<MilestoneAssessmentStudent[]>([]);
    protected readonly isLoading = signal(true);

    /**
     * The column headers, taken from the first row rather than fetched separately: every row carries the group's
     * stories in the same order, including the ones a given student never started.
     */
    protected readonly storyColumns = computed<MilestoneAssessmentStory[]>(() => this.students()[0]?.stories ?? []);

    constructor() {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.courseId.set(Number(params['courseId']));
            this.groupId.set(Number(params['groupId']));
            this.load();
        });
    }

    private load(): void {
        this.isLoading.set(true);
        this.milestoneAssessmentService
            .getAssessmentDashboard(this.courseId(), this.groupId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (students) => {
                    this.students.set(students);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.isLoading.set(false);
                    this.alertService.error('artemisApp.milestoneAssessment.loadFailed');
                },
            });
    }

    protected assessmentLink(student: MilestoneAssessmentStudent): (string | number)[] {
        return ['/course-management', this.courseId(), 'milestone-exercise-groups', this.groupId(), 'assessment', student.studentLogin];
    }

    /** How many of a student's stories are already assessed, which is what tells a tutor where to spend their time. */
    protected assessedCount(student: MilestoneAssessmentStudent): number {
        return student.stories.filter((story) => story.assessed).length;
    }
}
