package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Follow;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    // ========== ID-BASED QUERIES ==========
    @Query("SELECT f.followee.id FROM Follow f WHERE f.follower.id = :userId")
    Page<Long> findFolloweeIdsByFollowerId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f.follower.id FROM Follow f WHERE f.followee.id = :userId")
    Page<Long> findFollowerIdsByFolloweeId(@Param("userId") Long userId, Pageable pageable);

    // ========== EXISTS CHECKS ==========
    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    // ========== ENTITY-BASED QUERIES ==========
    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    // ========== PAGINATED FETCHES ==========
    @Query("SELECT f FROM Follow f WHERE f.followee.id = :followeeId")
    Page<Follow> findByFolloweeId(@Param("followeeId") Long followeeId, Pageable pageable);

    int deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);


    @Query("SELECT f FROM Follow f WHERE f.follower.id = :followerId")
    Page<Follow> findByFollowerId(@Param("followerId") Long followerId, Pageable pageable);

    // ========== COUNT QUERIES ==========
    @Query("SELECT COUNT(f) FROM Follow f WHERE f.followee.id = :followeeId")
    long countByFolloweeId(@Param("followeeId") Long followeeId);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.follower.id = :followerId")
    long countByFollowerId(@Param("followerId") Long followerId);

    // ========== LEGACY USER-OBJECT QUERY (consider deprecating) ==========
    List<Follow> findByFollower(User user);
}