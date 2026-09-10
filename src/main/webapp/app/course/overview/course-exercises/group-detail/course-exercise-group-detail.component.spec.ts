import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { BehaviorSubject, EMPTY, Observable, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { InformationBox } from 'app/shared-ui/information-box/information-box.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { CourseExerciseGroupDetailComponent } from 'app/course/overview/course-exercises/group-detail/course-exercise-group-detail.component';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { ExerciseProblemStatementDTO, ExerciseVariantGroupService, MilestoneStatusDTO } from 'app/course/manage/exercises/exercise-variant-group.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { EntityTitleService } from 'app/core/navbar/entity-title.service';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';

describe('CourseExerciseGroupDetailComponent', () => {
    let fixture: ComponentFixture<CourseExerciseGroupDetailComponent>;
    /** Server-computed achieved points per variant group id, as the ScoresStorageService would hold after a dashboard load. */
    let storedGroupPoints: Map<number, number>;

    /**
     * The live streams the component subscribes to, owned by the test so it can push a build result or a build-status
     * change at will. They stand in for the two root services the real page reaches the websocket through.
     */
    let latestResults: Map<number, BehaviorSubject<Result | undefined>>;
    let participationChanges: BehaviorSubject<Participation | undefined>;
    let pendingSubmissions: BehaviorSubject<ProgrammingSubmissionStateObj>;
    let registeredParticipations: { participationId?: number; exerciseId?: number }[];
    let releasedResultParticipationIds: number[];
    let releasedSubmissionParticipationIds: number[];

    /** The subject the component's result subscription for the given participation reads from, created on first use. */
    function latestResultOf(participationId: number): BehaviorSubject<Result | undefined> {
        const existing = latestResults.get(participationId);
        if (existing) {
            return existing;
        }
        const subject = new BehaviorSubject<Result | undefined>(undefined);
        latestResults.set(participationId, subject);
        return subject;
    }

    const GROUP_ID = 10;

    /** Two exercises in group 10 (cap 15), each worth 10 points and fully included in the score, participations 101 and 102. */
    function exercisesInGroup(groupMaxPoints: number | undefined): Exercise[] {
        const reference = { id: GROUP_ID, title: 'Sorting variants', maxPoints: groupMaxPoints };
        return [
            {
                id: 1,
                type: ExerciseType.TEXT,
                maxPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                exerciseVariantGroup: reference,
                studentParticipations: [{ id: 101 }],
                problemStatement: 'a',
            } as unknown as Exercise,
            {
                id: 2,
                type: ExerciseType.TEXT,
                maxPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                exerciseVariantGroup: reference,
                studentParticipations: [{ id: 102 }],
                problemStatement: 'b',
            } as unknown as Exercise,
        ];
    }

    /** An additional variant of the given inclusion type worth 10 points, joining group 10. */
    function extraVariant(includedInOverallScore: IncludedInOverallScore, groupMaxPoints: number | undefined): Exercise {
        const reference = { id: GROUP_ID, title: 'Sorting variants', maxPoints: groupMaxPoints };
        return { id: 3, type: ExerciseType.TEXT, maxPoints: 10, includedInOverallScore, exerciseVariantGroup: reference, problemStatement: 'c' } as unknown as Exercise;
    }

    /** A milestone group member, so the component's milestone-status effect actually runs. */
    function milestoneGroupMember(): Exercise {
        const reference = { id: GROUP_ID, title: 'Sprint 1', type: 'milestone' as const };
        return { id: 1, type: ExerciseType.PROGRAMMING, maxPoints: 10, exerciseVariantGroup: reference, problemStatement: 'a' } as unknown as Exercise;
    }

    async function setup(
        exercises: Exercise[],
        options?: {
            getProblemStatements?: () => Observable<ExerciseProblemStatementDTO[]>;
            getMilestoneStatus?: () => Observable<MilestoneStatusDTO>;
            getStudentParticipationWithLatestResult?: () => Observable<ProgrammingExerciseStudentParticipation>;
            /** The course's configured points accuracy; left unset the component rounds to the model's default of 1. */
            accuracyOfScores?: number;
        },
    ): Promise<void> {
        const course = { id: 1, exercises, accuracyOfScores: options?.accuracyOfScores } as Course;
        const route = {
            params: of({ groupId: String(GROUP_ID) }),
            parent: { parent: { snapshot: { params: { courseId: '1' } } } },
        } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [CourseExerciseGroupDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(CourseOverviewExercisesService, { loadIfNeeded: () => of({ exercises } as CourseExercisesForOverviewDTO) }),
                MockProvider(CourseStorageService, { getCourse: () => course, subscribeToCourseUpdates: () => EMPTY }),
                MockProvider(ExerciseVariantGroupService, {
                    getProblemStatements: (options?.getProblemStatements ?? (() => EMPTY)) as never,
                    getMilestoneStatus: (options?.getMilestoneStatus ?? (() => EMPTY)) as never,
                }),
                MockProvider(ProgrammingExerciseParticipationService, {
                    getStudentParticipationWithLatestResult: (options?.getStudentParticipationWithLatestResult ?? (() => EMPTY)) as never,
                }),
                MockProvider(AlertService),
                // The component injects CourseExerciseService for startMilestone(); the real one pulls in
                // ParticipationWebsocketService -> AccountService -> TranslateService, none of which this spec provides.
                MockProvider(CourseExerciseService),
                MockProvider(EntityTitleService),
                MockProvider(ProgrammingExercisePlantUmlExtensionWrapper, {
                    subscribeForInjectableElementsFound: () => EMPTY,
                    // A markdown-it plugin is a function; a bare object would make markdownIt.use() throw.
                    getExtension: () => (() => {}) as never,
                }),
                MockProvider(ArtemisServerDateService, { now: () => dayjs() }),
                MockProvider(DomSanitizer, { bypassSecurityTrustHtml: (value: string) => value }),
                { provide: ScoresStorageService, useValue: { getStoredAchievedGroupPoints: (_courseId: number, groupId: number) => storedGroupPoints.get(groupId) } },
                { provide: ParticipationService, useClass: MockParticipationService },
                {
                    provide: ParticipationWebsocketService,
                    useValue: {
                        subscribeForLatestResultOfParticipation: (participationId: number, _personal: boolean, exerciseId?: number) => {
                            registeredParticipations.push({ participationId, exerciseId });
                            return latestResultOf(participationId);
                        },
                        subscribeForParticipationChanges: () => participationChanges,
                        addParticipation: (participation: StudentParticipation, exercise: Exercise) => {
                            registeredParticipations.push({ participationId: participation.id, exerciseId: exercise?.id });
                        },
                        unsubscribeForLatestResultOfParticipation: (participationId: number) => releasedResultParticipationIds.push(participationId),
                    },
                },
                {
                    provide: ProgrammingSubmissionService,
                    useValue: {
                        getLatestPendingSubmissionByParticipationId: () => pendingSubmissions.asObservable(),
                        unsubscribeForLatestSubmissionOfParticipation: (participationId: number) => releasedSubmissionParticipationIds.push(participationId),
                    },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // Render nothing: this spec exercises the component's scoring logic, not its template.
            .overrideComponent(CourseExerciseGroupDetailComponent, { set: { template: '' } })
            .compileComponents();

        fixture = TestBed.createComponent(CourseExerciseGroupDetailComponent);
    }

    /** Access to protected members under test. */
    function comp(): {
        group: () => { id?: number; title?: string } | undefined;
        exercises: () => Exercise[];
        exerciseSumMaxPoints: () => number;
        effectiveGroupMaxPoints: () => number;
        capReducesMaxPoints: () => boolean;
        variantsInfoBoxData: () => InformationBox;
        pointsInfoBoxData: () => InformationBox;
        groupDateInfoBoxes: () => InformationBox[];
        renderedStatements: () => Map<number, SafeHtml>;
        exerciseParticipation: (exercise: Exercise) => StudentParticipation | undefined;
        exerciseLink: (exercise: Exercise) => string;
    } {
        return fixture.componentInstance as never;
    }

    /** Reads the protected computed under test. */
    function achievedGroupPoints(): number {
        return (fixture.componentInstance as unknown as { achievedGroupPoints: () => number }).achievedGroupPoints();
    }

    beforeEach(() => {
        storedGroupPoints = new Map();
        latestResults = new Map();
        participationChanges = new BehaviorSubject<Participation | undefined>(undefined);
        pendingSubmissions = new BehaviorSubject<ProgrammingSubmissionStateObj>({
            participationId: 555,
            submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
        });
        registeredParticipations = [];
        releasedResultParticipationIds = [];
        releasedSubmissionParticipationIds = [];
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('surfaces the authoritative server-computed points for the group', async () => {
        // The server already caps and plagiarism-adjusts this value; the component must display it verbatim.
        storedGroupPoints.set(GROUP_ID, 13);
        await setup(exercisesInGroup(15));
        expect(achievedGroupPoints()).toBe(13);
    });

    it('falls back to 0 when the server stored no contribution for the group', async () => {
        // e.g. before the dashboard scores load, or when the group contributes nothing.
        await setup(exercisesInGroup(15));
        expect(achievedGroupPoints()).toBe(0);
    });

    it('rounds a stored value that does not divide cleanly, instead of showing it at full float width', async () => {
        storedGroupPoints.set(GROUP_ID, 13.333333333333334);
        await setup(exercisesInGroup(15));
        expect(achievedGroupPoints()).toBe(13.3);
    });

    it('rounds the group maximum, so a sum of fractional variant points does not drift into the header', async () => {
        // 0.1 + 0.2 is 0.30000000000000004 in IEEE-754, which is exactly what the header used to print.
        const reference = { id: GROUP_ID, title: 'Sorting variants' };
        const fractionalVariant = (id: number, maxPoints: number) =>
            ({
                id,
                type: ExerciseType.TEXT,
                maxPoints,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                exerciseVariantGroup: reference,
            }) as unknown as Exercise;
        await setup([fractionalVariant(1, 0.1), fractionalVariant(2, 0.2)]);

        expect(comp().exerciseSumMaxPoints()).toBe(0.3);
        expect(comp().effectiveGroupMaxPoints()).toBe(0.3);
    });

    describe('effectiveGroupMaxPoints', () => {
        it('shows the variants sum when no cap is configured', async () => {
            await setup(exercisesInGroup(undefined));
            expect(comp().effectiveGroupMaxPoints()).toBe(20);
            expect(comp().capReducesMaxPoints()).toBe(false);
        });

        it('caps the maximum at a binding cap (below the variants sum)', async () => {
            await setup(exercisesInGroup(15));
            expect(comp().effectiveGroupMaxPoints()).toBe(15);
            expect(comp().capReducesMaxPoints()).toBe(true);
        });

        it('ignores a cap that exceeds the variants sum and shows the sum instead', async () => {
            await setup(exercisesInGroup(100));
            // A student can never earn more than the 20 the variants offer, so the course maximum is 20, not 100.
            expect(comp().effectiveGroupMaxPoints()).toBe(20);
            expect(comp().capReducesMaxPoints()).toBe(false);
        });

        it('treats a zero cap as a binding cap that discards all group points', async () => {
            await setup(exercisesInGroup(0));
            expect(comp().effectiveGroupMaxPoints()).toBe(0);
            expect(comp().capReducesMaxPoints()).toBe(true);
        });

        it('excludes a bonus variant from the maximum, matching the server inclusion rule', async () => {
            // Two INCLUDED_COMPLETELY variants (10 each) + one INCLUDED_AS_BONUS variant: the course maximum only counts
            // the first two, so the cap of 25 is not binding even though the raw sum of all variants would be 30.
            await setup([...exercisesInGroup(25), extraVariant(IncludedInOverallScore.INCLUDED_AS_BONUS, 25)]);
            expect(comp().exerciseSumMaxPoints()).toBe(20);
            expect(comp().effectiveGroupMaxPoints()).toBe(20);
            expect(comp().capReducesMaxPoints()).toBe(false);
        });

        it('excludes a not-included variant from the maximum, matching the server inclusion rule', async () => {
            await setup([...exercisesInGroup(15), extraVariant(IncludedInOverallScore.NOT_INCLUDED, 15)]);
            expect(comp().exerciseSumMaxPoints()).toBe(20);
            // The cap of 15 binds against the included sum of 20, not the raw sum of 30.
            expect(comp().effectiveGroupMaxPoints()).toBe(15);
            expect(comp().capReducesMaxPoints()).toBe(true);
        });
    });

    describe('group resolution', () => {
        it('resolves the group, its members and the sum of max points from the dashboard exercises', async () => {
            await setup(exercisesInGroup(15));
            expect(comp().group()?.id).toBe(GROUP_ID);
            expect(
                comp()
                    .exercises()
                    .map((e) => e.id),
            ).toEqual([1, 2]);
            expect(comp().exerciseSumMaxPoints()).toBe(20);
            expect(comp().variantsInfoBoxData().content.value).toBe(2);
            expect(comp().pointsInfoBoxData().isContentComponent).toBe(true);
        });

        it('resolves no group when the course has no exercises of that group', async () => {
            await setup([{ id: 5, type: ExerciseType.TEXT } as Exercise]);
            expect(comp().group()).toBeUndefined();
            expect(comp().exercises()).toEqual([]);
        });

        it('publishes the group title to the entity title service', async () => {
            await setup(exercisesInGroup(15));
            const titleService = TestBed.inject(EntityTitleService);
            const setTitleSpy = vi.spyOn(titleService, 'setTitle');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(setTitleSpy).toHaveBeenCalledWith(expect.anything(), [GROUP_ID], 'Sorting variants');
        });
    });

    describe('groupDateInfoBoxes', () => {
        function exercisesWithGroupDates(dates: { dueDate?: dayjs.Dayjs; startDate?: dayjs.Dayjs; assessmentDueDate?: dayjs.Dayjs }): Exercise[] {
            const reference = { id: GROUP_ID, title: 'Sorting variants', ...dates };
            return [{ id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: reference, problemStatement: 'a' } as unknown as Exercise];
        }

        it('shows the due-over box for a past due date', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().subtract(2, 'day') }));
            const boxes = comp().groupDateInfoBoxes();
            expect(boxes.map((b) => b.title)).toEqual(['artemisApp.courseOverview.exerciseDetails.submissionDueOver']);
        });

        it('shows a relative due date with tooltip when due within a week', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().add(3, 'day') }));
            const box = comp().groupDateInfoBoxes()[0];
            expect(box.title).toBe('artemisApp.courseOverview.exerciseDetails.submissionDue');
            expect(box.content.type).toBe('timeAgo');
            expect(box.tooltip).toBeDefined();
        });

        it('shows an absolute due date when due later than a week', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().add(2, 'week') }));
            const box = comp().groupDateInfoBoxes()[0];
            expect(box.content.type).toBe('dateTime');
            expect(box.tooltip).toBeUndefined();
        });

        it('marks a due date within 24 hours as danger', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().add(2, 'hour') }));
            expect(comp().groupDateInfoBoxes()[0].contentColor).toBe('danger');
        });

        it('adds the assessment-due box when the due date passed and assessment is still open', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().subtract(1, 'day'), assessmentDueDate: dayjs().add(1, 'day') }));
            const titles = comp()
                .groupDateInfoBoxes()
                .map((b) => b.title);
            expect(titles).toContain('artemisApp.courseOverview.exerciseDetails.assessmentDue');
        });

        it('adds the start-date box for a future start date', async () => {
            await setup(exercisesWithGroupDates({ startDate: dayjs().add(2, 'day') }));
            const titles = comp()
                .groupDateInfoBoxes()
                .map((b) => b.title);
            expect(titles).toEqual(['artemisApp.courseOverview.exerciseDetails.startDate']);
        });

        it('shows no boxes without group dates', async () => {
            await setup(exercisesWithGroupDates({}));
            expect(comp().groupDateInfoBoxes()).toEqual([]);
        });
    });

    describe('problem statements', () => {
        it('renders inline problem statements of the members', async () => {
            await setup(exercisesInGroup(undefined));
            fixture.detectChanges();
            await fixture.whenStable();
            const rendered = comp().renderedStatements();
            expect(rendered.get(1)).toBeDefined();
            expect(rendered.get(2)).toBeDefined();
        });

        it('batch-loads missing problem statements from the group preview endpoint', async () => {
            const reference = { id: GROUP_ID, title: 'Sorting variants' };
            const member = { id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: reference } as unknown as Exercise;
            const previews = of<ExerciseProblemStatementDTO[]>([{ exerciseId: 1, problemStatement: 'loaded **statement**' }]);
            await setup([member], { getProblemStatements: () => previews });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp().renderedStatements().get(1)).toBeDefined();
        });

        it('issues a single batch request for the whole group instead of one per member', async () => {
            // The whole point of the endpoint: opening a group must not fan out one detail request per member.
            const reference = { id: GROUP_ID, title: 'Sorting variants' };
            const memberIds = [1, 2, 3];
            const members = memberIds.map((id) => ({ id, type: ExerciseType.TEXT, exerciseVariantGroup: reference }) as unknown as Exercise);
            const previewsSpy = vi.fn(() => of<ExerciseProblemStatementDTO[]>(memberIds.map((id) => ({ exerciseId: id, problemStatement: `statement ${id}` }))));
            await setup(members, { getProblemStatements: previewsSpy });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(previewsSpy).toHaveBeenCalledTimes(1);
            expect(previewsSpy).toHaveBeenCalledWith(1, GROUP_ID);
            expect(comp().renderedStatements().size).toBe(3);
        });

        it('releases the requested group on a failed batch so a retry is possible', async () => {
            const reference = { id: GROUP_ID, title: 'Sorting variants' };
            const member = { id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: reference } as unknown as Exercise;
            const previewsSpy = vi.fn(() => throwError(() => new Error('boom')));
            await setup([member], { getProblemStatements: previewsSpy });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(previewsSpy).toHaveBeenCalled();
            const requested = (fixture.componentInstance as unknown as { requestedGroupIds: Set<number> })['requestedGroupIds'];
            expect(requested.has(GROUP_ID)).toBe(false);
        });
    });

    describe('helpers', () => {
        it('returns the graded participation and the exercise link', async () => {
            await setup(exercisesInGroup(undefined));
            const exercise = comp().exercises()[0];
            expect(comp().exerciseParticipation(exercise)?.id).toBe(101);
            expect(comp().exerciseParticipation({} as Exercise)).toBeUndefined();
            expect(comp().exerciseLink(exercise)).toBe('/courses/1/exercises/1');
        });

        it('selects the graded participation even when a practice run is listed first', async () => {
            // The dashboard response includes practice test runs in unspecified order; the card must not show them.
            const exercises = exercisesInGroup(undefined);
            exercises[0].studentParticipations = [{ id: 201, testRun: true } as StudentParticipation, { id: 101 } as StudentParticipation];
            await setup(exercises);
            expect(comp().exerciseParticipation(comp().exercises()[0])?.id).toBe(101);
        });
    });
    describe('milestone status', () => {
        /** Access to the protected milestone-status state under test. */
        function milestone(): {
            milestoneStatus: () => MilestoneStatusDTO | undefined;
            milestoneStatusFailed: () => boolean;
            isLoadingMilestoneStatus: () => boolean;
            retryMilestoneStatus: () => void;
        } {
            return fixture.componentInstance as never;
        }

        it('loads the milestone status for a milestone group', async () => {
            const status = { milestoneExerciseId: 99, started: false } as MilestoneStatusDTO;
            const statusSpy = vi.fn(() => of(status));
            await setup([milestoneGroupMember()], { getMilestoneStatus: statusSpy });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(statusSpy).toHaveBeenCalled();
            expect(milestone().milestoneStatus()).toBe(status);
            expect(milestone().milestoneStatusFailed()).toBe(false);
        });

        it('surfaces a failed status request instead of silently rendering nothing', async () => {
            // A 404 here is suppressed by the global alert handler, so without this the whole header - start button
            // included - simply disappears, which is exactly how the missing "Start exercise" button was reported.
            const statusSpy = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 404 })));
            await setup([milestoneGroupMember()], { getMilestoneStatus: statusSpy });
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            fixture.detectChanges();
            await fixture.whenStable();

            expect(statusSpy).toHaveBeenCalled();
            expect(milestone().milestoneStatus()).toBeUndefined();
            expect(milestone().milestoneStatusFailed()).toBe(true);
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.exerciseVariantGroup.detail.milestoneStatusLoadFailed');
            const requested = (fixture.componentInstance as unknown as { requestedMilestoneStatusGroupIds: Set<number> })['requestedMilestoneStatusGroupIds'];
            expect(requested.has(GROUP_ID)).toBe(false);
        });

        it('retries the status request after a failure', async () => {
            const status = { milestoneExerciseId: 99, started: false } as MilestoneStatusDTO;
            let firstCall = true;
            const statusSpy = vi.fn(() => {
                if (firstCall) {
                    firstCall = false;
                    return throwError(() => new HttpErrorResponse({ status: 404 }));
                }
                return of(status);
            });
            await setup([milestoneGroupMember()], { getMilestoneStatus: statusSpy as never });
            fixture.detectChanges();
            await fixture.whenStable();
            expect(milestone().milestoneStatusFailed()).toBe(true);

            milestone().retryMilestoneStatus();
            await fixture.whenStable();

            expect(statusSpy).toHaveBeenCalledTimes(2);
            expect(milestone().milestoneStatus()).toBe(status);
            expect(milestone().milestoneStatusFailed()).toBe(false);
            expect(milestone().isLoadingMilestoneStatus()).toBe(false);
        });
    });

    describe('milestone participation', () => {
        /** Access to the protected milestone-participation state under test. */
        function milestone(): {
            milestoneExercise: () => ProgrammingExercise | undefined;
            milestoneResult: () => Result | undefined;
        } {
            return fixture.componentInstance as never;
        }

        /** A started milestone whose participation the code-quality panel would read the group's SCA feedback from. */
        function startedStatus(): MilestoneStatusDTO {
            return { milestoneExerciseId: 99, started: true, participationId: 555 } as MilestoneStatusDTO;
        }

        function participationWithResult(result: Result): ProgrammingExerciseStudentParticipation {
            return {
                id: 555,
                exercise: { id: 99, type: 'milestone', staticCodeAnalysisEnabled: true } as unknown as ProgrammingExercise,
                submissions: [{ id: 777, results: [result] }],
            } as unknown as ProgrammingExerciseStudentParticipation;
        }

        it('loads the milestone participation with its latest result once the milestone has been started', async () => {
            const result = { id: 888, codeIssueCount: 2 } as Result;
            const participationSpy = vi.fn(() => of(participationWithResult(result)));
            await setup([milestoneGroupMember()], { getMilestoneStatus: () => of(startedStatus()), getStudentParticipationWithLatestResult: participationSpy });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(participationSpy).toHaveBeenCalledWith(555);
            expect(milestone().milestoneExercise()?.id).toBe(99);
            expect(milestone().milestoneResult()).toBe(result);
        });

        it('does not request a participation before the student has started the milestone', async () => {
            const participationSpy = vi.fn(() => EMPTY);
            const notStarted = { milestoneExerciseId: 99, started: false } as MilestoneStatusDTO;
            await setup([milestoneGroupMember()], { getMilestoneStatus: () => of(notStarted), getStudentParticipationWithLatestResult: participationSpy as never });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(participationSpy).not.toHaveBeenCalled();
            expect(milestone().milestoneResult()).toBeUndefined();
        });

        it('leaves the panel unrendered and stays retryable when the participation request fails', async () => {
            // Unlike the start action, this is supplementary information: a failure must not alert the student, and it
            // must not permanently block a later attempt either.
            const participationSpy = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 500 })));
            await setup([milestoneGroupMember()], { getMilestoneStatus: () => of(startedStatus()), getStudentParticipationWithLatestResult: participationSpy as never });
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            fixture.detectChanges();
            await fixture.whenStable();

            expect(participationSpy).toHaveBeenCalledOnce();
            expect(milestone().milestoneExercise()).toBeUndefined();
            expect(milestone().milestoneResult()).toBeUndefined();
            expect(alertSpy).not.toHaveBeenCalled();
            const requested = (fixture.componentInstance as unknown as { requestedMilestoneParticipationIds: Set<number> })['requestedMilestoneParticipationIds'];
            expect(requested.has(555)).toBe(false);
        });
    });

    describe('live updates', () => {
        /** Access to the protected live state under test. */
        function live(): {
            milestoneExercise: () => ProgrammingExercise | undefined;
            milestoneResult: () => Result | undefined;
            isMilestoneBuilding: () => boolean;
            isMilestoneQueued: () => boolean;
            exerciseParticipation: (exercise: Exercise) => StudentParticipation | undefined;
        } {
            return fixture.componentInstance as never;
        }

        function startedStatus(): MilestoneStatusDTO {
            return { milestoneExerciseId: 99, started: true, participationId: 555 } as MilestoneStatusDTO;
        }

        /** The REST snapshot the page starts from: one finished build worth 4 of the milestone's 20 points. */
        function initialParticipation(): ProgrammingExerciseStudentParticipation {
            return {
                id: 555,
                exercise: { id: 99, type: 'milestone', staticCodeAnalysisEnabled: true, maxPoints: 20 } as unknown as ProgrammingExercise,
                submissions: [{ id: 777, results: [{ id: 888, score: 20 } as Result] }],
            } as unknown as ProgrammingExerciseStudentParticipation;
        }

        async function setupStartedMilestone(accuracyOfScores?: number): Promise<void> {
            await setup([milestoneGroupMember()], {
                getMilestoneStatus: () => of(startedStatus()),
                getStudentParticipationWithLatestResult: () => of(initialParticipation()),
                accuracyOfScores,
            });
            fixture.detectChanges();
            await fixture.whenStable();
        }

        it('replaces the milestone result when a build finishes, keeping the exercise the REST snapshot brought along', async () => {
            await setupStartedMilestone();
            expect(live().milestoneResult()?.id).toBe(888);

            latestResultOf(555).next({ id: 999, score: 75, feedbacks: [] } as unknown as Result);

            expect(live().milestoneResult()?.id).toBe(999);
            // The websocket payload carries no exercise, so the only source of staticCodeAnalysisEnabled must survive it.
            expect(live().milestoneExercise()?.id).toBe(99);
            expect(live().milestoneExercise()?.staticCodeAnalysisEnabled).toBe(true);
        });

        it('converts the result date, which the websocket service deliberately leaves as it came off the wire', async () => {
            await setupStartedMilestone();

            latestResultOf(555).next({ id: 999, completionDate: '2026-09-10T10:00:00Z' } as unknown as Result);

            expect(dayjs.isDayjs(live().milestoneResult()?.completionDate)).toBe(true);
        });

        it('subscribes for the milestone participation as a personal subscription of its own exercise', async () => {
            await setupStartedMilestone();

            expect(registeredParticipations).toContainEqual({ participationId: 555, exerciseId: 99 });
        });

        it('reports a queued and then a running build, and stops reporting one once the result lands', async () => {
            await setupStartedMilestone();

            pendingSubmissions.next({ participationId: 555, submissionState: ProgrammingSubmissionState.IS_QUEUED });
            expect(live().isMilestoneQueued()).toBe(true);
            expect(live().isMilestoneBuilding()).toBe(false);

            pendingSubmissions.next({ participationId: 555, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION });
            expect(live().isMilestoneQueued()).toBe(false);
            expect(live().isMilestoneBuilding()).toBe(true);

            pendingSubmissions.next({ participationId: 555, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
            expect(live().isMilestoneQueued()).toBe(false);
            expect(live().isMilestoneBuilding()).toBe(false);
        });

        it('releases the shared websocket and submission state when the page goes away', async () => {
            await setupStartedMilestone();

            fixture.destroy();

            expect(releasedResultParticipationIds).toContain(555);
            // Leaving this out would leak the ProgrammingSubmissionService's per-participation state until logout.
            expect(releasedSubmissionParticipationIds).toContain(555);
        });

        it('renders a variant card from the participation the websocket updated, not the dashboard snapshot', async () => {
            const exercise = milestoneGroupMember();
            (exercise as Exercise).studentParticipations = [{ id: 101 } as StudentParticipation];
            await setup([exercise], { getMilestoneStatus: () => of(startedStatus()), getStudentParticipationWithLatestResult: () => of(initialParticipation()) });
            fixture.detectChanges();
            await fixture.whenStable();

            // Registering the participation is what lets the websocket service merge a result into it at all.
            expect(registeredParticipations).toContainEqual({ participationId: 101, exerciseId: exercise.id });
            expect(live().exerciseParticipation(exercise)?.submissions).toBeUndefined();

            const updated = { id: 101, exercise, submissions: [{ id: 1, results: [{ id: 2 } as Result] }] } as unknown as StudentParticipation;
            participationChanges.next(updated);

            expect(live().exerciseParticipation(exercise)).toBe(updated);
        });

        it('ignores participation updates that do not belong to this group', async () => {
            const exercise = milestoneGroupMember();
            (exercise as Exercise).studentParticipations = [{ id: 101 } as StudentParticipation];
            await setup([exercise], { getMilestoneStatus: () => of(startedStatus()), getStudentParticipationWithLatestResult: () => of(initialParticipation()) });
            fixture.detectChanges();
            await fixture.whenStable();

            // The participation stream is app-wide, so another page's exercise must not leak onto this group's cards.
            participationChanges.next({ id: 4242, exercise: { id: 4242 } as Exercise } as unknown as StudentParticipation);

            expect(live().exerciseParticipation(exercise)?.id).toBe(101);
        });

        it('follows the aggregated milestone score for the group points, and uses the stored value until one arrives', async () => {
            storedGroupPoints.set(GROUP_ID, 13);
            await setupStartedMilestone();
            expect(achievedGroupPoints()).toBe(13);

            // The server writes the group's points (story points minus the SCA penalty) onto the milestone's own result.
            latestResultOf(555).next({ id: 888, score: 75 } as unknown as Result);

            expect(achievedGroupPoints()).toBe(15);
        });

        it('rounds a live score that does not divide cleanly into the milestone points', async () => {
            await setupStartedMilestone();

            // 33.333 % of the milestone's 20 points is 6.6666 - the raw figure the header used to print in full.
            latestResultOf(555).next({ id: 888, score: 33.333 } as unknown as Result);

            expect(achievedGroupPoints()).toBe(6.7);
        });

        it('honours a course configured for more decimals rather than forcing a single one', async () => {
            await setupStartedMilestone(2);

            latestResultOf(555).next({ id: 888, score: 33.333 } as unknown as Result);

            expect(achievedGroupPoints()).toBe(6.67);
        });
    });
});
