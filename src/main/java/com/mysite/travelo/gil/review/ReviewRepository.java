package com.mysite.travelo.gil.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

//	특정 코스의 댓글 전체보기(정렬 디폴트값: 추천순 / 옵션값: 최신순, 오래된순)
	Page<Review> findByCourseCourseSeq(Pageable pageable, Integer courseSeq);
	
//	특정 코스의 댓글 개수 조회
    int countByCourseCourseSeq(Integer courseSeq);
	
//	특정 유저의 댓글 전체보기(정렬 디폴트값: 최신순 / 옵션값: 오래된순)
	Page<Review> findByUserUserSeq(Pageable pageable, Integer userSeq);
	
}