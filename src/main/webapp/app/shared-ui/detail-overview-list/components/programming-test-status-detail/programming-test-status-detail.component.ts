import { Component, input } from '@angular/core';
import type { ProgrammingTestStatusDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { RouterModule } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';

@Component({
    selector: 'jhi-programming-test-status-detail',
    templateUrl: 'programming-test-status-detail.component.html',
    imports: [RouterModule, TranslateDirective, UpdatingResultComponent, ProgrammingExerciseInstructorTriggerBuildButtonComponent, ProgrammingExerciseInstructorStatusComponent],
})
export class ProgrammingTestStatusDetailComponent {
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;

    detail = input.required<ProgrammingTestStatusDetail>();

    /**
     * Whether the participation has been built at least once, i.e. any of its submissions carries a result.
     * <p>
     * Deliberately not `submissions.first()`: `ProgrammingExerciseService.setLatestResultForTemplateAndSolution`
     * attaches the latest result to the LAST submission, so a repository built more than once would look resultless.
     *
     * @param participation the template or solution participation this row renders
     * @return true if at least one result exists
     */
    protected hasResult(participation: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation): boolean {
        return getAllResultsOfAllSubmissions(participation.submissions).length > 0;
    }
}
