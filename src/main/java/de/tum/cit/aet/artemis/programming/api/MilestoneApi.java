package de.tum.cit.aet.artemis.programming.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.MilestoneExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;

/**
 * Module boundary for the MilestoneExercise/UserStoryExercise feature (specializations of {@link ProgrammingExercise}, see
 * {@link MilestoneExercise}/{@link UserStoryExercise}).
 * <p>
 * Other modules (exercise, localci, localvc, ...) inject this as {@code Optional<MilestoneApi>} and never reference
 * {@link MilestoneExercise}/{@link UserStoryExercise} directly, so that Milestone/UserStory-specific behavior stays out of
 * shared, cross-module services.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class MilestoneApi extends AbstractMilestoneApi {

    private final MilestoneExerciseRepository milestoneExerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    public MilestoneApi(MilestoneExerciseRepository milestoneExerciseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseGradingService programmingExerciseGradingService) {
        this.milestoneExerciseRepository = milestoneExerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
    }

    public boolean isMilestoneExercise(Exercise exercise) {
        return exercise instanceof MilestoneExercise;
    }

    public boolean isUserStoryExercise(Exercise exercise) {
        return exercise instanceof UserStoryExercise;
    }

    /**
     * Starting only ever happens at the Milestone level (the client only offers a "Start" action there); a UserStoryExercise
     * has no repository/build plan of its own to start, so callers should redirect to the parent Milestone instead, whose
     * cascade ({@link #cascadeStartToUserStoryExercises}) creates a sibling participation for every UserStoryExercise child,
     * including this one.
     *
     * @param exercise the exercise a caller is about to start
     * @return the parent MilestoneExercise if {@code exercise} is a UserStoryExercise, empty otherwise
     */
    public Optional<Exercise> parentMilestoneOf(Exercise exercise) {
        if (exercise instanceof UserStoryExercise userStoryExercise) {
            return Optional.of(userStoryExercise.getMilestoneExercise());
        }
        return Optional.empty();
    }

    /**
     * Once a MilestoneExercise's own participation (with a real git repository and build plan) has been started, this creates a
     * sibling {@link ProgrammingExerciseStudentParticipation} for every {@link UserStoryExercise} child, pointing at the exact
     * same repository/build plan/branch. This is what allows "start the Milestone" to also start every UserStoryExercise
     * belonging to it, per the requirement that students only ever start participation at the Milestone level.
     * <p>
     * No-op if {@code exercise} is not a MilestoneExercise.
     *
     * @param exercise               the exercise that was just started
     * @param milestoneParticipation the freshly created (or already existing) participation for the Milestone itself
     * @param participant            the participant (student) starting the exercise
     */
    public void cascadeStartToUserStoryExercises(Exercise exercise, ProgrammingExerciseStudentParticipation milestoneParticipation, Participant participant) {
        if (!(exercise instanceof MilestoneExercise milestoneExercise)) {
            return;
        }
        MilestoneExercise milestoneWithChildren = milestoneExerciseRepository.findWithUserStoryExercisesByIdElseThrow(milestoneExercise.getId());
        for (UserStoryExercise userStoryExercise : milestoneWithChildren.getUserStoryExercises()) {
            if (findGradedParticipation(userStoryExercise, participant).isPresent()) {
                // Already started (e.g. a UserStoryExercise was added to the Milestone after the student first started it).
                continue;
            }
            ProgrammingExerciseStudentParticipation siblingParticipation = new ProgrammingExerciseStudentParticipation(milestoneParticipation.getBranch());
            siblingParticipation.setExercise(userStoryExercise);
            siblingParticipation.setParticipant(participant);
            siblingParticipation.setRepositoryUri(milestoneParticipation.getRepositoryUri());
            siblingParticipation.setBuildPlanId(milestoneParticipation.getBuildPlanId());
            siblingParticipation.setInitializationState(milestoneParticipation.getInitializationState());
            siblingParticipation.setInitializationDate(ZonedDateTime.now());
            studentParticipationRepository.saveAndFlush(siblingParticipation);
        }
    }

    private Optional<StudentParticipation> findGradedParticipation(Exercise exercise, Participant participant) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false);
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }
        else {
            throw new IllegalStateException("Unknown Participant type");
        }
    }

    /**
     * The exercise that owns the repositories the given exercise works on - itself, unless it is a MilestoneExercise created to
     * reuse another Milestone's repositories.
     *
     * @param exercise the exercise being started
     * @return the repository-owning exercise, never null
     */
    public ProgrammingExercise repositoryOwnerOf(ProgrammingExercise exercise) {
        if (exercise instanceof MilestoneExercise milestoneExercise && milestoneExercise.reusesRepositoriesOfAnotherMilestone()) {
            // Reloaded rather than dereferenced: the association is eager, but the source is only populated with the fields the
            // exercise-fetching query asked for, and the template participation carrying the repository uri is not among them
            return programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(milestoneExercise.getRepositorySourceMilestone().getId());
        }
        return exercise;
    }

    /**
     * Finds the participation whose repository a Milestone being started should continue in.
     * <p>
     * A Milestone created to reuse another Milestone's repositories shares one student repository with it and with every other
     * Milestone on those repositories: the student works on one continuously growing codebase across all of them, so starting a
     * later Milestone must not fork the template again and throw their work away.
     * <p>
     * Individual participations only. A Milestone is assessed per student (see {@code MilestoneAssessmentService}), and team
     * participations are not part of the sharing, so a team falls back to the regular copy.
     * <p>
     * Empty if {@code exercise} is not a MilestoneExercise.
     *
     * @param exercise      the exercise being started
     * @param participation the participation being started
     * @return the participation whose repository to continue in, or empty if the student has none yet
     */
    public Optional<ProgrammingExerciseStudentParticipation> findParticipationOnSharedMilestoneRepository(ProgrammingExercise exercise,
            ProgrammingExerciseStudentParticipation participation) {
        if (!(exercise instanceof MilestoneExercise milestoneExercise) || participation.isPracticeMode()) {
            return Optional.empty();
        }
        Optional<User> student = participation.getStudent();
        if (student.isEmpty()) {
            return Optional.empty();
        }
        long repositoryOwnerId = milestoneExercise.getRepositoryOwner().getId();
        return programmingExerciseStudentParticipationRepository.findAllMilestoneParticipationsSharingRepositoryByOwnerIdAndStudentId(repositoryOwnerId, student.get().getId())
                .stream().filter(existing -> !existing.getId().equals(participation.getId())).filter(existing -> existing.getRepositoryUri() != null).findFirst();
    }

    /**
     * Points a Milestone participation at the repository the student already works in, instead of at a copy of the template.
     * Mirrors what {@link #cascadeStartToUserStoryExercises} does for the user stories of one Milestone, one level up.
     *
     * @param participation       the participation being started
     * @param sharedParticipation the student's existing participation on the shared repository
     * @return the participation, pointing at the shared repository
     */
    public ProgrammingExerciseStudentParticipation adoptSharedMilestoneRepository(ProgrammingExerciseStudentParticipation participation,
            ProgrammingExerciseStudentParticipation sharedParticipation) {
        participation.setRepositoryUri(sharedParticipation.getRepositoryUri());
        participation.setBranch(sharedParticipation.getBranch());
        participation.setBuildPlanId(sharedParticipation.getBuildPlanId());
        participation.setInitializationState(InitializationState.REPO_COPIED);
        return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * A single push to a Milestone's repository triggers exactly one CI build (queued against the participation of the
     * Milestone the student is currently working on); fan that single result out into one additional Result per
     * UserStoryExercise sibling participation, reusing the grading pipeline unmodified for each (it self-scopes to each
     * UserStory's own test cases, see {@code ProgrammingExerciseGradingService#findActiveTestCasesScopedToExercise}).
     * <p>
     * Deliberately only this Milestone's user stories, even though Milestones created on its template repository share the
     * student's repository: every Milestone has a test repository of its own, so the build result only contains the tests of
     * the Milestone it was queued for. Handing it to another Milestone's participations would report all of its tests as
     * missing and reset the student's score there to zero.
     * <p>
     * No-op if {@code participation} does not belong to a MilestoneExercise.
     *
     * @param participation the participation the CI result was queued for
     * @param buildResult   the raw build result to reprocess for each UserStoryExercise sibling
     * @param testsExpected whether the build config expects tests to run
     */
    public void fanOutResultToUserStoryParticipations(ProgrammingExerciseParticipation participation, BuildResult buildResult, boolean testsExpected) {
        if (!(participation.getProgrammingExercise() instanceof MilestoneExercise milestoneExercise)
                || !(participation instanceof ProgrammingExerciseStudentParticipation milestoneParticipation)) {
            return;
        }
        milestoneParticipation.getStudent().ifPresent(student -> {
            List<ProgrammingExerciseStudentParticipation> userStorySiblings = programmingExerciseStudentParticipationRepository
                    .findAllUserStorySiblingsByMilestoneIdAndRepositoryUriAndStudentId(milestoneExercise.getId(), milestoneParticipation.getRepositoryUri(), student.getId());
            for (ProgrammingExerciseStudentParticipation userStoryParticipation : userStorySiblings) {
                programmingExerciseGradingService.processNewProgrammingExerciseResult(userStoryParticipation, buildResult, testsExpected);
            }
        });
    }

    /**
     * Adds every participation the student has on the same repository to the candidates whose token may authenticate.
     * <p>
     * One repository is shared by a MilestoneExercise, all of its UserStoryExercises, and - when Milestones were created to
     * reuse another Milestone's repositories - every Milestone of that chain with its own user stories. Starting a Milestone
     * creates one participation per exercise, all carrying the same repository URI (see {@link #cascadeStartToUserStoryExercises}
     * and {@link #findParticipationOnSharedMilestoneRepository}). The URI resolves to the repository-owning Milestone alone (the
     * other rows have project keys of their own), so without this only that one participation's token would ever be accepted -
     * and a student handed a token from a user story page, or from the Milestone they are currently working on, would get
     * "Authentication failed" on a repository they are perfectly entitled to clone.
     * <p>
     * Unchanged for anything but a MilestoneExercise.
     *
     * @param knownParticipations the participations already found for the resolved exercise
     * @param user                the user attempting authentication
     * @param exercise            the exercise the repository URI resolved to
     * @param repositoryUri       the repository being accessed
     * @return the participations whose token is valid for this repository
     */
    public List<ProgrammingExerciseStudentParticipation> findParticipationsSharingRepository(List<ProgrammingExerciseStudentParticipation> knownParticipations, User user,
            ProgrammingExercise exercise, String repositoryUri) {
        if (!(exercise instanceof MilestoneExercise)) {
            return knownParticipations;
        }
        List<ProgrammingExerciseStudentParticipation> sharing = programmingExerciseStudentParticipationRepository.findAllByRepositoryUriAndStudentId(repositoryUri, user.getId());
        if (sharing.isEmpty()) {
            return knownParticipations;
        }
        Set<Long> alreadyKnown = knownParticipations.stream().map(ProgrammingExerciseStudentParticipation::getId).collect(Collectors.toSet());
        List<ProgrammingExerciseStudentParticipation> allCandidates = new ArrayList<>(knownParticipations);
        sharing.stream().filter(participation -> !alreadyKnown.contains(participation.getId())).forEach(allCandidates::add);
        return allCandidates;
    }
}
