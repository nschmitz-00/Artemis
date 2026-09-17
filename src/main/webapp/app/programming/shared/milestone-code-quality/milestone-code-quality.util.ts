import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StaticCodeAnalysisIssue } from 'app/programming/shared/entities/static-code-analysis-issue.model';

/**
 * Parses the `StaticCodeAnalysisIssue` a static code analysis feedback carries in its detail text.
 * <p>
 * The server falls back to the plain message when the issue does not fit the column limit, so parsing is not guaranteed
 * to succeed - a single unreadable issue must not take a whole view down, and yields undefined instead.
 */
export function parseScaIssue(feedback: Feedback): StaticCodeAnalysisIssue | undefined {
    if (!feedback.detailText) {
        return undefined;
    }
    try {
        return StaticCodeAnalysisIssue.fromFeedback(feedback);
    } catch {
        return undefined;
    }
}

/**
 * The points one static code analysis feedback costs, as a non-negative number.
 * <p>
 * The same fallback the result dialog applies: the issue's own penalty when the server wrote one, otherwise the
 * feedback's credits, which are negative for a deduction.
 */
export function scaFeedbackPenalty(feedback: Feedback, issue: StaticCodeAnalysisIssue | undefined = parseScaIssue(feedback)): number {
    return Math.max(0, issue?.penalty ?? -(feedback.credits ?? 0));
}

/** The points a milestone result loses to static code analysis in total, as a non-negative number; 0 without a result. */
export function milestoneScaPenalty(result: Result | undefined): number {
    return (result?.feedbacks ?? []).filter((feedback) => Feedback.isStaticCodeAnalysisFeedback(feedback)).reduce((sum, feedback) => sum + scaFeedbackPenalty(feedback), 0);
}
