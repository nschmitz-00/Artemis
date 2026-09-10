package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneAssessmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneAssessmentStoryDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneAssessmentStudentDTO;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Assembles the tutor-facing views of a {@code MilestoneExerciseGroup}: the dashboard a tutor picks a student from, and
 * everything the milestone assessment page needs for the student they picked.
 * <p>
 * Grading a milestone is organised by student rather than by submission, unlike every other assessment flow in Artemis.
 * A group's user stories share one repository and one build, so assessing them one story at a time - which is what the
 * per-exercise dashboards offer - means reading the same codebase once per story, for a different student each time.
 * Both views here are therefore keyed on the student, and both order the stories the same way so a tab index means the
 * same thing on either page.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneAssessmentService {

    private final MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService;

    public MilestoneAssessmentService(MilestoneExerciseGroupRepository milestoneExerciseGroupRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ResultRepository resultRepository, ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService) {
        this.milestoneExerciseGroupRepository = milestoneExerciseGroupRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resultRepository = resultRepository;
        this.programmingFeedbackSynthesizerService = programmingFeedbackSynthesizerService;
    }

    /**
     * Every student who has started the group's milestone, with the standing of each of their user stories.
     * <p>
     * A student appears as soon as they have a milestone participation, because that is the moment the shared
     * repository exists - a story they never started still gets a row, with nothing to assess, which is itself what the
     * tutor needs to know.
     *
     * @param groupId  the id of the milestone exercise group
     * @param courseId the id of the course the group belongs to
     * @return one entry per student, ordered by login so the dashboard is stable across reloads
     */
    public List<MilestoneAssessmentStudentDTO> getAssessmentDashboard(long groupId, long courseId) {
        MilestoneExerciseGroup group = milestoneExerciseGroupRepository.findByIdAndCourseIdWithDetailsElseThrow(groupId, courseId);
        long milestoneExerciseId = milestoneExerciseId(group);
        List<UserStoryExercise> stories = orderedStories(group);

        // One query per story rather than one per (story, student): a group has a handful of stories and a course has
        // many students, so the per-student shape would be a query per cell of the table.
        Map<Long, Map<String, ProgrammingExerciseStudentParticipation>> participationsByStory = stories.stream().collect(Collectors.toMap(Exercise::getId,
                story -> byStudentLogin(programmingExerciseStudentParticipationRepository.findWithSubmissionsResultsAndAssessorByExerciseId(story.getId()))));

        return programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndRepositoryUriIsNotNullAndTestRunFalse(milestoneExerciseId).stream()
                .filter(participation -> participation.getStudent().isPresent()).sorted(Comparator.comparing(participation -> participation.getStudent().orElseThrow().getLogin()))
                .map(milestoneParticipation -> {
                    var student = milestoneParticipation.getStudent().orElseThrow();
                    List<MilestoneAssessmentStoryDTO> storyStates = stories.stream()
                            .map(story -> toStoryDTO(story, participationsByStory.get(story.getId()).get(student.getLogin()))).toList();
                    return new MilestoneAssessmentStudentDTO(student.getLogin(), student.getName(), milestoneParticipation.getId(), storyStates);
                }).toList();
    }

    /**
     * Everything the milestone assessment page renders for one student: the group-level information of its first tab
     * and the stories its remaining tabs grade.
     *
     * @param groupId      the id of the milestone exercise group
     * @param courseId     the id of the course the group belongs to
     * @param studentLogin the login of the student whose milestone is being assessed
     * @return the milestone's own information together with the ordered stories
     */
    public MilestoneAssessmentDTO getAssessmentForStudent(long groupId, long courseId, String studentLogin) {
        MilestoneExerciseGroup group = milestoneExerciseGroupRepository.findByIdAndCourseIdWithDetailsElseThrow(groupId, courseId);
        long milestoneExerciseId = milestoneExerciseId(group);
        MilestoneExercise milestoneExercise = (MilestoneExercise) programmingExerciseRepository.findByIdElseThrow(milestoneExerciseId);

        List<MilestoneAssessmentStoryDTO> stories = orderedStories(group).stream().map(story -> toStoryDTO(story, programmingExerciseStudentParticipationRepository
                .findWithSubmissionsResultsAndAssessorByExerciseIdAndStudentLogin(story.getId(), studentLogin).stream().findFirst().orElse(null))).toList();

        return new MilestoneAssessmentDTO(milestoneExerciseId, milestoneExercise.getTitle(), milestoneExercise.getProblemStatement(),
                Boolean.TRUE.equals(milestoneExercise.isStaticCodeAnalysisEnabled()), milestoneExercise.getMaxStaticCodeAnalysisPenalty(), milestoneExercise.getMaxPoints(),
                milestoneResult(milestoneExerciseId, studentLogin, milestoneExercise), stories);
    }

    /**
     * The student's latest milestone result, with the static code analysis feedback the group's issues live in.
     * <p>
     * Those rows sit in a typed table and only become client-shaped feedback through the synthesizer, so the explicit
     * overload is used: this runs off a plain REST request whose result graph does not reach the exercise, and letting
     * the synthesizer walk there itself would mean a lazy load that {@code open-in-view} being off does not allow.
     */
    @Nullable
    private ResultDTO milestoneResult(long milestoneExerciseId, String studentLogin, MilestoneExercise milestoneExercise) {
        Optional<ProgrammingExerciseStudentParticipation> milestoneParticipation = programmingExerciseStudentParticipationRepository
                .findByExerciseIdAndStudentLogin(milestoneExerciseId, studentLogin);
        if (milestoneParticipation.isEmpty()) {
            return null;
        }
        Optional<Result> result = resultRepository.findLatestResultWithFeedbacksForParticipation(milestoneParticipation.get().getId(), true);
        if (result.isEmpty()) {
            return null;
        }
        Result milestoneResult = result.get();
        programmingFeedbackSynthesizerService.attachSynthesizedFeedback(milestoneResult, milestoneExercise, false);
        // The submission came back with the result above, but its participation did not - and that is what the payload
        // is built from. Point it at the one already loaded rather than letting the DTO reach for an uninitialized proxy.
        if (milestoneResult.getSubmission() != null) {
            milestoneResult.getSubmission().setParticipation(milestoneParticipation.get());
        }
        return ResultDTO.of(milestoneResult);
    }

    /**
     * One story's standing for a single student. A story the student never started still produces an entry, with
     * everything but the exercise itself unset - "not started" is information a tutor needs, not a row to hide.
     */
    private MilestoneAssessmentStoryDTO toStoryDTO(UserStoryExercise story, @Nullable ProgrammingExerciseStudentParticipation participation) {
        if (participation == null) {
            return new MilestoneAssessmentStoryDTO(story.getId(), story.getTitle(), story.getMaxPoints(), null, null, null, null, false);
        }
        Submission latestSubmission = participation.findLatestSubmission().orElse(null);
        Result latestResult = latestSubmission == null ? null : latestSubmission.getLatestResult();
        String assessorLogin = latestResult == null || latestResult.getAssessor() == null ? null : latestResult.getAssessor().getLogin();
        boolean assessed = latestResult != null && latestResult.isManual() && latestResult.getCompletionDate() != null;
        return new MilestoneAssessmentStoryDTO(story.getId(), story.getTitle(), story.getMaxPoints(), participation.getId(),
                latestSubmission == null ? null : latestSubmission.getId(), latestResult == null ? null : latestResult.getScore(), assessorLogin, assessed);
    }

    /**
     * The group's user stories in a stable order. Ordered by id, so a tab index means the same thing on the dashboard
     * and on the assessment page, and stays put when a story is renamed.
     */
    private static List<UserStoryExercise> orderedStories(MilestoneExerciseGroup group) {
        return group.getExercises().stream().filter(UserStoryExercise.class::isInstance).map(UserStoryExercise.class::cast).sorted(Comparator.comparing(Exercise::getId)).toList();
    }

    private static long milestoneExerciseId(MilestoneExerciseGroup group) {
        Long milestoneExerciseId = group.getMilestoneExerciseId();
        if (milestoneExerciseId == null) {
            throw new IllegalStateException("The milestone group " + group.getId() + " has no anchor milestone exercise");
        }
        return milestoneExerciseId;
    }

    private static Map<String, ProgrammingExerciseStudentParticipation> byStudentLogin(Set<ProgrammingExerciseStudentParticipation> participations) {
        return participations.stream().filter(participation -> participation.getStudent().isPresent())
                .collect(Collectors.toMap(participation -> participation.getStudent().orElseThrow().getLogin(), Function.identity(), (first, second) -> first));
    }

}
