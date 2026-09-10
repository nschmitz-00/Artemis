import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Result } from 'app/exercise/shared/entities/result/result.model';

/**
 * One user story of a milestone group as it stands for a single student (mirrors the backend
 * {@code MilestoneAssessmentStoryDTO}).
 * <p>
 * Every field but the exercise itself is optional, because a story the student never started still produces an entry:
 * "not started" is what a tutor needs to see, not a row to hide.
 */
export interface MilestoneAssessmentStory {
    exerciseId: number;
    title: string;
    maxPoints?: number;
    participationId?: number;
    /** The submission the assessment editor is opened on; unset before the student's first build. */
    submissionId?: number;
    latestScore?: number;
    assessorLogin?: string;
    /**
     * Whether a manual assessment has been submitted. Optional rather than required because the server serializes with
     * `NON_EMPTY`, which drops a `false` on the wire - so absent means "not assessed".
     */
    assessed?: boolean;
}

/** One student's standing across a whole milestone group (mirrors the backend {@code MilestoneAssessmentStudentDTO}). */
export interface MilestoneAssessmentStudent {
    studentLogin: string;
    studentName?: string;
    milestoneParticipationId: number;
    stories: MilestoneAssessmentStory[];
}

/**
 * Everything the milestone assessment page needs for one student (mirrors the backend {@code MilestoneAssessmentDTO}).
 * <p>
 * {@link milestoneResult} is the milestone's own result, which is the only place the group's static code analysis
 * feedback exists: the fan-out copies only test case feedback down to the stories, and every story has static code
 * analysis switched off.
 */
export interface MilestoneAssessment {
    milestoneExerciseId: number;
    milestoneTitle: string;
    problemStatement?: string;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    milestoneMaxPoints?: number;
    milestoneResult?: Result;
    stories: MilestoneAssessmentStory[];
}

/** Reads the two tutor-facing views of a milestone exercise group. */
@Injectable({ providedIn: 'root' })
export class MilestoneAssessmentService {
    private readonly http = inject(HttpClient);

    private resourceUrl(courseId: number, groupId: number): string {
        return `api/exercise/courses/${courseId}/milestone-exercise-groups/${groupId}/assessment/students`;
    }

    /** Every student who has started the group's milestone, with the standing of each of their user stories. */
    getAssessmentDashboard(courseId: number, groupId: number): Observable<MilestoneAssessmentStudent[]> {
        return this.http.get<MilestoneAssessmentStudent[]>(this.resourceUrl(courseId, groupId));
    }

    /** The group-level information and the ordered stories the assessment page renders as tabs for one student. */
    getAssessmentForStudent(courseId: number, groupId: number, studentLogin: string): Observable<MilestoneAssessment> {
        return this.http.get<MilestoneAssessment>(`${this.resourceUrl(courseId, groupId)}/${studentLogin}`);
    }
}
