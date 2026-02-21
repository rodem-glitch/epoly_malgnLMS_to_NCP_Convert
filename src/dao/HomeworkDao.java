package dao;

import malgnsoft.db.*;
import java.util.LinkedHashSet;

public class HomeworkDao extends DataObject {

	public String[] statusList = { "1=>사용", "0=>중지" };
	public String[] onoffTypes = { "N=>온라인", "F=>집합" };
	public String defaultSubmitFileExt = "jpg|jpeg|gif|png|pdf|hwp|txt|doc|docx|xls|xlsx|ppt|pptx|zip|alz|7z|rar|egg|mp3";
	
	public String[] statusListMsg = { "1=>list.homework.status_list.1", "0=>list.homework.status_list.0" };
	public String[] onoffTypesMsg = { "N=>list.homework.onoff_types.N", "F=>list.homework.onoff_types.F" };

	public HomeworkDao() {
		this.table = "LM_HOMEWORK";
	}

	// 왜: 과제 제출 첨부파일 허용 확장자는 프론트 검증만으로는 우회가 가능하므로,
	//     서버가 이해할 수 있는 모드/확장자 포맷을 공통 규칙으로 강제합니다.
	public String normalizeSubmitFileExtMode(String mode) {
		if(mode == null) return "ALL";
		String normalized = mode.trim().toUpperCase();
		if("".equals(normalized)) return "ALL";
		if("ALL".equals(normalized)) return "ALL";
		if("DOC".equals(normalized)) return "DOC";
		if("IMAGE".equals(normalized)) return "IMAGE";
		if("ARCHIVE".equals(normalized)) return "ARCHIVE";
		if("AUDIO".equals(normalized)) return "AUDIO";
		if("CUSTOM".equals(normalized)) return "CUSTOM";
		return "";
	}

	public String normalizeSubmitFileExts(String rawExts) {
		if(rawExts == null) return "";

		String[] defaults = this.defaultSubmitFileExt.split("\\|");
		LinkedHashSet<String> allowSet = new LinkedHashSet<String>();
		for(int i = 0; i < defaults.length; i++) {
			String ext = defaults[i].trim().toLowerCase();
			if(!"".equals(ext)) allowSet.add(ext);
		}

		LinkedHashSet<String> selected = new LinkedHashSet<String>();
		String[] tokens = rawExts.toLowerCase().split("[^a-z0-9]+");
		for(int i = 0; i < tokens.length; i++) {
			String ext = tokens[i].trim();
			if("".equals(ext)) continue;
			if(allowSet.contains(ext)) selected.add(ext);
		}

		StringBuilder sb = new StringBuilder();
		int idx = 0;
		for(String ext : selected) {
			if(idx++ > 0) sb.append("|");
			sb.append(ext);
		}
		return sb.toString();
	}

	public String getPresetSubmitFileExts(String mode) {
		if("DOC".equals(mode)) return "pdf|hwp|txt|doc|docx|xls|xlsx|ppt|pptx";
		if("IMAGE".equals(mode)) return "jpg|jpeg|gif|png";
		if("ARCHIVE".equals(mode)) return "zip|alz|7z|rar|egg";
		if("AUDIO".equals(mode)) return "mp3";
		return this.defaultSubmitFileExt; // ALL
	}

	public String resolveSubmitFileExts(String mode, String customExts) {
		String normalizedMode = this.normalizeSubmitFileExtMode(mode);
		if("".equals(normalizedMode)) return "";
		if("CUSTOM".equals(normalizedMode)) return this.normalizeSubmitFileExts(customExts);
		return this.getPresetSubmitFileExts(normalizedMode);
	}

	public String toCommaSeparatedExts(String exts) {
		if(exts == null) return "";
		String normalized = exts.trim();
		if("".equals(normalized)) return "";
		return normalized.replace("|", ", ");
	}
}
