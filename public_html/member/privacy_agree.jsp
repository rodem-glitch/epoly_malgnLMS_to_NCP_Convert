<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//기본키
int id = m.ri("id");
if(id == 0) { m.jsError(_message.get("alert.common.required_key")); return; }

//동의 모드
// 왜: "SSO 첫 방문", "증명서 발급"은 기존 회원가입/개인정보활용동의 흐름과 목적이 달라서,
//     화면은 재사용하되(새로 만들지 않기) 저장/이동 로직만 분기합니다.
String ag = m.rs("ag"); // sso|cert (그 외는 기존 동의 화면)
boolean consentMode = "sso".equals(ag) || "cert".equals(ag);
int moduleId = m.ri("mid"); // 증명서 발급 등에서 기준키를 남기기 위한 값(선택)
String returl = m.rs("returl");
returl = returl.replaceAll("(\r\n|\r|\n|\n\r)", ""); // 왜: 헤더/스크립트 인젝션을 막기 위해 줄바꿈 제거
if(!"".equals(returl) && (!returl.startsWith("/") || -1 < returl.indexOf("://"))) returl = ""; // 외부 URL 차단

//객체
UserDao user = new UserDao();
MailDao mail = new MailDao();
WebpageDao webpage = new WebpageDao();
AgreementLogDao agreementLog = new AgreementLogDao(p, siteId);

//제한
String ek = m.encrypt("PRIVACY_" + id + "_AGREE_" + m.time("yyyyMMdd"));
if(!ek.equals(m.rs("ek"))) {
	m.jsError(_message.get("alert.common.abnormal_access"));
	return;
}

//정보
DataSet info = user.find("id = " + id + " AND site_id = " + siteId + " AND status = 1");
if(!info.next()) {
	m.jsError(_message.get("alert.member.nodata"));
	return;
}

if(!consentMode) {
	//개인정보활용에 동의함 | (SSO 사용 & SSO 개인정보약관 사용안함)
	if("Y".equals(info.s("privacy_yn"))
		|| (siteinfo.b("sso_yn")
		&& !siteinfo.b("sso_privacy_yn"))
	) {
		m.redirect("/main/index.jsp");
		return;
	}
}

//정보-사이트설정
DataSet siteconfig = SiteConfig.getArr(new String[] {"join_", "modify_", "user_etc_"});
boolean joinBlock = "Y".equals(siteconfig.s("join_config_yn"));

//폼체크
if(consentMode) {
	// 왜: sso/cert 동의는 별도의 수신동의(email/sms)와 무관하므로, 필수 동의 1개만 받습니다.
	f.addElement("agree_yn2", null, "hname:'개인정보 수집·이용 및 제공 동의', required:'Y'");
} else {
	f.addElement("agree_yn1", null, "hname:'이용약관', required:'Y'");
	f.addElement("agree_yn2", null, "hname:'개인정보 수집 및 이용', required:'Y'");
	f.addElement("email_yn", "Y", "hname:'이메일수신동의'");
	f.addElement("sms_yn", "Y", "hname:'SMS수신동의'");
	if(joinBlock) {

	}
}

//처리
if(m.isPost() && f.validate()) {

	if(consentMode) {
		//왜: returl이 없으면 정상 흐름(첫 방문/발급 화면)으로 복귀할 수 없으므로 차단합니다.
		if("".equals(returl)) { m.jsError("정상적인 경로로 접근해 주세요."); return; }

		String consentVer = "20260120";
		String logType = "sso".equals(ag) ? "sso" : "cert";
		String module = logType + "_" + consentVer;

		//중복 동의 방지(로그 최소화)
		boolean alreadyAgreed = "Y".equals(agreementLog.getOne(
			"SELECT agreement_yn FROM " + agreementLog.table
			+ " WHERE user_id = " + id
			+ " AND type = '" + logType + "'"
			+ " AND module = '" + module + "'"
			+ " ORDER BY reg_date DESC"
		));
		if(!alreadyAgreed) {
			//SSO 첫 방문 동의는 개인정보활용 동의(privacy_yn)도 함께 올려두는게 운영상 안전합니다.
			if("sso".equals(ag) && !"Y".equals(info.s("privacy_yn"))) {
				user.item("privacy_yn", "Y");
				if(!user.update("id = " + id + " AND site_id = " + siteId + " AND status = 1")) {
					m.jsAlert("수정하는 중 오류가 발생했습니다.");
					return;
				}
			}

			agreementLog.insertLog(siteinfo, info, logType, "Y", module, moduleId);
			m.log("agreement_" + siteId, "user_id=" + id + " type=" + logType + " module=" + module + " module_id=" + moduleId);
		}

		m.jsAlert("동의 처리가 완료되었습니다.");
		m.jsReplace(returl, "parent");
		return;
	} else {
		//변수
		String emailYn = f.get("email_yn", "N");
		String smsYn = f.get("sms_yn", "N");

		//수정
		user.item("email_yn", emailYn);
		user.item("sms_yn", smsYn);
		user.item("privacy_yn", "Y");
		if(!user.update("id = " + id + " AND site_id = " + siteId + " AND status = 1")) {
			m.jsAlert("수정하는 중 오류가 발생했습니다.");
			return;
		}
		if(joinBlock) {
			/*
			f.addElement("user_nm", null, "hname:'성명', required:'Y'");
			if(0 < siteconfig.i("join_dept_status")) f.addElement("dept_id", null, "hname:'회원소속', required:'Y'");
			if(0 < siteconfig.i("join_birthday_status")) f.addElement("birthday_year", null, "hname:'생년월일(년도)'" + (1 < siteconfig.i("join_birthday_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_birthday_status")) f.addElement("birthday_month", null, "hname:'생년월일(월)'" + (1 < siteconfig.i("join_birthday_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_birthday_status")) f.addElement("birthday_day", null, "hname:'생년월일(일)'" + (1 < siteconfig.i("join_birthday_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_gender_status")) f.addElement("gender", null, "hname:'성별', required:'Y'");
			if(0 < siteconfig.i("join_email_status")) f.addElement("email1", null, "hname:'이메일', glue:'email2'" + (1 < siteconfig.i("join_email_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_email_status")) f.addElement("email2", null, "hname:'이메일'" + (1 < siteconfig.i("join_email_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_mobile_status")) f.addElement("mobile1", null, "hname:'휴대폰번호'" + (1 < siteconfig.i("join_mobile_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_mobile_status")) f.addElement("mobile2", null, "hname:'휴대폰번호'" + (1 < siteconfig.i("join_mobile_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_mobile_status")) f.addElement("mobile3", null, "hname:'휴대폰번호'" + (1 < siteconfig.i("join_mobile_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_addr_status")) f.addElement("zipcode", null, "hname:'우편번호'" + (1 < siteconfig.i("join_addr_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_addr_status")) f.addElement("addr", null, "hname:'구 주소'");
			if(0 < siteconfig.i("join_addr_status")) f.addElement("new_addr", null, "hname:'도로명 주소'" + (1 < siteconfig.i("join_addr_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_addr_status")) f.addElement("addr_dtl", null, "hname:'상세주소'");
			if(0 < siteconfig.i("join_etc1_status")) f.addElement("etc1", null, "hname:'" + siteconfig.s("user_etc1_nm") + "'" + (1 < siteconfig.i("join_etc1_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_etc2_status")) f.addElement("etc2", null, "hname:'" + siteconfig.s("user_etc2_nm") + "'" + (1 < siteconfig.i("join_etc2_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_etc3_status")) f.addElement("etc3", null, "hname:'" + siteconfig.s("user_etc3_nm") + "'" + (1 < siteconfig.i("join_etc3_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_etc4_status")) f.addElement("etc4", null, "hname:'" + siteconfig.s("user_etc4_nm") + "'" + (1 < siteconfig.i("join_etc4_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_etc5_status")) f.addElement("etc5", null, "hname:'" + siteconfig.s("user_etc5_nm") + "'" + (1 < siteconfig.i("join_etc5_status") ? ", required:'Y'" : ""));
			if(0 < siteconfig.i("join_email_yn_status")) f.addElement("email_yn", "Y", "hname:'이메일수신동의'");
			if(0 < siteconfig.i("join_sms_yn_status")) f.addElement("sms_yn", "Y", "hname:'SMS수신동의'");
			*/
		}

		//동의기록
		agreementLog.insertLog(siteinfo, info, "email", emailYn, "privacy_agree");
		agreementLog.insertLog(siteinfo, info, "sms", smsYn, "privacy_agree");
		agreementLog.insertLog(siteinfo, info, "privacy", "Y", "privacy_agree");

		//메일-이메일동의여부
		p.setVar("type", m.getValue("email", agreementLog.typesMsg));
		p.setVar("agreement_yn", m.getValue(emailYn, agreementLog.receiveYnMsg));
		p.setVar("reg_date", m.time(_message.get("format.date.local")));
		mail.send(siteinfo, info, "receive", p);

		//메일-SMS동의여부
		p.setVar("type", m.getValue("sms", agreementLog.typesMsg));
		p.setVar("agreement_yn", m.getValue(smsYn, agreementLog.receiveYnMsg));
		p.setVar("reg_date", m.time(_message.get("format.date.local")));
		mail.send(siteinfo, info, "receive", p);

		//이동
		m.jsAlert("처리가 완료되었습니다. 다시 로그인 해 주세요.");
		m.jsReplace("/member/login.jsp", "parent");
		return;
	}
}

//출력
p.setLayout(ch);
p.setBody("member.privacy_agree");
p.setVar("form_script", f.getScript());

//동의 문구
// 왜: 동의서(버전)는 배포 없이 운영에서 관리할 수 있어야 하므로, DB(TB_WEBPAGE) 컨텐츠로 관리합니다.
String consentContent = "";
String consentTitle = "";
String consentBoxTitle = "";
if(consentMode) {
	String consentCode = "consent_" + ag + "_20260120";
	consentContent = webpage.getOne(
		"SELECT content FROM " + webpage.table
		+ " WHERE code = '" + consentCode + "'"
		+ " AND site_id = " + siteId
		+ " AND status = 1"
	);
	if("".equals(consentContent)) {
		m.errorLog("privacy_agree_missing_webpage: site_id=" + siteId + " code=" + consentCode);
		m.jsError("동의서 내용이 등록되어 있지 않습니다. 관리자에게 문의해 주세요.");
		return;
	}

	consentTitle = "sso".equals(ag) ? "SSO 첫 방문 동의" : "증명서 발급 동의";
	consentBoxTitle = "개인정보 수집·이용 및 제공 동의";
} else {
	p.setVar("clause", webpage.getOne("SELECT content FROM " + webpage.table + " WHERE code = 'clause' AND site_id = " + siteId + " AND status = 1"));
}
p.setLoop("receive_yn", m.arr2loop(user.receiveYn));

p.setVar("consent_mode", consentMode);
p.setVar("ag", ag);
p.setVar("consent_title", consentTitle);
p.setVar("consent_box_title", consentBoxTitle);
p.setVar("consent_content", consentContent);

p.setVar("join_block", joinBlock);
p.display();

%>
