package com.example.brocawebsite.syllabus;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/syllabus-import")
class SyllabusImportController {

    private final SyllabusImportService importService;

    SyllabusImportController(SyllabusImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SyllabusImportResponse importSyllabus(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "Daily Syllabus 2025-2026") String sheetName,
            @RequestParam(defaultValue = "true") boolean syncRoster,
            Authentication authentication
    ) {
        return importService.importFile(file, sheetName, syncRoster, authentication);
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SyllabusImportPreviewResponse previewSyllabus(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "Daily Syllabus 2025-2026") String sheetName,
            @RequestParam(defaultValue = "true") boolean syncRoster
    ) {
        return importService.previewFile(file, sheetName, syncRoster);
    }

    @GetMapping("/latest")
    SyllabusImportResponse latestImport() {
        return importService.latestImport();
    }

    @GetMapping("/history")
    SyllabusImportHistoryResponse importHistory() {
        return importService.importHistory();
    }

    @PostMapping("/{batchId}/restore")
    SyllabusImportRestoreResponse restoreImport(@PathVariable Long batchId, Authentication authentication) {
        return importService.restoreBatch(batchId, authentication);
    }

    @DeleteMapping("/{batchId}")
    SyllabusImportDeleteResponse deleteImport(@PathVariable Long batchId, Authentication authentication) {
        return importService.deleteBatch(batchId, authentication);
    }

    @GetMapping("/schedule")
    ScheduleDayResponse scheduleForDay(@RequestParam(required = false) String date) {
        return importService.scheduleForDay(date);
    }
}
