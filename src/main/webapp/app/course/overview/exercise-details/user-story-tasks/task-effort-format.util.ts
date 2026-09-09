/** Splits decimal hours into whole hours and minutes, rounding to the nearest minute. */
export function splitEffortHours(hours: number): { hours: number; minutes: number } {
    const totalMinutes = Math.round(hours * 60);
    return { hours: Math.floor(totalMinutes / 60), minutes: totalMinutes % 60 };
}

/** Renders decimal hours as `H:MMh`, e.g. 1.5 -> "1:30h". */
export function formatEffortHours(hours: number): string {
    const { hours: wholeHours, minutes } = splitEffortHours(hours);
    return `${wholeHours}:${minutes.toString().padStart(2, '0')}h`;
}

/**
 * Parses an "H:MM" duration - the same format {@link formatEffortHours} renders, with or without the trailing "h" -
 * into decimal hours. A bare whole number of hours (e.g. "2") is also accepted. Returns undefined for anything else.
 */
export function parseEffortHours(input: string): number | undefined {
    const trimmed = input.trim().replace(/h$/i, '');
    const withMinutes = /^(\d+):([0-5]\d)$/.exec(trimmed);
    if (withMinutes) {
        return Number(withMinutes[1]) + Number(withMinutes[2]) / 60;
    }
    if (/^\d+$/.test(trimmed)) {
        return Number(trimmed);
    }
    return undefined;
}
