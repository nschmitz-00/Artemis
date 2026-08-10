import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProgrammingExerciseLanguageState } from 'app/programming/manage/update/programming-exercise-language-state';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX, PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN } from 'app/foundation/constants/input.constants';

describe('ProgrammingExerciseLanguageState', () => {
    const feature = (overrides: Partial<ProgrammingLanguageFeature> = {}): ProgrammingLanguageFeature => ({
        programmingLanguage: ProgrammingLanguage.JAVA,
        sequentialTestRuns: true,
        staticCodeAnalysis: true,
        plagiarismCheckSupported: true,
        packageNameRequired: true,
        checkoutSolutionRepositoryAllowed: true,
        projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.MAVEN_BLACKBOX],
        auxiliaryRepositoriesSupported: true,
        ...overrides,
    });

    const features = new Map<ProgrammingLanguage, ProgrammingLanguageFeature>([
        [ProgrammingLanguage.JAVA, feature()],
        [
            ProgrammingLanguage.PYTHON,
            feature({
                programmingLanguage: ProgrammingLanguage.PYTHON,
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                checkoutSolutionRepositoryAllowed: false,
                packageNameRequired: false,
                projectTypes: [],
            }),
        ],
        [ProgrammingLanguage.HASKELL, feature({ programmingLanguage: ProgrammingLanguage.HASKELL, projectTypes: [] })],
    ]);

    const featureService = {
        getProgrammingLanguageFeature: (language: ProgrammingLanguage) => features.get(language),
        supportsProgrammingLanguage: (language: ProgrammingLanguage) => features.has(language),
    } as ProgrammingLanguageFeatureService;

    let exercise: ProgrammingExercise;
    let state: ProgrammingExerciseLanguageState;

    beforeEach(() => {
        exercise = { buildConfig: new ProgrammingExerciseBuildConfig() } as ProgrammingExercise;
        state = new ProgrammingExerciseLanguageState(featureService, { exercise: () => exercise });
    });

    it('should only offer the languages the instance supports, in enum order', () => {
        expect(state.supportedLanguages()).toEqual([ProgrammingLanguage.HASKELL, ProgrammingLanguage.JAVA, ProgrammingLanguage.PYTHON]);
    });

    it('should write the selected language through to the exercise', () => {
        state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

        expect(exercise.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
    });

    describe('selecting a programming language', () => {
        it('should derive the build options and project types from the language feature', () => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(state.staticCodeAnalysisAllowed).toBe(true);
            expect(state.sequentialTestRunsAllowed).toBe(true);
            expect(state.packageNameRequired).toBe(true);
            expect(state.auxiliaryRepositoriesSupported).toBe(true);
            // MAVEN_MAVEN is reachable only through the "with dependencies" checkbox, never as its own mode picker option
            expect(state.projectTypes).toEqual([ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.MAVEN_BLACKBOX]);
            expect(state.modePickerOptions?.map((option) => option.value)).toEqual([ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.MAVEN_BLACKBOX]);
        });

        it('should reset the options the new language does not support', () => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;
            exercise.staticCodeAnalysisEnabled = true;
            exercise.maxStaticCodeAnalysisPenalty = 20;
            exercise.buildConfig!.sequentialTestRuns = true;
            exercise.buildConfig!.checkoutSolutionRepository = true;

            state.selectedProgrammingLanguage = ProgrammingLanguage.PYTHON;

            expect(exercise.staticCodeAnalysisEnabled).toBe(false);
            expect(exercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            expect(exercise.buildConfig!.sequentialTestRuns).toBe(false);
            expect(exercise.buildConfig!.checkoutSolutionRepository).toBe(false);
        });

        it('should select the first project type of the new language', () => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(exercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
            expect(state.selectedProjectType).toBe(ProjectType.PLAIN_MAVEN);
        });

        /** Instructors typically test against the example solution for these, so the option defaults to on. */
        it('should check out the solution repository by default for Haskell', () => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.HASKELL;

            expect(exercise.buildConfig!.checkoutSolutionRepository).toBe(true);
        });

        it('should force a customized build plan for the empty language', () => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(exercise.customizeBuildPlan).toBe(false);
        });

        it('should not reset anything when the language is re-selected unchanged', () => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;
            exercise.projectType = ProjectType.MAVEN_BLACKBOX;

            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(exercise.projectType).toBe(ProjectType.MAVEN_BLACKBOX);
        });
    });

    describe('selecting a project type', () => {
        beforeEach(() => {
            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;
        });

        it('should fold the with-dependencies flag into the stored Java project type', () => {
            state.withDependencies = true;

            expect(exercise.projectType).toBe(ProjectType.MAVEN_MAVEN);
            // The mode picker still shows the plain variant; the checkbox carries the difference
            expect(state.selectedProjectType).toBe(ProjectType.PLAIN_MAVEN);

            state.withDependencies = false;

            expect(exercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
        });

        it('should disallow sequential test runs for a blackbox project', () => {
            state.selectedProjectType = ProjectType.MAVEN_BLACKBOX;

            expect(state.sequentialTestRunsAllowed).toBe(false);
            expect(exercise.projectType).toBe(ProjectType.MAVEN_BLACKBOX);
        });

        it('should disable the online editor for an Xcode project', () => {
            exercise.allowOnlineEditor = true;

            state.selectedProjectType = ProjectType.XCODE;

            expect(exercise.allowOnlineEditor).toBe(false);
        });

        it('should disable static code analysis for a FACT project', () => {
            exercise.staticCodeAnalysisEnabled = true;
            exercise.maxStaticCodeAnalysisPenalty = 20;

            state.selectedProjectType = ProjectType.FACT;

            expect(exercise.staticCodeAnalysisEnabled).toBe(false);
            expect(exercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
        });
    });

    describe('package name pattern', () => {
        it('should use the blackbox pattern only for blackbox projects', () => {
            state.setPackageNamePattern(ProgrammingLanguage.JAVA);
            expect(state.packageNamePattern).toBe(PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN);

            state.setPackageNamePattern(ProgrammingLanguage.JAVA, true);
            expect(state.packageNamePattern).toBe(PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX);
        });
    });

    describe('adopting an existing exercise', () => {
        it('should take over the stored selection without resetting the saved build options', () => {
            exercise = {
                programmingLanguage: ProgrammingLanguage.JAVA,
                projectType: ProjectType.MAVEN_MAVEN,
                staticCodeAnalysisEnabled: true,
                maxStaticCodeAnalysisPenalty: 20,
                buildConfig: { sequentialTestRuns: true } as ProgrammingExerciseBuildConfig,
            } as ProgrammingExercise;

            state.adoptFrom(exercise);

            // MAVEN_MAVEN is shown as its plain variant, and nothing the exercise was saved with is touched
            expect(state.selectedProjectType).toBe(ProjectType.PLAIN_MAVEN);
            expect(state.selectedProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(exercise.projectType).toBe(ProjectType.MAVEN_MAVEN);
            expect(exercise.staticCodeAnalysisEnabled).toBe(true);
            expect(exercise.buildConfig!.sequentialTestRuns).toBe(true);
        });

        it('should render the build options of the adopted language once the features are refreshed', () => {
            state.adoptFrom({ programmingLanguage: ProgrammingLanguage.PYTHON } as ProgrammingExercise);
            state.refreshLanguageFeatures(ProgrammingLanguage.PYTHON);

            expect(state.staticCodeAnalysisAllowed).toBe(false);
            expect(state.sequentialTestRunsAllowed).toBe(false);
        });
    });

    it('should drop the maximum penalty when static code analysis is switched off', () => {
        exercise.staticCodeAnalysisEnabled = false;
        exercise.maxStaticCodeAnalysisPenalty = 20;

        state.onStaticCodeAnalysisChanged();

        expect(exercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
    });

    describe('problem statement template', () => {
        it('should load the template for a new exercise', () => {
            const loadTemplate = vi.fn();
            state = new ProgrammingExerciseLanguageState(featureService, { exercise: () => exercise, loadTemplate });

            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(loadTemplate).toHaveBeenCalledExactlyOnceWith(ProgrammingLanguage.JAVA);
        });

        it('should not overwrite the statement of an exercise that already exists', () => {
            const loadTemplate = vi.fn();
            exercise.id = 42;
            state = new ProgrammingExerciseLanguageState(featureService, { exercise: () => exercise, loadTemplate });

            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(loadTemplate).not.toHaveBeenCalled();
        });

        it('should not overwrite an imported statement', () => {
            const loadTemplate = vi.fn();
            state = new ProgrammingExerciseLanguageState(featureService, { exercise: () => exercise, shouldLoadTemplate: () => false, loadTemplate });

            state.selectedProgrammingLanguage = ProgrammingLanguage.JAVA;

            expect(loadTemplate).not.toHaveBeenCalled();
        });
    });
});
