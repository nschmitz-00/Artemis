package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO returned after uploading a programming exercise explanation video, so the client can update its view of the
 * participation immediately without needing to refetch it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseExplanationVideoDTO(String explanationVideoPath, ZonedDateTime explanationVideoUploadDate) {
}
