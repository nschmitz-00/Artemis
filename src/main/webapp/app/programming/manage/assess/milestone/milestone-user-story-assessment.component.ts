import { Component, inject, input } from '@angular/core';

import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MilestoneAssessmentStateService } from 'app/programming/manage/assess/milestone/milestone-assessment-state.service';

/**
 * Lists the user stories of the milestone submission that is being assessed, each with what it currently scores and the
 * feedback the tutor wrote for it without pointing at a line of code.
 * <p>
 * A student pushes once and every user story is graded from that same submission, so the tutor reads the code once and
 * distributes the feedback over these sections instead of opening one assessment page per user story. Saving and submitting
 * belong to the surrounding assessment page - this panel only edits the state both share
 * (see {@link MilestoneAssessmentStateService}).
 */
@Component({
    selector: 'jhi-milestone-user-story-assessment',
    templateUrl: './milestone-user-story-assessment.component.html',
    imports: [TranslateDirective, UnreferencedFeedbackComponent, ArtemisTranslatePipe],
})
export class MilestoneUserStoryAssessmentComponent {
    protected readonly milestoneAssessmentState = inject(MilestoneAssessmentStateService);

    readonly readOnly = input<boolean>(false);

    protected onUnreferencedFeedbacksChange(userStoryExerciseId: number, feedbacks: Feedback[]): void {
        this.milestoneAssessmentState.setUnreferencedFeedback(userStoryExerciseId, feedbacks);
    }
}
