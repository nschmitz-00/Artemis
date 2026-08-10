import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, ChildrenOutletContexts, Router, RouterLink, RouterOutlet } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/account/user/user.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { UserStoryExercise } from 'app/programming/shared/entities/user-story-exercise.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { ExerciseSplitPanelComponent } from 'app/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProblemStatementComponent } from 'app/course/overview/exercise-details/problem-statement/problem-statement.component';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ResetRepoButtonComponent } from 'app/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { LtiInitializerComponent } from 'app/course/overview/exercise-details/lti-initializer/lti-initializer.component';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ProgrammingExerciseExplanationVideoComponent } from 'app/programming/shared/explanation-video/programming-exercise-explanation-video.component';

class ResizeObserverMock {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

describe('ExerciseSplitPanelComponent', () => {
    let fixture: ComponentFixture<ExerciseSplitPanelComponent>;
    let component: ExerciseSplitPanelComponent;
    let accountService: MockAccountService;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        await TestBed.configureTestingModule({
            imports: [ExerciseSplitPanelComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { openChat: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: { parent: {}, firstChild: undefined } },
                { provide: TranslateService, useClass: MockTranslateService },
                ChildrenOutletContexts,
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, {
                set: {
                    template: `
                        <jhi-resizable-panels>
                            @if (showEditorPanel()) {
                                <ng-template jhiPanel [label]="editorLabelKey()">Editor</ng-template>
                            }
                            @if (exercise().type !== ExerciseType.QUIZ) {
                                <ng-template jhiPanel [label]="'problemStatement'">Problem Statement</ng-template>
                            }
                            @if (showIris()) {
                                <ng-template jhiPanel [label]="'iris'" [startsCollapsed]="irisPanelStartsCollapsed()">Iris</ng-template>
                            }
                        </jhi-resizable-panels>
                    `,
                    imports: [ResizablePanelsComponent, PanelDirective],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseSplitPanelComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService) as unknown as MockAccountService;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as Exercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('should start the Iris panel collapsed for users who opted out of AI', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(true);
    });

    it('should not start the Iris panel collapsed for users who accepted AI', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(false);
    });

    it('should not start the Iris panel collapsed before the user made an AI selection', () => {
        accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(false);
    });

    describe('editor panel for milestone and user story exercises', () => {
        // ResizablePanelsComponent renders panels()[0] in the main container and tabs the rest into the right group next
        // to Communication. Milestone/UserStory exercises have no editor route, so an editor panel here would render
        // empty, take the main container, and push the problem statement into the right-hand tabs. Suppressing it puts
        // the problem statement first, matching a programming exercise that is worked on with an offline IDE.
        /** The label of the panel that ResizablePanelsComponent renders in the main container. */
        const mainContainerPanelLabel = (): string => fixture.debugElement.query(By.directive(ResizablePanelsComponent)).injector.get(ResizablePanelsComponent).leftPanel().label();

        it.each([ExerciseType.MILESTONE, ExerciseType.USER_STORY])('should put the problem statement in the main container for %s exercises', (type) => {
            fixture.componentRef.setInput('exercise', { id: 1, type } as Exercise);
            fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
            fixture.detectChanges();

            expect(component.usesRouterOutlet()).toBeFalsy();
            expect(component.showEditorPanel()).toBeFalsy();
            expect(mainContainerPanelLabel()).toBe('problemStatement');
        });

        it('should still show the editor panel for other participated exercise types', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as Exercise);
            fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
            fixture.detectChanges();

            expect(component.showEditorPanel()).toBeTruthy();
        });
    });

    describe('communication panel for user story exercises', () => {
        // Only the parent Milestone owns a channel (UserStoryExerciseService never creates one), so discussion about a
        // user story has to target the Milestone. DiscussionSectionComponent resolves the channel from the exercise it
        // is given, via getChannelOfExercise(course.id, exercise.id).
        const courseWithCommunication = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING } as Course;

        /** Mirrors the student details payload: the milestone is serialized without its course. */
        const userStoryWithMilestone = () =>
            ({
                id: 7,
                type: ExerciseType.USER_STORY,
                course: courseWithCommunication,
                milestoneExercise: { id: 2, type: ExerciseType.MILESTONE },
            }) as Exercise;

        it('should point the communication panel at the parent milestone, not the user story', () => {
            fixture.componentRef.setInput('exercise', userStoryWithMilestone());
            fixture.detectChanges();

            expect(component.showDiscussion()).toBeTruthy();
            // The milestone's exercise id, but the user story's course - the payload omits the course on the milestone
            expect(component.discussionExercise()?.id).toBe(2);
            expect(component.discussionExercise()?.course?.id).toBe(courseWithCommunication.id);
        });

        it('should hide the communication panel for a user story whose milestone is not loaded', () => {
            fixture.componentRef.setInput('exercise', { id: 7, type: ExerciseType.USER_STORY, course: courseWithCommunication } as Exercise);
            fixture.detectChanges();

            expect(component.discussionExercise()).toBeUndefined();
            expect(component.showDiscussion()).toBeFalsy();
        });

        it('should not mutate the user story when deriving the milestone discussion target', () => {
            const userStory = userStoryWithMilestone();
            fixture.componentRef.setInput('exercise', userStory);
            fixture.detectChanges();

            component.discussionExercise();

            expect((userStory as UserStoryExercise).milestoneExercise?.course).toBeUndefined();
        });

        it('should keep targeting the exercise itself for every other type', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MILESTONE, course: courseWithCommunication } as Exercise);
            fixture.detectChanges();

            expect(component.showDiscussion()).toBeTruthy();
            expect(component.discussionExercise()?.id).toBe(1);
        });
    });

    it('navigates only when the target route identity changes, not when the participation object is replaced (prevents the navigate-thrash loop on incoming results, #12976)', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);

        // Programming exercise with the online editor: navigating to the code editor is expected on the first run.
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', 1, 'code-editor', 5], expect.anything());

        navigateSpy.mockClear();

        // An incoming result replaces the participation object but keeps its id. This must NOT re-navigate — otherwise
        // navigation thrashes and re-creates the code-editor subtree in a loop, flooding the server with requests.
        fixture.componentRef.setInput('studentParticipation', { id: 5, submissions: [{ id: 9 }] } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).not.toHaveBeenCalled();

        // A genuine switch to a different participation still navigates.
        fixture.componentRef.setInput('studentParticipation', { id: 6 } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', 1, 'code-editor', 6], expect.anything());
    });

    it('should keep the problem statement open for users who opted out of AI when an editor panel is shown', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
        fixture.componentRef.setInput('studentParticipation', { id: 1 } as StudentParticipation);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        expect(component.irisPanelStartsCollapsed()).toBe(false);
        expect(resizablePanels.isRightPanelCollapsed()).toBe(false);
        expect(resizablePanels.activeRightIndex()).toBe(0);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Problem Statement');
    });
});

describe('ExerciseSplitPanelComponent explanation video widget rendering', () => {
    // Unlike the suite above (which substitutes a trivial stub template), this suite renders the component's real
    // templateUrl so it proves where `<jhi-programming-exercise-explanation-video>` actually ends up in the DOM -
    // specifically that it's reachable in the always-shown "Problem Statement" panel, not only behind the online
    // code editor (which is hidden entirely whenever `allowOnlineEditor` is false, i.e. offline-IDE-only exercises).
    let fixture: ComponentFixture<ExerciseSplitPanelComponent>;

    const baseProgrammingExercise = { id: 1, type: ExerciseType.PROGRAMMING, requiresExplanationVideo: true } as unknown as Exercise;
    const participation = { id: 5 } as StudentParticipation;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        await TestBed.configureTestingModule({
            imports: [ExerciseSplitPanelComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { openChat: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: { parent: {}, firstChild: undefined } },
                { provide: TranslateService, useClass: MockTranslateService },
                ChildrenOutletContexts,
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, {
                set: {
                    imports: [
                        RouterOutlet,
                        RouterLink,
                        ResizablePanelsComponent,
                        PanelDirective,
                        MockComponent(ProblemStatementComponent),
                        MockComponent(IrisBaseChatbotComponent),
                        MockComponent(IrisLogoComponent),
                        MockDirective(TranslateDirective),
                        MockComponent(ResetRepoButtonComponent),
                        MockComponent(ComplaintsStudentViewComponent),
                        MockComponent(RatingComponent),
                        MockComponent(ModelingEditorComponent),
                        MockComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent),
                        MockComponent(CompetencyContributionComponent),
                        MockComponent(LtiInitializerComponent),
                        MockComponent(DiscussionSectionComponent),
                        ProgrammingExerciseExplanationVideoComponent,
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseSplitPanelComponent);
        fixture.componentRef.setInput('exercise', baseProgrammingExercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('irisEnabled', false);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('renders the explanation video widget in the problem statement panel even when the online editor is disabled (offline IDE only)', () => {
        fixture.componentRef.setInput('exercise', { ...baseProgrammingExercise, allowOnlineEditor: false } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', participation);
        fixture.detectChanges();

        const widget = fixture.debugElement.query(By.directive(ProgrammingExerciseExplanationVideoComponent));
        expect(widget).not.toBeNull();
    });

    it('renders the explanation video widget when the online editor is enabled too', () => {
        fixture.componentRef.setInput('exercise', { ...baseProgrammingExercise, allowOnlineEditor: true } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', participation);
        fixture.detectChanges();

        const widget = fixture.debugElement.query(By.directive(ProgrammingExerciseExplanationVideoComponent));
        expect(widget).not.toBeNull();
    });

    it('does not render the explanation video widget when the exercise does not require one', () => {
        fixture.componentRef.setInput('exercise', { ...baseProgrammingExercise, requiresExplanationVideo: false, allowOnlineEditor: false } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', participation);
        fixture.detectChanges();

        const widget = fixture.debugElement.query(By.directive(ProgrammingExerciseExplanationVideoComponent));
        expect(widget).toBeNull();
    });

    it('does not render the explanation video widget before the student has a participation', () => {
        fixture.componentRef.setInput('exercise', { ...baseProgrammingExercise, allowOnlineEditor: false } as unknown as Exercise);
        fixture.detectChanges();

        const widget = fixture.debugElement.query(By.directive(ProgrammingExerciseExplanationVideoComponent));
        expect(widget).toBeNull();
    });

    it('does not render the explanation video widget for non-programming exercises', () => {
        fixture.componentRef.setInput('exercise', { id: 2, type: ExerciseType.TEXT, requiresExplanationVideo: true } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', participation);
        fixture.detectChanges();

        const widget = fixture.debugElement.query(By.directive(ProgrammingExerciseExplanationVideoComponent));
        expect(widget).toBeNull();
    });
});
