<%@ page contentType="text/html; charset=utf-8" %><%@ include file="/init.jsp" %><%

//로그인
if(0 == userId) { auth.loginForm(); return; }

//폼입력
String mid = m.rs("mid");
String md = m.rs("md", "post");
if("".equals(mid)) { m.jsAlert("기본키는 반드시 지정해야 합니다."); return; }

//객체
BoardDao board = new BoardDao();
ClFileDao file = new ClFileDao();
HomeworkDao homework = new HomeworkDao();
HomeworkTaskDao homeworkTask = new HomeworkTaskDao();

//변수
String allowExt = homework.defaultSubmitFileExt;
int maxPostSize = 100; //Config.getInt("maxPostSize");
String submitFileExtMode = "";
String submitFileExts = "";
int homeworkId = 0;
int homeworkTaskId = 0;

// 왜: 과제 제출 첨부는 과제별 허용 형식 옵션을 서버에서 강제해야 우회 업로드를 막을 수 있습니다.
if(md.matches("^homework_[0-9]+$")) {
	homeworkId = m.parseInt(md.substring(9));
} else if(md.matches("^homework_task_[0-9]+$")) {
	homeworkTaskId = m.parseInt(md.substring(14));
	DataSet tinfo = homeworkTask.find("id = " + homeworkTaskId + " AND site_id = " + siteId + " AND status = 1");
	if(!tinfo.next()) {
		out.print("{\"success\":false, \"error\":\"추가 과제 정보를 찾을 수 없습니다.\", \"reset\":true}");
		return;
	}
	homeworkId = tinfo.i("homework_id");
}

if(homeworkId > 0) {
	DataSet hinfo = homework.find("id = " + homeworkId + " AND site_id = " + siteId + " AND status != -1");
	if(!hinfo.next()) {
		out.print("{\"success\":false, \"error\":\"과제 정보를 찾을 수 없습니다.\", \"reset\":true}");
		return;
	}

	submitFileExtMode = homework.normalizeSubmitFileExtMode(hinfo.s("submit_file_ext_mode"));
	if("".equals(submitFileExtMode)) {
		out.print("{\"success\":false, \"error\":\"과제 허용 파일 형식 설정이 올바르지 않습니다.\", \"reset\":true}");
		return;
	}
	submitFileExts = homework.normalizeSubmitFileExts(hinfo.s("submit_file_exts"));
	allowExt = homework.resolveSubmitFileExts(submitFileExtMode, submitFileExts);
	if("".equals(allowExt)) {
		out.print("{\"success\":false, \"error\":\"과제 허용 확장자 설정을 확인해 주세요.\", \"reset\":true}");
		return;
	}

	m.log(
		"homework_upload",
		"resolve module=" + md + ", homework_id=" + homeworkId + ", task_id=" + homeworkTaskId
		+ ", mode=" + submitFileExtMode + ", ext_cnt=" + allowExt.split("\\|").length
		+ ", user_id=" + userId + ", site_id=" + siteId
	);
}

//폼체크
f.addElement("filename", "", "hname:'파일', required:'Y', allow:'" + allowExt + "'");

//업로드
if(m.isPost()) {

	//제한-파일유형
	if(!f.validate()) { out.print("{\"success\":false, \"error\":\"" + f.errMsg + "\", \"reset\":true}"); return; }
	
	//제한-파일크기
	if((maxPostSize * 1024 * 1024) < f.getLong("filesize")) { out.print("{\"success\":false, \"error\":\"100MB를 초과하여 업로드 할 수 없습니다.\", \"reset\":true}"); return; }

	//등록
	File attFile = f.saveFile("filename");
	if(attFile == null) { out.print("{\"success\":false, \"error\":\"파일이 정상적으로 업로드되지 않았습니다.\", \"reset\":true}"); return; }

	file.item("module", md);
	file.item("module_id", mid);
	file.item("site_id", siteId);
	file.item("file_nm", f.getFileName("filename"));
	file.item("filename", f.getFileName("filename"));
	file.item("filetype", f.getFileType("filename"));
	file.item("filesize", attFile.length());
	file.item("realname", attFile.getName());
	file.item("main_yn", "N");
	file.item("reg_date", m.time("yyyyMMddHHmmss"));
	file.item("status", 1);
	file.insert();

	//파일리사이징
	try {
		if(f.getFileName("filename").matches("(?i)^.+\\.(jpg|jpeg|png|gif|bmp)$")) {
			if(300 * 1024 < attFile.length()) { //300KB
				String imgPath = m.getUploadPath(f.getFileName("filename"));
				String cmd = "convert -resize 1100x> " + imgPath + " " + imgPath;
				Runtime.getRuntime().exec(cmd);

			}
		}
	}
	catch(RuntimeException re) { m.errorLog("RuntimeException : " + re.getMessage(), re); }
	catch(Exception e) { m.errorLog("Exception : " + e.getMessage(), e); }
	out.print("{\"success\":true}");
	return;
}

//제한확장자-NFUploader deprecated
String limitExt = "swf;mp4;flv;mov;qt;mpeg;wmv;wma;asf;mp3;avi;wmp;rmp;ra;exe;jsp;asp;aspx;php;php3;html";
String limitExtConv = m.replace(limitExt, ";", ", ");
limitExt += ";" + limitExt.toUpperCase();

//출력
//p.setRoot(Config.getDocRoot() + "/sysop/html");
p.setLayout("blank");
p.setBody("classroom.file_upload");
p.setVar("p_title", "파일 첨부");
p.setVar("md", md);
p.setVar("mid", mid);
p.setVar("web_url", webUrl);
p.setVar("max_file_size", maxPostSize * 1024);

//NFUploader deprecated
p.setVar("limit_block", !"".equals(limitExt));
p.setVar("limit_ext", limitExt);
p.setVar("limit_ext_conv", limitExtConv);

p.display();

%>
