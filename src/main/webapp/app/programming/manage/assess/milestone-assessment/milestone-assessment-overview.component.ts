import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { inject } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MilestoneCodeQualityComponent } from 'app/programming/shared/milestone-code-quality/milestone-code-quality.component';
import { MilestoneAssessment } from './milestone-assessment.service';

/**
 * The milestone assessment page's first tab: what a tutor needs to read the group's shared codebase before grading any
 * one story against it.
 * <p>
 * Both halves are group-level and exist nowhere else on the page. The problem statement is the brief the whole
 * repository was written against - the milestone itself is never rendered to anyone, so this is the only surface a
 * tutor can reach it from. The static code analysis issues are charged once for the entire group, so they belong here
 * rather than on any single story's tab.
 */
@Component({
    selector: 'jhi-milestone-assessment-overview',
    templateUrl: './milestone-assessment-overview.component.html',
    styleUrl: './milestone-assessment-overview.component.scss',
    imports: [TranslateDirective, MilestoneCodeQualityComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MilestoneAssessmentOverviewComponent {
    private readonly sanitizer = inject(DomSanitizer);

    readonly assessment = input.required<MilestoneAssessment>();

    /**
     * The milestone rebuilt as an exercise, which is the shape {@link MilestoneCodeQualityComponent} prices its issues
     * against. Only the three fields it reads are carried; sending the whole exercise would mean shipping the problem
     * statement and the course twice.
     */
    protected readonly milestoneExercise = computed<ProgrammingExercise>(() => {
        const assessment = this.assessment();
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = assessment.milestoneExerciseId;
        exercise.staticCodeAnalysisEnabled = assessment.staticCodeAnalysisEnabled === true;
        exercise.maxStaticCodeAnalysisPenalty = assessment.maxStaticCodeAnalysisPenalty;
        exercise.maxPoints = assessment.milestoneMaxPoints;
        return exercise;
    });

    /**
     * The milestone's problem statement as HTML.
     * <p>
     * Task syntax is stripped to its name rather than rendered, exactly as the student group page does it: a task link
     * addresses test cases of an exercise this page never opens, so it would resolve to nothing.
     */
    protected readonly problemStatementHtml = computed<SafeHtml | undefined>(() => {
        const problemStatement = this.assessment().problemStatement;
        if (!problemStatement) {
            return undefined;
        }
        return this.sanitizer.bypassSecurityTrustHtml(htmlForMarkdown(problemStatement.replace(taskRegex, (_match, name: string) => name)));
    });
}
