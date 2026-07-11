package com.example.brocawebsite.student;

import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/parent-contacts")
class ParentContactController {

    private final ParentContactService parentContactService;
    private final ParentContactImportService parentContactImportService;

    ParentContactController(ParentContactService parentContactService, ParentContactImportService parentContactImportService) {
        this.parentContactService = parentContactService;
        this.parentContactImportService = parentContactImportService;
    }

    @GetMapping
    ParentContactResponse contacts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String contactStatus,
            @RequestParam(required = false) Boolean includeInactive
    ) {
        return parentContactService.contacts(q, classId, contactStatus, includeInactive);
    }

    @PostMapping("/{studentId}")
    ParentContactRow updateContact(@PathVariable Long studentId, @RequestBody ParentContactUpdateRequest request) {
        return parentContactService.updateContact(studentId, request);
    }

    @GetMapping("/template")
    ResponseEntity<byte[]> template() {
        String filename = "broca-parent-contacts-template-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(parentContactImportService.templateWorkbook());
    }

    @PostMapping(value = "/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ParentContactImportPreviewResponse importPreview(@RequestParam("file") MultipartFile file) {
        return parentContactImportService.preview(file);
    }

    @PostMapping(value = "/import-apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ParentContactImportPreviewResponse importApply(@RequestParam("file") MultipartFile file) {
        return parentContactImportService.apply(file);
    }
}
