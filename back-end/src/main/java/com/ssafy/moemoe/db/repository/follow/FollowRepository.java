package com.ssafy.moemoe.db.repository.follow;

import com.ssafy.moemoe.db.entity.cat.Cat;
import com.ssafy.moemoe.db.entity.follow.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    @Query(
            "select f " +
                    "from Follow f " +
                    "where f.member.memberId = :memberId and f.cat.catId = :catId"
    )
    Optional<Follow> findByMemberAndCat(@Param("memberId") UUID memberId, @Param("catId") Long catId);

    @Query("SELECT f.cat " +
            "FROM Follow f " +
            "JOIN f.member m " +
            "WHERE m.memberId = :memberId")
    List<Cat> findCatsByMemberId(@Param("memberId") UUID memberId);
}