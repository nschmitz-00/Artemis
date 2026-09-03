import { describe, expect, it } from 'vitest';
import { formatEffortHours, parseEffortHours } from 'app/course/overview/exercise-details/user-story-tasks/task-effort-format.util';

describe('task-effort-format.util', () => {
    describe('formatEffortHours', () => {
        it('should format whole hours', () => {
            expect(formatEffortHours(1)).toBe('1:00h');
        });

        it('should format fractional hours as minutes', () => {
            expect(formatEffortHours(1.5)).toBe('1:30h');
            expect(formatEffortHours(0.25)).toBe('0:15h');
        });

        it('should round to the nearest minute', () => {
            expect(formatEffortHours(2.999)).toBe('3:00h');
        });
    });

    describe('parseEffortHours', () => {
        it('should parse an "h:mm" string', () => {
            expect(parseEffortHours('1:30')).toBe(1.5);
            expect(parseEffortHours('0:15')).toBe(0.25);
        });

        it('should tolerate the trailing "h" that formatEffortHours renders', () => {
            expect(parseEffortHours('1:30h')).toBe(1.5);
        });

        it('should tolerate surrounding whitespace', () => {
            expect(parseEffortHours('  1:30  ')).toBe(1.5);
        });

        it('should accept a bare whole number of hours', () => {
            expect(parseEffortHours('2')).toBe(2);
        });

        it('should reject an invalid minutes component', () => {
            expect(parseEffortHours('1:60')).toBeUndefined();
            expect(parseEffortHours('1:7')).toBeUndefined();
        });

        it('should reject unparsable input', () => {
            expect(parseEffortHours('not a duration')).toBeUndefined();
            expect(parseEffortHours('')).toBeUndefined();
        });

        it('should round-trip with formatEffortHours', () => {
            expect(parseEffortHours(formatEffortHours(1.5))).toBe(1.5);
        });
    });
});
