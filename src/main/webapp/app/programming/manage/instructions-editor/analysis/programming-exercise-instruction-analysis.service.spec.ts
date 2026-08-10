import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { problemStatement, problemStatementRepeatedTestCases } from 'test/helpers/sample/problemStatement.json';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseInstructionAnalysisService', () => {
    const taskRegex = /\[task\](.*)/g;

    let analysisService: ProgrammingExerciseInstructionAnalysisService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ProgrammingExerciseInstructionAnalysisService, { provide: TranslateService, useClass: MockTranslateService }],
        });

        analysisService = TestBed.inject(ProgrammingExerciseInstructionAnalysisService);
    });

    it('should analyse problem statement without any issues correctly', () => {
        const testCases = ['testMergeSort', 'testBubbleSort'];
        const { invalidTestCases, missingTestCases, repeatedTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(problemStatement, taskRegex, testCases);

        expect(invalidTestCases).toHaveLength(0);
        expect(missingTestCases).toHaveLength(0);
        expect(repeatedTestCases).toHaveLength(0);
        expect(completeAnalysis).toEqual(new Map());
    });

    it('should analyse problem statement with issues correctly', () => {
        const testCases = ['testBubbleSortNew']; // test name was changed, the new test name is missing.
        const expectedAnalysis = new Map();
        expectedAnalysis.set(0, { lineNumber: 0, invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] });
        expectedAnalysis.set(2, {
            lineNumber: 2,
            invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
        });

        const { invalidTestCases, missingTestCases, repeatedTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(problemStatement, taskRegex, testCases);

        expect(invalidTestCases).toEqual(['testBubbleSort', 'testMergeSort']);
        expect(missingTestCases).toEqual(['testBubbleSortNew']);
        expect(repeatedTestCases).toHaveLength(0);
        expect(completeAnalysis).toEqual(expectedAnalysis);
    });

    it('should analyse problem statement with repeated test cases', () => {
        const testCases = ['testBubbleSort'];
        const expectedAnalysis = new Map();
        expectedAnalysis.set(0, { lineNumber: 0, repeatedTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.repeatedTestCase'] });
        expectedAnalysis.set(2, { lineNumber: 2, repeatedTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.repeatedTestCase'] });

        const { invalidTestCases, missingTestCases, repeatedTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(
            problemStatementRepeatedTestCases,
            taskRegex,
            testCases,
        );

        expect(invalidTestCases).toHaveLength(0);
        expect(missingTestCases).toHaveLength(0);
        expect(repeatedTestCases).toEqual(['testBubbleSort']);
        expect(completeAnalysis).toEqual(expectedAnalysis);
    });

    // A milestone owns the test repository, but its tests are meant to be referenced from its user stories' problem statements,
    // not from the milestone's own - so without taking those into account every test case is reported as unused.
    describe('test cases covered by a related problem statement', () => {
        const emptyMilestoneStatement = 'Build the sorting application.';

        it('should not report a test case as missing when a related problem statement references it', () => {
            const { missingTestCases } = analysisService.analyzeProblemStatement(emptyMilestoneStatement, taskRegex, ['testBubbleSort', 'testMergeSort'], [problemStatement]);

            expect(missingTestCases).toHaveLength(0);
        });

        it('should still report a test case that no related problem statement references', () => {
            const { missingTestCases } = analysisService.analyzeProblemStatement(
                emptyMilestoneStatement,
                taskRegex,
                ['testBubbleSort', 'testMergeSort', 'testUnclaimed'],
                [problemStatement],
            );

            expect(missingTestCases).toEqual(['testUnclaimed']);
        });

        it('should report every test case as missing when no related problem statement is given', () => {
            const { missingTestCases } = analysisService.analyzeProblemStatement(emptyMilestoneStatement, taskRegex, ['testBubbleSort', 'testMergeSort']);

            expect(missingTestCases).toEqual(['testBubbleSort', 'testMergeSort']);
        });

        /** The milestone's own statement stays the authority for what is a valid reference; coverage only silences "missing". */
        it('should not treat a test case referenced only elsewhere as making the own statement invalid', () => {
            const { invalidTestCases, missingTestCases } = analysisService.analyzeProblemStatement(
                '[task][Sort](testBubbleSort)',
                taskRegex,
                ['testBubbleSort', 'testMergeSort'],
                [problemStatement],
            );

            expect(invalidTestCases).toHaveLength(0);
            expect(missingTestCases).toHaveLength(0);
        });
    });
});
