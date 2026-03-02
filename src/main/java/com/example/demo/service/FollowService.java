package com.example.demo.service;

import com.example.demo.dto.ProfileResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserFollow;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;

    @Transactional
    public void followUser(UUID followingId, String followerEmail) {
        Profile follower = profileRepository.findByEmail(followerEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        if (follower.getId().equals(followingId)) {
            throw new RuntimeException("You cannot follow yourself");
        }

        Profile following = profileRepository.findById(followingId)
                .orElseThrow(() -> new RuntimeException("User to follow not found"));

        if (followRepository.existsByFollowerIdAndFollowingId(follower.getId(), following.getId())) {
            return; // Already following, idempotent
        }

        UserFollow follow = UserFollow.builder()
                .follower(follower)
                .following(following)
                .build();

        followRepository.save(follow);

        // Update denormalised counts
        follower.setFollowingCount((follower.getFollowingCount() != null ? follower.getFollowingCount() : 0) + 1);
        following.setFollowersCount((following.getFollowersCount() != null ? following.getFollowersCount() : 0) + 1);
        profileRepository.save(follower);
        profileRepository.save(following);
    }

    @Transactional
    public void unfollowUser(UUID followingId, String followerEmail) {
        Profile follower = profileRepository.findByEmail(followerEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        boolean existed = followRepository.existsByFollowerIdAndFollowingId(follower.getId(), followingId);
        followRepository.deleteByFollowerIdAndFollowingId(follower.getId(), followingId);

        if (existed) {
            Profile following = profileRepository.findById(followingId).orElse(null);
            follower.setFollowingCount(
                    Math.max(0, (follower.getFollowingCount() != null ? follower.getFollowingCount() : 0) - 1));
            profileRepository.save(follower);
            if (following != null) {
                following.setFollowersCount(
                        Math.max(0, (following.getFollowersCount() != null ? following.getFollowersCount() : 0) - 1));
                profileRepository.save(following);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<ProfileResponse> getFollowers(UUID userId, Pageable pageable) {
        return followRepository.findByFollowingId(userId, pageable)
                .map(follow -> profileService.mapToResponse(follow.getFollower()));
    }

    @Transactional(readOnly = true)
    public Page<ProfileResponse> getFollowing(UUID userId, Pageable pageable) {
        return followRepository.findByFollowerId(userId, pageable)
                .map(follow -> profileService.mapToResponse(follow.getFollowing()));
    }
}
