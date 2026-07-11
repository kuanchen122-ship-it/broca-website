package com.example.brocawebsite.learning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScheduleLearningContentParserTests {

    private final ScheduleLearningContentParser parser = new ScheduleLearningContentParser();

    @Test
    void extractsApprovedVocabularyAndStopsBeforeTheWordList() {
        String content = """
                Daily Curriculum • Oxford Discover 1 review
                Homework ❶ Listen & Review: Oxford Discover SB U4 ❷ Listen & Read: The Lazy Grasshopper
                2026-07-11 (六) 單字考核 Basic English Words 中級單字
                01. lesson 課 02. question 問題 03. story 故事 04. test 考試；測驗
                05. study 讀書；學習* 06. swing 盪鞦韆* 07. front 前面 08. back 後面的
                09. left 左邊 10. right 右邊
                """;

        var parsed = parser.parse("C1A", content);

        assertThat(parsed.hasPublicContent()).isTrue();
        assertThat(parsed.category()).isEqualTo("VOCABULARY");
        assertThat(parsed.vocabularyText().lines()).hasSize(10);
        assertThat(parsed.vocabularyText()).contains("study\t讀書；學習", "swing\t盪鞦韆", "right\t右邊");
        assertThat(parsed.homeworkNote()).contains("Listen & Review", "Listen & Read");
        assertThat(parsed.homeworkNote()).doesNotContain("Basic English Words", "lesson 課");
    }

    @Test
    void doesNotInventPublicContentFromCurriculumOnly() {
        var parsed = parser.parse("C4A", "Daily Curriculum • 國中文法複習講義 - Unit 7 pp. 48-49");

        assertThat(parsed.hasPublicContent()).isFalse();
        assertThat(parsed.vocabularyText()).isBlank();
        assertThat(parsed.homeworkNote()).isBlank();
    }
}
