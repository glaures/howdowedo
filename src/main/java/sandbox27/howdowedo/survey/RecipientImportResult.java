package sandbox27.howdowedo.survey;

/**
 * Outcome of importing a batch of e-mail addresses into a survey's recipient list.
 *
 * @param added      addresses newly stored
 * @param duplicates addresses skipped because they were already on the list (or repeated in the input)
 * @param invalid    tokens skipped because they were not a valid e-mail address
 */
public record RecipientImportResult(int added, int duplicates, int invalid) {
}
