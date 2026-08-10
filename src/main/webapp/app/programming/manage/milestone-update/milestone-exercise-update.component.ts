import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MilestoneExercise } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { ProgrammingExerciseLanguageState } from 'app/programming/manage/update/programming-exercise-language-state';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { Subject } from 'rxjs';
import { loadCourseExerciseCategories } from 'app/exercise/course-exercises/course-utils';
import { EXERCISE_TITLE_NAME_PATTERN, PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN } from 'app/foundation/constants/input.constants';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseTitleChannelNamePrimengComponent } from 'app/exercise/exercise-title-channel-name-primeng/exercise-title-channel-name-primeng.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { ProgrammingExerciseGradingComponent } from 'app/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { MilestoneUserStoriesComponent } from 'app/programming/manage/milestone-update/milestone-user-stories.component';
import { MilestoneTestCaseCoverageComponent } from 'app/programming/manage/milestone-update/milestone-test-case-coverage.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { MarkdownEditorHeight } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { InputTextModule } from 'primeng/inputtext';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-milestone-exercise-update',
    templateUrl: './milestone-exercise-update.component.html',
    styleUrls: ['../../shared/programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        ExerciseTitleChannelNamePrimengComponent,
        CategorySelectorPrimengComponent,
        ProgrammingExerciseGradingComponent,
        MilestoneUserStoriesComponent,
        MilestoneTestCaseCoverageComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseLanguageComponent,
        FormFooterComponent,
        InputTextModule,
        ArtemisTranslatePipe,
    ],
})
export class MilestoneExerciseUpdateComponent implements OnInit {
    private readonly milestoneExerciseService = inject(MilestoneExerciseService);
    private readonly courseService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);

    private readonly profileService = inject(ProfileService);
    private readonly programmingLanguageFeatureService = inject(ProgrammingLanguageFeatureService);

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly titlePattern = EXERCISE_TITLE_NAME_PATTERN;
    protected readonly shortNamePattern = PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN;

    /**
     * The same language / project type / build option state machine the plain programming exercise form uses, so the section
     * rendered by {@link ProgrammingExerciseLanguageComponent} behaves identically here.
     * <p>
     * No template loading hook: a milestone's problem statement is its overview, and the tasks live in its user stories - pulling
     * in a language's exercise template would drop task markup referencing test cases into the wrong statement.
     */
    protected readonly languageState = new ProgrammingExerciseLanguageState(this.programmingLanguageFeatureService, {
        exercise: () => this.milestoneExercise(),
    });

    // All fields are always shown (there is no simple/detailed mode toggle for MilestoneExercise, unlike ProgrammingExercise).
    protected readonly isEditFieldDisplayedRecord = Object.fromEntries(Object.values(ProgrammingExerciseInputField).map((field) => [field, true])) as Record<
        ProgrammingExerciseInputField,
        boolean
    >;

    /**
     * The grading section renders everything the plain programming exercise form shows except the points and bonus points inputs:
     * both are the sums over the user stories, so letting them be typed here would immediately be overwritten on the next child
     * change. They are rendered read-only above the section instead.
     */
    protected readonly gradingFieldsDisplayedRecord = computed(() => ({
        ...this.isEditFieldDisplayedRecord,
        [ProgrammingExerciseInputField.POINTS]: false,
        [ProgrammingExerciseInputField.BONUS_POINTS]: false,
    }));

    /**
     * Required by the grading section, which offers to reset a test case's visibility on import. A milestone is never imported,
     * so this stays at its defaults.
     */
    protected readonly importOptions: ImportOptions = {
        recreateBuildPlans: false,
        updateTemplate: false,
        setTestCaseVisibilityToAfterDueDate: true,
    };

    milestoneExercise = signal<MilestoneExercise>(undefined!);

    /**
     * The problem statements of the user stories, handed to the instruction editor's status bar so it does not report their test
     * cases as unused. The milestone owns the test repository, but the tests are meant to be distributed across the user stories
     * rather than listed in the milestone's own problem statement - without this every test case shows up as missing here.
     */
    protected readonly userStoryProblemStatements = computed(
        () => this.milestoneExercise()?.userStoryExercises?.map((userStoryExercise) => userStoryExercise.problemStatement ?? '') ?? [],
    );
    isSaving = signal(false);
    isCreate = signal(false);
    existingCategories = signal<ExerciseCategory[]>([]);

    protected supportedLanguages: string[] = [];
    protected customBuildPlansSupported = '';
    protected inProductionEnvironment = false;
    /** The language section re-renders the instructions through this; the milestone form never triggers it. */
    protected readonly rerenderSubject = new Subject<void>();

    ngOnInit(): void {
        const exerciseIdParam = this.activatedRoute.snapshot.paramMap.get('exerciseId');
        const courseIdParam = this.activatedRoute.snapshot.paramMap.get('courseId');
        this.isCreate.set(!exerciseIdParam);

        this.supportedLanguages = this.languageState.supportedLanguages();
        this.inProductionEnvironment = this.profileService.isProduction();
        if (this.profileService.isProfileActive(PROFILE_LOCALCI)) {
            this.customBuildPlansSupported = PROFILE_LOCALCI;
        }

        if (exerciseIdParam) {
            this.milestoneExerciseService.find(Number(exerciseIdParam)).subscribe({
                next: (res: HttpResponse<MilestoneExercise>) => {
                    this.milestoneExercise.set(res.body!);
                    this.adoptLanguageOfLoadedExercise(res.body!);
                    this.loadCategories(res.body!.course?.id);
                },
                error: (error: HttpErrorResponse) => this.alertService.addErrorAlert(error.message),
            });
        } else if (courseIdParam) {
            this.courseService.find(Number(courseIdParam)).subscribe((res) => {
                const newMilestoneExercise = new MilestoneExercise(res.body!, undefined);
                this.milestoneExercise.set(newMilestoneExercise);
                this.loadCategories(res.body!.id);
                // A new milestone starts on the course default, the same way a new programming exercise does
                this.onProgrammingLanguageChange(res.body!.defaultProgrammingLanguage ?? ProgrammingLanguage.JAVA);
            });
        }
    }

    /**
     * Takes over the stored language and project type without running the reset logic, then re-reads the language's feature set
     * so the build options render correctly - going through the setters would wipe the options the milestone was saved with.
     */
    private adoptLanguageOfLoadedExercise(milestoneExercise: MilestoneExercise): void {
        this.languageState.adoptFrom(milestoneExercise);
        this.languageState.refreshLanguageFeatures(milestoneExercise.programmingLanguage!);
        this.languageState.setPackageNamePattern(milestoneExercise.programmingLanguage!, milestoneExercise.projectType === ProjectType.MAVEN_BLACKBOX);
    }

    /**
     * The config {@link ProgrammingExerciseLanguageComponent} renders from. Only the language section's slice is meaningful
     * here; the remaining members of the type belong to sections the milestone form does not show (auxiliary repositories,
     * import options, grading) and are filled with inert defaults.
     * <p>
     * Mutated in place and returned by the same reference on every call, exactly like the programming exercise form does: a
     * fresh object per change-detection pass would keep re-notifying the child's input signal and, with a child effect writing
     * back a two-way model, spin change detection (NG0103).
     */
    private readonly creationConfig: ProgrammingExerciseCreationConfig = Object.assign({}) as ProgrammingExerciseCreationConfig;

    protected getCreationConfig(): ProgrammingExerciseCreationConfig {
        return Object.assign(this.creationConfig, {
            // A milestone is never created through the import flows, so these are constant
            isImportFromExistingExercise: false,
            isImportFromFile: false,
            isImportFromSharing: false,
            isExamMode: false,
            isEdit: !this.isCreate(),
            showSummary: false,
            titleNamePattern: this.titlePattern,
            shortNamePattern: this.shortNamePattern,
            exerciseCategories: this.milestoneExercise().categories ?? [],
            existingCategories: this.existingCategories(),
            updateCategories: (categories: ExerciseCategory[]) => this.updateCategories(categories),
            supportedLanguages: this.supportedLanguages,
            selectedProgrammingLanguage: this.languageState.selectedProgrammingLanguage,
            onProgrammingLanguageChange: (language: ProgrammingLanguage) => this.onProgrammingLanguageChange(language),
            projectTypes: this.languageState.projectTypes,
            selectedProjectType: this.languageState.selectedProjectType,
            onProjectTypeChange: (projectType: ProjectType) => this.onProjectTypeChange(projectType),
            modePickerOptions: this.languageState.modePickerOptions,
            withDependencies: this.languageState.withDependencies,
            onWithDependenciesChanged: (withDependencies: boolean) => this.onWithDependenciesChanged(withDependencies),
            packageNameRequired: this.languageState.packageNameRequired,
            packageNamePattern: this.languageState.packageNamePattern,
            staticCodeAnalysisAllowed: this.languageState.staticCodeAnalysisAllowed,
            onStaticCodeAnalysisChanged: () => this.languageState.onStaticCodeAnalysisChanged(),
            sequentialTestRunsAllowed: this.languageState.sequentialTestRunsAllowed,
            checkoutSolutionRepositoryAllowed: this.languageState.checkoutSolutionRepositoryAllowed,
            auxiliaryRepositoriesSupported: this.languageState.auxiliaryRepositoriesSupported,
            customBuildPlansSupported: this.customBuildPlansSupported,
            inProductionEnvironment: this.inProductionEnvironment,
            buildPlanLoaded: this.languageState.buildPlanLoaded,
            problemStatementLoaded: true,
            templateParticipationResultLoaded: true,
            hasUnsavedChanges: false,
            rerenderSubject: this.rerenderSubject.asObservable(),
            validIdeSelection: () => true,
            validOnlineIdeSelection: () => true,
        } satisfies Partial<ProgrammingExerciseCreationConfig>);
    }

    /**
     * Mirrors ProgrammingExerciseUpdateComponent#onProgrammingLanguageChange, minus the unsaved-changes guard: that guard only
     * exists to protect a problem statement the language template would overwrite, and the milestone form never loads templates.
     */
    protected onProgrammingLanguageChange(language: ProgrammingLanguage): ProgrammingLanguage {
        this.languageState.setPackageNamePattern(language);
        this.languageState.selectedProgrammingLanguage = language;
        return language;
    }

    protected onProjectTypeChange(projectType: ProjectType): ProjectType {
        this.languageState.selectedProjectType = projectType;
        this.languageState.setPackageNamePattern(this.milestoneExercise().programmingLanguage!, projectType === ProjectType.MAVEN_BLACKBOX);
        return projectType;
    }

    protected onWithDependenciesChanged(withDependencies: boolean): boolean {
        this.languageState.setWithDependenciesFlag(withDependencies);
        return withDependencies;
    }

    private loadCategories(courseId?: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories.set(existingCategories);
        });
    }

    /**
     * The problem statement is what students see when they open the Milestone (the exercise details page renders it for
     * every non-quiz exercise type), so this doubles as the Milestone's description.
     */
    updateProblemStatement(problemStatement: string) {
        this.milestoneExercise.update((milestoneExercise) => {
            milestoneExercise.problemStatement = problemStatement;
            return milestoneExercise;
        });
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.milestoneExercise.update((milestoneExercise) => {
            milestoneExercise.categories = categories;
            return milestoneExercise;
        });
    }

    save() {
        if (this.isSaving()) {
            return;
        }
        this.isSaving.set(true);
        Exercise.sanitize(this.milestoneExercise());

        const saveObservable = this.milestoneExercise().id
            ? this.milestoneExerciseService.update(this.milestoneExercise())
            : this.milestoneExerciseService.create(this.milestoneExercise());

        saveObservable.subscribe({
            next: () => {
                this.isSaving.set(false);
                this.navigationUtilService.navigateBack(['/course-management', this.milestoneExercise().course!.id!, 'exercises']);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                this.alertService.addAlert({ type: AlertType.DANGER, message: error.headers.get('X-artemisApp-alert') ?? 'error.unexpectedError', disableTranslation: true });
            },
        });
    }

    previousState() {
        this.navigationUtilService.navigateBack(['/course-management', this.milestoneExercise().course!.id!, 'exercises']);
    }
}
