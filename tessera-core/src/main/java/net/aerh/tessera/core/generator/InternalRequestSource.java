package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.generator.RenderRequest;

/**
 * Package-private bridge that exposes the internal {@link RenderRequest} an api-level fluent
 * builder would render. {@link CompositeBuilderImpl} calls this on each child it accumulates
 * so the composite fan-out can dispatch them through the same package-private
 * {@code DefaultEngine#renderInternal} switch as standalone renders.
 *
 * <p>External {@code RenderSpec} implementations (i.e. anyone who consumes the api but is not
 * one of Tessera's 7 built-in BuilderImpls) cannot be added to a composite. The api-level
 * {@code CompositeBuilder.add(RenderSpec)} validates this at runtime and surfaces a clear
 * {@link IllegalArgumentException}; the alternative was to declare this bridge on the public
 * api interface, which would leak {@code RenderRequest} (and the package-private records that
 * implement it) onto the consumer-visible surface in violation of.
 */
interface InternalRequestSource {

    /**
     * Builds the internal {@link RenderRequest} this builder would dispatch via {@code render()}.
     */
    RenderRequest toInternalRequest();
}
