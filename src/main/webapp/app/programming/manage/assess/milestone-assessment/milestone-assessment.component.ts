import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumUiTabComponent, TumUiTabListComponent, TumUiTabValue, TumUiTabsComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { MilestoneAssessment, MilestoneAssessmentService, MilestoneAssessmentStory } from './milestone-assessment.service';
import { MilestoneAssessmentOverviewComponent } from './milestone-assessment-overview.component';

/** The tab value of the group-level first tab; every other tab is keyed by its user story exercise id. */
const OVERVIEW_TAB = 'milestone';

/**
 * Grades one student's whole milestone: a group-level first tab, then one tab per user story.
 * <p>
 * Only the active story's assessment editor is mounted at a time, and that is a hard constraint rather than a
 * preference: `DomainService` is `providedIn: 'root'` and holds a single global domain, so two live editors would
 * fight over which repository the code editor is pointed at. Switching tabs therefore tears the previous editor down,
 * which is also why a switch has to be refused while it holds unsaved feedback.
 * <p>
 * The student is named at the top and in the title bar. A tutor must never be in doubt whose work is on screen -
 * unlike every other assessment page, this one is reached by choosing a person rather than by being handed a
 * submission.
 */
@Component({
    selector: 'jhi-milestone-assessment',
    templateUrl: './milestone-assessment.component.html',
    styleUrl: './milestone-assessment.component.scss',
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiTabsComponent,
        TumUiTabListComponent,
        TumUiTabComponent,
        CourseTitleBarTitleDirective,
        MilestoneAssessmentOverviewComponent,
        CodeEditorTutorAssessmentContainerComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MilestoneAssessmentComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly milestoneAssessmentService = inject(MilestoneAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    protected readonly courseId = signal<number>(0);
    protected readonly groupId = signal<number>(0);
    protected readonly studentLogin = signal<string>('');
    protected readonly assessment = signal<MilestoneAssessment | undefined>(undefined);
    protected readonly isLoading = signal(true);
    protected readonly activeTab = signal<TumUiTabValue>(OVERVIEW_TAB);

    protected readonly OVERVIEW_TAB = OVERVIEW_TAB;

    /** The stories that can actually be opened. One the student never started has no submission to assess. */
    protected readonly stories = computed<MilestoneAssessmentStory[]>(() => this.assessment()?.stories ?? []);

    /** The story the active tab addresses, or undefined while the group-level tab is shown. */
    protected readonly activeStory = computed<MilestoneAssessmentStory | undefined>(() => {
        const active = this.activeTab();
        return active === OVERVIEW_TAB ? undefined : this.stories().find((story) => String(story.exerciseId) === String(active));
    });

    /** The editor is only mounted for a story the student actually submitted something for. */
    protected readonly activeSubmissionId = computed<number | undefined>(() => this.activeStory()?.submissionId);

    /** The mounted assessment editor, so a tab switch can ask it whether it holds unsaved feedback. */
    private assessmentContainer?: CodeEditorTutorAssessmentContainerComponent;

    constructor() {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.courseId.set(Number(params['courseId']));
            this.groupId.set(Number(params['groupId']));
            this.studentLogin.set(params['studentLogin']);
            this.load();
        });
    }

    private load(): void {
        this.isLoading.set(true);
        this.milestoneAssessmentService
            .getAssessmentForStudent(this.courseId(), this.groupId(), this.studentLogin())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (assessment) => {
                    this.assessment.set(assessment);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.isLoading.set(false);
                    this.alertService.error('artemisApp.milestoneAssessment.loadFailed');
                },
            });
    }

    protected registerContainer(container: CodeEditorTutorAssessmentContainerComponent | undefined): void {
        this.assessmentContainer = container;
    }

    /**
     * Switches tabs, refusing while the mounted editor holds feedback the tutor has not saved.
     * <p>
     * The panel is destroyed on switch (see the class comment), so leaving without asking would discard that feedback
     * silently. Reuses the confirmation the routed assessment page's deactivate guard shows for the same reason.
     */
    protected onTabChange(value: TumUiTabValue): void {
        if (value === undefined || value === this.activeTab()) {
            return;
        }
        if (this.assessmentContainer?.hasPendingChanges && !window.confirm(this.translateService.instant('artemisApp.programmingAssessment.confirmLeave'))) {
            return;
        }
        this.assessmentContainer = undefined;
        this.activeTab.set(value);
    }

    /**
     * Moves to the next story instead of navigating away, which is what the assessment editor's "Assess next" button
     * would otherwise do. Bound into the editor through its {@code overrideNextSubmission} input.
     */
    protected readonly assessNextStory = (): void => {
        const stories = this.stories();
        const currentIndex = stories.findIndex((story) => String(story.exerciseId) === String(this.activeTab()));
        const next = stories.slice(currentIndex + 1).find((story) => story.submissionId !== undefined);
        if (next) {
            this.onTabChange(String(next.exerciseId));
        }
    };
}
