package com.ssafy.moemoe.api.service.board;

import com.ssafy.moemoe.api.request.board.BoardSaveReq;
import com.ssafy.moemoe.api.request.board.ReactionDetailReq;
import com.ssafy.moemoe.api.response.board.BoardLoadResp;
import com.ssafy.moemoe.api.response.board.BoardResp;
import com.ssafy.moemoe.api.service.S3Uploader;
import com.ssafy.moemoe.db.entity.board.Board;
import com.ssafy.moemoe.db.entity.board.Reaction;
import com.ssafy.moemoe.db.entity.cat.Cat;
import com.ssafy.moemoe.db.entity.member.Member;
import com.ssafy.moemoe.db.entity.university.University;
import com.ssafy.moemoe.db.repository.board.BoardRepository;
import com.ssafy.moemoe.db.repository.board.ReactionRepository;
import com.ssafy.moemoe.db.repository.cat.CatRepository;
import com.ssafy.moemoe.db.repository.member.MemberRepository;
import com.ssafy.moemoe.db.repository.university.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Board 관련 비즈니스 로직 처리를 위한 서비스 구현 정의.
 */
@Service("BoardService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardServiceImpl implements BoardService {

    Logger LOGGER = LoggerFactory.getLogger(BoardServiceImpl.class);
    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final UniversityRepository universityRepository;
    private final ReactionRepository reactionRepository;
    private final CatRepository catRepository;
    private final S3Uploader s3Uploader;

    @Override
    @Transactional
    public BoardResp createBoard(UUID member_id, MultipartFile multiPartFile, BoardSaveReq boardSaveReq) {
        Member member = memberRepository.findById(member_id).orElseThrow(() -> new IllegalArgumentException("사용자 ID 확인해달라 냥!"));
        University university = universityRepository.findById(boardSaveReq.getUniversityId()).orElseThrow(() -> new IllegalArgumentException("학교 ID 확인해달라 냥!"));
        Cat cat = catRepository.findById(boardSaveReq.getCatId()).orElseThrow(() -> new IllegalArgumentException("고양이 ID 확인해달라 냥!"));

        // S3에 이미지 등록
        String img;
        try {
            img = s3Uploader.upload(multiPartFile, "board");
        }
        catch (IOException e) {
            throw new IllegalArgumentException("파일 업로드에 문제가 발생했습니다.(board)");
        }
        LOGGER.info("================url===============\n" + img);


        Board board = boardRepository.save(Board.builder().lat(boardSaveReq.getLat()).lng(boardSaveReq.getLng())
                .content(boardSaveReq.getContent()).image(img).member(member).university(university).cat(cat).build());


        return BoardResp.builder().boardId(board.getBoardId()).catId(cat.getCatId()).universityId(university.getUniversityId()).memberNickname(member.getNickname())
                .lat(board.getLat()).lng(board.getLng()).content(board.getContent()).build();
    }

    @Override
    public Page<BoardLoadResp> searchAllBoard(UUID memberId, Long universityId, Pageable pageable) {
        Page<BoardLoadResp> page = boardRepository.findBoardByIdAndTag(universityId, pageable);
        List<BoardLoadResp> list = page.getContent();

        for (BoardLoadResp cur : list) {
            cur.setMyEmotion(reactionRepository.checkReation(memberId, cur.getBoardId()));
        }

        return page;
    }

    @Override
    @Transactional
    public void updateReaction(UUID memberId, ReactionDetailReq reactionDetailReq) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("해당 유저는 없습니다. id=" + memberId));
        Board board = boardRepository.findById(reactionDetailReq.getBoardId()).orElseThrow(() -> new IllegalArgumentException("해당 게시물은 없습니다. id=" + reactionDetailReq.getBoardId()));
        Optional<Reaction> reaction = reactionRepository.findByMemberMemberIdAndBoardBoardId(memberId, board.getBoardId());
        String nextReact = reactionDetailReq.getEmotion();
        String prevReact = null;
        int prevEmo = 0;
        int nextEmo = 0;

        //reaction 객체가 비어있는지 검사
        if (reaction.isEmpty()) { //비어있다면
            //등록한 내용으로 리액션 생성
            reactionRepository.save(Reaction.builder().react(nextReact).board(board).member(member).build());
            prevEmo = 0; //이전 감정은 더하거나 뺄 게 없음
            nextEmo = 1; //이후 감정은 더하기 해야함
        } else { //있다면
            //등록한 내용과 같다면
            prevReact = reaction.get().getReact();

            if(reaction.get().getReact().equals(nextReact)){
                //등록한 reaction 삭제
                reactionRepository.deleteReation(memberId, reactionDetailReq);

                prevEmo = -1; //이전 감저은 빼야함
                nextEmo = 0; //이후 감정은 더하거나 뺄 게 없음
            } else {//다르다면
                //등록한 reaction을 지금 등록한 내용으로 수정하기
                reaction.get().setReact(nextReact);
                reactionRepository.save(reaction.get());
                prevEmo = -1; //이전 감정은 빼야함
                nextEmo = 1; //이후 감정은 더해야함
            }
        }

        //등록한 반응의 수를 늘리거나 늘리지 않는 부분
        switch (nextReact) {
            case "recommend":
                board.updateRecommend(board.getRecommend() + (1*nextEmo));
                break;
            case "good":
                board.updateGood(board.getGood() + (1*nextEmo));
                break;
            case "impressed":
                board.updateImpressed(board.getImpressed() + (1*nextEmo));
                break;
            case "sad":
                board.updateSad(board.getSad() + (1*nextEmo));
                break;
            case "angry":
                board.updateAngry(board.getAngry() + (1*nextEmo));
                break;
            default:
                throw new IllegalArgumentException("존재하지 않은 이모지 종류입니다.");
        }

        //이전 감정이 있을 때만 수행해야함
        if (prevReact != null) {
            //이전에 등록한 반응의 수를 줄이거나 줄이지 않는 부분
            switch (prevReact) {
                case "recommend":
                    board.updateRecommend(board.getRecommend() + (1*prevEmo));
                    break;
                case "good":
                    board.updateGood(board.getGood() + (1*prevEmo));
                    break;
                case "impressed":
                    board.updateImpressed(board.getImpressed() + (1*prevEmo));
                    break;
                case "sad":
                    board.updateSad(board.getSad() + (1*prevEmo));
                    break;
                case "angry":
                    board.updateAngry(board.getAngry() + (1*prevEmo));
                    break;
                default:
                    throw new IllegalArgumentException("존재하지 않은 이모지 종류입니다.");
            }
        }
    }

    @Override
    @Transactional
    public void deleteReaction(UUID memberId, ReactionDetailReq reactionDetailReq) {
        Board board = boardRepository.findById(reactionDetailReq.getBoardId()).orElseThrow(() -> new IllegalArgumentException("해당 게시물은 없습니다. id=" + reactionDetailReq.getBoardId()));
        String reat = reactionDetailReq.getEmotion();

        switch (reat) {
            case "recommend":
                board.updateRecommend(board.getRecommend() - 1);
                break;
            case "good":
                board.updateGood(board.getGood() - 1);
                break;
            case "impressed":
                board.updateImpressed(board.getImpressed() - 1);
                break;
            case "sad":
                board.updateSad(board.getSad() - 1);
                break;
            case "angry":
                board.updateAngry(board.getAngry() - 1);
                break;
            default:
                throw new IllegalArgumentException("존재하지 않은 이모지 종류입니다.");
        }

        // 해당 이모지 삭제
        reactionRepository.deleteReation(memberId, reactionDetailReq);
    }

}
