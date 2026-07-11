package com.example.brocawebsite.learning;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class ScheduleLearningContentParser {

    private static final Pattern VOCABULARY_ITEM = Pattern.compile(
            "(?:^|\\s)\\d{1,2}[.)、]\\s*"
                    + "([A-Za-z][A-Za-z'’-]*(?:\\s+[A-Za-z][A-Za-z'’-]*){0,2})\\s+"
                    + "([\\p{IsHan}][\\p{IsHan}（）()、，,；;：:*＊\\s]{0,32}?)"
                    + "(?=\\s+\\d{1,2}[.)、]|\\s*$)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HOMEWORK_START = Pattern.compile("(?i)\\bHomework\\b|(?:作業|回家功課)\\s*[:：]?");
    private static final Pattern HOMEWORK_END = Pattern.compile(
            "(?i)(?:\\s+\\d{4}-\\d{2}-\\d{2}\\s*\\([^)]*\\)\\s*單字考核"
                    + "|\\s+Basic English Words\\b|\\s+Vocabulary(?:\\s+Quiz)?\\b"
                    + "|\\s+單字考核|\\s+Q\\s*[:：])");
    private static final Pattern QUESTION_AND_ANSWER = Pattern.compile(
            "(?i)Q\\s*[:：]\\s*(.+?)\\s+A\\s*[:：]\\s*(.+?)(?=\\s+Q\\s*[:：]|$)");

    ParsedScheduleLearningContent parse(String classLabel, String rawContent) {
        String content = normalizeInline(rawContent);
        String vocabularyText = extractVocabulary(content);
        String sentencePattern = extractQuestionAndAnswer(content);
        String homeworkNote = extractHomework(content);
        String category = category(content, vocabularyText, sentencePattern);
        String title = title(classLabel, vocabularyText, sentencePattern, homeworkNote);
        return new ParsedScheduleLearningContent(
                category,
                title,
                vocabularyText,
                sentencePattern,
                homeworkNote,
                !vocabularyText.isBlank() || !sentencePattern.isBlank() || !homeworkNote.isBlank());
    }

    private String extractVocabulary(String content) {
        if (content.isBlank()) return "";

        Set<String> seen = new LinkedHashSet<>();
        StringBuilder result = new StringBuilder();
        Matcher matcher = VOCABULARY_ITEM.matcher(content);
        while (matcher.find() && seen.size() < 60) {
            String word = matcher.group(1).trim();
            String meaning = matcher.group(2)
                    .replaceAll("[\\s*＊,，、;；]+$", "")
                    .trim();
            String key = word.toLowerCase(Locale.ROOT);
            if (word.isBlank() || meaning.isBlank() || !seen.add(key)) continue;
            if (!result.isEmpty()) result.append('\n');
            result.append(word).append('\t').append(meaning);
        }
        return result.toString();
    }

    private String extractHomework(String content) {
        if (content.isBlank()) return "";
        Matcher start = HOMEWORK_START.matcher(content);
        if (!start.find()) return "";

        String segment = content.substring(start.end()).trim();
        Matcher end = HOMEWORK_END.matcher(segment);
        if (end.find()) segment = segment.substring(0, end.start()).trim();
        if (segment.isBlank()) return "";

        String formatted = segment
                .replaceAll("\\s*([❶❷❸❹❺❻❼❽❾❿])\\s*", "\n$1 ")
                .replaceAll("\\s+[-•]\\s+", "\n- ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return limit(formatted, 1600);
    }

    private String extractQuestionAndAnswer(String content) {
        if (content.isBlank()) return "";
        Matcher matcher = QUESTION_AND_ANSWER.matcher(content);
        StringBuilder result = new StringBuilder();
        int count = 0;
        while (matcher.find() && count < 6) {
            String question = matcher.group(1).trim();
            String answer = matcher.group(2).trim();
            if (question.isBlank() || answer.isBlank()) continue;
            if (!result.isEmpty()) result.append("\n\n");
            result.append("Q: ").append(question).append("\nA: ").append(answer);
            count++;
        }
        return limit(result.toString(), 1600);
    }

    private String category(String content, String vocabularyText, String sentencePattern) {
        if (!vocabularyText.isBlank()) return "VOCABULARY";
        String lower = content.toLowerCase(Locale.ROOT);
        if (!sentencePattern.isBlank() || lower.contains("grammar") || content.contains("文法")) return "GRAMMAR";
        if (lower.contains("phonics") || lower.contains("sound")) return "PHONICS";
        if (lower.contains("reading") || lower.contains("reader") || lower.contains("comprehension")) return "READING";
        if (lower.contains("quiz") || lower.contains("exam") || lower.contains("test") || content.contains("測驗")) return "EXAM";
        return "GENERAL";
    }

    private String title(String classLabel, String vocabularyText, String sentencePattern, String homeworkNote) {
        String prefix = classLabel == null || classLabel.isBlank() ? "今日" : classLabel.trim() + " ";
        if (!vocabularyText.isBlank()) return prefix + "今日單字與複習";
        if (!sentencePattern.isBlank()) return prefix + "今日句型與複習";
        if (!homeworkNote.isBlank()) return prefix + "今日作業提醒";
        return prefix + "今日學習重點";
    }

    private String normalizeInline(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim() + "...";
    }

    record ParsedScheduleLearningContent(
            String category,
            String title,
            String vocabularyText,
            String sentencePattern,
            String homeworkNote,
            boolean hasPublicContent
    ) {
    }
}
