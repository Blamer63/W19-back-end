package com.example.demo.service;

import com.example.demo.dto.ReportRequest;
import com.example.demo.entity.ContentReport;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostComment;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ContentReportRepository;
import com.example.demo.repository.PostCommentRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ContentReportRepository contentReportRepository;
    private final ProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;

    @Transactional
    public void createReport(UUID reporterId, ReportRequest request) {
        Profile reporter = profileRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));

        ContentReport.ContentReportBuilder reportBuilder = ContentReport.builder()
                .reporter(reporter)
                .reason(request.getReason())
                .description(request.getDescription());

        if (request.getPostId() != null) {
            Post post = postRepository.findById(request.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            reportBuilder.post(post);
        } else if (request.getCommentId() != null) {
            PostComment comment = postCommentRepository.findById(request.getCommentId())
                    .orElseThrow(() -> new RuntimeException("Comment not found"));
            reportBuilder.comment(comment);
        } else {
            throw new RuntimeException("Must report either a post or a comment");
        }

        contentReportRepository.save(reportBuilder.build());
    }
}
