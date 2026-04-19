package net.aerh.tessera.api.assets;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CacheLocatorTest {

    private static final String MC_VER = "26.1.2";

    @Test
    void builderOverrideTakesPrecedenceOverEverything() {
        Path override = Path.of("C:", "custom", "cache");
        Path resolved = CacheLocator.resolve(override, MC_VER);

        assertThat(resolved).isEqualTo(override.resolve("assets").resolve(MC_VER));
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_ASSET_DIR", value = "/env/cache/root")
    void envVarUsedWhenBuilderOverrideNull() {
        Path resolved = CacheLocator.resolve(null, MC_VER);

        assertThat(resolved).isEqualTo(Path.of("/env/cache/root").resolve("assets").resolve(MC_VER));
    }

    @Test
    @SetSystemProperty(key = "user.home", value = "/fake/home")
    @SetEnvironmentVariable(key = "LOCALAPPDATA", value = "C:\\Users\\Foo\\AppData\\Local")
    void windowsDefaultUsesLocalAppDataWhenSet() {
        Path base = CacheLocator.defaultFor("Windows 11");

        assertThat(base).isEqualTo(Path.of("C:\\Users\\Foo\\AppData\\Local").resolve("Tessera"));
    }

    @Test
    @SetSystemProperty(key = "user.home", value = "/fake/home")
    @ClearEnvironmentVariable(key = "LOCALAPPDATA")
    void windowsDefaultFallsBackToUserProfileWhenLocalAppDataUnset() {
        Path base = CacheLocator.defaultFor("Windows 11");

        assertThat(base).isEqualTo(Path.of("/fake/home", "AppData", "Local", "Tessera"));
    }

    @Test
    @SetSystemProperty(key = "user.home", value = "/Users/foo")
    void macDefaultUsesLibraryCaches() {
        Path base = CacheLocator.defaultFor("Mac OS X");

        assertThat(base).isEqualTo(Path.of("/Users/foo", "Library", "Caches", "Tessera"));
    }

    @Test
    @SetSystemProperty(key = "user.home", value = "/home/foo")
    @SetEnvironmentVariable(key = "XDG_CACHE_HOME", value = "/custom/xdg")
    void linuxUsesXdgCacheHomeWhenSet() {
        Path base = CacheLocator.defaultFor("Linux");

        assertThat(base).isEqualTo(Path.of("/custom/xdg").resolve("tessera"));
    }

    @Test
    @SetSystemProperty(key = "user.home", value = "/home/foo")
    @ClearEnvironmentVariable(key = "XDG_CACHE_HOME")
    void linuxFallsBackToHomeDotCacheWhenXdgUnset() {
        Path base = CacheLocator.defaultFor("Linux");

        assertThat(base).isEqualTo(Path.of("/home/foo", ".cache").resolve("tessera"));
    }

    @Test
    void nullMcVerThrowsNpe() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> CacheLocator.resolve(null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mcVer must not be null");
    }
}
