package com.mysite.travelo.hyo.place;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Integer>, JpaSpecificationExecutor<Place> {
    // 전체 가져오는 리스트 형식
    Page<Place> findAll(Specification<Place> spec, Pageable pageable);

    List<Place> findAll(Specification<Place> spec);
    // contentId를 기반으로 가져옴. 기본 사용처: 거리 기반 검색
    Place findByContentId(String contentId);

    Place findById(int placeSeq);
    Optional<Place> findByPlaceSeq(Integer placeSeq);

    @Query("SELECT p FROM Place p ORDER BY p.likeCount DESC, p.viewCount DESC")
    List<Place> findAllOrderByLikeCountDescViewCountDesc(Pageable pageable);

}
