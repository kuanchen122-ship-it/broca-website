package com.example.brocawebsite.syllabus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class SyllabusWorkbookParser {

    private static final List<Integer> DATE_COLUMNS = List.of(1, 3, 5, 7, 9, 11);
    private static final int CLASS_LABEL_COLUMN = 0;
    private static final int READING_FIRST_DATA_ROW = 3;
    private static final int READING_CHINESE_NAME_COLUMN = 1;
    private static final int READING_ENGLISH_NAME_COLUMN = 2;
    private static final int READING_SCHOOL_COLUMN = 3;
    private static final int READING_GRADE_COLUMN = 4;
    private static final int READING_CLASS_COLUMN = 5;
    private static final int WARNING_LIMIT = 30;
    private static final int HEADER_TEXT_LIMIT = 32;
    private static final String DEFAULT_SHEET_NAME = "Daily Syllabus 2025-2026";
    private static final String READING_ASSIGNMENT_SHEET_NAME = "Reading Assignment";
    private static final List<String> ROSTER_SHEET_NAME_HINTS = List.of(
            READING_ASSIGNMENT_SHEET_NAME,
            "Check Record"
    );

    private static final Pattern FULL_DATE_PATTERN = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})");
    private static final Pattern SHORT_DATE_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})(?!\\d)");
    private static final Pattern SCHOOL_YEAR_PATTERN = Pattern.compile("(20\\d{2})\\s*-\\s*(20\\d{2})");
    private static final Pattern PAREN_TEACHER_PATTERN = Pattern.compile("\\(([^)]{2,60})\\)");
    private static final Pattern TIME_TEACHER_PATTERN = Pattern.compile("(?:^|\\s)\\d{3,4}\\s*-\\s*\\d{3,4}\\s+(主任|[A-Za-z][A-Za-z.'-]{1,24})(?=\\s|$)");
    private static final Pattern TEACHER_TOKEN_PATTERN = Pattern.compile("^(主任|[A-Za-z][A-Za-z.'-]{1,24})");
    private static final Pattern ROSTER_CLASS_CODE_PATTERN = Pattern.compile("^(?:[A-Z][0-9][A-Z]|GEPT[0-9A-Z-]*)$");
    private static final Map<String, String> TEACHER_NAME_CANONICAL = Map.ofEntries(
            Map.entry("daphne", "Daphne"),
            Map.entry("dapnhe", "Daphne"),
            Map.entry("daphane", "Daphne"),
            Map.entry("daphnei", "Daphne"),
            Map.entry("joseph", "Joseph"),
            Map.entry("josheph", "Joseph"),
            Map.entry("jpseph", "Joseph"),
            Map.entry("kelly", "Kelly"),
            Map.entry("vanessa", "Vanessa"),
            Map.entry("dhey", "Dhey"),
            Map.entry("naomi", "Naomi"),
            Map.entry("naom", "Naomi"),
            Map.entry("neomi", "Naomi"),
            Map.entry("noami", "Naomi"),
            Map.entry("主任", "主任")
    );

    ParsedSyllabusWorkbook parse(MultipartFile file, String requestedSheetName) throws IOException {
        return parse(file, requestedSheetName, true);
    }

    ParsedSyllabusWorkbook parse(MultipartFile file, String requestedSheetName, boolean includeReadingAssignmentRoster) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.TAIWAN);

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findSheet(workbook, requestedSheetName);
            int inferredStartYear = inferStartYear(sheet.getSheetName());
            List<ParsedLessonPlan> lessons = new ArrayList<>();
            List<ParsedStudentEnrollment> studentEnrollments = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int[] warningCount = {0};

            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (!looksLikeDateHeader(row, formatter, inferredStartYear)) {
                    continue;
                }

                DateHeader dateHeader = readDateHeader(row, formatter, inferredStartYear);
                int nextHeaderRow = findNextDateHeader(sheet, rowIndex + 2, formatter, inferredStartYear);

                for (int classRowIndex = rowIndex + 2; classRowIndex < nextHeaderRow; classRowIndex++) {
                    Row classRow = sheet.getRow(classRowIndex);
                    if (classRow == null) {
                        continue;
                    }

                    String classLabel = clean(formatCell(classRow.getCell(CLASS_LABEL_COLUMN), formatter));
                    if (classLabel.isBlank()) {
                        continue;
                    }
                    if (isNonClassLabel(classLabel)) {
                        continue;
                    }

                    for (Integer dateColumn : DATE_COLUMNS) {
                        String content = cleanContent(formatCell(classRow.getCell(dateColumn), formatter));
                        if (content.isBlank()) {
                            continue;
                        }

                        LocalDate lessonDate = parseFullDateText(content)
                                .or(() -> dateHeader.dateByColumn(dateColumn))
                                .orElse(null);
                        if (lessonDate == null) {
                            addWarning(warnings, warningCount, "第 " + (classRowIndex + 1) + " 列 " + columnLabel(dateColumn)
                                    + " 欄有內容，但上方日期無法辨識，已略過。");
                            continue;
                        }

                        int approvalColumn = dateColumn + 1;
                        String approvalValue = clean(formatCell(classRow.getCell(approvalColumn), formatter));
                        String approvalStatus = mapApprovalStatus(approvalValue);
                        if ("NEEDS_REVIEW".equals(approvalStatus)) {
                            addWarning(warnings, warningCount, "第 " + (classRowIndex + 1) + " 列 "
                                    + columnLabel(approvalColumn) + " 欄狀態為「" + approvalValue
                                    + "」，不是 OK 或空白，已標記為需確認。");
                        }

                        lessons.add(new ParsedLessonPlan(
                                lessonDate,
                                classLabel,
                                extractTeacherNames(content),
                                content,
                                approvalStatus,
                                approvalValue,
                                sheet.getSheetName(),
                                classRowIndex + 1,
                                columnLabel(dateColumn)
                        ));
                    }
                }
            }

            if (lessons.isEmpty()) {
                addWarning(warnings, warningCount, "沒有找到可匯入的課程進度，請確認工作表格式是否仍為 Daily Syllabus。");
            }

            if (includeReadingAssignmentRoster) {
                studentEnrollments.addAll(readReadingAssignmentRoster(workbook, formatter, warnings, warningCount));
            }

            return new ParsedSyllabusWorkbook(sheet.getSheetName(), List.copyOf(lessons),
                    List.copyOf(studentEnrollments), warningCount[0],
                    List.copyOf(warnings));
        }
    }

    private Sheet findSheet(Workbook workbook, String requestedSheetName) {
        String sheetName = clean(requestedSheetName);
        if (sheetName.isBlank()) {
            sheetName = DEFAULT_SHEET_NAME;
        }

        Sheet requestedSheet = findSheetByNameHint(workbook, sheetName);
        if (requestedSheet != null) {
            return requestedSheet;
        }

        Sheet defaultSheet = findSheetByNameHint(workbook, DEFAULT_SHEET_NAME);
        if (defaultSheet != null) {
            return defaultSheet;
        }

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getSheetName().toLowerCase(Locale.ROOT).contains("daily syllabus")) {
                return sheet;
            }
        }

        throw new IllegalArgumentException("找不到 Daily Syllabus 工作表。");
    }

    private Sheet findSheetByNameHint(Workbook workbook, String sheetNameHint) {
        String normalizedHint = normalizeSheetName(sheetNameHint);
        if (normalizedHint.isBlank()) {
            return null;
        }

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (normalizeSheetName(sheet.getSheetName()).equals(normalizedHint)) {
                return sheet;
            }
        }

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (normalizeSheetName(sheet.getSheetName()).contains(normalizedHint)) {
                return sheet;
            }
        }

        return null;
    }

    private String normalizeSheetName(String value) {
        return clean(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean looksLikeDateHeader(Row row, DataFormatter formatter, int inferredStartYear) {
        if (row == null) {
            return false;
        }

        long dateCount = DATE_COLUMNS.stream()
                .map(column -> readHeaderDateCell(row.getCell(column), formatter, inferredStartYear))
                .filter(Optional::isPresent)
                .count();

        return dateCount >= 2;
    }

    private DateHeader readDateHeader(Row row, DataFormatter formatter, int inferredStartYear) {
        List<DateColumn> dates = new ArrayList<>();
        for (Integer column : DATE_COLUMNS) {
            readHeaderDateCell(row.getCell(column), formatter, inferredStartYear)
                    .ifPresent(date -> dates.add(new DateColumn(column, date)));
        }
        return new DateHeader(dates);
    }

    private int findNextDateHeader(Sheet sheet, int startRow, DataFormatter formatter, int inferredStartYear) {
        for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            if (looksLikeDateHeader(sheet.getRow(rowIndex), formatter, inferredStartYear)) {
                return rowIndex;
            }
        }
        return sheet.getLastRowNum() + 1;
    }

    private Optional<LocalDate> readHeaderDateCell(Cell cell, DataFormatter formatter, int inferredStartYear) {
        if (cell == null) {
            return Optional.empty();
        }

        if (isNumericDateCell(cell)) {
            try {
                return Optional.of(DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }

        String text = clean(formatCell(cell, formatter));
        if (text.length() > HEADER_TEXT_LIMIT || text.toLowerCase(Locale.ROOT).contains("curriculum")) {
            return Optional.empty();
        }
        return parseDateText(text, inferredStartYear);
    }

    private boolean isNumericDateCell(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            return DateUtil.isCellDateFormatted(cell);
        }

        if (type == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
            return DateUtil.isCellDateFormatted(cell);
        }

        return false;
    }

    private Optional<LocalDate> parseDateText(String rawText, int inferredStartYear) {
        if (rawText.isBlank()) {
            return Optional.empty();
        }

        String text = rawText
                .replace("年", "/")
                .replace("月", "/")
                .replace("日", "")
                .replace(".", "/");

        Matcher fullDate = FULL_DATE_PATTERN.matcher(text);
        if (fullDate.find()) {
            return parseDate(fullDate.group(1), fullDate.group(2), fullDate.group(3));
        }

        Matcher shortDate = SHORT_DATE_PATTERN.matcher(text);
        if (shortDate.find() && inferredStartYear > 0) {
            int month = Integer.parseInt(shortDate.group(1));
            int year = month >= 7 ? inferredStartYear : inferredStartYear + 1;
            return parseDate(String.valueOf(year), shortDate.group(1), shortDate.group(2));
        }

        return Optional.empty();
    }

    private Optional<LocalDate> parseFullDateText(String rawText) {
        if (rawText.isBlank()) {
            return Optional.empty();
        }

        String text = rawText
                .replace("年", "/")
                .replace("月", "/")
                .replace("日", "")
                .replace(".", "/");

        Matcher fullDate = FULL_DATE_PATTERN.matcher(text);
        if (fullDate.find()) {
            return parseDate(fullDate.group(1), fullDate.group(2), fullDate.group(3));
        }

        return Optional.empty();
    }

    private Optional<LocalDate> parseDate(String year, String month, String day) {
        try {
            String normalized = "%s-%02d-%02d".formatted(year, Integer.parseInt(month), Integer.parseInt(day));
            return Optional.of(LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException | NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private int inferStartYear(String sheetName) {
        Matcher matcher = SCHOOL_YEAR_PATTERN.matcher(sheetName);
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String formatCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }

    private String cleanContent(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceAll("[ \\t]+", " "))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String mapApprovalStatus(String approvalValue) {
        if (approvalValue.isBlank()) {
            return "TEACHER_DRAFT";
        }
        if ("OK".equalsIgnoreCase(approvalValue)) {
            return "DIRECTOR_APPROVED";
        }
        return "NEEDS_REVIEW";
    }

    private boolean isNonClassLabel(String classLabel) {
        String normalized = classLabel.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("DAY")
                || normalized.equals("DATE")
                || normalized.equals("WEEK")
                || normalized.equals("CLASS")
                || normalized.equals("CLASS NAME")
                || normalized.equals("MON")
                || normalized.equals("TUE")
                || normalized.equals("WED")
                || normalized.equals("THU")
                || normalized.equals("FRI")
                || normalized.equals("SAT")
                || normalized.equals("SUN")
                || normalized.equals("班級")
                || normalized.equals("日期")
                || normalized.equals("星期");
    }

    private List<ParsedStudentEnrollment> readReadingAssignmentRoster(Workbook workbook, DataFormatter formatter,
                                                                     List<String> warnings, int[] warningCount) {
        Sheet sheet = findRosterSheet(workbook);
        if (sheet == null) {
            addWarning(warnings, warningCount, "找不到 Reading Assignment / Check Record 學生名單工作表，已略過學生名單同步。");
            return List.of();
        }

        List<ParsedStudentEnrollment> enrollments = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int rowIndex = READING_FIRST_DATA_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String chineseName = clean(formatCell(row.getCell(READING_CHINESE_NAME_COLUMN), formatter));
            String englishName = clean(formatCell(row.getCell(READING_ENGLISH_NAME_COLUMN), formatter));
            String school = clean(formatCell(row.getCell(READING_SCHOOL_COLUMN), formatter));
            String gradeLevel = clean(formatCell(row.getCell(READING_GRADE_COLUMN), formatter));
            String classCode = normalizeRosterClassCode(formatCell(row.getCell(READING_CLASS_COLUMN), formatter));

            if (isRosterHeaderRow(chineseName, englishName, classCode)) {
                continue;
            }

            if (chineseName.isBlank() || classCode.isBlank()) {
                continue;
            }
            if (!ROSTER_CLASS_CODE_PATTERN.matcher(classCode).matches()) {
                continue;
            }

            String studentNo = readingAssignmentStudentNo(chineseName, englishName, school, gradeLevel);
            String enrollmentKey = studentNo + "|" + classCode;
            if (!seen.add(enrollmentKey)) {
                continue;
            }

            enrollments.add(new ParsedStudentEnrollment(studentNo, chineseName, englishName, school, gradeLevel, classCode));
        }

        if (enrollments.isEmpty()) {
            addWarning(warnings, warningCount, sheet.getSheetName() + " 有工作表，但沒有解析到學生/班級名單。");
        }
        return enrollments;
    }

    private Sheet findRosterSheet(Workbook workbook) {
        for (String sheetName : ROSTER_SHEET_NAME_HINTS) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet != null) {
                return sheet;
            }
        }

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String normalizedSheetName = sheet.getSheetName().toLowerCase(Locale.ROOT);
            if (ROSTER_SHEET_NAME_HINTS.stream()
                    .map(hint -> hint.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedSheetName::contains)) {
                return sheet;
            }
        }

        return null;
    }

    private boolean isRosterHeaderRow(String chineseName, String englishName, String classCode) {
        String normalizedChineseName = chineseName.toLowerCase(Locale.ROOT);
        String normalizedEnglishName = englishName.toLowerCase(Locale.ROOT);
        String normalizedClassCode = classCode.toLowerCase(Locale.ROOT);
        return normalizedChineseName.contains("chinese")
                || normalizedChineseName.contains("姓名")
                || normalizedEnglishName.contains("english")
                || normalizedClassCode.equals("class")
                || normalizedClassCode.equals("班級");
    }

    private String normalizeRosterClassCode(String rawClassCode) {
        return clean(rawClassCode)
                .replaceAll("\\s+", "")
                .replaceAll("(請假|缺席|補課|停課)$", "")
                .toUpperCase(Locale.ROOT);
    }

    private String readingAssignmentStudentNo(String chineseName, String englishName, String school, String gradeLevel) {
        String rawKey = "%s|%s|%s".formatted(chineseName, englishName, school);
        String stableKey = UUID.nameUUIDFromBytes(rawKey.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
        return "RA-" + stableKey;
    }

    private String extractTeacherNames(String content) {
        Set<String> names = new LinkedHashSet<>();

        Matcher parenthesized = PAREN_TEACHER_PATTERN.matcher(content);
        while (parenthesized.find()) {
            teacherNameFromText(parenthesized.group(1)).ifPresent(names::add);
        }

        Matcher timedTeacher = TIME_TEACHER_PATTERN.matcher(content);
        while (timedTeacher.find()) {
            teacherNameFromText(timedTeacher.group(1)).ifPresent(names::add);
        }

        return String.join("、", names);
    }

    private Optional<String> teacherNameFromText(String rawText) {
        String text = clean(rawText).replaceAll("\\d{3,4}\\s*-\\s*\\d{3,4}", "").trim();
        if (text.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = TEACHER_TOKEN_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String candidate = normalizeTeacherName(matcher.group(1));
        return candidate.isBlank() ? Optional.empty() : Optional.of(candidate);
    }

    private String normalizeTeacherName(String teacherName) {
        String normalized = teacherName.trim().replaceAll("\\s+", " ");
        return TEACHER_NAME_CANONICAL.getOrDefault(normalized.toLowerCase(Locale.ROOT), "");
    }

    private void addWarning(List<String> warnings, int[] warningCount, String warning) {
        warningCount[0]++;
        if (warnings.size() < WARNING_LIMIT) {
            warnings.add(warning);
        }
    }

    private String columnLabel(int zeroBasedColumn) {
        int column = zeroBasedColumn + 1;
        StringBuilder label = new StringBuilder();
        while (column > 0) {
            int remainder = (column - 1) % 26;
            label.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }
        return label.toString();
    }

    private record DateHeader(List<DateColumn> dateColumns) {
        Optional<LocalDate> dateByColumn(int column) {
            return dateColumns.stream()
                    .filter(dateColumn -> dateColumn.column() == column)
                    .map(DateColumn::date)
                    .findFirst()
                    .or(() -> dateColumns.stream()
                            .filter(dateColumn -> dateColumn.column() > column && dateColumn.column() <= column + 2)
                            .map(DateColumn::date)
                            .findFirst())
                    .or(() -> dateColumns.stream()
                            .filter(dateColumn -> dateColumn.column() < column && dateColumn.column() >= column - 2)
                            .reduce((first, second) -> second)
                            .map(DateColumn::date))
                    .or(() -> dateColumns.stream()
                            .filter(dateColumn -> dateColumn.column() < column)
                            .reduce((first, second) -> second)
                            .map(DateColumn::date));
        }
    }

    private record DateColumn(int column, LocalDate date) {
    }
}
