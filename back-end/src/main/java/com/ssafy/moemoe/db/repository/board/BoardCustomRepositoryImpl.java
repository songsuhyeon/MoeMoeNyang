package com.ssafy.moemoe.db.repository.board;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.moemoe.api.response.board.BoardLoadResp;
import com.ssafy.moemoe.api.response.board.QBoardLoadResp;
import com.ssafy.moemoe.db.entity.board.Board;
import com.ssafy.moemoe.db.entity.board.QBoard;
import com.ssafy.moemoe.db.entity.cat.QCat;
import com.ssafy.moemoe.db.entity.member.QMember;
import com.ssafy.moemoe.db.entity.university.QUniversity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/**
 * 유저 모델 관련 디비 쿼리 생성을 위한 구현 정의.
 */
public class BoardCustomRepositoryImpl implements BoardCustomRepository {
    @Autowired
    private JPAQueryFactory jpaQueryFactory;

    QBoard qBoard = QBoard.board;
    QUniversity qUniversity = QUniversity.university;
    QCat qCat = QCat.cat;
    QMember qMember = QMember.member;

    @Override
    public Page<BoardLoadResp> findBoardByIdAndTag(Long universityId, Pageable pageable) {

        List<BoardLoadResp> content = jpaQueryFactory
                .select(new QBoardLoadResp(qBoard, qCat, qMember, qUniversity))
                .from(qBoard)
                .leftJoin(qBoard.cat, qCat)
                .leftJoin(qBoard.member, qMember)
                .leftJoin(qBoard.university, qUniversity)
                .where(qUniversity.universityId.eq(universityId))
                .orderBy(qBoard.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Board> countQuery = jpaQueryFactory
                .select(qBoard)
                .from(qBoard)
                .where(qUniversity.universityId.eq(universityId));

        return PageableExecutionUtils.getPage(content, pageable, () -> countQuery.fetchCount());
    }
}
