package net.aerh.tessera.api.nbt;

import net.aerh.tessera.api.exception.ParseException;

/**
 * Parses a raw NBT string into a {@link ParsedItem}.
 *
 * @see ParsedItem
 * @see net.aerh.tessera.spi.NbtFormatHandler
 */
public interface NbtParser {

    /**
     * Parses the given NBT input string and returns the structured item data.
     *
     * @param input The raw NBT string (SNBT, component format, etc.).
     * @return The parsed item.
     * @throws ParseException if the input cannot be parsed into a valid item.
     */
    ParsedItem parse(String input) throws ParseException;
}
