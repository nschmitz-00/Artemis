import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Message } from 'primeng/message';

import { MilestoneExercise, MilestoneTestCaseCoverage } from 'app/programming/shared/entities/milestone-exercise.model';
import { MilestoneExerciseService } from 'app/programming/manage/services/milestone-exercise.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

const EMPTY_COVERAGE: MilestoneTestCaseCoverage = { orphanTestCases: [], duplicateTestCases: [] };

/**
 * Warns the editor about test cases of a Milestone that its user stories do not claim exactly once.
 * <p>
 * Grading a user story only considers the test cases its problem statement references (see
 * ProgrammingExerciseGradingService#findActiveTestCasesScopedToExercise, server), so a test case no user story references is
 * unreachable for students, and one several user stories reference pays out several times. This is a warning only - a Milestone
 * being authored one user story at a time legitimately passes through both states - so it renders nothing when there is nothing
 * to report, and stays silent (rather than alerting) if the request fails.
 */
@Component({
    selector: 'jhi-milestone-test-case-coverage',
    templateUrl: './milestone-test-case-coverage.component.html',
    imports: [TranslateDirective, Message],
})
export class MilestoneTestCaseCoverageComponent {
    private readonly milestoneExerciseService = inject(MilestoneExerciseService);

    milestoneExercise = input.required<MilestoneExercise>();

    // A params function returning undefined keeps the loader from running at all, which is what should happen for a Milestone
    // that has not been saved yet: it owns no test repository, so there is nothing to check.
    private readonly coverageResource = rxResource({
        params: () => this.milestoneExercise().id,
        stream: ({ params: milestoneExerciseId }) => this.milestoneExerciseService.getTestCaseCoverage(milestoneExerciseId),
        defaultValue: EMPTY_COVERAGE,
    });

    // Reading `value()` on a resource in the error state rethrows, so a failed request has to be mapped back to "nothing to
    // report" here: this panel is an optional hint on a form the editor is in the middle of filling in, and must never break it.
    private readonly coverage = computed(() => (this.coverageResource.hasValue() ? this.coverageResource.value() : EMPTY_COVERAGE));

    protected readonly orphanTestCases = computed(() => this.coverage().orphanTestCases ?? []);
    protected readonly duplicateTestCases = computed(() => this.coverage().duplicateTestCases ?? []);
    protected readonly hasIssues = computed(() => this.orphanTestCases().length > 0 || this.duplicateTestCases().length > 0);
}
