package com.mysite.travelo.gil.review;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.mysite.travelo.DataNotFoundException;
import com.mysite.travelo.gil.course.Course;
import com.mysite.travelo.yeon.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	
//	특정 코스의 댓글 목록 조회(정렬 디폴트값: 인기순 / 옵션값: 최신순, 오래된순)
	public Page<Review> getReviews(int page, Integer courseSeq, String sortBy) {
		
		Pageable pageable;
		
//		정렬
        if ("popularity".equals(sortBy)) {
            pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "recommendCount").and(Sort.by(Sort.Direction.DESC, "createDate"))); // 추천 많은순, 최신순
        } else if ("latest".equals(sortBy)) {
        	pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "createDate")); // 최신순
        } else if ("oldest".equals(sortBy)) {
        	pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.ASC, "createDate")); // 오래된순
        } else {
        	pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "recommendCount").and(Sort.by(Sort.Direction.DESC, "createDate"))); // 추천 많은순, 최신순(디폴트)
        }
		
		return reviewRepository.findByCourseCourseSeq(pageable, courseSeq);
	}
	
//	특정 코스의 댓글 개수 조회
    public int getReviewsCountByCourse(Integer courseSeq) {
    	
        return reviewRepository.countByCourseCourseSeq(courseSeq);
    }
	
//	특정 유저의 댓글 목록 조회(정렬 디폴트값: 최신순 / 옵션값: 오래된순)
	public Page<Map<String, Object>> getMyReviews(int page, Integer userSeq, String sortBy){
		
		Pageable pageable;
		
//		정렬
		if ("latest".equals(sortBy)) {
			pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "createDate")); // 최신순
		} else if ("popularity".equals(sortBy)) {
			pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "recommendCount")); // 추천순
		} else {
			pageable = PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "createDate")); // 최신순(디폴트)
		}
		
		Page<Review> reviews = reviewRepository.findByUserUserSeq(pageable, userSeq);
		
//		해당 댓글이 참조하는 코스의 순차번호와 코스명을 같이 반환
		return reviews.map(review -> {
            Map<String, Object> map = new HashMap<>();
            map.put("review", review);
            map.put("courseSeq", review.getCourse().getCourseSeq());
            map.put("courseTitle", review.getCourse().getTitle());
            return map;
        });
	}
	
//	댓글 작성
	public void create(Course course, String content, SiteUser user) {
		Review review = new Review();
		review.setContent(content);
		review.setUser(user); // 임시 회원 정보
		review.setCreateDate(LocalDateTime.now());
	    review.setRecommendCount(0); // 기본값 설정
		review.setCourse(course);
		this.reviewRepository.save(review);
	}
	
//	댓글 조회
	public Review getReview(Integer reviewSeq) {
		Optional<Review> or = this.reviewRepository.findById(reviewSeq);
		if(or.isPresent()) {
			return or.get();
		} else {
			throw new DataNotFoundException("댓글을 찾을 수 없습니다.");
		}
	}
	
//	댓글 수정
	public void modify(Review review, String content) {
		review.setContent(content);
		review.setModifyDate(LocalDateTime.now());
		this.reviewRepository.save(review);
	}
	
//	댓글 삭제
	public void delete(Review review) {
		this.reviewRepository.delete(review);
	}
	
//	댓글 추천 수 증가
	public void increaseRecommendCount(Integer reviewSeq) {
		
		Review review = getReview(reviewSeq);
        review.setRecommendCount(review.getRecommendCount() + 1);
        reviewRepository.save(review);
	}
	
//	댓글 추천 수 감소
	public void decreaseRecommendCount(Integer reviewSeq) {
		
		Review review = getReview(reviewSeq);
        review.setRecommendCount(review.getRecommendCount() - 1);
        reviewRepository.save(review);
	}

}