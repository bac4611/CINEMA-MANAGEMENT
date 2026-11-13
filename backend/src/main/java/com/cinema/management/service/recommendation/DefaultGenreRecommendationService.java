package com.cinema.management.service.recommendation;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.GenreRecommendationResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
public class DefaultGenreRecommendationService implements GenreRecommendationService {
    private static final String TRAINING_DATA = """
            to_mo,tre_em,nhe_nhang,P,HoatHinh,Hoạt hình
            to_mo,tre_em,vua_phai,P,HoatHinh,Hoạt hình
            to_mo,gia_dinh,nhe_nhang,P,HoatHinh,Hoạt hình
            to_mo,gia_dinh,vua_phai,P,HoatHinh,Hoạt hình
            vui,tre_em,nhe_nhang,P,HoatHinh,Hoạt hình
            vui,tre_em,vua_phai,P,HoatHinh,Hoạt hình
            thu_gian,tre_em,nhe_nhang,P,HoatHinh,Hoạt hình
            thu_gian,tre_em,vua_phai,P,HoatHinh,Hoạt hình
            met_moi,tre_em,nhe_nhang,P,HoatHinh,Hoạt hình
            met_moi,tre_em,vua_phai,P,HoatHinh,Hoạt hình
            vui,gia_dinh,nhe_nhang,P,HoatHinh,Hoạt hình
            vui,gia_dinh,gay_can,P,HoatHinh,Hoạt hình
            thu_gian,gia_dinh,nhe_nhang,P,GiaDinh,Gia đình
            met_moi,gia_dinh,nhe_nhang,P,GiaDinh,Gia đình
            am_ap,gia_dinh,nhe_nhang,P,GiaDinh,Gia đình
            am_ap,gia_dinh,vua_phai,P,GiaDinh,Gia đình
            vui,gia_dinh,vua_phai,P,GiaDinh,Gia đình
            thu_gian,gia_dinh,vua_phai,P,GiaDinh,Gia đình
            met_moi,gia_dinh,vua_phai,P,GiaDinh,Gia đình
            am_ap,tre_em,nhe_nhang,P,GiaDinh,Gia đình
            am_ap,tre_em,vua_phai,P,GiaDinh,Gia đình
            vui,ban_be,nhe_nhang,P,GiaDinh,Gia đình
            thu_gian,mot_minh,nhe_nhang,P,GiaDinh,Gia đình
            met_moi,mot_minh,nhe_nhang,P,GiaDinh,Gia đình
            buon,mot_minh,nhe_nhang,T13,CamDong,Cảm động
            buon,mot_minh,vua_phai,T13,CamDong,Cảm động
            buon,gia_dinh,nhe_nhang,P,CamDong,Cảm động
            buon,gia_dinh,vua_phai,P,CamDong,Cảm động
            met_moi,mot_minh,nhe_nhang,T13,CamDong,Cảm động
            met_moi,mot_minh,vua_phai,T13,CamDong,Cảm động
            thu_gian,mot_minh,vua_phai,T13,CamDong,Cảm động
            am_ap,mot_minh,nhe_nhang,T13,CamDong,Cảm động
            am_ap,mot_minh,vua_phai,T13,CamDong,Cảm động
            buon,ban_be,nhe_nhang,T13,CamDong,Cảm động
            thu_gian,nguoi_yeu,nhe_nhang,T13,CamDong,Cảm động
            am_ap,nguoi_yeu,nhe_nhang,T13,CamDong,Cảm động
            lang_man,nguoi_yeu,nhe_nhang,T13,TinhCam,Tình cảm
            lang_man,nguoi_yeu,vua_phai,T13,TinhCam,Tình cảm
            buon,nguoi_yeu,nhe_nhang,T13,TinhCam,Tình cảm
            buon,nguoi_yeu,vua_phai,T13,TinhCam,Tình cảm
            am_ap,nguoi_yeu,vua_phai,T13,TinhCam,Tình cảm
            thu_gian,nguoi_yeu,vua_phai,T13,TinhCam,Tình cảm
            vui,nguoi_yeu,nhe_nhang,T13,TinhCam,Tình cảm
            vui,nguoi_yeu,vua_phai,T13,TinhCam,Tình cảm
            lang_man,mot_minh,nhe_nhang,T13,TinhCam,Tình cảm
            lang_man,mot_minh,vua_phai,T13,TinhCam,Tình cảm
            lang_man,ban_be,nhe_nhang,T13,TinhCam,Tình cảm
            am_ap,ban_be,nhe_nhang,T13,TinhCam,Tình cảm
            hoi_hop,ban_be,gay_can,T18,KinhDi,Kinh dị
            hoi_hop,mot_minh,gay_can,T18,KinhDi,Kinh dị
            hoi_hop,nguoi_yeu,gay_can,T18,KinhDi,Kinh dị
            so_hai,ban_be,gay_can,T18,KinhDi,Kinh dị
            so_hai,mot_minh,gay_can,T18,KinhDi,Kinh dị
            so_hai,nguoi_yeu,gay_can,T18,KinhDi,Kinh dị
            to_mo,ban_be,gay_can,T18,KinhDi,Kinh dị
            to_mo,mot_minh,gay_can,T18,KinhDi,Kinh dị
            to_mo,nguoi_yeu,gay_can,T18,KinhDi,Kinh dị
            kich_tinh,ban_be,gay_can,T18,KinhDi,Kinh dị
            hoi_hop,ban_be,vua_phai,T18,KinhDi,Kinh dị
            hoi_hop,mot_minh,vua_phai,T18,KinhDi,Kinh dị
            kich_tinh,ban_be,gay_can,T13,HanhDong,Hành động
            kich_tinh,mot_minh,gay_can,T13,HanhDong,Hành động
            kich_tinh,nguoi_yeu,gay_can,T13,HanhDong,Hành động
            vui,ban_be,gay_can,T13,HanhDong,Hành động
            vui,mot_minh,gay_can,T13,HanhDong,Hành động
            hoi_hop,ban_be,gay_can,T13,HanhDong,Hành động
            hoi_hop,mot_minh,gay_can,T13,HanhDong,Hành động
            to_mo,ban_be,gay_can,T13,HanhDong,Hành động
            to_mo,mot_minh,gay_can,T13,HanhDong,Hành động
            kich_tinh,gia_dinh,vua_phai,T13,HanhDong,Hành động
            vui,gia_dinh,gay_can,T16,HanhDong,Hành động
            met_moi,ban_be,gay_can,T16,HanhDong,Hành động
            to_mo,mot_minh,vua_phai,T16,KhoaHocVienTuong,Khoa học viễn tưởng
            to_mo,ban_be,vua_phai,T16,KhoaHocVienTuong,Khoa học viễn tưởng
            to_mo,nguoi_yeu,vua_phai,T16,KhoaHocVienTuong,Khoa học viễn tưởng
            to_mo,gia_dinh,vua_phai,T16,KhoaHocVienTuong,Khoa học viễn tưởng
            kich_tinh,ban_be,vua_phai,T13,KhoaHocVienTuong,Khoa học viễn tưởng
            kich_tinh,mot_minh,vua_phai,T13,KhoaHocVienTuong,Khoa học viễn tưởng
            kich_tinh,nguoi_yeu,vua_phai,T13,KhoaHocVienTuong,Khoa học viễn tưởng
            hoi_hop,ban_be,vua_phai,T13,KhoaHocVienTuong,Khoa học viễn tưởng
            hoi_hop,mot_minh,vua_phai,T13,KhoaHocVienTuong,Khoa học viễn tưởng
            vui,ban_be,vua_phai,T13,KhoaHocVienTuong,Khoa học viễn tưởng
            met_moi,mot_minh,vua_phai,T16,KhoaHocVienTuong,Khoa học viễn tưởng
            vui,mot_minh,vua_phai,T16,KhoaHocVienTuong,Khoa học viễn tưởng
            to_mo,mot_minh,nhe_nhang,T13,TrinhTham,Trinh thám
            to_mo,ban_be,nhe_nhang,T13,TrinhTham,Trinh thám
            to_mo,nguoi_yeu,nhe_nhang,T13,TrinhTham,Trinh thám
            hoi_hop,mot_minh,nhe_nhang,T13,TrinhTham,Trinh thám
            hoi_hop,ban_be,nhe_nhang,T13,TrinhTham,Trinh thám
            hoi_hop,nguoi_yeu,nhe_nhang,T13,TrinhTham,Trinh thám
            so_hai,mot_minh,vua_phai,T13,TrinhTham,Trinh thám
            so_hai,ban_be,vua_phai,T13,TrinhTham,Trinh thám
            so_hai,nguoi_yeu,vua_phai,T13,TrinhTham,Trinh thám
            kich_tinh,mot_minh,vua_phai,T16,TrinhTham,Trinh thám
            kich_tinh,ban_be,vua_phai,T16,TrinhTham,Trinh thám
            hoi_hop,gia_dinh,nhe_nhang,T13,TrinhTham,Trinh thám
            """;

    private static final List<TrainingExample> EXAMPLES = parseTrainingData();
    private static final Map<String, GenreRecommendationResult> EXACT_RESULTS = buildExactResults();

    @Override
    public GenreRecommendationResult predict(GenreRecommendationInput input) {
        if (input == null) {
            return null;
        }
        var exact = EXACT_RESULTS.get(key(input));
        if (exact != null) {
            return exact;
        }
        return EXAMPLES.stream()
                .map(example -> new ScoredExample(example, similarity(input, example.input())))
                .sorted(Comparator.comparingInt(ScoredExample::score).reversed()
                        .thenComparing(item -> item.example().result().genreCode()))
                .findFirst()
                .map(item -> new GenreRecommendationResult(
                        item.example().result().genreCode(),
                        item.example().result().genreName(),
                        Math.min(0.99f, item.score() / 11f)))
                .orElse(null);
    }

    private static List<TrainingExample> parseTrainingData() {
        return Stream.of(TRAINING_DATA.strip().split("\\R"))
                .map(line -> line.split(",", 6))
                .filter(columns -> columns.length == 6)
                .map(columns -> new TrainingExample(
                        new GenreRecommendationInput(columns[0], columns[1], columns[2], columns[3]),
                        new GenreRecommendationResult(columns[4], columns[5], 1f)))
                .toList();
    }

    private static Map<String, GenreRecommendationResult> buildExactResults() {
        Map<String, GenreRecommendationResult> results = new LinkedHashMap<>();
        EXAMPLES.forEach(example -> results.putIfAbsent(key(example.input()), example.result()));
        return Map.copyOf(results);
    }

    private static int similarity(GenreRecommendationInput left, GenreRecommendationInput right) {
        int score = 0;
        if (same(left.mood(), right.mood())) score += 4;
        if (same(left.companion(), right.companion())) score += 2;
        if (same(left.intensity(), right.intensity())) score += 2;
        if (same(left.ageRating(), right.ageRating())) score += 3;
        return score;
    }

    private static boolean same(String left, String right) {
        return Objects.equals(normalize(left), normalize(right));
    }

    private static String key(GenreRecommendationInput input) {
        return String.join("|", normalize(input.mood()), normalize(input.companion()),
                normalize(input.intensity()), normalize(input.ageRating()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record TrainingExample(GenreRecommendationInput input, GenreRecommendationResult result) {}
    private record ScoredExample(TrainingExample example, int score) {}
}
