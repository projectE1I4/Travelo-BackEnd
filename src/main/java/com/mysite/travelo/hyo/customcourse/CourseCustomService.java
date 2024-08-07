package com.mysite.travelo.hyo.customcourse;

import com.mysite.travelo.gil.course.Course;
import com.mysite.travelo.gil.course.CourseList;
import com.mysite.travelo.gil.course.CourseRepository;
import com.mysite.travelo.hyo.place.Place;
import com.mysite.travelo.hyo.place.PlaceRepository;
import com.mysite.travelo.yeon.user.SiteUser;
import com.mysite.travelo.yeon.user.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
@Service
public class CourseCustomService {

    private final CourseListRepository courseListRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;

    // 생성, Create
    // 이후에 userSeq 변경 필요.
    @Transactional
    public Map<String, Object> create(CustomCourseRequest request, SiteUser loginUser) {

        // 응답 반환용 response map.
        Map<String, Object> response = new HashMap<>();

        // Course 생성
        Course course = new Course();

        // 이미 받아온 request에 임시 저장된 값들을 불러서, 실제 DB에 저장하기

        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setAuthor(loginUser);
        course.setPrivateYn(request.getPrivateYn());

        // 초기화용 set 데이터
        course.setCreateDate(LocalDateTime.now());
        course.setViewCount(0);
        course.setLikeCount(0);

        List<CourseList> courseLists = new ArrayList<>();
        List<Place> placeList = new ArrayList<>();

        // Course에 들어가는 PlaceList 생성
        for (int placeSeq : request.getPlaceSeqs()) {
            Place place = placeRepository.findById(placeSeq);

            // 장소 유효 여부 검사
            if(place == null) {
                throw new IllegalArgumentException("장소가 유효하지 않습니다.");
            }
            
            CourseList courseList = new CourseList();

            Optional<CourseList> optionalCourseList = courseListRepository.findById(placeSeq);
            if(optionalCourseList.isPresent()) {
                CourseList coursePlaceList = optionalCourseList.get();
                coursePlaceList.setCourse(course);
                courseLists.add(coursePlaceList);
            }

            //placeList에 place들을 저장함.
            //즉, 리스트에 장소를 저장함.
            placeList.add(place);
            courseList.setPlace(place);
            courseList.setCourse(course);
            // courseList에 해당 장소 내용들을 저장함.
            // 이 과정에서 동시에 AreaCode를 가져와서 set함.
            course.setAreaCode(place.getAreaCode());
            response.put("message", "코스 장소 목록 저장 성공.");
            response.put("placeList", placeList);
            this.courseListRepository.save(courseList);
        }
        
        request.setCourse(course);

        course.setCourseList(courseLists);

        // 장소 리스트를 request에도 set해줌.
        courseRepository.save(course);
        response.put("message", "코스 저장 성공.");
        response.put("course", course);

        return response;
    }

            // 코스 수정
    @Transactional
    public Map<String, Object> modifiedCourse(Integer courseSeq, CustomCourseRequest request, Map<String, Object> response) {

        /* *
         * 다음에 하나라도 해당될 경우 코스를 찾을 수 없다고 함.
         * 1. 기존 코스(currentCourse)가 null 인 경우
         * 2. existingCourse.getCourseSeq()와 받아온 courseSeq가 일치하지 않은 경우
         * 3. 기본 코스의 seq 값이 0보다 작을 경우.
         */

        // 기존 코스를 가져오기.
        // courseSeq를 입력 받아서 그에 알맞는 기존 코스(수정 대상)를 가져옴.
        Course currentCourse = courseRepository.findByCourseSeq(courseSeq);

        if (currentCourse == null) {
            throw new IllegalArgumentException("코스를 찾을 수 없습니다.");
        }

        // 변경 사항 비교용 불린 변수
        boolean isModified = false;

        // 제목에 변경사항이 있는지 체크
        if(!currentCourse.getTitle().equals(request.getTitle())){
            currentCourse.setTitle(request.getTitle());
            isModified = true;
            response.put("modyfiedTitle", currentCourse.getTitle());
        }

        // 변경사항이 있는지, 디스크립션에 변경이 있는지 체크
        if(!currentCourse.getDescription().equals(request.getDescription())){
            currentCourse.setDescription(request.getDescription());
            isModified = true;
            response.put("modyfiedDescription", currentCourse.getDescription());
        }

        // 변경사항이 있는지, 공개 비공개 여부에 변경이 있는지 체크
        if(!currentCourse.getPrivateYn().equals(request.getPrivateYn())){
            currentCourse.setPrivateYn(request.getPrivateYn());
            isModified = true;
            response.put("modyfiedPrivate", currentCourse.getPrivateYn());
        }

        // 변경사항이 있었는지 체크

        List<CourseList> courseLists = new ArrayList<>();
            System.out.println("PlaceSeqs : " + request.getPlaceSeqs());
            // 변경사항이 없었을 경우, 코스 리스트에는 변경이 있는지 체크
                for (int placeSeq : request.getPlaceSeqs()) {
                    Optional<Place> optionalPlace = placeRepository.findByPlaceSeq(placeSeq);

                    if (optionalPlace.isPresent()) {

                    Place place = optionalPlace.get();
                    CourseList courseList = new CourseList();
                    courseList.setPlace(place);
                    courseList.setCourse(currentCourse);
                    courseLists.add(courseList);

                    isModified = true;
                    courseListRepository.saveAll(courseLists);

                    } else {

                        throw new IllegalArgumentException("장소가 유효하지 않습니다.");
                    }

                }
                    currentCourse.setCourseList(courseLists);

                if(isModified){
                    currentCourse.setModifyDate(LocalDateTime.now());
                    courseRepository.save(currentCourse);

                    response.put("message", "코스가 수정되었습니다.");
                    response.put("modyfiedList", currentCourse);
                }

        // response 메시지 반환
        return response;
    }



    // 코스 삭제
    public Map<String, Object> deleteCourse(Integer courseSeq, Map<String, Object> response) {

        // 삭제할 대상 가져오기
        Course deleteTarget = courseRepository.findByCourseSeq(courseSeq);

        // 삭제할 대상의 존재 여부 확인.
        if(deleteTarget == null || deleteTarget.getCourseSeq() == null){
            response.put("error", "삭제할 대상이 없습니다.");
        } else {
            this.courseRepository.delete(deleteTarget);
            response.put("message", "성공적으로 삭제하였습니다.");
        }

        return response;
    }

    //map에 pin 찍기 위해서 장소의 좌표값만 가져옴.
    @Transactional
    public Map<String, Object> mappin(List<Place> placeList){
        // 응답용 response 생성
        Map<String, Object> response = new HashMap<>();

        // 장소 최대 수 검사
        if(placeList.size()>6){
            response.put("error", "장소는 최대 6개까지 추가할 수 있습니다.");
            return response;
        }

        // 좌표와 장소번호를 저장할 placeMap 생성
        Map<Integer, Map<String, Double>> placeMap = new HashMap<>();

        // 받아온 placeList에 place가 존재하는 만큼 반복
        for (Place place : placeList) {

            System.out.println("Place ID: " + place.getPlaceSeq());
            Optional<Place> optionalPlace = placeRepository.findById(place.getPlaceSeq());

            Map<String, Double> pin = new HashMap<>();

            if(optionalPlace.isPresent()) {
            // y, x 좌표를 각각 pin이라는 map에 저장
                Place placemap = optionalPlace.get();
                pin.put("Longitude", placemap.getLongitude());
                pin.put("latitude", placemap.getLatitude());

            }
            // placeMap에 placeSeq와 pin을 저장
            placeMap.put(place.getPlaceSeq(), pin);
        }

        // response에 placeMap을 저장.
        // 여기서 response의 형태는 response<placeMap, <placeSeq, <longitude, latitude>>>
        response.put("placeMap", placeMap);
        return response;
    }

    // 안전한 String 변환을 위한 유틸리티 메서드
    private String convertToString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

}
