package com.example.brocawebsite.learning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/learning-posts")
class LearningPublicController {

    private final LearningPostService learningPostService;

    LearningPublicController(LearningPostService learningPostService) {
        this.learningPostService = learningPostService;
    }

    @GetMapping
    LearningPublicResponse posts(@RequestParam(required = false) String date,
                                 @RequestParam(required = false) String classLabel) {
        return learningPostService.publicPosts(date, classLabel);
    }
}
