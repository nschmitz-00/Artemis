package de.tum.cit.aet.artemis.programming.domain;

/**
 * How far a student has come with one user story of a Milestone, as shown in the student's progress overview.
 * <p>
 * The status is derived from the result that actually counts for the user story's score (the same one the course score is
 * calculated from), never from the raw latest build: a build after the due date, or a manual assessment that is not published
 * yet, must not change what the student is told they achieved.
 */
public enum UserStoryProgressStatus {

    /**
     * The student has no counting result for this user story yet - they have not started the Milestone, or no build has been
     * graded against this user story so far.
     */
    NOT_STARTED,

    /**
     * The student has a counting result that pays out less than the full points of the user story.
     */
    IN_PROGRESS,

    /**
     * The student's counting result reaches 100 %, so the user story pays out its full points.
     */
    COMPLETED
}
