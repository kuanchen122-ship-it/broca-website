package com.example.brocawebsite.student;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class ParentContactImportService {

    private static final List<TemplateColumn> TEMPLATE_COLUMNS = List.of(
            new TemplateColumn("studentNo", "學生編號"),
            new TemplateColumn("chineseName", "中文姓名"),
            new TemplateColumn("englishName", "英文名"),
            new TemplateColumn("school", "學校"),
            new TemplateColumn("gradeLevel", "年級"),
            new TemplateColumn("classCodes", "班級"),
            new TemplateColumn("parentName", "家長姓名"),
            new TemplateColumn("parentPhone", "家長電話"),
            new TemplateColumn("parentLineId", "LINE ID"),
            new TemplateColumn("pickupNote", "接送備註"),
            new TemplateColumn("emergencyContactName", "緊急聯絡人"),
            new TemplateColumn("emergencyContactPhone", "緊急聯絡電話")
    );

    private static final Map<String, String> HEADER_ALIASES = headerAliases();

    private final JdbcTemplate jdbcTemplate;

    ParentContactImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    byte[] templateWorkbook() {
        List<ContactImportBase> students = listStudents();
        Map<Long, String> classCodes = classCodesByStudent();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("家長資料匯入");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle lockedStyle = lockedTextStyle(workbook);
            CellStyle editableStyle = editableTextStyle(workbook);

            Row header = sheet.createRow(0);
            for (int index = 0; index < TEMPLATE_COLUMNS.size(); index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(TEMPLATE_COLUMNS.get(index).label());
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ContactImportBase student : students) {
                Row row = sheet.createRow(rowIndex++);
                setCell(row, 0, student.studentNo(), lockedStyle);
                setCell(row, 1, student.chineseName(), lockedStyle);
                setCell(row, 2, student.englishName(), lockedStyle);
                setCell(row, 3, student.school(), lockedStyle);
                setCell(row, 4, student.gradeLevel(), lockedStyle);
                setCell(row, 5, classCodes.getOrDefault(student.id(), ""), lockedStyle);
                setCell(row, 6, student.parentName(), editableStyle);
                setCell(row, 7, student.parentPhone(), editableStyle);
                setCell(row, 8, student.parentLineId(), editableStyle);
                setCell(row, 9, student.pickupNote(), editableStyle);
                setCell(row, 10, student.emergencyContactName(), editableStyle);
                setCell(row, 11, student.emergencyContactPhone(), editableStyle);
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, TEMPLATE_COLUMNS.size() - 1));
            for (int index = 0; index < TEMPLATE_COLUMNS.size(); index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.max(sheet.getColumnWidth(index), 4200));
            }
            sheet.setColumnWidth(9, 7200);

            Sheet guide = workbook.createSheet("填寫說明");
            guide.createRow(0).createCell(0).setCellValue("布魯卡美語家長資料匯入說明");
            guide.createRow(1).createCell(0).setCellValue("1. 建議只填右側白底欄位：家長姓名、家長電話、LINE ID、接送備註、緊急聯絡人、緊急聯絡電話。");
            guide.createRow(2).createCell(0).setCellValue("2. 學生編號、姓名、班級請盡量不要改，系統會用它們比對學生。");
            guide.createRow(3).createCell(0).setCellValue("3. 空白欄位不會覆蓋系統既有資料。若要清空資料，請先到後台單筆編輯。");
            guide.createRow(4).createCell(0).setCellValue("4. 電話欄位已設定為文字格式，請保留 0 開頭。");
            guide.autoSizeColumn(0);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "無法產生家長資料匯入模板。", exception);
        }
    }

    ParentContactImportPreviewResponse preview(MultipartFile file) {
        return process(file, false);
    }

    ParentContactImportPreviewResponse apply(MultipartFile file) {
        return process(file, true);
    }

    private ParentContactImportPreviewResponse process(MultipartFile file, boolean apply) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇要匯入的 Excel 或 CSV 檔案。");
        }

        List<ImportedContactRow> importedRows = parseFile(file);
        StudentIndexes indexes = buildIndexes(listStudents());
        Set<Long> seenStudents = new HashSet<>();
        List<ParentContactImportPreviewRow> previewRows = new ArrayList<>();
        int appliedRows = 0;

        for (ImportedContactRow imported : importedRows) {
            PreviewDraft draft = buildPreview(imported, indexes, seenStudents);
            if (apply && draft.willUpdate()) {
                applyRow(draft.base(), imported);
                appliedRows++;
            }
            previewRows.add(draft.toRow());
        }

        return new ParentContactImportPreviewResponse(
                apply,
                previewRows.size(),
                countRows(previewRows, row -> row.matchedStudentId() != null),
                countRows(previewRows, ParentContactImportPreviewRow::willUpdate),
                countRows(previewRows, row -> "UNCHANGED".equals(row.status())),
                countRows(previewRows, row -> "NOT_FOUND".equals(row.status())),
                countRows(previewRows, row -> "DUPLICATE_MATCH".equals(row.status()) || "DUPLICATE_ROW".equals(row.status())),
                countRows(previewRows, row -> "INVALID".equals(row.status())),
                appliedRows,
                summary(),
                previewRows
        );
    }

    private PreviewDraft buildPreview(ImportedContactRow imported, StudentIndexes indexes, Set<Long> seenStudents) {
        MatchResult match = matchStudent(imported, indexes);
        if (match.status() != MatchStatus.MATCHED) {
            return new PreviewDraft(imported, null, match.status().name(), match.message(), false, null, match.method(), List.of());
        }

        ContactImportBase base = match.base();
        if (!seenStudents.add(base.id())) {
            return new PreviewDraft(imported, base, "DUPLICATE_ROW", "同一位學生在匯入檔出現超過一次，請先整理檔案。", false, base.id(), match.method(), List.of());
        }

        List<String> changes = changes(base, imported);
        if (changes.isEmpty()) {
            return new PreviewDraft(imported, base, "UNCHANGED", "沒有需要更新的欄位。", false, base.id(), match.method(), changes);
        }

        return new PreviewDraft(imported, base, "READY_TO_UPDATE", "可更新：" + String.join("、", changes), true, base.id(), match.method(), changes);
    }

    private List<String> changes(ContactImportBase base, ImportedContactRow row) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "家長姓名", row.parentName(), base.parentName());
        addChange(changes, "家長電話", row.parentPhone(), normalizePhone(base.parentPhone()));
        addChange(changes, "LINE ID", row.parentLineId(), base.parentLineId());
        addChange(changes, "接送備註", row.pickupNote(), base.pickupNote());
        addChange(changes, "緊急聯絡人", row.emergencyContactName(), base.emergencyContactName());
        addChange(changes, "緊急聯絡電話", row.emergencyContactPhone(), normalizePhone(base.emergencyContactPhone()));
        return changes;
    }

    private void addChange(List<String> changes, String label, String importedValue, String currentValue) {
        if (isBlank(importedValue)) {
            return;
        }
        if (!Objects.equals(normalizeCompare(importedValue), normalizeCompare(currentValue))) {
            changes.add(label);
        }
    }

    private void applyRow(ContactImportBase base, ImportedContactRow row) {
        jdbcTemplate.update("""
                        update students
                        set parent_name = ?,
                            parent_phone = ?,
                            parent_line_id = ?,
                            pickup_note = ?,
                            emergency_contact_name = ?,
                            emergency_contact_phone = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                keepExisting(row.parentName(), base.parentName()),
                keepExisting(row.parentPhone(), base.parentPhone()),
                keepExisting(row.parentLineId(), base.parentLineId()),
                keepExisting(row.pickupNote(), base.pickupNote()),
                keepExisting(row.emergencyContactName(), base.emergencyContactName()),
                keepExisting(row.emergencyContactPhone(), base.emergencyContactPhone()),
                base.id());
    }

    private MatchResult matchStudent(ImportedContactRow row, StudentIndexes indexes) {
        if (!isBlank(row.studentNo())) {
            ContactImportBase byStudentNo = indexes.byStudentNo().get(key(row.studentNo()));
            if (byStudentNo != null) {
                return MatchResult.matched(byStudentNo, "學生編號");
            }
            return MatchResult.notFound("找不到學生編號：" + row.studentNo());
        }

        List<ContactImportBase> candidates = List.of();
        String method = "";
        if (!isBlank(row.chineseName()) && !isBlank(row.englishName()) && !isBlank(row.school())) {
            candidates = indexes.byFullIdentity().getOrDefault(identityKey(row.chineseName(), row.englishName(), row.school()), List.of());
            method = "中文姓名 + 英文名 + 學校";
        } else if (!isBlank(row.chineseName()) && !isBlank(row.englishName())) {
            candidates = indexes.byNamePair().getOrDefault(identityKey(row.chineseName(), row.englishName(), ""), List.of());
            method = "中文姓名 + 英文名";
        } else if (!isBlank(row.chineseName())) {
            candidates = indexes.byChineseName().getOrDefault(key(row.chineseName()), List.of());
            method = "中文姓名";
        }

        if (candidates.isEmpty()) {
            return MatchResult.notFound("找不到可比對的學生，請保留學生編號或完整姓名。");
        }
        if (candidates.size() > 1) {
            return MatchResult.duplicate("比對到多位學生，請補上學生編號或學校避免誤寫。", method);
        }
        return MatchResult.matched(candidates.get(0), method);
    }

    private StudentIndexes buildIndexes(List<ContactImportBase> students) {
        Map<String, ContactImportBase> byStudentNo = new HashMap<>();
        Map<String, List<ContactImportBase>> byFullIdentity = new HashMap<>();
        Map<String, List<ContactImportBase>> byNamePair = new HashMap<>();
        Map<String, List<ContactImportBase>> byChineseName = new HashMap<>();

        for (ContactImportBase student : students) {
            if (!isBlank(student.studentNo())) {
                byStudentNo.put(key(student.studentNo()), student);
            }
            if (!isBlank(student.chineseName()) && !isBlank(student.englishName()) && !isBlank(student.school())) {
                byFullIdentity.computeIfAbsent(identityKey(student.chineseName(), student.englishName(), student.school()), ignored -> new ArrayList<>()).add(student);
            }
            if (!isBlank(student.chineseName()) && !isBlank(student.englishName())) {
                byNamePair.computeIfAbsent(identityKey(student.chineseName(), student.englishName(), ""), ignored -> new ArrayList<>()).add(student);
            }
            if (!isBlank(student.chineseName())) {
                byChineseName.computeIfAbsent(key(student.chineseName()), ignored -> new ArrayList<>()).add(student);
            }
        }
        return new StudentIndexes(byStudentNo, byFullIdentity, byNamePair, byChineseName);
    }

    private List<ImportedContactRow> parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream inputStream = file.getInputStream()) {
            if (filename.endsWith(".csv")) {
                return parseCsv(inputStream);
            }
            return parseWorkbook(inputStream);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "無法讀取匯入檔案，請確認檔案是否存在。", exception);
        }
    }

    private List<ImportedContactRow> parseWorkbook(InputStream inputStream) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.TAIWAN);
            HeaderMap headerMap = findHeader(sheet, formatter);
            List<ImportedContactRow> rows = new ArrayList<>();
            for (int rowIndex = headerMap.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                ImportedContactRow imported = importedFrom(rowIndex + 1, headerMap.headers(), column -> formatter.formatCellValue(row.getCell(column)).trim());
                if (!imported.isCompletelyBlank()) {
                    rows.add(imported);
                }
            }
            return rows;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 解析失敗，請確認格式或改用系統下載的模板。", exception);
        }
    }

    private HeaderMap findHeader(Sheet sheet, DataFormatter formatter) {
        int maxRow = Math.min(sheet.getLastRowNum(), 10);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, Integer> headers = new LinkedHashMap<>();
            int lastCell = Math.max(row.getLastCellNum(), TEMPLATE_COLUMNS.size());
            for (int column = 0; column < lastCell; column++) {
                String canonical = canonicalHeader(formatter.formatCellValue(row.getCell(column)));
                if (canonical != null) {
                    headers.putIfAbsent(canonical, column);
                }
            }
            if (headers.containsKey("studentNo") || headers.containsKey("chineseName")) {
                return new HeaderMap(rowIndex, headers);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到標題列，請使用系統下載的匯入模板。");
    }

    private List<ImportedContactRow> parseCsv(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            if (lines.isEmpty()) {
                return List.of();
            }
            List<String> headerCells = parseCsvLine(stripBom(lines.get(0)));
            Map<String, Integer> headers = new LinkedHashMap<>();
            for (int column = 0; column < headerCells.size(); column++) {
                String canonical = canonicalHeader(headerCells.get(column));
                if (canonical != null) {
                    headers.putIfAbsent(canonical, column);
                }
            }
            if (!headers.containsKey("studentNo") && !headers.containsKey("chineseName")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 標題需要包含學生編號或中文姓名。");
            }

            List<ImportedContactRow> rows = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                List<String> cells = parseCsvLine(lines.get(index));
                ImportedContactRow imported = importedFrom(index + 1, headers, column -> column < cells.size() ? cells.get(column).trim() : "");
                if (!imported.isCompletelyBlank()) {
                    rows.add(imported);
                }
            }
            return rows;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private ImportedContactRow importedFrom(int rowNumber, Map<String, Integer> headers, CellReader reader) {
        return new ImportedContactRow(
                rowNumber,
                value(headers, "studentNo", reader),
                value(headers, "chineseName", reader),
                value(headers, "englishName", reader),
                value(headers, "school", reader),
                value(headers, "gradeLevel", reader),
                value(headers, "classCodes", reader),
                value(headers, "parentName", reader),
                normalizePhone(value(headers, "parentPhone", reader)),
                value(headers, "parentLineId", reader),
                value(headers, "pickupNote", reader),
                value(headers, "emergencyContactName", reader),
                normalizePhone(value(headers, "emergencyContactPhone", reader))
        );
    }

    private String value(Map<String, Integer> headers, String key, CellReader reader) {
        Integer column = headers.get(key);
        return column == null ? "" : blankToEmpty(reader.read(column));
    }

    private String canonicalHeader(String rawHeader) {
        String normalized = normalizeHeader(rawHeader);
        if (normalized.isEmpty()) {
            return null;
        }
        return HEADER_ALIASES.get(normalized);
    }

    private static Map<String, String> headerAliases() {
        Map<String, String> aliases = new HashMap<>();
        alias(aliases, "studentNo", "學生編號", "學生代號", "學號", "studentNo", "student_no", "student id");
        alias(aliases, "chineseName", "中文姓名", "學生中文姓名", "姓名", "chineseName", "chinese name");
        alias(aliases, "englishName", "英文名", "英文姓名", "englishName", "english name");
        alias(aliases, "school", "學校", "school");
        alias(aliases, "gradeLevel", "年級", "grade", "gradeLevel", "grade level");
        alias(aliases, "classCodes", "班級", "班級代號", "class", "classCodes", "class codes");
        alias(aliases, "parentName", "家長姓名", "家長", "parentName", "parent name");
        alias(aliases, "parentPhone", "家長電話", "電話", "手機", "parentPhone", "parent phone");
        alias(aliases, "parentLineId", "LINE ID", "Line ID", "lineId", "parentLineId", "parent_line_id");
        alias(aliases, "pickupNote", "接送備註", "接送", "pickupNote", "pickup note");
        alias(aliases, "emergencyContactName", "緊急聯絡人", "緊急聯絡姓名", "emergencyContact", "emergencyContactName");
        alias(aliases, "emergencyContactPhone", "緊急聯絡電話", "緊急電話", "emergencyPhone", "emergencyContactPhone");
        return Map.copyOf(aliases);
    }

    private static void alias(Map<String, String> aliases, String canonical, String... labels) {
        for (String label : labels) {
            aliases.put(normalizeHeader(label), canonical);
        }
    }

    private static String normalizeHeader(String value) {
        return blankToEmptyStatic(value)
                .replace("\uFEFF", "")
                .replaceAll("[\\s\\u3000:：/／()（）_\\-]", "")
                .toLowerCase(Locale.ROOT);
    }

    private List<ContactImportBase> listStudents() {
        return jdbcTemplate.query("""
                        select id, student_no, chinese_name, english_name, school, grade_level,
                               parent_name, parent_phone, parent_line_id,
                               pickup_note, emergency_contact_name, emergency_contact_phone, active
                        from students
                        order by active desc, grade_level, chinese_name, english_name
                        """,
                (rs, rowNum) -> new ContactImportBase(
                        rs.getLong("id"),
                        rs.getString("student_no"),
                        rs.getString("chinese_name"),
                        rs.getString("english_name"),
                        rs.getString("school"),
                        rs.getString("grade_level"),
                        rs.getString("parent_name"),
                        rs.getString("parent_phone"),
                        rs.getString("parent_line_id"),
                        rs.getString("pickup_note"),
                        rs.getString("emergency_contact_name"),
                        rs.getString("emergency_contact_phone"),
                        rs.getBoolean("active")
                ));
    }

    private Map<Long, String> classCodesByStudent() {
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        select ce.student_id, c.code
                        from class_enrollments ce
                        join classes c on c.id = ce.class_id
                        where ce.active = true
                          and c.status = 'ACTIVE'
                        order by ce.student_id, c.code
                        """,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> grouped.computeIfAbsent(rs.getLong("student_id"), ignored -> new ArrayList<>())
                        .add(rs.getString("code")));

        Map<Long, String> result = new LinkedHashMap<>();
        grouped.forEach((studentId, codes) -> result.put(studentId, String.join("/", codes)));
        return result;
    }

    private ParentContactSummary summary() {
        return new ParentContactSummary(
                count("select count(*) from students where active = true"),
                count("""
                        select count(*) from students
                        where active = true
                          and parent_name is not null and trim(parent_name) <> ''
                          and parent_phone is not null and trim(parent_phone) <> ''
                          and parent_line_id is not null and trim(parent_line_id) <> ''
                        """),
                count("""
                        select count(*) from students
                        where active = true
                          and (parent_name is null or trim(parent_name) = '')
                        """),
                count("""
                        select count(*) from students
                        where active = true
                          and (parent_phone is null or trim(parent_phone) = '')
                        """),
                count("""
                        select count(*) from students
                        where active = true
                          and (parent_line_id is null or trim(parent_line_id) = '')
                        """),
                count("""
                        select count(*) from students
                        where active = true
                          and parent_name is not null and trim(parent_name) <> ''
                          and parent_phone is not null and trim(parent_phone) <> ''
                          and (parent_line_id is null or trim(parent_line_id) = '')
                        """)
        );
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int countRows(List<ParentContactImportPreviewRow> rows, RowPredicate predicate) {
        int count = 0;
        for (ParentContactImportPreviewRow row : rows) {
            if (predicate.test(row)) {
                count++;
            }
        }
        return count;
    }

    private void setCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(blankToEmpty(value));
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle lockedTextStyle(Workbook workbook) {
        CellStyle style = editableTextStyle(workbook);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle editableTextStyle(Workbook workbook) {
        DataFormat format = workbook.createDataFormat();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(format.getFormat("@"));
        return style;
    }

    private String keepExisting(String importedValue, String currentValue) {
        return isBlank(importedValue) ? blankToNull(currentValue) : blankToNull(importedValue);
    }

    private String normalizePhone(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "");
    }

    private String normalizeCompare(String value) {
        return blankToEmpty(value).trim();
    }

    private String identityKey(String chineseName, String englishName, String school) {
        return key(chineseName) + "|" + key(englishName) + "|" + key(school);
    }

    private String key(String value) {
        return blankToEmpty(value).trim().toLowerCase(Locale.ROOT);
    }

    private String stripBom(String value) {
        return value == null ? "" : value.replace("\uFEFF", "");
    }

    private String blankToEmpty(String value) {
        return blankToEmptyStatic(value);
    }

    private static String blankToEmptyStatic(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TemplateColumn(String key, String label) {
    }

    private record HeaderMap(int rowIndex, Map<String, Integer> headers) {
    }

    private record StudentIndexes(
            Map<String, ContactImportBase> byStudentNo,
            Map<String, List<ContactImportBase>> byFullIdentity,
            Map<String, List<ContactImportBase>> byNamePair,
            Map<String, List<ContactImportBase>> byChineseName
    ) {
    }

    private record ContactImportBase(
            Long id,
            String studentNo,
            String chineseName,
            String englishName,
            String school,
            String gradeLevel,
            String parentName,
            String parentPhone,
            String parentLineId,
            String pickupNote,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean active
    ) {
    }

    private record ImportedContactRow(
            int rowNumber,
            String studentNo,
            String chineseName,
            String englishName,
            String school,
            String gradeLevel,
            String classCodes,
            String parentName,
            String parentPhone,
            String parentLineId,
            String pickupNote,
            String emergencyContactName,
            String emergencyContactPhone
    ) {
        boolean isCompletelyBlank() {
            return List.of(studentNo, chineseName, englishName, school, gradeLevel, classCodes,
                            parentName, parentPhone, parentLineId, pickupNote,
                            emergencyContactName, emergencyContactPhone)
                    .stream()
                    .allMatch(value -> value == null || value.isBlank());
        }
    }

    private record PreviewDraft(
            ImportedContactRow imported,
            ContactImportBase base,
            String status,
            String message,
            boolean willUpdate,
            Long matchedStudentId,
            String matchMethod,
            List<String> changes
    ) {
        ParentContactImportPreviewRow toRow() {
            return new ParentContactImportPreviewRow(
                    imported.rowNumber(),
                    status,
                    message,
                    willUpdate,
                    matchedStudentId,
                    base == null ? "" : base.chineseName() + (base.englishName() == null || base.englishName().isBlank() ? "" : " " + base.englishName()),
                    matchMethod,
                    imported.studentNo(),
                    imported.chineseName(),
                    imported.englishName(),
                    imported.school(),
                    imported.gradeLevel(),
                    imported.classCodes(),
                    imported.parentName(),
                    imported.parentPhone(),
                    imported.parentLineId(),
                    imported.pickupNote(),
                    imported.emergencyContactName(),
                    imported.emergencyContactPhone(),
                    changes
            );
        }
    }

    private record MatchResult(MatchStatus status, ContactImportBase base, String message, String method) {
        static MatchResult matched(ContactImportBase base, String method) {
            return new MatchResult(MatchStatus.MATCHED, base, "已比對到學生。", method);
        }

        static MatchResult notFound(String message) {
            return new MatchResult(MatchStatus.NOT_FOUND, null, message, "");
        }

        static MatchResult duplicate(String message, String method) {
            return new MatchResult(MatchStatus.DUPLICATE_MATCH, null, message, method);
        }
    }

    private enum MatchStatus {
        MATCHED,
        NOT_FOUND,
        DUPLICATE_MATCH
    }

    private interface CellReader {
        String read(int column);
    }

    private interface RowPredicate {
        boolean test(ParentContactImportPreviewRow row);
    }
}
