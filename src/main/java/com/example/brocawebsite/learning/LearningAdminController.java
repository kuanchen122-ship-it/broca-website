package com.example.brocawebsite.learning;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/learning-posts")
class LearningAdminController {

    private final LearningPostService learningPostService;

    LearningAdminController(LearningPostService learningPostService) {
        this.learningPostService = learningPostService;
    }

    @GetMapping
    LearningAdminResponse posts(@RequestParam(required = false) String date,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String classLabel) {
        return learningPostService.adminPosts(date, status, classLabel);
    }

    @GetMapping("/class-options")
    List<String> classOptions() {
        return learningPostService.classOptions();
    }

    @PostMapping
    LearningAdminPost create(@RequestBody LearningPostRequest request, Authentication authentication) {
        return learningPostService.create(request, authentication.getName());
    }

    @PutMapping("/{postId}")
    LearningAdminPost update(@PathVariable Long postId,
                             @RequestBody LearningPostRequest request,
                             Authentication authentication) {
        return learningPostService.update(postId, request, authentication.getName());
    }

    @PostMapping("/{postId}/status")
    LearningAdminPost updateStatus(@PathVariable Long postId,
                                   @RequestBody Map<String, String> request,
                                   Authentication authentication) {
        return learningPostService.updateStatus(postId, request == null ? null : request.get("status"), authentication.getName());
    }

    @DeleteMapping("/{postId}")
    LearningDeleteResponse delete(@PathVariable Long postId) {
        return learningPostService.delete(postId);
    }
}
