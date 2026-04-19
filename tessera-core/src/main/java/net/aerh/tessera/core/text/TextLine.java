package net.aerh.tessera.core.text;

import net.aerh.tessera.api.text.TextSegment;

import java.util.List;

/**
 * A single horizontal line of rendered text, composed of one or more {@link TextSegment}s.
 *
 * @param segments The ordered list of segments making up this line.
 * @param width The computed character width of this line.
 */
public record TextLine(List<TextSegment> segments, int width) {

    public TextLine {
        segments = List.copyOf(segments);
    }
}
