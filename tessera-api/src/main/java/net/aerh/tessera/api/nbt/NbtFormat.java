package net.aerh.tessera.api.nbt;

/**
 * Minecraft NBT dialect enumeration used by {@link net.aerh.tessera.api.generator.FromNbtBuilder}
 * to route an NBT payload through the correct parser.
 *
 * <p>The default value is {@link #AUTO}: the impl inspects the input with a bounded
 * structural-signature scan and routes to the matching dialect handler. Pass an explicit
 * value to {@code FromNbtBuilder.format(NbtFormat)} to skip the detector (for example
 * when the input is known to come from an adversarial source and should never be
 * auto-classified).
 *
 * <p>When {@link #AUTO} is chosen and the detector cannot match any signature, the
 * impl throws {@link net.aerh.tessera.api.exception.ParseException} with a message
 * instructing the caller to supply an explicit {@code.format(...)} value.
 *
 * @see net.aerh.tessera.api.generator.FromNbtBuilder#format(NbtFormat)
 */
public enum NbtFormat {

    /**
     * Default routing: detector inspects the payload and picks one of the concrete dialects
     * below. Ambiguous payloads throw {@code ParseException}.
     */
    AUTO,

    /**
     * Minecraft's text-format NBT ("SNBT"); e.g. {@code {id:minecraft:diamond_sword,Count:1b}}.
     * Keys are typically unquoted; primitives carry suffix letters (b, s, l, f, d).
     */
    SNBT,

    /**
     * Post-1.20.5 JSON component format: {@code {"id":"minecraft:x","components":{...}}}.
     */
    JSON_COMPONENT,

    /**
     * 1.12 and earlier legacy binary compound, characterised by a numeric-id schema
     * like {@code {"id":276,"Count":1}}.
     */
    PRE_FLATTENING,

    /**
     * 1.13 to 1.20.4 binary compound with the {@code "tag"} child key carrying the
     * per-item metadata sub-compound.
     */
    POST_FLATTENING
}
