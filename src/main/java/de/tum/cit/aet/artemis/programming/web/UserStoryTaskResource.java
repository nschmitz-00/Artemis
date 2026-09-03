package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UserStoryTaskDTO;
import de.tum.cit.aet.artemis.programming.service.UserStoryTaskService;

/**
 * REST controller for the tasks a participant creates for themself while working on a {@link UserStoryExercise}.
 * <p>
 * Every endpoint acts on the requesting user's own participation, or a task on it - there is no way to read or write
 * someone else's board through here.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class UserStoryTaskResource {

    private static final Logger log = LoggerFactory.getLogger(UserStoryTaskResource.class);

    private final UserStoryTaskService userStoryTaskService;

    private final UserRepository userRepository;

    public UserStoryTaskResource(UserStoryTaskService userStoryTaskService, UserRepository userRepository) {
        this.userStoryTaskService = userStoryTaskService;
        this.userRepository = userRepository;
    }

    /**
     * GET /user-story-exercises/:exerciseId/tasks : Get the requesting user's tasks for the story.
     *
     * @param exerciseId the id of the user story exercise
     * @return the ResponseEntity with status 200 (OK) and the user's tasks in the body
     */
    @GetMapping("user-story-exercises/{exerciseId}/tasks")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserStoryTaskDTO>> getTasks(@PathVariable long exerciseId) {
        log.debug("REST request to get the tasks for user story exercise {}", exerciseId);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryTaskService.findAllForUser(exerciseId, user));
    }

    /**
     * POST /user-story-exercises/:exerciseId/tasks : Create a new task on the requesting user's board for the story.
     *
     * @param exerciseId the id of the user story exercise
     * @param taskDTO    the task to create
     * @return the ResponseEntity with status 200 (OK) and the created task in the body
     */
    @PostMapping("user-story-exercises/{exerciseId}/tasks")
    @EnforceAtLeastStudent
    public ResponseEntity<UserStoryTaskDTO> createTask(@PathVariable long exerciseId, @RequestBody UserStoryTaskDTO taskDTO) {
        log.debug("REST request to create a task for user story exercise {} : {}", exerciseId, taskDTO);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryTaskService.create(exerciseId, taskDTO, user));
    }

    /**
     * PUT /user-story-tasks/:taskId : Update a task the requesting user owns.
     *
     * @param taskId  the id of the task to update
     * @param taskDTO the new field values
     * @return the ResponseEntity with status 200 (OK) and the updated task in the body
     */
    @PutMapping("user-story-tasks/{taskId}")
    @EnforceAtLeastStudent
    public ResponseEntity<UserStoryTaskDTO> updateTask(@PathVariable long taskId, @RequestBody UserStoryTaskDTO taskDTO) {
        log.debug("REST request to update task {} : {}", taskId, taskDTO);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryTaskService.update(taskId, taskDTO, user));
    }

    /**
     * PUT /user-story-tasks/:taskId/advance-state : Shift a task the requesting user owns one step forward (NEW to
     * IN_PROGRESS, or IN_PROGRESS to DONE).
     *
     * @param taskId the id of the task to advance
     * @return the ResponseEntity with status 200 (OK) and the task in its new state
     */
    @PutMapping("user-story-tasks/{taskId}/advance-state")
    @EnforceAtLeastStudent
    public ResponseEntity<UserStoryTaskDTO> advanceTaskState(@PathVariable long taskId) {
        log.debug("REST request to advance the state of task {}", taskId);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryTaskService.advanceState(taskId, user));
    }

    /**
     * PUT /user-story-exercises/:exerciseId/tasks/reorder : Reorder the requesting user's board (drag-and-drop) to
     * match the given sequence of task ids.
     *
     * @param exerciseId     the id of the user story exercise
     * @param orderedTaskIds every task currently on the board, in the new order
     * @return the ResponseEntity with status 200 (OK) and the tasks in their new order
     */
    @PutMapping("user-story-exercises/{exerciseId}/tasks/reorder")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserStoryTaskDTO>> reorderTasks(@PathVariable long exerciseId, @RequestBody List<Long> orderedTaskIds) {
        log.debug("REST request to reorder the tasks for user story exercise {} : {}", exerciseId, orderedTaskIds);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryTaskService.reorder(exerciseId, orderedTaskIds, user));
    }

    /**
     * DELETE /user-story-tasks/:taskId : Delete a task the requesting user owns.
     *
     * @param taskId the id of the task to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("user-story-tasks/{taskId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteTask(@PathVariable long taskId) {
        log.debug("REST request to delete task {}", taskId);
        User user = userRepository.getUser();
        userStoryTaskService.delete(taskId, user);
        return ResponseEntity.ok().build();
    }
}
