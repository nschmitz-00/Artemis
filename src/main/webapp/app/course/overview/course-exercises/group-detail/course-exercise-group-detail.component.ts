import { ChangeDetectionStrategy, Component, DestroyRef, EnvironmentInjector, afterNextRender, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faLayerGroup, faPlayCircle, faRotateRight, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { filter, finalize, map, skip } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { DifficultyLevel, Exercise, IncludedInOverallScore, getExerciseUrlSegment, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ExerciseVariantGroupService, MilestoneStatusDTO } from 'app/course/manage/exercises/exercise-variant-group.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { InformationBox, InformationBoxComponent } from 'app/shared-ui/information-box/information-box.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { isDateLessThanAWeekInTheFuture } from 'app/foundation/util/date.utils';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Participation, getLatestSubmission } from 'app/exercise/shared/entities/participation/participation.model';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { MilestoneCodeQualityComponent } from './milestone-code-quality/milestone-code-quality.component';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [
        RouterLink,
        FaIconComponent,
        ArtemisDatePipe,
        ArtemisTimeAgoPipe,
        ArtemisTranslatePipe,
        TranslateDirective,
        ExerciseHeadersInformationComponent,
        InformationBoxComponent,
        TumUiTooltipDirective,
        ExerciseActionButtonComponent,
        FeatureToggleDirective,
        CodeButtonComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownItem,
        MilestoneCodeQualityComponent,
    ],
    /* preserveWhitespaces: false is required here because the global tsconfig sets preserveWhitespaces: true,
     * which inserts whitespace text nodes that break [contentComponent] slot matching in jhi-information-box. */
    preserveWhitespaces: false,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly courseOverviewExercisesService = inject(CourseOverviewExercisesService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly exerciseVariantGroupService = inject(ExerciseVariantGroupService);
    private readonly entityTitleService = inject(EntityTitleService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly plantUmlWrapper = inject(ProgrammingExercisePlantUmlExtensionWrapper);
    private readonly sanitizer = inject(DomSanitizer);
    private readonly injector = inject(EnvironmentInjector);

    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faWrench = faWrench;
    protected readonly faRotateRight = faRotateRight;
    protected readonly getIcon = getIcon;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly FeatureToggle = FeatureToggle;

    private readonly serverDateService = inject(ArtemisServerDateService);
    private readonly scoresStorageService = inject(ScoresStorageService);
    private readonly participationService = inject(ParticipationService);
    private readonly courseExerciseService = inject(CourseExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly participationWebsocketService = inject(ParticipationWebsocketService);
    private readonly programmingSubmissionService = inject(ProgrammingSubmissionService);
    private readonly now = this.serverDateService.now();

    /** Whether the requesting student has started the group's anchor milestone exercise; undefined until loaded. */
    protected readonly milestoneStatus = signal<MilestoneStatusDTO | undefined>(undefined);
    protected readonly isStartingMilestone = signal(false);
    /**
     * Whether the milestone-status request failed. Without it the header simply renders nothing when the request fails
     * - no button, no message - which is indistinguishable from "this group has no start action", and a 404 (the most
     * likely failure here) is suppressed by the global alert handler, so the failure was completely invisible.
     */
    protected readonly milestoneStatusFailed = signal(false);
    protected readonly isLoadingMilestoneStatus = signal(false);
    /** Milestone groups whose status has already been requested, so revisiting a group does not re-fetch it. */
    private readonly requestedMilestoneStatusGroupIds = new Set<number>();

    /**
     * The student's own participation in the group's anchor milestone, with its latest result and feedback - the only
     * place the group's static code analysis feedback lives (see `MilestoneCodeQualityComponent`). Undefined until the
     * milestone has been started and the request has come back.
     */
    private readonly milestoneParticipation = signal<ProgrammingExerciseStudentParticipation | undefined>(undefined);

    /**
     * The most recent milestone result pushed over the websocket, which supersedes the one the participation request
     * brought along. Undefined until a build finishes while this page is open.
     * <p>
     * A result arrives here twice per push: once when the build finishes, and once again ~half a second later when
     * `MilestoneScoreService` has aggregated the group's story points onto it. Both updates carry the same result id
     * and the same static code analysis feedback - the aggregation rewrites the very same row - so the code-quality
     * box does not flicker between two different issue sets; only the score changes.
     */
    private readonly liveMilestoneResult = signal<Result | undefined>(undefined);
    /** Whether the milestone's build is queued / running, so the code-quality box can say so instead of showing a stale count. */
    protected readonly isMilestoneBuilding = signal(false);
    protected readonly isMilestoneQueued = signal(false);
    /** The participation the live subscriptions below are currently open for, so they can be released after it changes. */
    private subscribedMilestoneParticipationId?: number;
    private milestoneResultSubscription?: Subscription;
    private milestoneSubmissionSubscription?: Subscription;
    /** Milestone participations already requested, so an unrelated re-render does not re-issue the request. */
    private readonly requestedMilestoneParticipationIds = new Set<number>();

    /** The anchor milestone exercise itself, which is where static code analysis is configured. */
    protected readonly milestoneExercise = computed<ProgrammingExercise | undefined>(() => this.milestoneParticipation()?.exercise);

    /**
     * The latest result of the milestone's own build, which carries the group's static code analysis feedback. Prefers
     * whatever the websocket last delivered over the snapshot the one-shot participation request brought along.
     */
    protected readonly milestoneResult = computed<Result | undefined>(() => {
        const liveResult = this.liveMilestoneResult();
        if (liveResult) {
            return liveResult;
        }
        const participation = this.milestoneParticipation();
        return participation ? getLatestSubmissionResult(getLatestSubmission(participation)) : undefined;
    });

    private readonly groupId = signal<number | undefined>(undefined);
    private readonly courseExercises = signal<Exercise[]>([]);
    protected readonly course = signal<Course | undefined>(undefined);

    /**
     * Websocket-updated participations of the group's member exercises, keyed by exercise id. A milestone build fans a
     * result out to every started user story (server-side: `ProgrammingExerciseGradingService.fanOutResultToUserStoryExercise`),
     * and each of those results is broadcast on the student's personal topic - so the cards below can stay current
     * without re-fetching the course dashboard.
     */
    private readonly liveParticipations = signal<Map<number, StudentParticipation>>(new Map());
    /** Member participations already handed to the websocket service, so an unrelated re-render does not re-register them. */
    private readonly registeredVariantParticipationIds = new Set<number>();

    private readonly problemStatements = signal<Map<number, string>>(new Map());
    /** Groups whose member previews have already been requested, so revisiting a group does not re-fetch them. */
    private readonly requestedGroupIds = new Set<number>();

    protected readonly renderedStatements = signal<Map<number, SafeHtml>>(new Map());
    private plantUmlCallbacks: Array<() => void> = [];

    protected readonly group = computed<CourseExerciseGroup | undefined>(() => {
        const groupId = this.groupId();
        if (groupId === undefined) {
            return undefined;
        }
        return buildGroupsFromExercises(this.courseExercises()).find((candidate) => candidate.id === groupId);
    });
    protected readonly exercises = computed<Exercise[]>(() => this.group()?.exercises ?? []);

    /**
     * Sum of maxPoints over the INCLUDED_COMPLETELY variants only, matching the server's inclusion rule; bonus and
     * not-included variants must not inflate the denominator.
     */
    protected readonly exerciseSumMaxPoints = computed<number>(() =>
        this.roundPoints(
            this.exercises()
                .filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY)
                .reduce((sum, ex) => sum + (ex.maxPoints ?? 0), 0),
        ),
    );

    /**
     * The group's real maximum: min(sum of variant max points, group cap), since a cap above the sum cannot raise
     * what is achievable. The cap is detected with an explicit undefined check so a zero cap still applies.
     */
    protected readonly effectiveGroupMaxPoints = computed<number>(() => {
        const cap = this.group()?.maxPoints;
        const sum = this.exerciseSumMaxPoints();
        return this.roundPoints(cap !== undefined ? Math.min(sum, cap) : sum);
    });

    /** Whether the cap actually reduces the achievable maximum (set and strictly below the variants' sum). Only then is
     * the cap explanation (tooltip / callout) meaningful; a cap ≥ the sum behaves exactly like no cap. */
    protected readonly capReducesMaxPoints = computed<boolean>(() => {
        const cap = this.group()?.maxPoints;
        return cap !== undefined && cap < this.exerciseSumMaxPoints();
    });

    /**
     * The student's group points, taken from the authoritative server value via {@link ScoresStorageService}, which is
     * already capped and plagiarism-adjusted. Falls back to 0 until the dashboard scores load.
     * <p>
     * Once a live milestone result has arrived it takes precedence: for a milestone group the group's points *are* the
     * milestone result's score, which the server recomputes as `sum(story points) - static code analysis penalty` and
     * writes onto that very result (`MilestoneScoreService`). That value is not plagiarism-adjusted, unlike the stored
     * one, so a flagged student's figure only re-aligns on the next dashboard load - the accepted price of showing a
     * live number, and it only ever applies after the student's own push.
     */
    protected readonly achievedGroupPoints = computed<number>(() => {
        const liveScore = this.liveMilestoneResult()?.score;
        const milestoneMaxPoints = this.milestoneExercise()?.maxPoints;
        if (liveScore !== undefined && milestoneMaxPoints !== undefined) {
            return this.roundPoints((liveScore / 100) * milestoneMaxPoints);
        }
        const group = this.group();
        if (group?.id === undefined) {
            return 0;
        }
        return this.roundPoints(this.scoresStorageService.getStoredAchievedGroupPoints(this.courseId, group.id) ?? 0);
    });

    /**
     * The milestone group's description, which is its anchor MilestoneExercise's problem statement. The milestone itself
     * is never rendered to students, so it arrives via the milestone-status request the view already makes rather than
     * with the dashboard payload — the callout therefore falls back to the generic heading until that resolves.
     *
     * Rendered the same way the member previews are (see {@link renderProblemStatements}), minus the PlantUML extension:
     * that one is stateful (setExerciseId plus callbacks flushed in afterNextRender) and cannot be driven from a pure
     * computed. A milestone blurb needing PlantUML would have to move into renderProblemStatements.
     */
    protected readonly milestoneDescriptionHtml = computed<SafeHtml | undefined>(() => {
        const problemStatement = this.milestoneStatus()?.problemStatement;
        if (!problemStatement) {
            return undefined;
        }
        // Strip task syntax — [task][Name](tests) → Name — so it renders as plain text instead of a link.
        const preprocessed = problemStatement.replace(taskRegex, (_match, name: string) => name);
        return this.sanitizer.bypassSecurityTrustHtml(htmlForMarkdown(preprocessed));
    });

    protected readonly pointsInfoBoxData = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseDetails.points',
        content: { type: 'string', value: '' },
        isContentComponent: true,
    }));

    protected readonly variantsInfoBoxData = computed<InformationBox>(() => ({
        title: this.group()?.type === 'milestone' ? 'artemisApp.exerciseVariantGroup.detail.milestoneVariants' : 'artemisApp.exerciseVariantGroup.detail.variants',
        content: { type: 'string', value: this.exercises().length },
    }));

    /** Dynamic date info boxes for the group header, mirroring the exercise due-date + assessment-due logic. */
    protected readonly groupDateInfoBoxes = computed<InformationBox[]>(() => {
        const group = this.group();
        const now = this.now;
        const dueDate = group?.dueDate;
        const startDate = group?.startDate;
        const assessmentDueDate = group?.assessmentDueDate;
        const items: InformationBox[] = [];

        if (dueDate) {
            if (dueDate.isBefore(now)) {
                items.push({
                    title: 'artemisApp.courseOverview.exerciseDetails.submissionDueOver',
                    content: { type: 'dateTime', value: dueDate },
                    isContentComponent: true,
                });
            } else {
                const relative = isDateLessThanAWeekInTheFuture(dueDate, now);
                const color = dueDate.isBetween(now, now.add(1, 'day')) ? 'danger' : 'body-color';
                items.push({
                    title: 'artemisApp.courseOverview.exerciseDetails.submissionDue',
                    content: { type: relative ? 'timeAgo' : 'dateTime', value: dueDate },
                    isContentComponent: true,
                    contentColor: color,
                    tooltip: relative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
                    tooltipParams: relative ? { date: dueDate.format('lll') } : undefined,
                });
            }
            if (dueDate.isBefore(now) && assessmentDueDate?.isAfter(now)) {
                items.push({
                    title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
                    content: { type: 'dateTime', value: assessmentDueDate },
                    isContentComponent: true,
                    tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
                    tooltipParams: { date: assessmentDueDate.format('lll') },
                });
            }
        }
        if (startDate?.isAfter(now)) {
            const relative = isDateLessThanAWeekInTheFuture(startDate, now);
            items.push({
                title: 'artemisApp.courseOverview.exerciseDetails.startDate',
                content: { type: relative ? 'timeAgo' : 'dateTime', value: startDate },
                isContentComponent: true,
            });
        }

        return items;
    });

    protected courseId = 0;

    constructor() {
        this.courseId = Number(this.route.parent?.parent?.snapshot.params['courseId']);
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.groupId.set(Number(params['groupId'])));

        this.plantUmlWrapper
            .subscribeForInjectableElementsFound()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((cb) => this.plantUmlCallbacks.push(cb));

        effect(() => {
            const exercises = this.exercises();
            const statements = this.problemStatements();
            untracked(() => this.renderProblemStatements(exercises, statements));
        });

        // The course itself is already loaded by the course overview container this route lives in; the only field read
        // from it here is maxComplaintTimeDays, via the exercise header.
        this.course.set(this.courseStorageService.getCourse(this.courseId));
        this.courseStorageService
            .subscribeToCourseUpdates(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((course) => this.course.set(course));

        // The exercises come from the same endpoint the exercises tab uses, freshly for this navigation, and the same
        // response populates the achieved variant group points that {@link achievedGroupPoints} reads out of the
        // ScoresStorageService.
        this.courseOverviewExercisesService
            .loadIfNeeded(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (overview) => this.courseExercises.set(overview.exercises ?? []),
            });

        toObservable(this.group)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((g) => {
                if (g?.id !== undefined && g.title) {
                    this.entityTitleService.setTitle(EntityType.EXERCISE_VARIANT_GROUP, [g.id], g.title);
                }
            });

        effect(() => {
            const group = this.group();
            const groupId = group?.id;
            if (group === undefined || groupId === undefined || this.requestedGroupIds.has(groupId)) {
                return;
            }
            // The dashboard strips problem statements to stay small, so any member missing one needs the batch preview
            // request. When every member already carries its statement (e.g. inlined by a caller), there is nothing to do.
            const needsPreview = (group.exercises ?? []).some((exercise) => exercise.id !== undefined && exercise.problemStatement === undefined);
            if (!needsPreview) {
                return;
            }
            this.requestedGroupIds.add(groupId);
            // One lightweight batch request for the whole group instead of one heavyweight exercise-details request per member.
            this.exerciseVariantGroupService
                .getProblemStatements(this.courseId, groupId)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: (previews) => {
                        const next = new Map(this.problemStatements());
                        for (const preview of previews) {
                            if (preview.problemStatement !== undefined) {
                                next.set(preview.exerciseId, preview.problemStatement);
                            }
                        }
                        this.problemStatements.set(next);
                    },
                    error: () => {
                        // The group was optimistically marked as requested; release it so a later change (or revisit)
                        // can retry the batch instead of leaving the previews permanently blocked.
                        this.requestedGroupIds.delete(groupId);
                    },
                });
        });

        effect(() => {
            const group = this.group();
            const groupId = group?.id;
            if (group?.type !== 'milestone' || groupId === undefined || this.requestedMilestoneStatusGroupIds.has(groupId)) {
                return;
            }
            untracked(() => this.loadMilestoneStatus(groupId));
        });

        effect(() => {
            const participationId = this.milestoneStatus()?.participationId;
            if (participationId === undefined || this.requestedMilestoneParticipationIds.has(participationId)) {
                return;
            }
            untracked(() => this.loadMilestoneParticipation(participationId));
        });

        // The participation request above is a one-shot snapshot. A build triggered by a push from the student's own
        // IDE - the normal case for a milestone, whose repository is shared across the whole group - has to reach this
        // page without a reload, so the same participation is also followed live.
        effect(() => {
            const status = this.milestoneStatus();
            untracked(() => this.subscribeToMilestoneUpdates(status?.participationId, status?.milestoneExerciseId));
        });

        // Registering the member participations is what lets the websocket service merge an incoming result into a
        // participation at all: an unregistered participation is silently skipped when a result arrives for it.
        effect(() => {
            const exercises = this.exercises();
            untracked(() => this.registerVariantParticipations(exercises));
        });

        // One app-wide stream carrying every participation the websocket service updated; the initial (seed) value is
        // skipped because it only replays what is already on screen.
        this.participationWebsocketService
            .subscribeForParticipationChanges()
            .pipe(skip(1), takeUntilDestroyed(this.destroyRef))
            .subscribe((participation) => this.applyLiveParticipation(participation));

        this.destroyRef.onDestroy(() => this.tearDownMilestoneSubscriptions());
    }

    /**
     * (Re)opens the milestone participation's live result and build-status subscriptions. Called with an undefined
     * participation id while the milestone has not been started yet, which only releases whatever may still be open.
     */
    private subscribeToMilestoneUpdates(participationId: number | undefined, milestoneExerciseId: number | undefined): void {
        if (participationId === this.subscribedMilestoneParticipationId) {
            return;
        }
        this.tearDownMilestoneSubscriptions();
        if (participationId === undefined) {
            return;
        }
        this.subscribedMilestoneParticipationId = participationId;

        this.milestoneResultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId, true, milestoneExerciseId)
            .pipe(
                // The subject seeds with undefined; only actual results are of interest here.
                filter((result): result is Result => !!result),
                // ParticipationWebsocketService deliberately leaves the wire's date strings alone, so every consumer
                // converts them itself (the same step UpdatingResultComponent does).
                map((result) => cloneWith(result, { completionDate: convertDateFromServer(result.completionDate) })),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((result) => this.liveMilestoneResult.set(result));

        if (milestoneExerciseId === undefined) {
            return;
        }
        // Purely so the box can say "building" between the push and the result instead of showing a count that is
        // known to be out of date.
        this.milestoneSubmissionSubscription = this.programmingSubmissionService
            .getLatestPendingSubmissionByParticipationId(participationId, milestoneExerciseId, true)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(({ submissionState }) => {
                this.isMilestoneQueued.set(submissionState === ProgrammingSubmissionState.IS_QUEUED);
                this.isMilestoneBuilding.set(submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
            });
    }

    /**
     * Releases the milestone's live subscriptions.
     * <p>
     * The submission subscription is dropped locally first and only then handed back to the
     * {@link ProgrammingSubmissionService}, so that service sees the remaining observers when it decides whether the
     * shared per-participation state may go - releasing only the local subscription leaks that state until logout
     * (the ordering `UpdatingResultComponent.tearDownSubmissionSubscription` documents).
     */
    private tearDownMilestoneSubscriptions(): void {
        const participationId = this.subscribedMilestoneParticipationId;
        this.milestoneResultSubscription?.unsubscribe();
        this.milestoneResultSubscription = undefined;
        this.milestoneSubmissionSubscription?.unsubscribe();
        this.milestoneSubmissionSubscription = undefined;
        this.subscribedMilestoneParticipationId = undefined;
        this.isMilestoneBuilding.set(false);
        this.isMilestoneQueued.set(false);
        if (participationId === undefined) {
            return;
        }
        const exercise = this.milestoneExercise();
        if (exercise) {
            // Only actually closes the shared websocket subscription once the exercise is past due; while it is still
            // running the stream is deliberately kept open for the rest of the app.
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participationId, exercise);
        }
        this.programmingSubmissionService.unsubscribeForLatestSubmissionOfParticipation(participationId);
    }

    /**
     * Hands the group's member participations to the websocket service, which caches them and merges incoming results
     * into them. Only the graded participation is registered, matching what the cards render.
     */
    private registerVariantParticipations(exercises: Exercise[]): void {
        for (const exercise of exercises) {
            const participation = this.participationService.getSpecificStudentParticipation(exercise.studentParticipations ?? [], false);
            if (participation?.id === undefined || this.registeredVariantParticipationIds.has(participation.id)) {
                continue;
            }
            this.registeredVariantParticipationIds.add(participation.id);
            // The exercise is passed explicitly: the dashboard payload's participations carry no back-reference to it,
            // and addParticipation refuses a participation it cannot link to one.
            this.participationWebsocketService.addParticipation(participation, exercise);
        }
    }

    /**
     * Records a websocket-updated participation so the group's cards render its new result. The stream is app-wide, so
     * anything that is not one of this group's own registered participations is ignored. A replacement map is stored
     * because a signal only notifies when the reference changes.
     */
    private applyLiveParticipation(participation: Participation | undefined): void {
        const participationId = participation?.id;
        const exerciseId = participation?.exercise?.id;
        if (participationId === undefined || exerciseId === undefined || !this.registeredVariantParticipationIds.has(participationId)) {
            return;
        }
        const next = new Map(this.liveParticipations());
        next.set(exerciseId, participation as StudentParticipation);
        this.liveParticipations.set(next);
    }

    /**
     * Loads the milestone's own participation with its latest result, which is what the code-quality panel reads the
     * group's static code analysis feedback from. Deliberately quiet on failure: unlike the start action, this is
     * supplementary information, so a failure simply leaves the panel unrendered rather than alerting the student. The
     * participation is released from the requested set again so a later revisit retries.
     */
    private loadMilestoneParticipation(participationId: number): void {
        this.requestedMilestoneParticipationIds.add(participationId);
        this.programmingExerciseParticipationService
            .getStudentParticipationWithLatestResult(participationId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (participation) => this.milestoneParticipation.set(participation),
                error: () => this.requestedMilestoneParticipationIds.delete(participationId),
            });
    }

    /**
     * Loads whether the student has started the group's anchor milestone. The group is marked as requested up front so
     * an unrelated re-render does not re-issue it; a failure releases that mark again and is surfaced, so the student
     * gets a retry instead of a header that silently renders nothing.
     */
    private loadMilestoneStatus(groupId: number): void {
        this.requestedMilestoneStatusGroupIds.add(groupId);
        this.milestoneStatusFailed.set(false);
        this.isLoadingMilestoneStatus.set(true);
        this.exerciseVariantGroupService
            .getMilestoneStatus(this.courseId, groupId)
            .pipe(
                finalize(() => this.isLoadingMilestoneStatus.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (status) => this.milestoneStatus.set(status),
                error: (error: HttpErrorResponse) => {
                    this.requestedMilestoneStatusGroupIds.delete(groupId);
                    this.milestoneStatusFailed.set(true);
                    this.alertService.error('artemisApp.exerciseVariantGroup.detail.milestoneStatusLoadFailed');
                },
            });
    }

    /** Retries the milestone-status request after a failure, from the button rendered in the start action's place. */
    protected retryMilestoneStatus(): void {
        const groupId = this.group()?.id;
        if (groupId === undefined || this.isLoadingMilestoneStatus()) {
            return;
        }
        this.loadMilestoneStatus(groupId);
    }

    private renderProblemStatements(exercises: Exercise[], statements: Map<number, string>): void {
        this.plantUmlCallbacks = [];
        const map = new Map<number, SafeHtml>();

        for (const exercise of exercises) {
            if (exercise.id === undefined) continue;
            const ps = exercise.problemStatement ?? statements.get(exercise.id);
            if (!ps) continue;

            // Strip task syntax — [task][Name](tests) → Name — so it renders as plain text instead of a link.
            const preprocessed = ps.replace(taskRegex, (_match, name: string) => name);
            this.plantUmlWrapper.setExerciseId(exercise.id);
            const html = htmlForMarkdown(preprocessed, [this.plantUmlWrapper.getExtension()]);
            map.set(exercise.id, this.sanitizer.bypassSecurityTrustHtml(html));
        }

        this.renderedStatements.set(map);

        afterNextRender(
            () => {
                this.plantUmlCallbacks.forEach((cb) => cb());
                this.plantUmlCallbacks = [];
            },
            { injector: this.injector },
        );
    }

    /**
     * Rounds a points figure the way every other Artemis points display does (see
     * {@code ExerciseHeadersInformationComponent.achievedPoints}, which renders this page's own variant cards), so a
     * derived value does not reach the header at full float width - `(75 / 100) * 20` is `14.999999999999998`.
     * <p>
     * Guarded on the course being present rather than handing the helper an undefined one: it reports that to Sentry on
     * every call, and the computeds below re-run on every change detection - an expected gap before
     * {@link CourseStorageService} has the course would become a stream of captured errors rather than a single one.
     */
    private roundPoints(value: number): number {
        const course = this.course();
        return course ? roundValueSpecifiedByCourseSettings(value, course) : value;
    }

    /**
     * The graded participation for a variant. The dashboard also returns practice runs in unspecified order, so the
     * first entry could otherwise show practice points on the card.
     */
    protected exerciseParticipation(exercise: Exercise): StudentParticipation | undefined {
        const liveParticipation = exercise.id !== undefined ? this.liveParticipations().get(exercise.id) : undefined;
        return liveParticipation ?? this.participationService.getSpecificStudentParticipation(exercise.studentParticipations ?? [], false);
    }

    protected exerciseLink(exercise: Exercise): string {
        return `/courses/${this.courseId}/exercises/${exercise.id}`;
    }

    /**
     * Starts the group's anchor milestone exercise for the current student, so every UserStoryExercise in the group
     * shares its repository (server-side: `ParticipationService.shareSiblingRepositoryIfAvailable`) instead of each
     * provisioning its own once the student starts it.
     */
    protected startMilestone(): void {
        const status = this.milestoneStatus();
        if (!status || status.started || this.isStartingMilestone()) {
            return;
        }
        this.isStartingMilestone.set(true);
        this.courseExerciseService
            .startExercise(status.milestoneExerciseId)
            .pipe(finalize(() => this.isStartingMilestone.set(false)))
            .subscribe({
                next: (participation) => {
                    const programmingParticipation = participation as ProgrammingExerciseStudentParticipation;
                    this.milestoneStatus.set(
                        cloneWith(status, { started: true, participationId: programmingParticipation.id, repositoryUri: programmingParticipation.repositoryUri }),
                    );
                },
                error: (error: HttpErrorResponse) => {
                    if (error.status !== 403) {
                        this.alertService.error('artemisApp.exercise.startError');
                    }
                },
            });
    }

    /** Where the group's "Instructor actions" dropdown entry for one member exercise links to: its own course-management detail page. */
    protected exerciseManagementRouterLink(exercise: Exercise): (string | number)[] {
        return ['/course-management', this.courseId, getExerciseUrlSegment(exercise.type), exercise.id ?? 0];
    }

    /**
     * The milestone's own participation, wrapped as a single-element array for `jhi-code-button`'s `[participations]`
     * input - the group view shows the "Code" button for the milestone's (shared) repository directly, instead of the
     * plain "started" text a normal exercise page would show once a participation exists.
     */
    protected readonly milestoneCodeButtonParticipations = computed<ProgrammingExerciseStudentParticipation[]>(() => {
        const status = this.milestoneStatus();
        if (!status?.started || status.participationId === undefined) {
            return [];
        }
        const participation = new ProgrammingExerciseStudentParticipation();
        participation.id = status.participationId;
        participation.repositoryUri = status.repositoryUri;
        return [participation];
    });

    protected routerLinkForMilestoneRepository(): (string | number)[] {
        const status = this.milestoneStatus();
        if (!status?.participationId) {
            return ['/courses', this.courseId, 'exercises', status?.milestoneExerciseId ?? 0];
        }
        return ['/courses', this.courseId, 'exercises', status.milestoneExerciseId, 'repository', status.participationId];
    }
}
