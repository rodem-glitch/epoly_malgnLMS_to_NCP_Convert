package kr.go.growailms.job.service;

import kr.go.growailms.job.service.dto.JobRecruitItem;

import java.util.ArrayList;
import java.util.List;

public final class JobRecruitInterleaveUtil {
    private JobRecruitInterleaveUtil() {
    }

    public static List<JobRecruitItem> interleaveProvidersForAll(List<JobRecruitItem> items) {
        // 왜: 통합(ALL) 결과는 내부적으로 [Work24..., JobKorea...] 순서로 들어오는 케이스가 많습니다.
        //      그런데 자연어 검색은 display만큼만 잘라 노출하므로, 앞부분이 Work24로만 채워져
        //      "잡코리아가 같이 검색이 안 되는 것처럼" 보일 수 있습니다.
        //      그래서 같은 목록 안에서 provider를 교차시켜 초반에도 섞이게 합니다.
        if (items == null || items.isEmpty()) return List.of();

        List<JobRecruitItem> jobkorea = new ArrayList<>();
        List<JobRecruitItem> others = new ArrayList<>();
        for (JobRecruitItem item : items) {
            if (item == null || item.infoSvc() == null) {
                others.add(item);
                continue;
            }
            String svc = item.infoSvc().trim().toUpperCase();
            if ("JOBKOREA".equals(svc)) jobkorea.add(item);
            else others.add(item);
        }

        if (jobkorea.isEmpty() || others.isEmpty()) {
            return items;
        }

        List<JobRecruitItem> out = new ArrayList<>(items.size());
        int i = 0;
        int j = 0;
        while (i < others.size() || j < jobkorea.size()) {
            if (i < others.size()) out.add(others.get(i++));
            if (j < jobkorea.size()) out.add(jobkorea.get(j++));
        }
        return out;
    }
}

