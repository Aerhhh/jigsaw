package net.aerh.tessera.core.nbt;

import net.aerh.tessera.api.nbt.NbtFormat;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Structural-signature routing for NBT dialect detection.
 *
 * <p>Implementation is a bounded {@code O(n)} string scan - no recursion, no parse, no
 * try-parse chain. A deeply nested malicious payload cannot cause {@code StackOverflowError}
 * or OOM because we only inspect substrings; the caller's handler chain is responsible for
 * bounded-depth parsing once the format has been routed.
 *
 * <p>Signature precedence (most specific wins):
 * <ol>
 *   <li>{@link NbtFormat#JSON_COMPONENT}: quoted {@code "id"} AND quoted {@code "components"}.</li>
 *   <li>{@link NbtFormat#POST_FLATTENING}: quoted {@code "id"} AND quoted {@code "tag"}.</li>
 *   <li>{@link NbtFormat#PRE_FLATTENING}: {@code "id"} with a numeric right-hand side
 *       (matches {@code "id":276} style legacy schemas).</li>
 *   <li>{@link NbtFormat#SNBT}: first key unquoted (leading non-quote character after the
 *       opening brace) OR a primitive suffix like {@code 1b} / {@code 5s} appears.</li>
 * </ol>
 *
 * <p>Ambiguous inputs (empty compound, garbage, matchless) return {@link Optional#empty()};
 * callers must supply an explicit {@code.format(NbtFormat.X)} override when this happens.
 *
 * @see NbtFormat
 */
public final class NbtDialectDetector {

    /** Defensive scan length cap: inputs larger than this are truncated for signature inspection. */
    private static final int MAX_SCAN_LENGTH = 1_048_576;

    /** Matches {@code "id":<digits>} anywhere in the payload (legacy pre-flattening). */
    private static final Pattern NUMERIC_ID = Pattern.compile("\"id\"\\s*:\\s*\\d+");

    /** Matches an SNBT primitive suffix: a digit followed by NBT type letter b/s/l/f/d. */
    private static final Pattern SNBT_SUFFIX = Pattern.compile("\\d+[bslfdBSLFD]\\b");

    private NbtDialectDetector() {
    }

    /**
     * Returns the detected {@link NbtFormat} for {@code input}, or {@link Optional#empty()}
     * when no signature fires.
     *
     * <p>Null and blank inputs produce {@link Optional#empty()} (no NPE).
     */
    public static Optional<NbtFormat> detect(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return Optional.empty();
        }
        // Defensive: bound scan length to O(1MB).
        String scan = trimmed.length() > MAX_SCAN_LENGTH
                ? trimmed.substring(0, MAX_SCAN_LENGTH)
                : trimmed;

        boolean hasIdQuoted = scan.contains("\"id\"");
        boolean hasComponents = scan.contains("\"components\"");
        if (hasIdQuoted && hasComponents) {
            return Optional.of(NbtFormat.JSON_COMPONENT);
        }

        boolean hasTagQuoted = scan.contains("\"tag\"");
        if (hasIdQuoted && hasTagQuoted) {
            return Optional.of(NbtFormat.POST_FLATTENING);
        }

        // Pre-flattening: numeric id like "id":276.
        if (NUMERIC_ID.matcher(scan).find()) {
            return Optional.of(NbtFormat.PRE_FLATTENING);
        }

        // SNBT: first character after the opening '{' is non-quote (unquoted key),
        // or a primitive suffix (1b / 5s / etc.) is present anywhere in the payload.
        if (isFirstKeyUnquoted(scan) || SNBT_SUFFIX.matcher(scan).find()) {
            return Optional.of(NbtFormat.SNBT);
        }

        return Optional.empty();
    }

    /**
     * Returns {@code true} when the first key in the compound (i.e. the first non-whitespace
     * character after the opening {@code '{'}) is a letter or underscore, NOT a double quote.
     * SNBT is the only dialect where keys are routinely unquoted.
     */
    private static boolean isFirstKeyUnquoted(String scan) {
        // Scan past '{' and any whitespace.
        int i = 1;
        while (i < scan.length() && Character.isWhitespace(scan.charAt(i))) {
            i++;
        }
        if (i >= scan.length()) {
            return false;
        }
        char first = scan.charAt(i);
        // Quoted key or closing brace -> not SNBT-style.
        if (first == '"' || first == '}') {
            return false;
        }
        // Unquoted SNBT keys look like identifiers; a leading letter/underscore is the
        // discriminator.
        return Character.isLetter(first) || first == '_';
    }
}
