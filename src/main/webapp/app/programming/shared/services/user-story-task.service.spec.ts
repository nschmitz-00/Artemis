import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserStoryTaskService } from 'app/programming/shared/services/user-story-task.service';
import { UserStoryTask } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';

describe('UserStoryTaskService', () => {
    let service: UserStoryTaskService;
    let httpMock: HttpTestingController;

    const task: UserStoryTask = { id: 1, title: 'Set up repo', taskPoints: 2, priority: 'MEDIUM', estimatedEffortHours: 1.5, state: 'NEW' };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(UserStoryTaskService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should GET the tasks for an exercise', () => {
        service.getTasks(42).subscribe((tasks) => expect(tasks).toEqual([task]));

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/programming/user-story-exercises/42/tasks');
        req.flush([task]);
    });

    it('should POST a new task for an exercise', () => {
        service.createTask(42, task).subscribe((created) => expect(created).toEqual(task));

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.url).toBe('api/programming/user-story-exercises/42/tasks');
        expect(req.request.body).toEqual(task);
        req.flush(task);
    });

    it('should PUT an updated task', () => {
        service.updateTask(1, task).subscribe((updated) => expect(updated).toEqual(task));

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toBe('api/programming/user-story-tasks/1');
        expect(req.request.body).toEqual(task);
        req.flush(task);
    });

    it('should PUT the ordered task ids to reorder the board', () => {
        const second: UserStoryTask = { ...task, id: 2 };
        service.reorderTasks(42, [2, 1]).subscribe((tasks) => expect(tasks).toEqual([second, task]));

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toBe('api/programming/user-story-exercises/42/tasks/reorder');
        expect(req.request.body).toEqual([2, 1]);
        req.flush([second, task]);
    });

    it('should PUT to advance a task state', () => {
        const advanced: UserStoryTask = { ...task, state: 'IN_PROGRESS' };
        service.advanceState(1).subscribe((updated) => expect(updated).toEqual(advanced));

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toBe('api/programming/user-story-tasks/1/advance-state');
        req.flush(advanced);
    });

    it('should DELETE a task', () => {
        service.deleteTask(1).subscribe();

        const req = httpMock.expectOne({ method: 'DELETE' });
        expect(req.request.url).toBe('api/programming/user-story-tasks/1');
        req.flush(null);
    });
});
