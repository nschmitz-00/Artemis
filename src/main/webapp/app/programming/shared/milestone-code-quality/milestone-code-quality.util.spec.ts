import { describe, expect, it } from 'vitest';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StaticCodeAnalysisIssue } from 'app/programming/shared/entities/static-code-analysis-issue.model';
import { milestoneScaPenalty, parseScaIssue, scaFeedbackPenalty } from 'app/programming/shared/milestone-code-quality/milestone-code-quality.util';

describe('milestone code quality util', () => {
    function scaFeedback(issue: Partial<StaticCodeAnalysisIssue> | string, credits = 0): Feedback {
        return {
            type: FeedbackType.AUTOMATIC,
            text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + 'Bad Practice',
            detailText: typeof issue === 'string' ? issue : JSON.stringify(issue),
            credits,
        } as Feedback;
    }

    it('prefers the penalty the issue carries', () => {
        expect(scaFeedbackPenalty(scaFeedback({ penalty: 2 }, -5))).toBe(2);
    });

    it('falls back to the negated credits when the detail text is not a parsable issue', () => {
        const feedback = scaFeedback('plain message that did not fit the column', -1.5);
        expect(parseScaIssue(feedback)).toBeUndefined();
        expect(scaFeedbackPenalty(feedback)).toBe(1.5);
    });

    it('never reports a negative penalty', () => {
        expect(scaFeedbackPenalty(scaFeedback('message', 3))).toBe(0);
    });

    it('sums only static code analysis feedback of a result', () => {
        const testFeedback = { type: FeedbackType.AUTOMATIC, text: 'testA', credits: -4, testCase: { id: 1 } } as Feedback;
        const result = { feedbacks: [scaFeedback({ penalty: 1 }), scaFeedback({ penalty: 2.5 }), testFeedback] } as Result;
        expect(milestoneScaPenalty(result)).toBe(3.5);
        expect(milestoneScaPenalty(undefined)).toBe(0);
    });
});
