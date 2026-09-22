package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.programming.domain.event.MilestoneScoreRecomputationRequestedEvent;

/**
 * Hands a {@link MilestoneScoreRecomputationRequestedEvent} to the instance that schedules milestone score recomputations.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class MilestoneScoreRecomputationListener {

    private final InstanceMessageSendService instanceMessageSendService;

    public MilestoneScoreRecomputationListener(InstanceMessageSendService instanceMessageSendService) {
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * Schedules the recomputation of every student's score on the milestone.
     *
     * @param event the event naming the milestone whose scores are stale
     */
    @EventListener
    public void onMilestoneScoreRecomputationRequested(MilestoneScoreRecomputationRequestedEvent event) {
        instanceMessageSendService.sendMilestoneScoreScheduleForGroup(event.milestoneExerciseId());
    }
}
