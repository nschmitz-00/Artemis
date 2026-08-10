import { ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import {
    APP_NAME_PATTERN_FOR_SWIFT,
    PACKAGE_NAME_PATTERN_FOR_DART,
    PACKAGE_NAME_PATTERN_FOR_GO,
    PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX,
    PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN,
} from 'app/foundation/constants/input.constants';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';

/**
 * The parts of the surrounding form that the state machine cannot know about itself.
 */
export interface ProgrammingExerciseLanguageStateHooks {
    /** The exercise being edited. A getter rather than a value, so the state always reads the form's current exercise. */
    exercise: () => ProgrammingExercise;
    /**
     * Whether changing the language or project type should pull in the matching problem statement template. False while
     * importing (the imported statement must survive) and for exercises that already exist.
     */
    shouldLoadTemplate?: () => boolean;
    /** Loads the problem statement template for the given language and re-renders the instructions. */
    loadTemplate?: (language: ProgrammingLanguage) => void;
}

/**
 * The language and build-configuration state machine of a programming exercise form.
 * <p>
 * Selecting a language or project type is not a plain assignment: it looks the language's feature set up, derives which build
 * options are even offered (static code analysis, sequential test runs, checking out the solution repository, auxiliary
 * repositories), rebuilds the project type mode picker, and resets the options that the new selection no longer supports.
 * Java additionally folds the "with dependencies" checkbox into the stored project type (PLAIN_MAVEN vs MAVEN_MAVEN).
 * <p>
 * This lives outside the form components so the milestone form and the plain programming exercise form share one
 * implementation - {@link ProgrammingExerciseLanguageComponent} renders the same section for both, and a rule that changes
 * here changes for both at once.
 */
export class ProgrammingExerciseLanguageState {
    public packageNamePattern = '';
    public packageNameRequired = true;
    public staticCodeAnalysisAllowed = false;
    public checkoutSolutionRepositoryAllowed = false;
    public sequentialTestRunsAllowed = false;
    public auxiliaryRepositoriesSupported = false;
    public projectTypes?: ProjectType[] = [];
    public modePickerOptions?: ModePickerOption<ProjectType>[] = [];
    public buildPlanLoaded = false;

    private selectedProgrammingLanguageValue!: ProgrammingLanguage;
    private selectedProjectTypeValue?: ProjectType;
    private withDependenciesValue = false;

    constructor(
        private readonly programmingLanguageFeatureService: ProgrammingLanguageFeatureService,
        private readonly hooks: ProgrammingExerciseLanguageStateHooks,
    ) {}

    private get exercise(): ProgrammingExercise {
        return this.hooks.exercise();
    }

    /**
     * Adopts the language and project type of an already-loaded exercise, without running any of the reset logic and without
     * touching the exercise - the stored selection is authoritative when a form opens on an existing exercise, and going
     * through the setters would reset the very build options that were loaded.
     * <p>
     * Deliberately leaves {@link withDependencies} alone: it is only ever offered while creating an exercise, and inferring it
     * from a MAVEN_MAVEN / GRADLE_GRADLE project type here would change what a later project type change writes back.
     *
     * @param exercise the exercise the form was opened on
     */
    adoptFrom(exercise: ProgrammingExercise): void {
        this.selectedProgrammingLanguageValue = exercise.programmingLanguage!;
        if (exercise.projectType === ProjectType.MAVEN_MAVEN) {
            this.selectedProjectTypeValue = ProjectType.PLAIN_MAVEN;
        } else if (exercise.projectType === ProjectType.GRADLE_GRADLE) {
            this.selectedProjectTypeValue = ProjectType.PLAIN_GRADLE;
        } else {
            this.selectedProjectTypeValue = exercise.projectType;
        }
    }

    /**
     * Re-reads the feature set of the given language into the flags the form renders from, without touching the exercise.
     * Call this after {@link adoptFrom} to render an existing exercise's build options correctly without resetting them.
     */
    refreshLanguageFeatures(language: ProgrammingLanguage): void {
        const programmingLanguageFeature = this.programmingLanguageFeatureService.getProgrammingLanguageFeature(language);
        if (!programmingLanguageFeature) {
            return;
        }
        this.packageNameRequired = programmingLanguageFeature.packageNameRequired;
        this.staticCodeAnalysisAllowed = programmingLanguageFeature.staticCodeAnalysis;
        this.checkoutSolutionRepositoryAllowed = programmingLanguageFeature.checkoutSolutionRepositoryAllowed;
        this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
        this.auxiliaryRepositoriesSupported = programmingLanguageFeature.auxiliaryRepositoriesSupported;
        // filter out MAVEN_MAVEN and GRADLE_GRADLE because they are not directly selectable but only via a checkbox
        this.projectTypes = programmingLanguageFeature.projectTypes?.filter((projectType) => projectType !== ProjectType.MAVEN_MAVEN && projectType !== ProjectType.GRADLE_GRADLE);
        this.modePickerOptions = this.projectTypes?.map((projectType) => ({
            value: projectType,
            labelKey: 'artemisApp.programmingExercise.projectTypes.' + projectType.toString(),
            btnClass: 'btn-secondary',
        }));
    }

    /**
     * Will also trigger loading the corresponding programming exercise language template.
     *
     * @param language to change to.
     */
    set selectedProgrammingLanguage(language: ProgrammingLanguage) {
        const languageChanged = this.selectedProgrammingLanguageValue !== language;
        this.selectedProgrammingLanguageValue = language;
        // Write the selection through to the exercise here rather than leaving it to the template loader, which only runs for a
        // newly created exercise on a form that loads templates at all - a form that does not (the milestone form) would
        // otherwise save an exercise without a programming language.
        this.exercise.programmingLanguage = language;
        this.refreshLanguageFeatures(language);

        if (languageChanged) {
            this.resetBuildOptionSelections();
            // Reset project type when changing programming language as not all programming languages support (the same) project types
            this.exercise.projectType = this.projectTypes?.[0];
            this.selectedProjectTypeValue = this.projectTypes?.[0];
            this.withDependenciesValue = false;
            this.buildPlanLoaded = false;
            if (this.exercise.buildConfig) {
                this.exercise.buildConfig.buildPlanConfiguration = undefined;
            } else {
                this.exercise.buildConfig = new ProgrammingExerciseBuildConfig();
            }
            this.exercise.customizeBuildPlan = language === ProgrammingLanguage.EMPTY;
        }

        // If we switch to another language which does not support static code analysis we need to reset options related to static code analysis
        if (!this.staticCodeAnalysisAllowed) {
            this.disableStaticCodeAnalysis();
        }

        if (!this.sequentialTestRunsAllowed) {
            this.exercise.buildConfig!.sequentialTestRuns = false;
        }

        if (language === ProgrammingLanguage.HASKELL || language === ProgrammingLanguage.OCAML) {
            // Instructors typically test against the example solution for Haskell and OCAML exercises.
            // If supported by the current CI configuration, this line activates the option per default.
            this.exercise.buildConfig!.checkoutSolutionRepository = this.checkoutSolutionRepositoryAllowed;
        }
        if (!this.checkoutSolutionRepositoryAllowed) {
            this.exercise.buildConfig!.checkoutSolutionRepository = false;
        }

        this.loadTemplateIfWanted(language);
    }

    get selectedProgrammingLanguage(): ProgrammingLanguage {
        return this.selectedProgrammingLanguageValue;
    }

    /**
     * Will also trigger loading the corresponding project type template.
     *
     * @param type to change to.
     */
    set selectedProjectType(type: ProjectType) {
        this.updateProjectTypeSettings(type);
        this.loadTemplateIfWanted(this.exercise.programmingLanguage!);
    }

    get selectedProjectType(): ProjectType | undefined {
        return this.selectedProjectTypeValue;
    }

    /**
     * Only applies to Java programming exercises: whether the template and solution projects include a dependency, which is
     * expressed by the stored project type (PLAIN_MAVEN vs MAVEN_MAVEN, PLAIN_GRADLE vs GRADLE_GRADLE).
     */
    set withDependencies(withDependencies: boolean) {
        this.withDependenciesValue = withDependencies;
        this.selectedProjectType = this.exercise.projectType!;
    }

    get withDependencies(): boolean {
        return this.withDependenciesValue;
    }

    /**
     * Sets the flag without re-deriving the project type from it. Use this when the caller updates the project type itself;
     * the {@link withDependencies} setter is the one that re-derives.
     */
    setWithDependenciesFlag(withDependencies: boolean): void {
        this.withDependenciesValue = withDependencies;
    }

    private updateProjectTypeSettings(type: ProjectType): void {
        if (ProjectType.XCODE === type) {
            // Disable Online Editor
            this.exercise.allowOnlineEditor = false;
        } else if (ProjectType.FACT === type) {
            // Disallow SCA for C (FACT)
            this.disableStaticCodeAnalysis();
        }

        // update the project types for java programming exercises according to whether dependencies should be included
        if (this.exercise.programmingLanguage === ProgrammingLanguage.JAVA) {
            const programmingLanguageFeature = this.programmingLanguageFeatureService.getProgrammingLanguageFeature(ProgrammingLanguage.JAVA)!;
            if (type === ProjectType.MAVEN_BLACKBOX) {
                this.selectedProjectTypeValue = ProjectType.MAVEN_BLACKBOX;
                this.exercise.projectType = ProjectType.MAVEN_BLACKBOX;
                this.sequentialTestRunsAllowed = false;
            } else if (type === ProjectType.PLAIN_MAVEN || type === ProjectType.MAVEN_MAVEN) {
                this.selectedProjectTypeValue = ProjectType.PLAIN_MAVEN;
                this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
                this.exercise.projectType = this.withDependenciesValue ? ProjectType.MAVEN_MAVEN : ProjectType.PLAIN_MAVEN;
            } else {
                this.selectedProjectTypeValue = ProjectType.PLAIN_GRADLE;
                this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
                this.exercise.projectType = this.withDependenciesValue ? ProjectType.GRADLE_GRADLE : ProjectType.PLAIN_GRADLE;
            }
        } else {
            this.selectedProjectTypeValue = type;
            this.exercise.projectType = type;
        }

        this.resetBuildOptionSelections();
    }

    /**
     * Drops the maximum penalty when static code analysis is switched off; there is nothing to cap otherwise.
     * Forms with import-specific behaviour wrap this rather than replace it.
     */
    onStaticCodeAnalysisChanged(): void {
        if (!this.exercise.staticCodeAnalysisEnabled) {
            this.exercise.maxStaticCodeAnalysisPenalty = undefined;
        }
    }

    /**
     * Selects the validation pattern the package name field is checked against; it differs per language, and Java/Kotlin
     * additionally have a laxer variant for blackbox projects.
     *
     * @param language          the selected programming language
     * @param useBlackboxPattern whether the selected project type is MAVEN_BLACKBOX
     */
    setPackageNamePattern(language: ProgrammingLanguage, useBlackboxPattern = false): void {
        switch (language) {
            case ProgrammingLanguage.SWIFT:
                this.packageNamePattern = APP_NAME_PATTERN_FOR_SWIFT;
                break;
            case ProgrammingLanguage.JAVA:
            case ProgrammingLanguage.KOTLIN:
                this.packageNamePattern = useBlackboxPattern ? PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX : PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN;
                break;
            case ProgrammingLanguage.GO:
                this.packageNamePattern = PACKAGE_NAME_PATTERN_FOR_GO;
                break;
            case ProgrammingLanguage.DART:
                this.packageNamePattern = PACKAGE_NAME_PATTERN_FOR_DART;
                break;
        }
    }

    /**
     * The languages the running instance actually supports, in the order the enum declares them.
     */
    supportedLanguages(): string[] {
        return Object.values(ProgrammingLanguage).filter((language) => this.programmingLanguageFeatureService.supportsProgrammingLanguage(language));
    }

    disableStaticCodeAnalysis(): void {
        this.exercise.staticCodeAnalysisEnabled = false;
        this.exercise.maxStaticCodeAnalysisPenalty = undefined;
    }

    private resetBuildOptionSelections(): void {
        this.disableStaticCodeAnalysis();
        if (this.exercise.buildConfig) {
            this.exercise.buildConfig.sequentialTestRuns = false;
        }
    }

    /**
     * Only load the problem statement template when creating a new exercise, never when editing or importing one - it would
     * overwrite the statement the instructor already has.
     */
    private loadTemplateIfWanted(language: ProgrammingLanguage): void {
        if (this.exercise.id === undefined && (this.hooks.shouldLoadTemplate?.() ?? true)) {
            this.hooks.loadTemplate?.(language);
        }
    }
}
