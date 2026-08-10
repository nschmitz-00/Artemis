import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { provideHttpClient } from '@angular/common/http';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseLanguageComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseLanguageComponent>;
    let comp: ProgrammingExerciseLanguageComponent;

    let theiaServiceMock!: { getTheiaImages: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        theiaServiceMock = {
            getTheiaImages: vi.fn(),
        };
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: TheiaService,
                    useValue: theiaServiceMock,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseLanguageComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            programmingLanguage: true,
            projectType: true,
            withExemplaryDependency: true,
            packageName: true,
            enableStaticCodeAnalysis: true,
            sequentialTestRuns: true,
            customizeBuildScript: true,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should not load TheiaComponent when online IDE is not allowed', () => {
        comp.programmingExercise().allowOnlineIde = false;
        fixture.detectChanges();
        expect(comp.programmingExerciseTheiaComponent()).toBeUndefined();
    });

    it('should load TheiaComponent when online IDE is allowed', () => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        comp.programmingExercise().allowOnlineIde = true;
        fixture.detectChanges();
        expect(comp.programmingExerciseTheiaComponent()).toBeDefined();
    });

    // These build option checkboxes only render for a language whose feature set allows them, and the exemplary dependency one
    // additionally only for a not-yet-created Java exercise. They are easy to lose to a config that no longer reports the flags.
    describe('build option checkboxes', () => {
        const renderWith = (config: Partial<ProgrammingExerciseCreationConfig>, exercise: Partial<ProgrammingExercise>) => {
            fixture.componentRef.setInput('programmingExerciseCreationConfig', { ...programmingExerciseCreationConfigMock, ...config });
            fixture.componentRef.setInput('programmingExercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
            fixture.detectChanges();
            return fixture.nativeElement;
        };

        const javaConfig = {
            projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE],
            modePickerOptions: [{ value: ProjectType.PLAIN_MAVEN, labelKey: 'maven', btnClass: 'btn-secondary' }],
            staticCodeAnalysisAllowed: true,
            sequentialTestRunsAllowed: true,
        };

        it('should offer static code analysis and sequential test runs when the language allows them', () => {
            const compiled = renderWith(javaConfig, { programmingLanguage: ProgrammingLanguage.JAVA });

            expect(compiled.querySelector('#field_staticCodeAnalysisEnabled')).not.toBeNull();
            expect(compiled.querySelector('#field_sequentialTestRuns')).not.toBeNull();
        });

        it('should offer the exemplary dependency for a new Java exercise', () => {
            const compiled = renderWith(javaConfig, { programmingLanguage: ProgrammingLanguage.JAVA });

            expect(compiled.querySelector('#field_with_dependencies')).not.toBeNull();
        });

        it('should not offer the exemplary dependency once the exercise exists', () => {
            const compiled = renderWith(javaConfig, { id: 42, programmingLanguage: ProgrammingLanguage.JAVA });

            expect(compiled.querySelector('#field_with_dependencies')).toBeNull();
        });

        it('should hide the options the language does not support', () => {
            const compiled = renderWith({ ...javaConfig, staticCodeAnalysisAllowed: false, sequentialTestRunsAllowed: false }, { programmingLanguage: ProgrammingLanguage.PYTHON });

            expect(compiled.querySelector('#field_staticCodeAnalysisEnabled')).toBeNull();
            expect(compiled.querySelector('#field_sequentialTestRuns')).toBeNull();
        });
    });
});
