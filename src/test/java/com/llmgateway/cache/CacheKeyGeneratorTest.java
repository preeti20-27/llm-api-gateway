package com.llmgateway.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyGeneratorTest {

    private final CacheKeyGenerator generator = new CacheKeyGenerator();

    @Test
    void generate_isDeterministic() {
        assertThat(generator.generate("gemini-1.5-flash", "hello", 100))
                .isEqualTo(generator.generate("gemini-1.5-flash", "hello", 100));
    }

    @Test
    void generate_normalizesWhitespaceInPrompt() {
        assertThat(generator.generate("gemini-1.5-flash", "  hello   world  ", null))
                .isEqualTo(generator.generate("gemini-1.5-flash", "hello world", null));
    }

    @Test
    void generate_differsWhenModelDiffers() {
        assertThat(generator.generate("gemini-1.5-flash", "hello", null))
                .isNotEqualTo(generator.generate("gemini-1.5-pro", "hello", null));
    }

    @Test
    void generate_differsWhenMaxTokensDiffers() {
        assertThat(generator.generate("gemini-1.5-flash", "hello", 100))
                .isNotEqualTo(generator.generate("gemini-1.5-flash", "hello", 200));
    }

    @Test
    void generate_differsWhenPromptDiffers() {
        assertThat(generator.generate("gemini-1.5-flash", "hello", null))
                .isNotEqualTo(generator.generate("gemini-1.5-flash", "goodbye", null));
    }
}
