package net.aerh.tessera.skyblock.tooltip;

import net.aerh.tessera.api.generator.RenderSpec;
import net.aerh.tessera.skyblock.data.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the behaviour of {@link SkyBlockTooltipBuilder}.
 *
 * <p>Post the builder's {@code build()} returns a
 * {@link SkyBlockTooltipSpec} (a {@link RenderSpec}) rather than the now-package-private
 * Tessera {@code TooltipRequest} record; assertions read the spec's fields directly.
 */
class SkyBlockTooltipBuilderTest {

    // -----------------------------------------------------------------------
    // Basic construction
    // -----------------------------------------------------------------------

    @Test
    void buildWithNameAndRarity() {
        Rarity legendary = Rarity.byName("legendary").orElseThrow();

        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Hyperion")
            .rarity(legendary)
            .type("SWORD")
            .build();

        assertThat(spec).isInstanceOf(RenderSpec.class);
        assertThat(spec.lines()).isNotEmpty();
        // First line must contain the item name
        assertThat(spec.lines().get(0)).contains("Hyperion");
        // First line must be prefixed with the rarity color code
        assertThat(spec.lines().get(0)).startsWith("&6");
    }

    @Test
    void buildWithoutRarityHasNoColorPrefix() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Plain Item")
            .build();

        assertThat(spec.lines()).isNotEmpty();
        // No rarity so no color prefix - line starts directly with the name
        assertThat(spec.lines().get(0)).isEqualTo("Plain Item");
    }

    // -----------------------------------------------------------------------
    // Stat placeholder resolution
    // -----------------------------------------------------------------------

    @Test
    void resolvesStatPlaceholderWithValue() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Sword")
            .lore("%%damage:500%%")
            .build();

        List<String> lines = spec.lines();
        // One of the lines must contain resolved stat text with a + sign or stat display
        String loreText = String.join(" ", lines);
        assertThat(loreText).contains("Damage");
        assertThat(loreText).contains("+500");
    }

    @Test
    void resolvesStatPlaceholderWithoutValue() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Sword")
            .lore("%%strength%%")
            .build();

        String loreText = String.join(" ", spec.lines());
        assertThat(loreText).contains("Strength");
    }

    @Test
    void resolveFlavorPlaceholder() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Sword")
            .lore("%%soulbound%%")
            .build();

        String loreText = String.join(" ", spec.lines());
        assertThat(loreText).containsIgnoringCase("Soulbound");
    }

    @Test
    void resolvesAbilityFlavorPlaceholderWithNameAndType() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Wand")
            .lore("%%ability:Wither Impact:RIGHT CLICK%%")
            .build();

        String loreText = String.join(" ", spec.lines());
        assertThat(loreText).contains("Ability");
        assertThat(loreText).contains("Wither Impact");
        assertThat(loreText).contains("RIGHT CLICK");
        assertThat(loreText).doesNotContain("{abilityName}");
        assertThat(loreText).doesNotContain("{abilityType}");
    }

    @Test
    void unknownPlaceholderIsLeftUnchanged() {
        String unknownPlaceholder = "%%not_a_real_key:99%%";
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .lore(unknownPlaceholder)
            .build();

        String loreText = String.join(" ", spec.lines());
        assertThat(loreText).contains(unknownPlaceholder);
    }

    // -----------------------------------------------------------------------
    // Rarity footer
    // -----------------------------------------------------------------------

    @Test
    void addsRarityFooterLine() {
        Rarity legendary = Rarity.byName("legendary").orElseThrow();

        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Hyperion")
            .rarity(legendary)
            .type("SWORD")
            .build();

        List<String> lines = spec.lines();
        String lastLine = lines.get(lines.size() - 1);
        // Footer must contain the bold rarity name in uppercase
        assertThat(lastLine).contains("LEGENDARY");
        assertThat(lastLine).contains("SWORD");
        assertThat(lastLine).contains("&l");
    }

    @Test
    void noneRarityProducesNoFooter() {
        Rarity none = Rarity.byName("none").orElseThrow();

        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .rarity(none)
            .build();

        List<String> lines = spec.lines();
        // No footer separator or rarity line expected
        assertThat(lines).hasSize(1);
    }

    @Test
    void nullRarityProducesNoFooter() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .rarity(null)
            .build();

        List<String> lines = spec.lines();
        assertThat(lines).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Empty lore
    // -----------------------------------------------------------------------

    @Test
    void emptyLoreProducesOnlyNameAndFooter() {
        Rarity rare = Rarity.byName("rare").orElseThrow();

        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Some Item")
            .rarity(rare)
            .lore("")
            .build();

        // name line + separator + footer = 3 lines
        assertThat(spec.lines()).hasSize(3);
    }

    @Test
    void nullLoreProducesOnlyNameAndFooter() {
        Rarity rare = Rarity.byName("rare").orElseThrow();

        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Some Item")
            .rarity(rare)
            .lore(null)
            .build();

        assertThat(spec.lines()).hasSize(3);
    }

    // -----------------------------------------------------------------------
    // buildSlashCommand
    // -----------------------------------------------------------------------

    @Test
    void buildSlashCommandProducesValidString() {
        Rarity legendary = Rarity.byName("legendary").orElseThrow();

        String command = SkyBlockTooltipBuilder.builder()
            .name("Hyperion")
            .rarity(legendary)
            .type("SWORD")
            .buildSlashCommand();

        assertThat(command).startsWith("/");
        assertThat(command).contains("item_name");
        assertThat(command).contains("Hyperion");
    }

    @Test
    void buildSlashCommandContainsRarityName() {
        Rarity epic = Rarity.byName("epic").orElseThrow();

        String command = SkyBlockTooltipBuilder.builder()
            .name("Livid Dagger")
            .rarity(epic)
            .buildSlashCommand();

        assertThat(command).contains("epic");
    }

    // -----------------------------------------------------------------------
    // SkyBlockTooltipSpec settings pass-through
    // -----------------------------------------------------------------------

    @Test
    void alphaIsPassedThrough() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .alpha(128)
            .build();

        assertThat(spec.alpha()).isEqualTo(128);
    }

    @Test
    void paddingIsPassedThrough() {
        SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .padding(10)
            .build();

        assertThat(spec.padding()).isEqualTo(10);
    }
}
