import { describe, expect, it } from 'vitest';
import { CourseManagementExerciseDTO, exerciseFromCourseManagementDTO } from 'app/course/shared/entities/course-content-response.dto';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

describe('exerciseFromCourseManagementDTO', () => {
    function programmingPayload(type: ExerciseType): CourseManagementExerciseDTO {
        return { id: 7, title: 'Sorting', type, dueDate: '2026-09-30T12:00:00Z' } as CourseManagementExerciseDTO;
    }

    // A milestone or user story exercise arrives on this endpoint under its own discriminator. The switch used to have
    // no case for either, so it returned undefined and the management exercises page failed on the whole list.
    it.each([ExerciseType.PROGRAMMING, ExerciseType.MILESTONE, ExerciseType.USER_STORY])('should convert a %s exercise into a programming exercise', (type) => {
        const exercise = exerciseFromCourseManagementDTO(programmingPayload(type));

        expect(exercise).toBeInstanceOf(ProgrammingExercise);
        expect(exercise.id).toBe(7);
        expect(exercise.dueDate).toBeDefined();
    });

    // The management page tells the milestone exercise apart from its user stories by this value alone: it drops the
    // milestone, whose group card already stands for it, and lists the stories under that card.
    it.each([ExerciseType.PROGRAMMING, ExerciseType.MILESTONE, ExerciseType.USER_STORY])('should keep the %s discriminator on the converted exercise', (type) => {
        expect(exerciseFromCourseManagementDTO(programmingPayload(type)).type).toBe(type);
    });

    // Without these, every member of a milestone group reads as a plain variant: the course scores count a user story's
    // points on top of the milestone's, and the sidebar stops grouping them under it.
    it('should keep the variant group type and its anchor milestone exercise', () => {
        const payload = {
            ...programmingPayload(ExerciseType.USER_STORY),
            exerciseVariantGroup: { id: 10, title: 'Sprint 1', type: 'milestone', milestoneExerciseId: 99 },
        } as CourseManagementExerciseDTO;

        const group = exerciseFromCourseManagementDTO(payload).exerciseVariantGroup;

        expect(group?.type).toBe('milestone');
        expect(group?.milestoneExerciseId).toBe(99);
    });
});
