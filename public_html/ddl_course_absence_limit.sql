-- 결석 기준(자동 F/미수료) 기능 추가용 DDL
-- 왜: 과목별로 "결석 n회 이상" 기준을 저장해야 자동 판정을 일관되게 처리할 수 있습니다.

ALTER TABLE `LM_COURSE`
  ADD COLUMN `LIMIT_ABSENCE_YN` varchar(1) NOT NULL DEFAULT 'N' COMMENT '결석 기준 사용여부',
  ADD COLUMN `LIMIT_ABSENCE_CNT` int NOT NULL DEFAULT '0' COMMENT '결석 허용 횟수(이상 시 자동 미수료)';
